package my.passman.ui.screens.pin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import my.passman.data.SettingsRepository
import kotlin.time.Duration.Companion.milliseconds

@HiltViewModel(assistedFactory = PinViewModel.Factory::class)
class PinViewModel @AssistedInject constructor(
    private val settingsRepository: SettingsRepository,
    @Assisted private val mode: PinMode,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PinScreenState(mode = mode))
    val uiState: StateFlow<PinScreenState> = _uiState.asStateFlow()

    private var firstEnteredPin: String = ""

    init {
        if (mode == PinMode.UNLOCK) {
            viewModelScope.launch {
                settingsRepository.pinLength.collect { length ->
                    _uiState.update { it.copy(expectedLength = length) }
                }
            }
        }
    }

    fun onDigitClick(digit: String) {
        if (_uiState.value.isValidating) return

        val currentPin = _uiState.value.pin
        val maxLength = if (_uiState.value.mode == PinMode.SET) 6 else _uiState.value.expectedLength

        if (currentPin.length < maxLength) {
            val newPin = currentPin + digit
            _uiState.update { it.copy(pin = newPin, error = null) }

            // Auto-submit for confirmation or unlocking
            if (_uiState.value.mode != PinMode.SET && newPin.length == _uiState.value.expectedLength) {
                handlePinEntryComplete()
            }
        }
    }

    fun onConfirmClick() {
        if (_uiState.value.pin.length >= 4) {
            handlePinEntryComplete()
        }
    }

    fun onDeleteClick() {
        if (_uiState.value.isValidating) {
            return
        }

        _uiState.update {
            if (it.pin.isNotEmpty()) {
                it.copy(pin = it.pin.dropLast(1), error = null)
            } else {
                it
            }
        }
    }

    private fun handlePinEntryComplete() {
        val enteredPin = _uiState.value.pin
        viewModelScope.launch {
            when (_uiState.value.mode) {
                PinMode.SET -> {
                    firstEnteredPin = enteredPin
                    _uiState.update {
                        it.copy(
                            pin = "",
                            mode = PinMode.CONFIRM,
                            expectedLength = enteredPin.length,
                        )
                    }
                }

                PinMode.CONFIRM -> {
                    if (enteredPin == firstEnteredPin) {
                        settingsRepository.setPin(enteredPin)
                        _uiState.update { it.copy(isSuccess = true) }
                    } else {
                        _uiState.update { it.copy(pin = "", error = "mismatch") }
                    }
                }

                PinMode.UNLOCK -> {
                    _uiState.update { it.copy(isValidating = true) }
                    delay(500.milliseconds)
                    val storedHash = settingsRepository.pinHash.first()
                    if (storedHash == null || settingsRepository.hashPin(enteredPin) == storedHash) {
                        _uiState.update { it.copy(isSuccess = true, isValidating = false) }
                    } else {
                        _uiState.update {
                            it.copy(
                                pin = "",
                                error = "invalid",
                                isValidating = false,
                            )
                        }
                    }
                }
            }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(mode: PinMode): PinViewModel
    }
}
