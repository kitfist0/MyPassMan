package my.passman.ui

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifySequence
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import my.passman.data.AppTheme
import my.passman.data.SettingsRepository
import my.passman.data.SyncProvider
import my.passman.sync.SyncResult
import my.passman.sync.SyncScheduler
import my.passman.sync.google.GoogleSyncManager
import my.passman.sync.yandex.YandexSyncManager
import my.passman.ui.screens.pin.PinMode
import my.passman.util.AppNightMode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    private val pinHash = MutableStateFlow<String?>(null)
    private val appTheme = MutableStateFlow(AppTheme.SYSTEM)
    private val dynamicColor = MutableStateFlow(false)
    private val syncProvider = MutableStateFlow(SyncProvider.NONE)

    private val settingsRepository =
        mockk<SettingsRepository> {
            every { pinHash } returns this@MainViewModelTest.pinHash
            every { appTheme } returns this@MainViewModelTest.appTheme
            every { dynamicColor } returns this@MainViewModelTest.dynamicColor
            every { syncProvider } returns this@MainViewModelTest.syncProvider
        }
    private val googleSyncManager = mockk<GoogleSyncManager> { coEvery { sync() } returns SyncResult.UpToDate }
    private val yandexSyncManager = mockk<YandexSyncManager> { coEvery { sync() } returns SyncResult.UpToDate }
    private val syncScheduler = mockk<SyncScheduler>(relaxUnitFun = true)
    private val appNightMode = mockk<AppNightMode>(relaxUnitFun = true)
    private val timeSource = TestTimeSource()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(): MainViewModel =
        MainViewModel(
            settingsRepository,
            googleSyncManager,
            yandexSyncManager,
            syncScheduler,
            timeSource,
            appNightMode,
        ).also { advanceUntilIdle() }

    private val MainViewModel.isLocked get() = lockScreen.value != null

    @Test
    fun isLoading_untilPinStateKnown() =
        runTest {
            val pending = MutableSharedFlow<String?>()
            every { settingsRepository.pinHash } returns pending
            val vm = viewModel()
            assertTrue(vm.isLoading.value)

            pending.emit(null)
            advanceUntilIdle()

            assertFalse(vm.isLoading.value)
        }

    @Test
    fun navigateTo_changesCurrentScreen() =
        runTest {
            val vm = viewModel()
            assertEquals(Screen.List, vm.currentScreen.value)

            vm.navigateTo(Screen.Tags)

            assertEquals(Screen.Tags, vm.currentScreen.value)
        }

    @Nested
    inner class Lock {
        @Test
        fun withoutPin_startsUnlocked() =
            runTest {
                val vm = viewModel()

                assertFalse(vm.isLocked)
                assertTrue(vm.hasBeenUnlocked.value)
            }

        @Test
        fun withPin_startsLocked() =
            runTest {
                pinHash.value = "hash"

                val vm = viewModel()

                assertEquals(PinMode.UNLOCK, vm.lockScreen.value?.mode)
                assertFalse(vm.hasBeenUnlocked.value)
                assertFalse(vm.isLoading.value)
            }

        @Test
        fun unlock_removesLockAndKeepsScreen() =
            runTest {
                pinHash.value = "hash"
                val vm = viewModel()

                vm.onPinSuccess(PinMode.UNLOCK)

                assertFalse(vm.isLocked)
                assertTrue(vm.hasBeenUnlocked.value)
                assertEquals(Screen.List, vm.currentScreen.value)
            }

        @Test
        fun settingPin_returnsToSettings() =
            runTest {
                val vm = viewModel()
                vm.navigateTo(Screen.Pin(PinMode.SET))

                vm.onPinSuccess(PinMode.CONFIRM)

                assertEquals(Screen.Settings, vm.currentScreen.value)
                assertFalse(vm.isLocked)
            }

        @Test
        fun pinSetWhileRunning_doesNotLock() =
            runTest {
                val vm = viewModel()

                pinHash.value = "hash"
                advanceUntilIdle()

                assertFalse(vm.isLocked)
            }

        @Test
        fun pinChangedAfterUnlock_doesNotRelock() =
            runTest {
                pinHash.value = "hash"
                val vm = viewModel()
                vm.onPinSuccess(PinMode.UNLOCK)

                pinHash.value = "new-hash"
                advanceUntilIdle()

                assertFalse(vm.isLocked)
            }
    }

    @Nested
    inner class AutoLock {
        private fun TestScope.unlockedWithPin(): MainViewModel {
            pinHash.value = "hash"
            return viewModel().also { it.onPinSuccess(PinMode.UNLOCK) }
        }

        @Test
        fun afterTimeoutInBackground_locks() =
            runTest {
                val vm = unlockedWithPin()

                vm.onAppBackgrounded()
                timeSource += 30.seconds
                vm.onAppForegrounded()

                assertEquals(PinMode.UNLOCK, vm.lockScreen.value?.mode)
                assertTrue(vm.hasBeenUnlocked.value, "the vault stays composed under the lock screen")
            }

        @Test
        fun beforeTimeout_staysUnlocked() =
            runTest {
                val vm = unlockedWithPin()

                vm.onAppBackgrounded()
                timeSource += 29.seconds
                vm.onAppForegrounded()

                assertFalse(vm.isLocked)
            }

        @Test
        fun withoutPin_neverLocks() =
            runTest {
                val vm = viewModel()

                vm.onAppBackgrounded()
                timeSource += 1.hours
                vm.onAppForegrounded()

                assertFalse(vm.isLocked)
            }

        @Test
        fun afterPinRemoved_neverLocks() =
            runTest {
                val vm = unlockedWithPin()
                pinHash.value = null
                advanceUntilIdle()

                vm.onAppBackgrounded()
                timeSource += 60.seconds
                vm.onAppForegrounded()

                assertFalse(vm.isLocked)
            }

        @Test
        fun foregroundWithoutBackground_doesNothing() =
            runTest {
                val vm = unlockedWithPin()
                timeSource += 60.seconds

                vm.onAppForegrounded()

                assertFalse(vm.isLocked)
            }

        @Test
        fun backgroundTimeIsMeasuredOncePerVisit() =
            runTest {
                val vm = unlockedWithPin()
                vm.onAppBackgrounded()
                timeSource += 60.seconds
                vm.onAppForegrounded()
                vm.onPinSuccess(PinMode.UNLOCK)

                timeSource += 60.seconds
                vm.onAppForegrounded()

                assertFalse(vm.isLocked)
            }

        @Test
        fun whileAlreadyLocked_keepsSameLockScreen() =
            runTest {
                pinHash.value = "hash"
                val vm = viewModel()
                val lock = vm.lockScreen.value

                vm.onAppBackgrounded()
                timeSource += 60.seconds
                vm.onAppForegrounded()

                assertSame(lock, vm.lockScreen.value, "re-creating it would reset the PIN screen's state")
            }
    }

    @Nested
    inner class Theme {
        @Test
        fun appThemeAndDynamicColor_followSettings() =
            runTest {
                val vm = viewModel()
                backgroundScope.launch { vm.appTheme.collect {} }
                backgroundScope.launch { vm.dynamicColor.collect {} }

                appTheme.value = AppTheme.DARK
                dynamicColor.value = true
                advanceUntilIdle()

                assertEquals(AppTheme.DARK, vm.appTheme.value)
                assertTrue(vm.dynamicColor.value)
            }

        @Test
        fun nightMode_isUpdatedOnlyWhenThemeChanges() =
            runTest {
                every { settingsRepository.appTheme } returns flowOf(AppTheme.LIGHT, AppTheme.LIGHT, AppTheme.DARK)

                viewModel()

                verifySequence {
                    appNightMode.set(AppTheme.LIGHT)
                    appNightMode.set(AppTheme.DARK)
                }
            }
    }

    @Nested
    inner class StartupSync {
        @Test
        fun google_schedulesAndSyncs() =
            runTest {
                syncProvider.value = SyncProvider.GOOGLE_DRIVE

                viewModel()

                verify { syncScheduler.enablePeriodicSync() }
                coVerify(exactly = 1) { googleSyncManager.sync() }
                coVerify(exactly = 0) { yandexSyncManager.sync() }
            }

        @Test
        fun yandex_schedulesAndSyncs() =
            runTest {
                syncProvider.value = SyncProvider.YANDEX_DISK

                viewModel()

                verify { syncScheduler.enablePeriodicSync() }
                coVerify(exactly = 1) { yandexSyncManager.sync() }
                coVerify(exactly = 0) { googleSyncManager.sync() }
            }

        @Test
        fun none_doesNothing() =
            runTest {
                viewModel()

                verify(exactly = 0) { syncScheduler.enablePeriodicSync() }
                coVerify(exactly = 0) { googleSyncManager.sync() }
                coVerify(exactly = 0) { yandexSyncManager.sync() }
            }

        @Test
        fun providerEnabledLater_doesNotSyncAgain() =
            runTest {
                viewModel()

                syncProvider.value = SyncProvider.GOOGLE_DRIVE
                advanceUntilIdle()

                coVerify(exactly = 0) { googleSyncManager.sync() }
            }
    }
}
