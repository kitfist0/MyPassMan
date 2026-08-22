package my.passman.ui

import my.passman.ui.screens.pin.PinMode
import java.util.UUID

sealed class Screen {
    data object List : Screen()
    data class Edit(
        val recordId: Long? = null,
        val sessionKey: String = UUID.randomUUID().toString(),
    ) : Screen()

    data object Settings : Screen()
    data object Tags : Screen()
    data class Pin(val mode: PinMode) : Screen()
}
