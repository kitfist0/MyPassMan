package my.passman.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Single-row table holding when the vault's contents (records or tags) last changed. Sync compares
 * it against the value stamped on the remote backup, so it must move on every change, deletions
 * included. Kept in the database so it commits atomically with the change it describes.
 */
@Entity(tableName = "vault_state")
data class VaultState(
    @PrimaryKey
    val id: Int = SINGLE_ROW_ID,
    @ColumnInfo(name = "changed_at")
    val changedAt: Long,
) {
    companion object {
        const val SINGLE_ROW_ID = 0
    }
}
