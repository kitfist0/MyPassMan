package my.passman.sync.yandex

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

data class RemoteYandexFile(
    val path: String,
    val contentTimestamp: Long,
)

/**
 * Wraps [YandexDiskRestApi] around a single fixed backup file kept in the app's
 * private "App Folder" (`app:/...`), mirroring how [my.passman.sync.google.GoogleDriveApiClient]
 * uses Drive's hidden `appDataFolder`. Requires the Yandex OAuth app to be
 * registered with the `cloud_api:disk.app_folder` scope.
 *
 * Yandex doesn't offer a custom-metadata field like Drive's `appProperties`, so
 * content recency is tracked via the resource's own `modified` timestamp instead
 * — less precise than Drive's approach (it reflects upload wall-clock time, not
 * necessarily the underlying data's own recency), but adequate since an upload
 * always immediately follows the local data being finalized.
 */
class YandexDiskApiClient @Inject constructor(
    private val api: YandexDiskRestApi,
) {
    suspend fun findBackupFile(accessToken: String): RemoteYandexFile? {
        val response = api.getResourceMeta(authHeader(accessToken), BACKUP_PATH)
        if (!response.isSuccessful) return null
        val meta = response.body() ?: return null
        val timestamp = parseIsoTimestamp(meta.modified) ?: return null
        return RemoteYandexFile(path = meta.path, contentTimestamp = timestamp)
    }

    suspend fun uploadBackup(
        accessToken: String,
        data: ByteArray,
    ): RemoteYandexFile {
        val link =
            api.getUploadLink(authHeader(accessToken), BACKUP_PATH).body()
                ?: error("Yandex Disk did not return an upload link")
        val body = data.toRequestBody("application/octet-stream".toMediaType())
        api.uploadBytes(link.href, body)
        // The upload response carries no metadata, so re-fetch it for an authoritative timestamp.
        return findBackupFile(accessToken) ?: RemoteYandexFile(BACKUP_PATH, System.currentTimeMillis())
    }

    suspend fun downloadBackup(accessToken: String): ByteArray {
        val link =
            api.getDownloadLink(authHeader(accessToken), BACKUP_PATH).body()
                ?: error("Yandex Disk did not return a download link")
        val response = api.downloadBytes(link.href)
        return response.body()?.bytes() ?: error("Yandex Disk returned an empty download")
    }

    suspend fun deleteBackup(accessToken: String) {
        api.deleteFile(authHeader(accessToken), BACKUP_PATH)
    }

    private fun authHeader(accessToken: String) = "OAuth $accessToken"

    private fun parseIsoTimestamp(iso: String?): Long? {
        if (iso == null) return null
        return try {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).parse(iso)?.time
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        const val BACKUP_PATH = "app:/backup.pman"
    }
}
