package my.passman.sync

import android.app.PendingIntent
import androidx.room.withTransaction
import kotlinx.coroutines.flow.first
import my.passman.data.AppDatabase
import my.passman.data.RecordDao
import my.passman.data.SettingsRepository
import my.passman.data.SyncProvider
import my.passman.data.TagDao
import my.passman.util.BackupManager
import javax.crypto.BadPaddingException
import javax.inject.Inject

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

/**
 * Orchestrates one sync pass against the app's Drive `appDataFolder` backup
 * file. Conflict policy is whole-payload "newer content wins": local content
 * recency is the most recent [my.passman.data.Record.modified] timestamp
 * across all records, remote content recency is the `contentTimestamp` the
 * uploading device stamped into the file's `appProperties`. Whichever side is
 * newer fully replaces the other — there is no per-record merge here (manual
 * file import via Settings still merges per-record via
 * [my.passman.data.RecordDao.importRecords]).
 *
 * Tag-only edits (rename/add/delete with no record touched) do not bump the
 * local recency signal, since [my.passman.data.Tag] has no modified column;
 * such changes sync on the next pass that also touches a record.
 */
class SyncManager @Inject constructor(
    private val appDatabase: AppDatabase,
    private val recordDao: RecordDao,
    private val tagDao: TagDao,
    private val backupManager: BackupManager,
    private val settingsRepository: SettingsRepository,
    private val driveAuthManager: DriveAuthManager,
    private val driveApiClient: DriveApiClient,
) {
    suspend fun sync(): SyncResult {
        if (settingsRepository.syncProvider.first() != SyncProvider.GOOGLE_DRIVE) return SyncResult.Disabled
        val passphrase = settingsRepository.getSyncPassphrase() ?: return SyncResult.Disabled

        return try {
            when (val authResult = driveAuthManager.authorize()) {
                is DriveAuthResult.Authorized -> performSync(authResult.accessToken, passphrase)
                is DriveAuthResult.ConsentRequired -> SyncResult.ConsentRequired(authResult.pendingIntent)
                is DriveAuthResult.Failed -> SyncResult.Failed(authResult.message)
            }
        } catch (_: BadPaddingException) {
            SyncResult.InvalidPassphrase
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
        if (settingsRepository.syncProvider.first() != SyncProvider.GOOGLE_DRIVE) return SyncResult.Disabled
        val passphrase = settingsRepository.getSyncPassphrase() ?: return SyncResult.Disabled

        return try {
            when (val authResult = driveAuthManager.authorize()) {
                is DriveAuthResult.Authorized -> {
                    val accessToken = authResult.accessToken
                    driveApiClient.findBackupFile(accessToken)?.let { remoteFile ->
                        driveApiClient.deleteBackup(accessToken, remoteFile.id)
                    }
                    val localChangedAt = recordDao.getMaxModified() ?: 0L
                    uploadLocal(accessToken, null, localChangedAt, passphrase)
                    SyncResult.Uploaded
                }
                is DriveAuthResult.ConsentRequired -> SyncResult.ConsentRequired(authResult.pendingIntent)
                is DriveAuthResult.Failed -> SyncResult.Failed(authResult.message)
            }
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

        val remoteFile = driveApiClient.findBackupFile(accessToken)
        val localChanged = localChangedAt != lastSyncedLocal
        val remoteChanged = remoteFile != null && remoteFile.contentTimestamp != lastSyncedRemote

        return when {
            remoteFile == null -> {
                uploadLocal(accessToken, null, localChangedAt, passphrase)
                SyncResult.Uploaded
            }

            localChanged && remoteChanged -> {
                if (localChangedAt >= remoteFile.contentTimestamp) {
                    uploadLocal(accessToken, remoteFile.id, localChangedAt, passphrase)
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
                uploadLocal(accessToken, remoteFile.id, localChangedAt, passphrase)
                SyncResult.Uploaded
            }

            else -> SyncResult.UpToDate
        }
    }

    private suspend fun uploadLocal(
        accessToken: String,
        existingFileId: String?,
        localChangedAt: Long,
        passphrase: CharArray,
    ) {
        val payload = backupManager.exportPayload()
        val encrypted = backupManager.encryptPayload(payload, passphrase)
        val remote =
            driveApiClient.uploadBackup(accessToken, existingFileId, encrypted, localChangedAt)
        settingsRepository.recordSyncSuccess(
            localChangedAt = localChangedAt,
            contentTimestamp = remote.contentTimestamp,
        )
    }

    private suspend fun downloadRemote(
        accessToken: String,
        remoteFile: RemoteBackupFile,
        passphrase: CharArray,
    ) {
        val encrypted = driveApiClient.downloadBackup(accessToken, remoteFile.id)
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
