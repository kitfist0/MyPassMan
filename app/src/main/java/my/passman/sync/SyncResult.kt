package my.passman.sync

import android.app.PendingIntent

/** Shared outcome contract for every cloud sync backend (Google Drive, Yandex Disk, ...). */
sealed class SyncResult {
    data object UpToDate : SyncResult()

    data object Uploaded : SyncResult()

    data object Downloaded : SyncResult()

    data object Disabled : SyncResult()

    data class ConsentRequired(
        val pendingIntent: PendingIntent,
    ) : SyncResult()

    // The remote backup couldn't be decrypted — the stored sync passphrase doesn't
    // match the one it was encrypted with (e.g. it was set up from another device).
    data object InvalidPassphrase : SyncResult()

    // No cached provider credential — the caller must show its own interactive login
    // (e.g. Yandex's WebView OAuth flow) before a sync can proceed.
    data object LoginRequired : SyncResult()

    data class Failed(
        val message: String?,
    ) : SyncResult()
}
