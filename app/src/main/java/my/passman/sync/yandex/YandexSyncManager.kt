package my.passman.sync.yandex

import androidx.room.withTransaction
import kotlinx.coroutines.flow.first
import my.passman.data.AppDatabase
import my.passman.data.RecordDao
import my.passman.data.SettingsRepository
import my.passman.data.SyncProvider
import my.passman.data.TagDao
import my.passman.sync.SyncResult
import my.passman.util.BackupManager
import javax.crypto.BadPaddingException
import javax.inject.Inject

/**
 * Yandex Disk counterpart to [my.passman.sync.google.GoogleSyncManager] — same "newer content wins"
 * conflict policy and [SyncResult] contract, just talking to Yandex's App Folder
 * instead of Drive's `appDataFolder`. See that class for the policy rationale.
 */
class YandexSyncManager @Inject constructor(
    private val appDatabase: AppDatabase,
    private val recordDao: RecordDao,
    private val tagDao: TagDao,
    private val backupManager: BackupManager,
    private val settingsRepository: SettingsRepository,
    private val yandexAuthManager: YandexAuthManager,
    private val yandexDiskApiClient: YandexDiskApiClient,
) {
    suspend fun sync(): SyncResult {
        if (settingsRepository.syncProvider.first() != SyncProvider.YANDEX_DISK) return SyncResult.Disabled
        val passphrase = settingsRepository.getSyncPassphrase() ?: return SyncResult.Disabled

        return try {
            when (val authResult = yandexAuthManager.authorize()) {
                is YandexAuthResult.Authorized -> performSync(authResult.accessToken, passphrase)
                YandexAuthResult.LoginRequired -> SyncResult.LoginRequired
            }
        } catch (_: BadPaddingException) {
            SyncResult.InvalidPassphrase
        } catch (_: YandexUnauthorizedException) {
            SyncResult.LoginRequired
        } catch (e: Exception) {
            SyncResult.Failed(e.message)
        } finally {
            passphrase.fill('\u0000')
        }
    }

    /**
     * Deletes the existing remote backup (if any) and uploads a fresh copy encrypted
     * with the currently stored passphrase. Intended for when the user can no longer
     * remember the passphrase the remote backup was originally encrypted with.
     */
    suspend fun resetRemoteBackup(): SyncResult {
        if (settingsRepository.syncProvider.first() != SyncProvider.YANDEX_DISK) return SyncResult.Disabled
        val passphrase = settingsRepository.getSyncPassphrase() ?: return SyncResult.Disabled

        return try {
            when (val authResult = yandexAuthManager.authorize()) {
                is YandexAuthResult.Authorized -> {
                    val accessToken = authResult.accessToken
                    if (yandexDiskApiClient.findBackupFile(accessToken) != null) {
                        yandexDiskApiClient.deleteBackup(accessToken)
                    }
                    val localChangedAt = recordDao.getMaxModified() ?: 0L
                    uploadLocal(accessToken, localChangedAt, passphrase)
                    SyncResult.Uploaded
                }
                YandexAuthResult.LoginRequired -> SyncResult.LoginRequired
            }
        } catch (_: YandexUnauthorizedException) {
            SyncResult.LoginRequired
        } catch (e: Exception) {
            SyncResult.Failed(e.message)
        } finally {
            passphrase.fill('\u0000')
        }
    }

    private suspend fun performSync(
        accessToken: String,
        passphrase: CharArray,
    ): SyncResult {
        val localChangedAt = recordDao.getMaxModified() ?: 0L
        val lastSyncedLocal = settingsRepository.lastSyncedLocalChangedAt.first() ?: -1L
        val lastSyncedRemote = settingsRepository.lastSyncedContentTimestamp.first() ?: -1L

        val remoteFile = yandexDiskApiClient.findBackupFile(accessToken)
        val localChanged = localChangedAt != lastSyncedLocal
        val remoteChanged = remoteFile != null && remoteFile.contentTimestamp != lastSyncedRemote

        return when {
            remoteFile == null -> {
                uploadLocal(accessToken, localChangedAt, passphrase)
                SyncResult.Uploaded
            }

            localChanged && remoteChanged -> {
                if (localChangedAt >= remoteFile.contentTimestamp) {
                    uploadLocal(accessToken, localChangedAt, passphrase)
                    SyncResult.Uploaded
                } else {
                    downloadRemote(accessToken, remoteFile, passphrase)
                    SyncResult.Downloaded
                }
            }

            remoteChanged -> {
                downloadRemote(accessToken, remoteFile, passphrase)
                SyncResult.Downloaded
            }

            localChanged -> {
                uploadLocal(accessToken, localChangedAt, passphrase)
                SyncResult.Uploaded
            }

            else -> SyncResult.UpToDate
        }
    }

    private suspend fun uploadLocal(
        accessToken: String,
        localChangedAt: Long,
        passphrase: CharArray,
    ) {
        val payload = backupManager.exportPayload()
        val encrypted = backupManager.encryptPayload(payload, passphrase)
        val remote = yandexDiskApiClient.uploadBackup(accessToken, encrypted)
        settingsRepository.recordSyncSuccess(
            localChangedAt = localChangedAt,
            contentTimestamp = remote.contentTimestamp,
        )
    }

    private suspend fun downloadRemote(
        accessToken: String,
        remoteFile: RemoteYandexFile,
        passphrase: CharArray,
    ) {
        val encrypted = yandexDiskApiClient.downloadBackup(accessToken)
        val payload = backupManager.decryptPayload(encrypted, passphrase)
        appDatabase.withTransaction {
            recordDao.deleteAllRecords()
            tagDao.deleteAllTags()
            tagDao.upsertTags(payload.tags)
            recordDao.upsertRecords(payload.records)
        }
        val localChangedAt = recordDao.getMaxModified() ?: 0L
        settingsRepository.recordSyncSuccess(
            localChangedAt = localChangedAt,
            contentTimestamp = remoteFile.contentTimestamp,
        )
    }
}
