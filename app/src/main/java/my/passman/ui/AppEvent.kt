package my.passman.ui

import android.app.PendingIntent

/** One-shot UI events any ViewModel can raise via [AppEventBus], collected once at the top of MainActivity. */
sealed class AppEvent {
    data object RequestExportFile : AppEvent()

    data object RequestImportFile : AppEvent()

    data class RequestDriveConsent(
        val pendingIntent: PendingIntent,
    ) : AppEvent()

    data class ShowToast(
        val message: String,
    ) : AppEvent()
}
