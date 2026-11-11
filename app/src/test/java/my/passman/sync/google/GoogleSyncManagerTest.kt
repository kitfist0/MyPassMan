package my.passman.sync.google

import android.app.PendingIntent
import androidx.room.withTransaction
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import my.passman.data.AppDatabase
import my.passman.data.Record
import my.passman.data.RecordDao
import my.passman.data.SettingsRepository
import my.passman.data.SyncProvider
import my.passman.data.Tag
import my.passman.data.TagDao
import my.passman.sync.SyncResult
import my.passman.util.BackupManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.IOException
import javax.crypto.AEADBadTagException

class GoogleSyncManagerTest {
    private val syncProvider = MutableStateFlow(SyncProvider.GOOGLE_DRIVE)
    private val lastSyncedLocal = MutableStateFlow<Long?>(LAST_LOCAL)
    private val lastSyncedRemote = MutableStateFlow<Long?>(LAST_REMOTE)
    private val passphrase = "passphrase".toCharArray()
    private val pendingIntent = mockk<PendingIntent>()

    private val appDatabase = mockk<AppDatabase>()
    private val recordDao = mockk<RecordDao>(relaxUnitFun = true)
    private val tagDao = mockk<TagDao>(relaxUnitFun = true)
    private val backupManager = mockk<BackupManager>()
    private val settingsRepository =
        mockk<SettingsRepository>(relaxUnitFun = true) {
            every { syncProvider } returns this@GoogleSyncManagerTest.syncProvider
            every { lastSyncedLocalChangedAt } returns lastSyncedLocal
            every { lastSyncedContentTimestamp } returns lastSyncedRemote
            coEvery { getSyncPassphrase() } returns passphrase
        }
    private val authManager =
        mockk<GoogleDriveAuthManager>(relaxUnitFun = true) {
            coEvery { authorize() } returns GoogleDriveAuthResult.Authorized(TOKEN)
        }
    private val apiClient = mockk<GoogleDriveApiClient>(relaxUnitFun = true)

    private val manager =
        GoogleSyncManager(
            appDatabase = appDatabase,
            recordDao = recordDao,
            tagDao = tagDao,
            backupManager = backupManager,
            settingsRepository = settingsRepository,
            driveAuthManager = authManager,
            driveApiClient = apiClient,
        )

    @BeforeEach
    fun setUp() {
        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { appDatabase.withTransaction(any<suspend () -> Any?>()) } coAnswers {
            secondArg<suspend () -> Any?>().invoke()
        }
        coEvery { recordDao.getMaxModified() } returns LAST_LOCAL
        coEvery { backupManager.exportPayload() } returns localPayload
        coEvery { backupManager.encryptPayload(localPayload, any()) } returns ENCRYPTED_LOCAL
        coEvery { backupManager.decryptPayload(ENCRYPTED_REMOTE, any()) } returns remotePayload
        coEvery { apiClient.uploadBackup(TOKEN, any(), ENCRYPTED_LOCAL, any()) } answers {
            RemoteBackupFile(id = secondArg<String?>() ?: NEW_FILE_ID, contentTimestamp = arg(3))
        }
        coEvery { apiClient.downloadBackup(TOKEN, FILE_ID) } returns ENCRYPTED_REMOTE
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("androidx.room.RoomDatabaseKt")
    }

    private fun givenRemote(contentTimestamp: Long?) {
        coEvery { apiClient.findBackupFile(TOKEN) } returns contentTimestamp?.let { RemoteBackupFile(FILE_ID, it) }
    }

    private fun assertPassphraseWiped() = assertTrue(passphrase.all { it == '\u0000' })

    private fun verifyUploaded(
        existingFileId: String?,
        localChangedAt: Long,
    ) {
        coVerify(exactly = 1) { apiClient.uploadBackup(TOKEN, existingFileId, ENCRYPTED_LOCAL, localChangedAt) }
        coVerify(exactly = 0) { apiClient.downloadBackup(any(), any()) }
        coVerify { settingsRepository.recordSyncSuccess(localChangedAt = localChangedAt, contentTimestamp = localChangedAt) }
    }

    private fun verifyDownloaded() {
        coVerify(exactly = 0) { apiClient.uploadBackup(any(), any(), any(), any()) }
        coVerifyOrder {
            recordDao.deleteAllRecords()
            tagDao.deleteAllTags()
            tagDao.upsertTags(remotePayload.tags)
            recordDao.upsertRecords(remotePayload.records)
        }
    }

    @Nested
    inner class Sync {
        @Test
        fun disabled_whenAnotherProviderSelected() =
            runTest {
                syncProvider.value = SyncProvider.YANDEX_DISK

                assertEquals(SyncResult.Disabled, manager.sync())
                coVerify(exactly = 0) { authManager.authorize() }
            }

        @Test
        fun disabled_withoutStoredPassphrase() =
            runTest {
                coEvery { settingsRepository.getSyncPassphrase() } returns null

                assertEquals(SyncResult.Disabled, manager.sync())
                coVerify(exactly = 0) { authManager.authorize() }
            }

        @Test
        fun consentRequired_isPassedThrough() =
            runTest {
                coEvery { authManager.authorize() } returns GoogleDriveAuthResult.ConsentRequired(pendingIntent)

                assertEquals(SyncResult.ConsentRequired(pendingIntent), manager.sync())
                coVerify(exactly = 0) { apiClient.findBackupFile(any()) }
                assertPassphraseWiped()
            }

        @Test
        fun authFailure_reportsFailed() =
            runTest {
                coEvery { authManager.authorize() } returns GoogleDriveAuthResult.Failed("no account")

                assertEquals(SyncResult.Failed("no account"), manager.sync())
                coVerify(exactly = 0) { apiClient.findBackupFile(any()) }
                assertPassphraseWiped()
            }

        @Test
        fun noRemoteBackup_createsNewFile() =
            runTest {
                givenRemote(null)

                assertEquals(SyncResult.Uploaded, manager.sync())
                verifyUploaded(existingFileId = null, localChangedAt = LAST_LOCAL)
                assertPassphraseWiped()
            }

        @Test
        fun nothingChanged_isUpToDate() =
            runTest {
                givenRemote(LAST_REMOTE)

                assertEquals(SyncResult.UpToDate, manager.sync())
                coVerify(exactly = 0) { apiClient.uploadBackup(any(), any(), any(), any()) }
                coVerify(exactly = 0) { apiClient.downloadBackup(any(), any()) }
                coVerify(exactly = 0) { settingsRepository.recordSyncSuccess(any(), any()) }
                assertPassphraseWiped()
            }

        @Test
        fun onlyLocalChanged_updatesExistingFile() =
            runTest {
                givenRemote(LAST_REMOTE)
                coEvery { recordDao.getMaxModified() } returns LAST_LOCAL + 1

                assertEquals(SyncResult.Uploaded, manager.sync())
                verifyUploaded(existingFileId = FILE_ID, localChangedAt = LAST_LOCAL + 1)
            }

        @Test
        fun onlyRemoteChanged_replacesLocalData() =
            runTest {
                givenRemote(LAST_REMOTE + 1)
                coEvery { recordDao.getMaxModified() } returnsMany listOf(LAST_LOCAL, IMPORTED_MAX_MODIFIED)

                assertEquals(SyncResult.Downloaded, manager.sync())
                verifyDownloaded()
                coVerify {
                    settingsRepository.recordSyncSuccess(
                        localChangedAt = IMPORTED_MAX_MODIFIED,
                        contentTimestamp = LAST_REMOTE + 1,
                    )
                }
                assertPassphraseWiped()
            }

        @Test
        fun bothChanged_localNewer_updatesExistingFile() =
            runTest {
                givenRemote(LAST_REMOTE + 1)
                coEvery { recordDao.getMaxModified() } returns LAST_REMOTE + 2

                assertEquals(SyncResult.Uploaded, manager.sync())
                verifyUploaded(existingFileId = FILE_ID, localChangedAt = LAST_REMOTE + 2)
            }

        @Test
        fun bothChanged_sameTimestamp_prefersLocal() =
            runTest {
                givenRemote(LAST_REMOTE + 1)
                coEvery { recordDao.getMaxModified() } returns LAST_REMOTE + 1

                assertEquals(SyncResult.Uploaded, manager.sync())
                verifyUploaded(existingFileId = FILE_ID, localChangedAt = LAST_REMOTE + 1)
            }

        @Test
        fun bothChanged_remoteNewer_downloads() =
            runTest {
                givenRemote(LAST_REMOTE + 2)
                coEvery { recordDao.getMaxModified() } returnsMany listOf(LAST_REMOTE + 1, IMPORTED_MAX_MODIFIED)

                assertEquals(SyncResult.Downloaded, manager.sync())
                verifyDownloaded()
            }

        @Test
        fun freshInstall_downloadsExistingBackup() =
            runTest {
                lastSyncedLocal.value = null
                lastSyncedRemote.value = null
                givenRemote(LAST_REMOTE)
                coEvery { recordDao.getMaxModified() } returnsMany listOf(null, IMPORTED_MAX_MODIFIED)

                assertEquals(SyncResult.Downloaded, manager.sync())
                verifyDownloaded()
            }

        @Test
        fun freshInstall_withoutRemoteBackup_uploadsEmptyVault() =
            runTest {
                lastSyncedLocal.value = null
                lastSyncedRemote.value = null
                givenRemote(null)
                coEvery { recordDao.getMaxModified() } returns null

                assertEquals(SyncResult.Uploaded, manager.sync())
                verifyUploaded(existingFileId = null, localChangedAt = 0L)
            }

        @Test
        fun wrongPassphrase_reportsInvalidPassphrase_andKeepsLocalData() =
            runTest {
                givenRemote(LAST_REMOTE + 1)
                coEvery { backupManager.decryptPayload(any(), any()) } throws AEADBadTagException("tag mismatch")

                assertEquals(SyncResult.InvalidPassphrase, manager.sync())
                coVerify(exactly = 0) { recordDao.deleteAllRecords() }
                coVerify(exactly = 0) { tagDao.deleteAllTags() }
                coVerify(exactly = 0) { settingsRepository.recordSyncSuccess(any(), any()) }
                assertPassphraseWiped()
            }

        @Test
        fun networkError_reportsFailed() =
            runTest {
                coEvery { apiClient.findBackupFile(TOKEN) } throws IOException("offline")

                assertEquals(SyncResult.Failed("offline"), manager.sync())
                coVerify(exactly = 0) { settingsRepository.recordSyncSuccess(any(), any()) }
                assertPassphraseWiped()
            }

        @Test
        fun uploadError_doesNotRecordSuccess() =
            runTest {
                givenRemote(null)
                coEvery { apiClient.uploadBackup(any(), any(), any(), any()) } throws IOException("quota")

                assertEquals(SyncResult.Failed("quota"), manager.sync())
                coVerify(exactly = 0) { settingsRepository.recordSyncSuccess(any(), any()) }
                assertPassphraseWiped()
            }
    }

    @Nested
    inner class ResetRemoteBackup {
        @Test
        fun disabled_whenAnotherProviderSelected() =
            runTest {
                syncProvider.value = SyncProvider.NONE

                assertEquals(SyncResult.Disabled, manager.resetRemoteBackup())
                coVerify(exactly = 0) { apiClient.deleteBackup(any(), any()) }
            }

        @Test
        fun disabled_withoutStoredPassphrase() =
            runTest {
                coEvery { settingsRepository.getSyncPassphrase() } returns null

                assertEquals(SyncResult.Disabled, manager.resetRemoteBackup())
                coVerify(exactly = 0) { apiClient.deleteBackup(any(), any()) }
            }

        @Test
        fun consentRequired_isPassedThrough() =
            runTest {
                coEvery { authManager.authorize() } returns GoogleDriveAuthResult.ConsentRequired(pendingIntent)

                assertEquals(SyncResult.ConsentRequired(pendingIntent), manager.resetRemoteBackup())
                coVerify(exactly = 0) { apiClient.deleteBackup(any(), any()) }
                assertPassphraseWiped()
            }

        @Test
        fun authFailure_reportsFailed() =
            runTest {
                coEvery { authManager.authorize() } returns GoogleDriveAuthResult.Failed("no account")

                assertEquals(SyncResult.Failed("no account"), manager.resetRemoteBackup())
                coVerify(exactly = 0) { apiClient.deleteBackup(any(), any()) }
                assertPassphraseWiped()
            }

        @Test
        fun existingBackup_isDeletedThenRecreated() =
            runTest {
                givenRemote(LAST_REMOTE)

                assertEquals(SyncResult.Uploaded, manager.resetRemoteBackup())
                coVerifyOrder {
                    apiClient.deleteBackup(TOKEN, FILE_ID)
                    apiClient.uploadBackup(TOKEN, null, ENCRYPTED_LOCAL, LAST_LOCAL)
                    settingsRepository.recordSyncSuccess(localChangedAt = LAST_LOCAL, contentTimestamp = LAST_LOCAL)
                }
                assertPassphraseWiped()
            }

        @Test
        fun missingBackup_skipsDelete() =
            runTest {
                givenRemote(null)

                assertEquals(SyncResult.Uploaded, manager.resetRemoteBackup())
                coVerify(exactly = 0) { apiClient.deleteBackup(any(), any()) }
                verifyUploaded(existingFileId = null, localChangedAt = LAST_LOCAL)
            }

        @Test
        fun error_reportsFailed() =
            runTest {
                givenRemote(LAST_REMOTE)
                coEvery { apiClient.deleteBackup(TOKEN, FILE_ID) } throws IOException("offline")

                assertEquals(SyncResult.Failed("offline"), manager.resetRemoteBackup())
                coVerify(exactly = 0) { apiClient.uploadBackup(any(), any(), any(), any()) }
                assertPassphraseWiped()
            }
    }

    @Test
    fun signOut_delegatesToAuthManager() =
        runTest {
            manager.signOut()

            coVerify { authManager.signOut() }
        }

    @Test
    fun authorize_delegatesToAuthManager() =
        runTest {
            assertEquals(GoogleDriveAuthResult.Authorized(TOKEN), manager.authorize())
        }

    private companion object {
        const val TOKEN = "token"
        const val FILE_ID = "file-id"
        const val NEW_FILE_ID = "new-file-id"
        const val LAST_LOCAL = 1_000L
        const val LAST_REMOTE = 2_000L
        const val IMPORTED_MAX_MODIFIED = 5_000L
        val ENCRYPTED_LOCAL = byteArrayOf(1, 2, 3)
        val ENCRYPTED_REMOTE = byteArrayOf(4, 5, 6)

        val localPayload =
            BackupManager.BackupPayload(
                records = listOf(record(id = 1, name = "local")),
                tags = listOf(Tag(id = 1, name = "work")),
            )
        val remotePayload =
            BackupManager.BackupPayload(
                records = listOf(record(id = 2, name = "remote")),
                tags = listOf(Tag(id = 2, name = "home")),
            )

        fun record(
            id: Long,
            name: String,
        ) = Record(
            id = id,
            created = 0L,
            modified = 0L,
            name = name,
            login = "login",
            secret = "secret",
            comment = "",
        )
    }
}
