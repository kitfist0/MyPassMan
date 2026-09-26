package my.passman.ui

/** One-shot UI events any ViewModel can raise via [AppEventBus], collected once at the top of MainActivity. */
sealed class AppEvent {
    data class ShowToast(
        val text: Text,
    ) : AppEvent()
}
