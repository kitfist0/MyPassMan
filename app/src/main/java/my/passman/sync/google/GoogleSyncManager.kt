package my.passman.sync.google

import kotlinx.coroutines.flow.first
import my.passman.data.SettingsRepository
import my.passman.data.SyncProvider
import my.passman.data.VaultChangeTracker
import my.passman.sync.SyncResult
import my.passman.util.BackupManager
import javax.crypto.BadPaddingException
import javax.inject.Inject

/**
 * Orchestrates one sync pass against the app's Drive `appDataFolder` backup
 * file. Conflict policy is whole-payload "newer content wins": local content
 * recency is [VaultChangeTracker.changedAt], which every record and tag edit
 * (deletions included) moves; remote content recency is the `contentTimestamp`
 * the uploading device stamped into the file's `appProperties`. Whichever side
 * is newer fully replaces the other — there is no per-record merge here (manual
 * file import via Settings still merges per-record via
 * [my.passman.data.RecordDao.importRecords]).
 */
class GoogleSyncManager @Inject constructor(
    private val changeTracker: VaultChangeTracker,
    private val backupManager: BackupManager,
    private val settingsRepository: SettingsRepository,
    private val driveAuthManager: GoogleDriveAuthManager,
    private val driveApiClient: GoogleDriveApiClient,
) {
    suspend fun signOut() {
        driveAuthManager.signOut()
    }

    /** Requests just the account/consent step, with no data transfer — used to pick an account before setup. */
    suspend fun authorize(): GoogleDriveAuthResult = driveAuthManager.authorize()

    suspend fun sync(): SyncResult {
        if (settingsRepository.syncProvider.first() != SyncProvider.GOOGLE_DRIVE) return SyncResult.Disabled
        val passphrase = settingsRepository.getSyncPassphrase() ?: return SyncResult.Disabled

        return try {
            when (val authResult = driveAuthManager.authorize()) {
                is GoogleDriveAuthResult.Authorized -> performSync(authResult.accessToken, passphrase)
                is GoogleDriveAuthResult.ConsentRequired -> SyncResult.ConsentRequired(authResult.pendingIntent)
                is GoogleDriveAuthResult.Failed -> SyncResult.Failed(authResult.message)
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
                is GoogleDriveAuthResult.Authorized -> {
                    val accessToken = authResult.accessToken
                    driveApiClient.findBackupFile(accessToken)?.let { remoteFile ->
                        driveApiClient.deleteBackup(accessToken, remoteFile.id)
                    }
                    val localChangedAt = changeTracker.changedAt()
                    uploadLocal(accessToken, null, localChangedAt, passphrase)
                    SyncResult.Uploaded
                }
                is GoogleDriveAuthResult.ConsentRequired -> SyncResult.ConsentRequired(authResult.pendingIntent)
                is GoogleDriveAuthResult.Failed -> SyncResult.Failed(authResult.message)
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
        val localChangedAt = changeTracker.changedAt()
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
        backupManager.replaceVault(payload, changedAt = remoteFile.contentTimestamp)
        settingsRepository.recordSyncSuccess(
            localChangedAt = remoteFile.contentTimestamp,
            contentTimestamp = remoteFile.contentTimestamp,
        )
    }
}
