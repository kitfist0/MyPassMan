package my.passman.ui.screens.settings

import android.app.PendingIntent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import my.passman.R
import my.passman.data.AppTheme
import my.passman.data.RecordDao
import my.passman.data.SettingsRepository
import my.passman.data.SortOrder
import my.passman.data.SyncProvider
import my.passman.sync.SyncResult
import my.passman.sync.SyncScheduler
import my.passman.sync.google.GoogleDriveAuthResult
import my.passman.sync.google.GoogleSyncManager
import my.passman.sync.yandex.YandexAuthManager
import my.passman.sync.yandex.YandexAuthResult
import my.passman.sync.yandex.YandexSyncManager
import my.passman.ui.AppEvent
import my.passman.ui.AppEventBus
import my.passman.ui.screens.settings.SettingsViewModel.SettingsEvent
import my.passman.ui.textOf
import my.passman.util.BackupManager
import my.passman.util.BiometricAvailability
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import javax.crypto.AEADBadTagException

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val sortOrder = MutableStateFlow(SortOrder.BY_NAME)
    private val appTheme = MutableStateFlow(AppTheme.SYSTEM)
    private val dynamicColor = MutableStateFlow(false)
    private val pinHash = MutableStateFlow<String?>(null)
    private val fingerprintEnabled = MutableStateFlow(false)
    private val hasShownPinHint = MutableStateFlow(false)
    private val hasRecords = MutableStateFlow(true)
    private val syncProvider = MutableStateFlow(SyncProvider.NONE)
    private val lastSyncedAt = MutableStateFlow<Long?>(null)

    private val settingsRepository =
        mockk<SettingsRepository>(relaxUnitFun = true) {
            every { sortOrder } returns this@SettingsViewModelTest.sortOrder
            every { appTheme } returns this@SettingsViewModelTest.appTheme
            every { dynamicColor } returns this@SettingsViewModelTest.dynamicColor
            every { pinHash } returns this@SettingsViewModelTest.pinHash
            every { fingerprintEnabled } returns this@SettingsViewModelTest.fingerprintEnabled
            every { hasShownPinHint } returns this@SettingsViewModelTest.hasShownPinHint
            every { syncProvider } returns this@SettingsViewModelTest.syncProvider
            every { lastSyncedAt } returns this@SettingsViewModelTest.lastSyncedAt
            coEvery { setSyncProvider(any()) } answers { this@SettingsViewModelTest.syncProvider.value = firstArg() }
        }
    private val recordDao = mockk<RecordDao> { every { hasRecords() } returns hasRecords }
    private val backupManager = mockk<BackupManager>(relaxUnitFun = true)
    private val googleSyncManager = mockk<GoogleSyncManager>(relaxUnitFun = true)
    private val yandexSyncManager = mockk<YandexSyncManager>()
    private val yandexAuthManager =
        mockk<YandexAuthManager>(relaxUnitFun = true) {
            every { authorizeUrl } returns "https://oauth.example/authorize"
            every { redirectUri } returns "passman://yandex"
        }
    private val syncScheduler = mockk<SyncScheduler>(relaxUnitFun = true)
    private val biometricAvailability = mockk<BiometricAvailability> { every { isAvailable() } returns true }
    private val eventBus = AppEventBus()

    private val consentIntent = mockk<PendingIntent>()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Creates the view model and keeps [SettingsViewModel.uiState] subscribed, as the screen does. */
    private fun TestScope.viewModel(): SettingsViewModel {
        val vm =
            SettingsViewModel(
                settingsRepository,
                backupManager,
                googleSyncManager,
                yandexSyncManager,
                yandexAuthManager,
                syncScheduler,
                biometricAvailability,
                eventBus,
                recordDao,
            )
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        return vm
    }

    private val SettingsViewModel.state get() = uiState.value

    private fun <T> ReceiveChannel<T>.drain(): List<T> = generateSequence { tryReceive().getOrNull() }.toList()

    private fun toasts() = eventBus.events.drain().map { (it as AppEvent.ShowToast).text }

    private fun toast(
        resId: Int,
        vararg args: Any,
    ) = listOf(textOf(resId, *args))

    @Test
    fun state_reflectsRepository() =
        runTest {
            val vm = viewModel()

            sortOrder.value = SortOrder.BY_MODIFIED
            appTheme.value = AppTheme.DARK
            dynamicColor.value = true
            pinHash.value = "hash"
            fingerprintEnabled.value = true
            syncProvider.value = SyncProvider.YANDEX_DISK
            lastSyncedAt.value = 42
            advanceUntilIdle()

            with(vm.state) {
                assertEquals(SortOrder.BY_MODIFIED, sortOrder)
                assertEquals(AppTheme.DARK, theme)
                assertTrue(isDynamicColorEnabled)
                assertTrue(isPinEnabled)
                assertTrue(isFingerprintEnabled)
                assertEquals(SyncProvider.YANDEX_DISK, syncProvider)
                assertEquals(42L, lastSyncedAt)
            }
        }

    @Test
    fun exposesFingerprintAvailabilityAndYandexEndpoints() =
        runTest {
            every { biometricAvailability.isAvailable() } returns false
            val vm = viewModel()

            assertFalse(vm.isFingerprintAvailable)
            assertEquals("https://oauth.example/authorize", vm.yandexAuthorizeUrl)
            assertEquals("passman://yandex", vm.yandexRedirectUri)
        }

    @Test
    fun settersDelegateToRepository() =
        runTest {
            val vm = viewModel()

            vm.onSortOrderChange(SortOrder.BY_CREATED)
            vm.onThemeChange(AppTheme.LIGHT)
            vm.setDynamicColor(true)
            vm.setFingerprintEnabled(true)
            vm.dismissPinHint()
            advanceUntilIdle()

            coVerify {
                settingsRepository.setSortOrder(SortOrder.BY_CREATED)
                settingsRepository.setAppTheme(AppTheme.LIGHT)
                settingsRepository.setDynamicColor(true)
                settingsRepository.setFingerprintEnabled(true)
                settingsRepository.setHasShownPinHint()
            }
        }

    @Test
    fun simpleDialogs_canBeShownAndDismissed() =
        runTest {
            val vm = viewModel()
            val dialogs =
                listOf(
                    Triple(vm::showSortDialog, vm::dismissSortDialog, SettingsScreenState::showSortDialog),
                    Triple(vm::showThemeDialog, vm::dismissThemeDialog, SettingsScreenState::showThemeDialog),
                    Triple(vm::showAboutDialog, vm::dismissAboutDialog, SettingsScreenState::showAboutDialog),
                    Triple(vm::showDisablePinDialog, vm::dismissDisablePinDialog, SettingsScreenState::showDisablePinDialog),
                    Triple(vm::showSyncProviderDialog, vm::dismissSyncProviderDialog, SettingsScreenState::showSyncProviderDialog),
                )

            dialogs.forEach { (show, dismiss, isShown) ->
                show()
                advanceUntilIdle()
                assertTrue(isShown(vm.state))

                dismiss()
                advanceUntilIdle()
                assertFalse(isShown(vm.state))
            }
        }

    @Nested
    inner class Pin {
        @Test
        fun hint_shownWhenRecordsExistWithoutPin() =
            runTest {
                assertTrue(viewModel().state.showPinHint)
            }

        @Test
        fun hint_hiddenWithPin() =
            runTest {
                pinHash.value = "hash"

                assertFalse(viewModel().state.showPinHint)
            }

        @Test
        fun hint_hiddenOnceSeen() =
            runTest {
                hasShownPinHint.value = true

                assertFalse(viewModel().state.showPinHint)
            }

        @Test
        fun hint_hiddenWithoutRecords() =
            runTest {
                hasRecords.value = false

                assertFalse(viewModel().state.showPinHint)
            }

        @Test
        fun confirmDisablePin_closesDialogAndClearsPin() =
            runTest {
                val vm = viewModel()
                vm.showDisablePinDialog()

                vm.confirmDisablePin()
                advanceUntilIdle()

                assertFalse(vm.state.showDisablePinDialog)
                coVerify(exactly = 1) { settingsRepository.clearPin() }
            }
    }

    @Nested
    inner class Backup {
        @Test
        fun export_asksPasswordThenFileThenWrites() =
            runTest {
                val vm = viewModel()

                vm.onExportClick()
                advanceUntilIdle()
                assertTrue(vm.state.showBackupPasswordDialog)
                assertEquals(BackupMode.EXPORT, vm.state.backupMode)

                vm.onBackupPasswordEntered("pw")
                advanceUntilIdle()
                assertFalse(vm.state.showBackupPasswordDialog)
                assertNull(vm.state.backupMode)
                assertEquals(listOf(SettingsEvent.RequestExportFile), vm.events.drain())

                val output = ByteArrayOutputStream()
                vm.executeExport(output)
                advanceUntilIdle()

                coVerify { backupManager.exportDatabase(output, "pw".toCharArray()) }
                assertEquals(toast(R.string.export_success), toasts())
            }

        @Test
        fun export_failure_showsErrorAndForgetsPassword() =
            runTest {
                coEvery { backupManager.exportDatabase(any(), any()) } throws IOException("disk full")
                val vm = viewModel()
                vm.onExportClick()
                vm.onBackupPasswordEntered("pw")
                advanceUntilIdle()

                vm.executeExport(ByteArrayOutputStream())
                advanceUntilIdle()
                assertEquals(toast(R.string.export_error, "disk full"), toasts())

                vm.executeExport(ByteArrayOutputStream())
                advanceUntilIdle()
                coVerify(exactly = 1) { backupManager.exportDatabase(any(), "pw".toCharArray()) }
                coVerify(exactly = 1) { backupManager.exportDatabase(any(), CharArray(0)) }
            }

        @Test
        fun dismissBackupPasswordDialog_resetsMode() =
            runTest {
                val vm = viewModel()
                vm.onExportClick()

                vm.dismissBackupPasswordDialog()
                advanceUntilIdle()

                assertFalse(vm.state.showBackupPasswordDialog)
                assertNull(vm.state.backupMode)
            }

        @Test
        fun passwordWithoutMode_doesNothing() =
            runTest {
                val vm = viewModel()

                vm.onBackupPasswordEntered("pw")
                advanceUntilIdle()

                assertTrue(vm.events.drain().isEmpty())
                coVerify(exactly = 0) { backupManager.importDatabase(any<ByteArray>(), any()) }
            }

        @Test
        fun onImportClick_requestsFile() =
            runTest {
                val vm = viewModel()

                vm.onImportClick()
                advanceUntilIdle()

                assertEquals(listOf(SettingsEvent.RequestImportFile), vm.events.drain())
            }

        private fun TestScope.importWithPassword(vm: SettingsViewModel) {
            vm.onImportFileSelected(ByteArrayInputStream(byteArrayOf(1, 2, 3)))
            advanceUntilIdle()
            assertTrue(vm.state.showBackupPasswordDialog)
            assertEquals(BackupMode.IMPORT, vm.state.backupMode)

            vm.onBackupPasswordEntered("pw")
            advanceUntilIdle()
        }

        @Test
        fun import_readsFileAsksPasswordAndRestores() =
            runTest {
                val vm = viewModel()

                importWithPassword(vm)

                coVerify { backupManager.importDatabase(byteArrayOf(1, 2, 3), "pw".toCharArray()) }
                assertEquals(toast(R.string.import_success), toasts())
            }

        @Test
        fun import_wrongPassword_showsDedicatedError() =
            runTest {
                coEvery { backupManager.importDatabase(any<ByteArray>(), any()) } throws AEADBadTagException()
                val vm = viewModel()

                importWithPassword(vm)

                assertEquals(toast(R.string.import_error_wrong_password), toasts())
            }

        @Test
        fun import_otherFailure_showsMessage() =
            runTest {
                coEvery { backupManager.importDatabase(any<ByteArray>(), any()) } throws IllegalArgumentException("not a backup")
                val vm = viewModel()

                importWithPassword(vm)

                assertEquals(toast(R.string.import_error, "not a backup"), toasts())
            }

        @Test
        fun import_unreadableFile_showsErrorWithoutPasswordDialog() =
            runTest {
                val broken =
                    object : InputStream() {
                        override fun read(): Int = throw IOException("permission denied")
                    }
                val vm = viewModel()

                vm.onImportFileSelected(broken)
                advanceUntilIdle()

                assertFalse(vm.state.showBackupPasswordDialog)
                assertEquals(toast(R.string.read_file_error, "permission denied"), toasts())
            }
    }

    @Nested
    inner class SyncSetup {
        @Test
        fun selectingProvider_closesProviderDialog() =
            runTest {
                coEvery { googleSyncManager.authorize() } returns GoogleDriveAuthResult.Authorized("token")
                val vm = viewModel()
                vm.showSyncProviderDialog()

                vm.onSyncProviderSelected(SyncProvider.GOOGLE_DRIVE)
                advanceUntilIdle()

                assertFalse(vm.state.showSyncProviderDialog)
            }

        @Test
        fun google_authorized_asksPassphraseThenEnablesAndSyncs() =
            runTest {
                coEvery { googleSyncManager.authorize() } returns GoogleDriveAuthResult.Authorized("token")
                coEvery { googleSyncManager.sync() } returns SyncResult.Uploaded
                val vm = viewModel()

                vm.onSyncProviderSelected(SyncProvider.GOOGLE_DRIVE)
                advanceUntilIdle()
                assertTrue(vm.state.showSyncPassphraseDialog)

                vm.enableSync("phrase")
                advanceUntilIdle()

                assertFalse(vm.state.showSyncPassphraseDialog)
                coVerify {
                    settingsRepository.setSyncPassphrase("phrase")
                    settingsRepository.setSyncProvider(SyncProvider.GOOGLE_DRIVE)
                    syncScheduler.enablePeriodicSync()
                    googleSyncManager.sync()
                }
                assertEquals(toast(R.string.sync_uploaded), toasts())
            }

        @Test
        fun google_consentRequired_thenGranted_asksPassphrase() =
            runTest {
                coEvery { googleSyncManager.authorize() } returns GoogleDriveAuthResult.ConsentRequired(consentIntent)
                val vm = viewModel()

                vm.onSyncProviderSelected(SyncProvider.GOOGLE_DRIVE)
                advanceUntilIdle()
                assertEquals(listOf(SettingsEvent.RequestDriveConsent(consentIntent)), vm.events.drain())
                assertFalse(vm.state.showSyncPassphraseDialog)

                vm.onDriveConsentResult(granted = true)
                advanceUntilIdle()

                assertTrue(vm.state.showSyncPassphraseDialog)
                coVerify(exactly = 0) { googleSyncManager.sync() }
            }

        @Test
        fun google_consentDenied_abortsSetup() =
            runTest {
                coEvery { googleSyncManager.authorize() } returns GoogleDriveAuthResult.ConsentRequired(consentIntent)
                val vm = viewModel()
                vm.onSyncProviderSelected(SyncProvider.GOOGLE_DRIVE)
                advanceUntilIdle()

                vm.onDriveConsentResult(granted = false)
                advanceUntilIdle()

                assertFalse(vm.state.showSyncPassphraseDialog)
                assertEquals(toast(R.string.google_drive_access_denied), toasts())
                coVerify(exactly = 0) { googleSyncManager.sync() }
            }

        @Test
        fun google_authFailed_showsError() =
            runTest {
                coEvery { googleSyncManager.authorize() } returns GoogleDriveAuthResult.Failed("no network")
                val vm = viewModel()

                vm.onSyncProviderSelected(SyncProvider.GOOGLE_DRIVE)
                advanceUntilIdle()

                assertFalse(vm.state.showSyncPassphraseDialog)
                assertEquals(toast(R.string.google_sign_in_failed, "no network"), toasts())
            }

        @Test
        fun yandex_authorized_asksPassphrase() =
            runTest {
                coEvery { yandexAuthManager.authorize() } returns YandexAuthResult.Authorized("token")
                val vm = viewModel()

                vm.onSyncProviderSelected(SyncProvider.YANDEX_DISK)
                advanceUntilIdle()

                assertTrue(vm.state.showSyncPassphraseDialog)
            }

        @Test
        fun yandex_loginRequired_thenToken_asksPassphrase() =
            runTest {
                coEvery { yandexAuthManager.authorize() } returns YandexAuthResult.LoginRequired
                val vm = viewModel()

                vm.onSyncProviderSelected(SyncProvider.YANDEX_DISK)
                advanceUntilIdle()
                assertTrue(vm.state.showYandexLoginDialog)

                vm.onYandexTokenReceived("token")
                advanceUntilIdle()

                assertFalse(vm.state.showYandexLoginDialog)
                assertTrue(vm.state.showSyncPassphraseDialog)
                coVerify { yandexAuthManager.saveToken("token") }
                coVerify(exactly = 0) { yandexSyncManager.sync() }
            }

        @Test
        fun yandex_loginDismissed_abortsSelection() =
            runTest {
                coEvery { yandexAuthManager.authorize() } returns YandexAuthResult.LoginRequired
                val vm = viewModel()
                vm.onSyncProviderSelected(SyncProvider.YANDEX_DISK)
                advanceUntilIdle()

                vm.dismissYandexLoginDialog()
                advanceUntilIdle()

                assertFalse(vm.state.showYandexLoginDialog)
                assertFalse(vm.state.showSyncPassphraseDialog)
                verify(exactly = 0) { syncScheduler.disablePeriodicSync() }
            }

        @Test
        fun dismissSyncPassphraseDialog_hidesIt() =
            runTest {
                coEvery { yandexAuthManager.authorize() } returns YandexAuthResult.Authorized("token")
                val vm = viewModel()
                vm.onSyncProviderSelected(SyncProvider.YANDEX_DISK)
                advanceUntilIdle()

                vm.dismissSyncPassphraseDialog()
                advanceUntilIdle()

                assertFalse(vm.state.showSyncPassphraseDialog)
            }

        @Test
        fun syncFailureDuringSetup_rollsBackSync() =
            runTest {
                coEvery { googleSyncManager.authorize() } returns GoogleDriveAuthResult.Authorized("token")
                coEvery { googleSyncManager.sync() } returns SyncResult.Failed("boom")
                val vm = viewModel()
                vm.onSyncProviderSelected(SyncProvider.GOOGLE_DRIVE)
                advanceUntilIdle()

                vm.enableSync("phrase")
                advanceUntilIdle()

                verify { syncScheduler.disablePeriodicSync() }
                coVerify { settingsRepository.clearSyncState() }
                assertEquals(toast(R.string.sync_error, "boom"), toasts())
            }

        @Test
        fun wrongPassphraseDuringSetup_dismissingResetRollsBackSync() =
            runTest {
                coEvery { googleSyncManager.authorize() } returns GoogleDriveAuthResult.Authorized("token")
                coEvery { googleSyncManager.sync() } returns SyncResult.InvalidPassphrase
                val vm = viewModel()
                vm.onSyncProviderSelected(SyncProvider.GOOGLE_DRIVE)
                advanceUntilIdle()
                vm.enableSync("phrase")
                advanceUntilIdle()
                assertTrue(vm.state.showResetBackupDialog)

                vm.dismissResetBackupDialog()
                advanceUntilIdle()

                assertFalse(vm.state.showResetBackupDialog)
                verify { syncScheduler.disablePeriodicSync() }
                coVerify { settingsRepository.clearSyncState() }
            }
    }

    @Nested
    inner class DisableSync {
        @Test
        fun fromGoogle_signsOutAndClearsState() =
            runTest {
                syncProvider.value = SyncProvider.GOOGLE_DRIVE
                val vm = viewModel()

                vm.onSyncProviderSelected(SyncProvider.NONE)
                advanceUntilIdle()

                coVerify { googleSyncManager.signOut() }
                verify { syncScheduler.disablePeriodicSync() }
                coVerify { settingsRepository.clearSyncState() }
            }

        @Test
        fun fromYandex_signsOutAndClearsState() =
            runTest {
                syncProvider.value = SyncProvider.YANDEX_DISK
                val vm = viewModel()

                vm.disableSync()
                advanceUntilIdle()

                coVerify { yandexAuthManager.signOut() }
                verify { syncScheduler.disablePeriodicSync() }
                coVerify { settingsRepository.clearSyncState() }
            }

        @Test
        fun whenNotEnabled_onlyClearsState() =
            runTest {
                val vm = viewModel()

                vm.disableSync()
                advanceUntilIdle()

                coVerify(exactly = 0) { googleSyncManager.signOut() }
                coVerify(exactly = 0) { yandexAuthManager.signOut() }
                coVerify { settingsRepository.clearSyncState() }
            }
    }

    @Nested
    inner class SyncNow {
        private fun TestScope.syncWith(result: SyncResult): SettingsViewModel {
            syncProvider.value = SyncProvider.YANDEX_DISK
            coEvery { yandexSyncManager.sync() } returns result
            return viewModel().also {
                it.syncNow()
                advanceUntilIdle()
            }
        }

        @Test
        fun downloaded_showsToast() =
            runTest {
                syncWith(SyncResult.Downloaded)

                assertEquals(toast(R.string.sync_downloaded), toasts())
            }

        @Test
        fun upToDate_showsToast() =
            runTest {
                syncWith(SyncResult.UpToDate)

                assertEquals(toast(R.string.sync_up_to_date), toasts())
            }

        @Test
        fun usesGoogleWhenSelected() =
            runTest {
                syncProvider.value = SyncProvider.GOOGLE_DRIVE
                coEvery { googleSyncManager.sync() } returns SyncResult.Uploaded
                val vm = viewModel()

                vm.syncNow()
                advanceUntilIdle()

                assertEquals(toast(R.string.sync_uploaded), toasts())
            }

        @Test
        fun withoutProvider_doesNothing() =
            runTest {
                val vm = viewModel()

                vm.syncNow()
                advanceUntilIdle()

                assertTrue(toasts().isEmpty())
                coVerify(exactly = 0) { googleSyncManager.sync() }
                coVerify(exactly = 0) { yandexSyncManager.sync() }
            }

        @Test
        fun invalidPassphrase_offersReset() =
            runTest {
                assertTrue(syncWith(SyncResult.InvalidPassphrase).state.showResetBackupDialog)
            }

        @Test
        fun loginRequired_showsYandexLogin() =
            runTest {
                assertTrue(syncWith(SyncResult.LoginRequired).state.showYandexLoginDialog)
            }

        @Test
        fun yandexTokenOutsideSetup_retriesSync() =
            runTest {
                val vm = syncWith(SyncResult.LoginRequired)
                coEvery { yandexSyncManager.sync() } returns SyncResult.Uploaded

                vm.onYandexTokenReceived("token")
                advanceUntilIdle()

                assertFalse(vm.state.showSyncPassphraseDialog)
                assertEquals(toast(R.string.sync_uploaded), toasts())
            }

        @Test
        fun consentRequired_requestsConsentAndRetriesWhenGranted() =
            runTest {
                val vm = syncWith(SyncResult.ConsentRequired(consentIntent))
                assertEquals(listOf(SettingsEvent.RequestDriveConsent(consentIntent)), vm.events.drain())
                coEvery { yandexSyncManager.sync() } returns SyncResult.Downloaded

                vm.onDriveConsentResult(granted = true)
                advanceUntilIdle()

                assertEquals(toast(R.string.sync_downloaded), toasts())
            }

        @Test
        fun consentDeniedOutsideSetup_keepsSyncEnabled() =
            runTest {
                val vm = syncWith(SyncResult.ConsentRequired(consentIntent))

                vm.onDriveConsentResult(granted = false)
                advanceUntilIdle()

                assertEquals(toast(R.string.google_drive_access_denied), toasts())
                coVerify(exactly = 0) { settingsRepository.clearSyncState() }
            }

        @Test
        fun failureOutsideSetup_keepsSyncEnabled() =
            runTest {
                syncWith(SyncResult.Failed("timeout"))

                assertEquals(toast(R.string.sync_error, "timeout"), toasts())
                verify(exactly = 0) { syncScheduler.disablePeriodicSync() }
                coVerify(exactly = 0) { settingsRepository.clearSyncState() }
            }
    }

    @Nested
    inner class ResetBackup {
        private fun TestScope.resetWith(
            provider: SyncProvider,
            result: SyncResult,
        ): SettingsViewModel {
            syncProvider.value = provider
            coEvery { googleSyncManager.resetRemoteBackup() } returns result
            coEvery { yandexSyncManager.resetRemoteBackup() } returns result
            return viewModel().also {
                it.confirmResetBackup()
                advanceUntilIdle()
            }
        }

        @Test
        fun google_success_showsToast() =
            runTest {
                val vm = resetWith(SyncProvider.GOOGLE_DRIVE, SyncResult.Uploaded)

                assertFalse(vm.state.showResetBackupDialog)
                coVerify { googleSyncManager.resetRemoteBackup() }
                assertEquals(toast(R.string.reset_backup_success), toasts())
            }

        @Test
        fun yandex_failure_showsError() =
            runTest {
                resetWith(SyncProvider.YANDEX_DISK, SyncResult.Failed("quota"))

                coVerify { yandexSyncManager.resetRemoteBackup() }
                assertEquals(toast(R.string.reset_backup_error, "quota"), toasts())
            }

        @Test
        fun consentRequired_requestsConsent() =
            runTest {
                val vm = resetWith(SyncProvider.GOOGLE_DRIVE, SyncResult.ConsentRequired(consentIntent))

                assertEquals(listOf(SettingsEvent.RequestDriveConsent(consentIntent)), vm.events.drain())
            }

        @Test
        fun loginRequired_showsYandexLogin() =
            runTest {
                assertTrue(resetWith(SyncProvider.YANDEX_DISK, SyncResult.LoginRequired).state.showYandexLoginDialog)
            }

        @Test
        fun withoutProvider_doesNothing() =
            runTest {
                resetWith(SyncProvider.NONE, SyncResult.Uploaded)

                assertTrue(toasts().isEmpty())
                coVerify(exactly = 0) { googleSyncManager.resetRemoteBackup() }
                coVerify(exactly = 0) { yandexSyncManager.resetRemoteBackup() }
            }
    }
}
