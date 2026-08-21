package my.passman.ui.screens.pin

data class PinScreenState(
    val pin: String = "",
    val mode: PinMode = PinMode.UNLOCK,
    val error: String? = null,
    val isSuccess: Boolean = false
)

enum class PinMode {
    SET,
    CONFIRM,
    UNLOCK
}
