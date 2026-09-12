package my.passman.sync.google

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.HttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.services.drive.Drive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import com.google.api.services.drive.model.File as DriveFile

data class RemoteBackupFile(
    val id: String,
    val contentTimestamp: Long,
)

/**
 * Drive v3 client scoped to a single fixed file in the app's hidden
 * `appDataFolder`. The file's own content recency is tracked via a custom
 * `appProperties.contentTimestamp` field (set by whichever device last
 * uploaded) rather than Drive's `modifiedTime`, so sync conflict comparisons
 * reflect data recency, not upload wall-clock time.
 */
class GoogleDriveApiClient @Inject constructor(
    private val httpTransport: HttpTransport,
    private val jsonFactory: JsonFactory,
) {
    private fun driveService(accessToken: String): Drive {
        val credential = GoogleCredential().setAccessToken(accessToken)
        return Drive
            .Builder(httpTransport, jsonFactory, credential)
            .setApplicationName("MyPassMan")
            .build()
    }

    suspend fun findBackupFile(accessToken: String): RemoteBackupFile? =
        withContext(Dispatchers.IO) {
            val result =
                driveService(accessToken)
                    .files()
                    .list()
                    .setSpaces("appDataFolder")
                    .setQ("name = '$FILE_NAME' and trashed = false")
                    .setFields("files(id,appProperties)")
                    .setPageSize(1)
                    .execute()

            val file = result.files?.firstOrNull() ?: return@withContext null
            RemoteBackupFile(
                id = file.id,
                contentTimestamp = file.appProperties?.get("contentTimestamp")?.toLongOrNull() ?: 0L,
            )
        }

    suspend fun uploadBackup(
        accessToken: String,
        existingFileId: String?,
        data: ByteArray,
        contentTimestamp: Long,
    ): RemoteBackupFile =
        withContext(Dispatchers.IO) {
            val drive = driveService(accessToken)
            val content = ByteArrayContent("application/octet-stream", data)
            val metadata =
                DriveFile().setAppProperties(mapOf("contentTimestamp" to contentTimestamp.toString()))

            val result =
                if (existingFileId == null) {
                    metadata.name = FILE_NAME
                    metadata.parents = listOf("appDataFolder")
                    drive
                        .files()
                        .create(metadata, content)
                        .setFields("id,appProperties")
                        .execute()
                } else {
                    drive
                        .files()
                        .update(existingFileId, metadata, content)
                        .setFields("id,appProperties")
                        .execute()
                }

            RemoteBackupFile(id = result.id, contentTimestamp = contentTimestamp)
        }

    suspend fun downloadBackup(
        accessToken: String,
        fileId: String,
    ): ByteArray =
        withContext(Dispatchers.IO) {
            val outputStream = ByteArrayOutputStream()
            driveService(accessToken).files().get(fileId).executeMediaAndDownloadTo(outputStream)
            outputStream.toByteArray()
        }

    suspend fun deleteBackup(
        accessToken: String,
        fileId: String,
    ) {
        withContext(Dispatchers.IO) {
            driveService(accessToken).files().delete(fileId).execute()
        }
    }

    private companion object {
        const val FILE_NAME = "backup.pman"
    }
}
