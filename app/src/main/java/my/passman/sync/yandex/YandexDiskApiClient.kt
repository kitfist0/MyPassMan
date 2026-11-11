package my.passman.sync.yandex

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

data class RemoteYandexFile(
    val path: String,
    val contentTimestamp: Long,
)

/** Yandex rejected the cached OAuth token (expired or revoked), so the user has to log in again. */
class YandexUnauthorizedException : IOException("Yandex Disk rejected the access token")

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
        // Only a 404 means "no backup yet". Any other error must not be mistaken for it,
        // or sync would go on to overwrite a newer remote backup with local data.
        if (response.code() == HTTP_NOT_FOUND) return null
        val meta = response.requireSuccess("look up the backup").body() ?: return null
        val timestamp = parseIsoTimestamp(meta.modified) ?: return null
        return RemoteYandexFile(path = meta.path, contentTimestamp = timestamp)
    }

    suspend fun uploadBackup(
        accessToken: String,
        data: ByteArray,
    ): RemoteYandexFile {
        val link =
            api
                .getUploadLink(authHeader(accessToken), BACKUP_PATH, overwrite = true)
                .requireSuccess("get an upload link")
                .body()
                ?: error("Yandex Disk did not return an upload link")
        val body = data.toRequestBody("application/octet-stream".toMediaType())
        api.uploadBytes(link.href, body).requireSuccess("upload the backup")
        // The upload response carries no metadata, so re-fetch it for an authoritative timestamp.
        return findBackupFile(accessToken) ?: RemoteYandexFile(BACKUP_PATH, System.currentTimeMillis())
    }

    suspend fun downloadBackup(accessToken: String): ByteArray {
        val link =
            api
                .getDownloadLink(authHeader(accessToken), BACKUP_PATH)
                .requireSuccess("get a download link")
                .body()
                ?: error("Yandex Disk did not return a download link")
        val response = api.downloadBytes(link.href).requireSuccess("download the backup")
        return response.body()?.bytes() ?: error("Yandex Disk returned an empty download")
    }

    suspend fun deleteBackup(accessToken: String) {
        api.deleteFile(authHeader(accessToken), BACKUP_PATH)
    }

    private fun authHeader(accessToken: String) = "OAuth $accessToken"

    private fun <T> Response<T>.requireSuccess(action: String): Response<T> {
        if (code() == HTTP_UNAUTHORIZED) throw YandexUnauthorizedException()
        if (!isSuccessful) throw IOException("Yandex Disk failed to $action (HTTP ${code()})")
        return this
    }

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
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_NOT_FOUND = 404
    }
}
