package my.passman.sync.yandex

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PUT
import retrofit2.http.Query
import retrofit2.http.Url

@Keep
data class Link(
    val href: String,
    val method: String,
    val templated: Boolean,
)

@Keep
data class DiskFile(
    val path: String,
    val name: String,
    val type: String,
)

/**
 * Metadata for a single resource, returned by `GET resources` when [path] points
 * at a file rather than a directory (a directory listing comes back as
 * [DiskResources] instead). [modified] is an ISO-8601 timestamp string.
 */
@Keep
data class DiskFileMeta(
    val path: String,
    val name: String,
    val modified: String? = null,
    val type: String? = null,
)

@Keep
data class DiskResources(
    @SerializedName("_embedded") val embedded: Embedded?,
    val items: List<DiskFile> = emptyList(),
)

@Keep
data class Embedded(
    val items: List<DiskFile>,
    val limit: Int,
    val offset: Int,
    val path: String,
    val total: Int,
)

interface YandexDiskRestApi {
    companion object {
        const val BASE_URL = "https://cloud-api.yandex.net/v1/disk/"
    }

    @GET("resources/upload")
    suspend fun getUploadLink(
        @Header("Authorization") token: String,
        @Query("path") path: String,
    ): Response<Link>

    @GET("resources/download")
    suspend fun getDownloadLink(
        @Header("Authorization") token: String,
        @Query("path") path: String,
    ): Response<Link>

    @GET("resources")
    suspend fun listFiles(
        @Header("Authorization") token: String,
        @Query("path") path: String,
        @Query("limit") limit: Int = 1000,
        @Query("offset") offset: Int = 0,
    ): Response<DiskResources>

    @GET("resources")
    suspend fun getResourceMeta(
        @Header("Authorization") token: String,
        @Query("path") path: String,
        @Query("fields") fields: String = "path,name,modified,type",
    ): Response<DiskFileMeta>

    @DELETE("resources")
    suspend fun deleteFile(
        @Header("Authorization") token: String,
        @Query("path") path: String,
        @Query("permanently") permanently: Boolean = true,
    )

    // The upload/download links returned above point at a separate storage host,
    // so these two calls take the full URL directly rather than a BASE_URL-relative path.
    @PUT
    suspend fun uploadBytes(
        @Url url: String,
        @Body body: RequestBody,
    ): Response<Unit>

    @GET
    suspend fun downloadBytes(
        @Url url: String,
    ): Response<ResponseBody>
}
