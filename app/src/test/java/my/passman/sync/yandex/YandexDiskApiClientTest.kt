package my.passman.sync.yandex

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import retrofit2.Response
import java.io.IOException
import java.time.OffsetDateTime

class YandexDiskApiClientTest {
    private val api = mockk<YandexDiskRestApi>(relaxUnitFun = true)
    private val client = YandexDiskApiClient(api)

    private fun givenMeta(modified: String?) {
        coEvery { api.getResourceMeta(AUTH, PATH, any()) } returns
            Response.success(DiskFileMeta(path = PATH, name = "backup.pman", modified = modified, type = "file"))
    }

    private fun <T> errorResponse(code: Int): Response<T> = Response.error(code, "{}".toResponseBody(JSON))

    private fun epochMillis(iso: String) = OffsetDateTime.parse(iso).toInstant().toEpochMilli()

    @Nested
    inner class FindBackupFile {
        @Test
        fun returnsPathAndModifiedTimestamp() =
            runTest {
                givenMeta("2026-11-11T10:15:30+00:00")

                assertEquals(
                    RemoteYandexFile(PATH, epochMillis("2026-11-11T10:15:30+00:00")),
                    client.findBackupFile(TOKEN),
                )
            }

        @ParameterizedTest
        @ValueSource(strings = ["2026-11-11T13:15:30+03:00", "2026-11-11T10:15:30Z", "2026-11-11T05:15:30-05:00"])
        fun respectsTimezoneOffset(modified: String) =
            runTest {
                givenMeta(modified)

                assertEquals(epochMillis("2026-11-11T10:15:30Z"), client.findBackupFile(TOKEN)?.contentTimestamp)
            }

        @Test
        fun missingFile_returnsNull() =
            runTest {
                coEvery { api.getResourceMeta(AUTH, PATH, any()) } returns errorResponse(404)

                assertNull(client.findBackupFile(TOKEN))
            }

        @Test
        fun rejectedToken_throwsUnauthorized() =
            runTest {
                coEvery { api.getResourceMeta(AUTH, PATH, any()) } returns errorResponse(401)

                assertThrows<YandexUnauthorizedException> { client.findBackupFile(TOKEN) }
            }

        @ParameterizedTest
        @ValueSource(ints = [403, 429, 500, 503])
        fun otherErrors_throwInsteadOfReportingMissingFile(code: Int) =
            runTest {
                coEvery { api.getResourceMeta(AUTH, PATH, any()) } returns errorResponse(code)

                val error = assertThrows<IOException> { client.findBackupFile(TOKEN) }

                assertEquals("Yandex Disk failed to look up the backup (HTTP $code)", error.message)
            }

        @Test
        fun emptyBody_returnsNull() =
            runTest {
                coEvery { api.getResourceMeta(AUTH, PATH, any()) } returns Response.success(null)

                assertNull(client.findBackupFile(TOKEN))
            }

        @Test
        fun missingModified_returnsNull() =
            runTest {
                givenMeta(null)

                assertNull(client.findBackupFile(TOKEN))
            }

        @ParameterizedTest
        @ValueSource(strings = ["", "yesterday", "2026-11-11", "2026-11-11 10:15:30"])
        fun malformedModified_returnsNull(modified: String) =
            runTest {
                givenMeta(modified)

                assertNull(client.findBackupFile(TOKEN))
            }
    }

    @Nested
    inner class UploadBackup {
        @Test
        fun putsBytesToUploadLink_andReturnsRefetchedTimestamp() =
            runTest {
                val body = slot<RequestBody>()
                coEvery { api.getUploadLink(AUTH, PATH, true) } returns Response.success(Link(UPLOAD_HREF, "PUT", false))
                coEvery { api.uploadBytes(UPLOAD_HREF, capture(body)) } returns Response.success(Unit)
                givenMeta("2026-11-11T10:15:30+00:00")

                val result = client.uploadBackup(TOKEN, DATA)

                assertEquals(RemoteYandexFile(PATH, epochMillis("2026-11-11T10:15:30+00:00")), result)
                assertEquals("application/octet-stream", body.captured.contentType().toString())
                assertArrayEquals(DATA, Buffer().also { body.captured.writeTo(it) }.readByteArray())
            }

        @Test
        fun metadataUnavailableAfterUpload_fallsBackToCurrentTime() =
            runTest {
                coEvery { api.getUploadLink(AUTH, PATH, true) } returns Response.success(Link(UPLOAD_HREF, "PUT", false))
                coEvery { api.uploadBytes(UPLOAD_HREF, any()) } returns Response.success(Unit)
                coEvery { api.getResourceMeta(AUTH, PATH, any()) } returns errorResponse(404)

                val before = System.currentTimeMillis()
                val result = client.uploadBackup(TOKEN, DATA)
                val after = System.currentTimeMillis()

                assertEquals(PATH, result.path)
                assertTrue(result.contentTimestamp in before..after)
            }

        @Test
        fun uploadLinkError_throws_withoutUploading() =
            runTest {
                coEvery { api.getUploadLink(AUTH, PATH, true) } returns errorResponse(507)

                val error = assertThrows<IOException> { client.uploadBackup(TOKEN, DATA) }

                assertEquals("Yandex Disk failed to get an upload link (HTTP 507)", error.message)
                coVerify(exactly = 0) { api.uploadBytes(any(), any()) }
            }

        @Test
        fun emptyUploadLink_throws_withoutUploading() =
            runTest {
                coEvery { api.getUploadLink(AUTH, PATH, true) } returns Response.success(null)

                val error = assertThrows<IllegalStateException> { client.uploadBackup(TOKEN, DATA) }

                assertEquals("Yandex Disk did not return an upload link", error.message)
                coVerify(exactly = 0) { api.uploadBytes(any(), any()) }
            }

        @Test
        fun rejectedToken_throwsUnauthorized() =
            runTest {
                coEvery { api.getUploadLink(AUTH, PATH, true) } returns errorResponse(401)

                assertThrows<YandexUnauthorizedException> { client.uploadBackup(TOKEN, DATA) }
            }

        @Test
        fun failedPut_throws_withoutReportingSuccess() =
            runTest {
                coEvery { api.getUploadLink(AUTH, PATH, true) } returns Response.success(Link(UPLOAD_HREF, "PUT", false))
                coEvery { api.uploadBytes(UPLOAD_HREF, any()) } returns errorResponse(500)

                val error = assertThrows<IOException> { client.uploadBackup(TOKEN, DATA) }

                assertEquals("Yandex Disk failed to upload the backup (HTTP 500)", error.message)
                coVerify(exactly = 0) { api.getResourceMeta(any(), any(), any()) }
            }
    }

    @Nested
    inner class DownloadBackup {
        @Test
        fun returnsBytesFromDownloadLink() =
            runTest {
                coEvery { api.getDownloadLink(AUTH, PATH) } returns Response.success(Link(DOWNLOAD_HREF, "GET", false))
                coEvery { api.downloadBytes(DOWNLOAD_HREF) } returns Response.success(DATA.toResponseBody(OCTET_STREAM))

                assertArrayEquals(DATA, client.downloadBackup(TOKEN))
            }

        @Test
        fun downloadLinkError_throws_withoutDownloading() =
            runTest {
                coEvery { api.getDownloadLink(AUTH, PATH) } returns errorResponse(404)

                val error = assertThrows<IOException> { client.downloadBackup(TOKEN) }

                assertEquals("Yandex Disk failed to get a download link (HTTP 404)", error.message)
                coVerify(exactly = 0) { api.downloadBytes(any()) }
            }

        @Test
        fun emptyDownloadLink_throws_withoutDownloading() =
            runTest {
                coEvery { api.getDownloadLink(AUTH, PATH) } returns Response.success(null)

                val error = assertThrows<IllegalStateException> { client.downloadBackup(TOKEN) }

                assertEquals("Yandex Disk did not return a download link", error.message)
                coVerify(exactly = 0) { api.downloadBytes(any()) }
            }

        @Test
        fun rejectedToken_throwsUnauthorized() =
            runTest {
                coEvery { api.getDownloadLink(AUTH, PATH) } returns errorResponse(401)

                assertThrows<YandexUnauthorizedException> { client.downloadBackup(TOKEN) }
            }

        @Test
        fun failedDownload_throws() =
            runTest {
                coEvery { api.getDownloadLink(AUTH, PATH) } returns Response.success(Link(DOWNLOAD_HREF, "GET", false))
                coEvery { api.downloadBytes(DOWNLOAD_HREF) } returns errorResponse<ResponseBody>(500)

                val error = assertThrows<IOException> { client.downloadBackup(TOKEN) }

                assertEquals("Yandex Disk failed to download the backup (HTTP 500)", error.message)
            }

        @Test
        fun emptyDownload_throws() =
            runTest {
                coEvery { api.getDownloadLink(AUTH, PATH) } returns Response.success(Link(DOWNLOAD_HREF, "GET", false))
                coEvery { api.downloadBytes(DOWNLOAD_HREF) } returns Response.success(null)

                val error = assertThrows<IllegalStateException> { client.downloadBackup(TOKEN) }

                assertEquals("Yandex Disk returned an empty download", error.message)
            }
    }

    @Test
    fun deleteBackup_deletesFixedPathWithOAuthHeader() =
        runTest {
            client.deleteBackup(TOKEN)

            coVerify { api.deleteFile(AUTH, PATH, any()) }
        }

    private companion object {
        const val TOKEN = "token"
        const val AUTH = "OAuth token"
        const val PATH = "app:/backup.pman"
        const val UPLOAD_HREF = "https://uploader.example/upload"
        const val DOWNLOAD_HREF = "https://downloader.example/download"
        val DATA = byteArrayOf(1, 2, 3, 4)
        val JSON = "application/json".toMediaType()
        val OCTET_STREAM = "application/octet-stream".toMediaType()
    }
}
