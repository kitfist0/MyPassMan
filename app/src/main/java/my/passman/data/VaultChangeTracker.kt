package my.passman.data

import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock

/**
 * Wraps every write to the vault so that [VaultState.changedAt] moves in the same transaction.
 * Repositories and [my.passman.util.BackupManager] must route their writes through here, or the
 * change will not be picked up by sync.
 */
@Singleton
class VaultChangeTracker @Inject constructor(
    private val database: AppDatabase,
    private val vaultStateDao: VaultStateDao,
    private val clock: Clock,
) {
    /** When the vault last changed, or 0 for a vault that has never been written to. */
    suspend fun changedAt(): Long = vaultStateDao.getChangedAt() ?: 0L

    /** Runs a local edit and stamps the vault as changed now. */
    suspend fun <R> recordChange(block: suspend () -> R): R =
        database.withTransaction {
            val result = block()
            val previous = vaultStateDao.getChangedAt() ?: 0L
            // Strictly increasing even if the wall clock steps back, so sync never mistakes
            // an edit for "unchanged since the last sync".
            val now = maxOf(clock.now().toEpochMilliseconds(), previous + 1)
            vaultStateDao.upsert(VaultState(changedAt = now))
            result
        }

    /** Replaces the vault with a synced copy, which keeps the recency of the device it came from. */
    suspend fun <R> applySyncedChange(
        changedAt: Long,
        block: suspend () -> R,
    ): R =
        database.withTransaction {
            val result = block()
            vaultStateDao.upsert(VaultState(changedAt = changedAt))
            result
        }
}
