package my.passman.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject

data class RemoteBackupFile(
    val id: String,
    val contentTimestamp: Long,
)

/**
 * Minimal Drive REST v3 client scoped to a single fixed file in the app's
 * hidden `appDataFolder`. The file's own content recency is tracked via a
 * custom `appProperties.contentTimestamp` field (set by whichever device last
 * uploaded) rather than Drive's `modifiedTime`, so sync conflict comparisons
 * reflect data recency, not upload wall-clock time.
 */
class DriveApiClient
    @Inject
    constructor(
        private val httpClient: OkHttpClient,
    ) {
        suspend fun findBackupFile(accessToken: String): RemoteBackupFile? =
            withContext(Dispatchers.IO) {
                val url =
                    "https://www.googleapis.com/drive/v3/files"
                        .toHttpUrl()
                        .newBuilder()
                        .addQueryParameter("spaces", "appDataFolder")
                        .addQueryParameter("q", "name = '$FILE_NAME' and trashed = false")
                        .addQueryParameter("fields", "files(id,appProperties)")
                        .addQueryParameter("pageSize", "1")
                        .build()

                val request =
                    Request
                        .Builder()
                        .url(url)
                        .header("Authorization", "Bearer $accessToken")
                        .get()
                        .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Drive list failed: ${response.code}")
                    val files = JSONObject(response.body.string()).optJSONArray("files")
                    if (files == null || files.length() == 0) return@withContext null
                    val file = files.getJSONObject(0)
                    RemoteBackupFile(
                        id = file.getString("id"),
                        contentTimestamp =
                            file
                                .optJSONObject("appProperties")
                                ?.optString("contentTimestamp")
                                ?.toLongOrNull() ?: 0L,
                    )
                }
            }

        suspend fun uploadBackup(
            accessToken: String,
            existingFileId: String?,
            data: ByteArray,
            contentTimestamp: Long,
        ): RemoteBackupFile =
            withContext(Dispatchers.IO) {
                val metadata =
                    JSONObject().apply {
                        if (existingFileId == null) {
                            put("name", FILE_NAME)
                            put("parents", listOf("appDataFolder"))
                        }
                        put("appProperties", JSONObject().put("contentTimestamp", contentTimestamp.toString()))
                    }

                val body =
                    MultipartBody
                        .Builder()
                        .setType("multipart/related".toMediaType())
                        .addPart(
                            okhttp3.Headers.headersOf("Content-Type", "application/json; charset=UTF-8"),
                            metadata.toString().toRequestBody(),
                        ).addPart(
                            okhttp3.Headers.headersOf("Content-Type", "application/octet-stream"),
                            data.toRequestBody("application/octet-stream".toMediaType()),
                        ).build()

                val urlBuilder =
                    if (existingFileId == null) {
                        "https://www.googleapis.com/upload/drive/v3/files".toHttpUrl().newBuilder()
                    } else {
                        "https://www.googleapis.com/upload/drive/v3/files/$existingFileId".toHttpUrl().newBuilder()
                    }
                val url =
                    urlBuilder
                        .addQueryParameter("uploadType", "multipart")
                        .addQueryParameter("fields", "id,appProperties")
                        .build()

                val request =
                    Request
                        .Builder()
                        .url(url)
                        .header("Authorization", "Bearer $accessToken")
                        .let { if (existingFileId == null) it.post(body) else it.patch(body) }
                        .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Drive upload failed: ${response.code}")
                    val file = JSONObject(response.body.string())
                    RemoteBackupFile(id = file.getString("id"), contentTimestamp = contentTimestamp)
                }
            }

        suspend fun downloadBackup(
            accessToken: String,
            fileId: String,
        ): ByteArray =
            withContext(Dispatchers.IO) {
                val url =
                    "https://www.googleapis.com/drive/v3/files/$fileId"
                        .toHttpUrl()
                        .newBuilder()
                        .addQueryParameter("alt", "media")
                        .build()

                val request =
                    Request
                        .Builder()
                        .url(url)
                        .header("Authorization", "Bearer $accessToken")
                        .get()
                        .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Drive download failed: ${response.code}")
                    response.body.bytes()
                }
            }

        private companion object {
            const val FILE_NAME = "backup.pman"
        }
    }
