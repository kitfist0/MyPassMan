package my.passman.ui

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared [AppEvent] channel: any ViewModel can inject this and call [send] without needing its
 * own events plumbing wired into every screen that might trigger one. Collected once at the top
 * of MainActivity, so it works the same regardless of which screen is currently showing.
 */
@Singleton
class AppEventBus @Inject constructor() {
    private val _events = Channel<AppEvent>(Channel.BUFFERED)
    val events: ReceiveChannel<AppEvent> = _events

    suspend fun send(event: AppEvent) {
        _events.send(event)
    }
}
