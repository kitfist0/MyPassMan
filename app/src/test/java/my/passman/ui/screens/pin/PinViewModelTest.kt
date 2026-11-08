package my.passman.ui.screens.pin

import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import my.passman.data.SettingsRepository
import my.passman.util.BiometricAvailability
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PinViewModelTest {
    private val pinLength = MutableStateFlow(4)
    private val pinHash = MutableStateFlow<String?>("stored-hash")
    private val fingerprintEnabled = MutableStateFlow(false)

    private val settingsRepository =
        mockk<SettingsRepository>(relaxUnitFun = true) {
            every { pinLength } returns this@PinViewModelTest.pinLength
            every { pinHash } returns this@PinViewModelTest.pinHash
            every { fingerprintEnabled } returns this@PinViewModelTest.fingerprintEnabled
            coEvery { verifyPin(any()) } returns false
        }
    private val biometricAvailability =
        mockk<BiometricAvailability> {
            every { isAvailable() } returns true
        }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(mode: PinMode) = PinViewModel(settingsRepository, biometricAvailability, mode)

    private fun PinViewModel.enter(pin: String) = pin.forEach { onDigitClick(it.toString()) }

    @Test
    fun initialState_reflectsMode() {
        PinMode.entries.forEach { mode ->
            val state = viewModel(mode).uiState.value

            assertEquals(mode, state.mode)
            assertEquals("", state.pin)
            assertNull(state.error)
            assertFalse(state.isSuccess)
            assertFalse(state.isValidating)
        }
    }

    @Test
    fun isFingerprintAvailable_mirrorsBiometricAvailability() {
        every { biometricAvailability.isAvailable() } returns false

        assertFalse(viewModel(PinMode.UNLOCK).isFingerprintAvailable)
    }

    @Test
    fun fingerprintEnabled_followsRepository() =
        runTest {
            val vm = viewModel(PinMode.UNLOCK)

            vm.fingerprintEnabled.test {
                assertFalse(awaitItem())
                fingerprintEnabled.value = true
                assertTrue(awaitItem())
            }
        }

    @Test
    fun onFingerprintEnabled_persistsSetting() =
        runTest {
            val vm = viewModel(PinMode.SET)

            vm.onFingerprintEnabled()
            advanceUntilIdle()

            coVerify { settingsRepository.setFingerprintEnabled(true) }
        }

    @Test
    fun onFingerprintUnlockSuccess_marksSuccess() {
        val vm = viewModel(PinMode.UNLOCK)

        vm.onFingerprintUnlockSuccess()

        assertTrue(vm.uiState.value.isSuccess)
    }

    @Nested
    inner class Editing {
        @Test
        fun onDigitClick_appendsDigit() {
            val vm = viewModel(PinMode.SET)

            vm.enter("12")

            assertEquals("12", vm.uiState.value.pin)
        }

        @Test
        fun onDeleteClick_removesLastDigit() {
            val vm = viewModel(PinMode.SET)
            vm.enter("123")

            vm.onDeleteClick()

            assertEquals("12", vm.uiState.value.pin)
        }

        @Test
        fun onDeleteClick_onEmptyPin_keepsState() {
            val vm = viewModel(PinMode.SET)
            val before = vm.uiState.value

            vm.onDeleteClick()

            assertEquals(before, vm.uiState.value)
        }

        @Test
        fun typingAndDeleting_clearError() =
            runTest {
                val vm = viewModel(PinMode.UNLOCK)
                advanceUntilIdle()
                vm.enter("0000")
                advanceUntilIdle()
                assertEquals("invalid", vm.uiState.value.error)

                vm.onDigitClick("1")
                assertNull(vm.uiState.value.error)

                vm.enter("234")
                advanceUntilIdle()
                assertEquals("invalid", vm.uiState.value.error)
                vm.onDigitClick("1")
                vm.onDeleteClick()
                assertNull(vm.uiState.value.error)
            }
    }

    @Nested
    inner class SetMode {
        @Test
        fun onDigitClick_capsPinAtSixDigitsWithoutAutoSubmit() =
            runTest {
                val vm = viewModel(PinMode.SET)

                vm.enter("1234567")
                advanceUntilIdle()

                assertEquals("123456", vm.uiState.value.pin)
                assertEquals(PinMode.SET, vm.uiState.value.mode)
            }

        @Test
        fun onConfirmClick_withFewerThanFourDigits_isIgnored() =
            runTest {
                val vm = viewModel(PinMode.SET)
                vm.enter("123")

                vm.onConfirmClick()
                advanceUntilIdle()

                assertEquals(PinMode.SET, vm.uiState.value.mode)
                assertEquals("123", vm.uiState.value.pin)
            }

        @Test
        fun onConfirmClick_switchesToConfirmWithEnteredLength() =
            runTest {
                val vm = viewModel(PinMode.SET)
                vm.enter("12345")

                vm.onConfirmClick()
                advanceUntilIdle()

                val state = vm.uiState.value
                assertEquals(PinMode.CONFIRM, state.mode)
                assertEquals("", state.pin)
                assertEquals(5, state.expectedLength)
            }

        @Test
        fun setMode_doesNotReadStoredPinLength() =
            runTest {
                pinLength.value = 6
                val vm = viewModel(PinMode.SET)

                advanceUntilIdle()

                assertEquals(4, vm.uiState.value.expectedLength)
            }
    }

    @Nested
    inner class ConfirmFlow {
        private fun TestScope.vmAwaitingConfirmation(pin: String): PinViewModel {
            val vm = viewModel(PinMode.SET)
            vm.enter(pin)
            vm.onConfirmClick()
            advanceUntilIdle()
            return vm
        }

        @Test
        fun matchingPin_isSavedAndSucceeds() =
            runTest {
                val vm = vmAwaitingConfirmation("2580")

                vm.enter("2580")
                advanceUntilIdle()

                assertTrue(vm.uiState.value.isSuccess)
                coVerify(exactly = 1) { settingsRepository.setPin("2580") }
            }

        @Test
        fun mismatchingPin_showsErrorAndIsNotSaved() =
            runTest {
                val vm = vmAwaitingConfirmation("2580")

                vm.enter("1111")
                advanceUntilIdle()

                val state = vm.uiState.value
                assertEquals("mismatch", state.error)
                assertEquals("", state.pin)
                assertEquals(PinMode.CONFIRM, state.mode)
                assertFalse(state.isSuccess)
                coVerify(exactly = 0) { settingsRepository.setPin(any()) }
            }

        @Test
        fun confirmation_autoSubmitsAtFirstPinLength() =
            runTest {
                val vm = vmAwaitingConfirmation("123456")

                vm.enter("12345")
                advanceUntilIdle()
                assertFalse(vm.uiState.value.isSuccess)

                vm.onDigitClick("6")
                advanceUntilIdle()
                assertTrue(vm.uiState.value.isSuccess)
            }
    }

    @Nested
    inner class UnlockMode {
        @Test
        fun expectedLength_followsStoredPinLength() =
            runTest {
                val vm = viewModel(PinMode.UNLOCK)
                advanceUntilIdle()
                assertEquals(4, vm.uiState.value.expectedLength)

                pinLength.value = 6
                advanceUntilIdle()

                assertEquals(6, vm.uiState.value.expectedLength)
            }

        @Test
        fun correctPin_validatesThenSucceeds() =
            runTest {
                coEvery { settingsRepository.verifyPin("1234") } returns true
                val vm = viewModel(PinMode.UNLOCK)
                advanceUntilIdle()

                vm.uiState.test {
                    skipItems(1)
                    vm.enter("1234")
                    skipItems(3)
                    assertEquals("1234", awaitItem().pin)

                    assertTrue(awaitItem().isValidating)

                    val result = awaitItem()
                    assertTrue(result.isSuccess)
                    assertFalse(result.isValidating)
                }
            }

        @Test
        fun wrongPin_resetsAndShowsError() =
            runTest {
                val vm = viewModel(PinMode.UNLOCK)
                advanceUntilIdle()

                vm.enter("9999")
                advanceUntilIdle()

                val state = vm.uiState.value
                assertEquals("invalid", state.error)
                assertEquals("", state.pin)
                assertFalse(state.isSuccess)
                assertFalse(state.isValidating)
            }

        @Test
        fun noStoredPin_unlocksWithoutVerification() =
            runTest {
                pinHash.value = null
                val vm = viewModel(PinMode.UNLOCK)
                advanceUntilIdle()

                vm.enter("0000")
                advanceUntilIdle()

                assertTrue(vm.uiState.value.isSuccess)
                coVerify(exactly = 0) { settingsRepository.verifyPin(any()) }
            }

        @Test
        fun input_isIgnoredWhileValidating() =
            runTest {
                val vm = viewModel(PinMode.UNLOCK)
                advanceUntilIdle()
                vm.enter("1234")
                testScheduler.runCurrent()
                assertTrue(vm.uiState.value.isValidating)

                vm.onDigitClick("5")
                vm.onDeleteClick()

                assertEquals("1234", vm.uiState.value.pin)
                advanceUntilIdle()
                coVerify(exactly = 1) { settingsRepository.verifyPin("1234") }
            }
    }
}
