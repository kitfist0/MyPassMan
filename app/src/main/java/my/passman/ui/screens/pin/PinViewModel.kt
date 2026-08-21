package my.passman.ui.screens.pin

import androidx.lifecycle.ViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@HiltViewModel(assistedFactory = PinViewModel.Factory::class)
class PinViewModel @AssistedInject constructor(
    @Assisted private val mode: PinMode
) : ViewModel() {

    private val _uiState = MutableStateFlow(PinScreenState(mode = mode))
    val uiState: StateFlow<PinScreenState> = _uiState.asStateFlow()

    private var firstEnteredPin: String = ""

    fun onDigitClick(digit: String) {
        if (_uiState.value.pin.length < 4) {
            _uiState.update { it.copy(pin = it.pin + digit, error = null) }
            if (_uiState.value.pin.length == 4) {
                handlePinEntryComplete()
            }
        }
    }

    fun onDeleteClick() {
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
        when (_uiState.value.mode) {
            PinMode.SET -> {
                firstEnteredPin = enteredPin
                _uiState.update { it.copy(pin = "", mode = PinMode.CONFIRM) }
            }
            PinMode.CONFIRM -> {
                if (enteredPin == firstEnteredPin) {
                    _uiState.update { it.copy(isSuccess = true) }
                } else {
                    _uiState.update { it.copy(pin = "", error = "mismatch") }
                }
            }
            PinMode.UNLOCK -> {
                // Temporary logic for testing UI: any 4 digits work except "0000"
                if (enteredPin != "0000") {
                    _uiState.update { it.copy(isSuccess = true) }
                } else {
                    _uiState.update { it.copy(pin = "", error = "invalid") }
                }
            }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(mode: PinMode): PinViewModel
    }
}
