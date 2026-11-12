package my.passman.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface VaultStateDao {
    @Query("SELECT changed_at FROM vault_state WHERE id = ${VaultState.SINGLE_ROW_ID}")
    suspend fun getChangedAt(): Long?

    @Upsert
    suspend fun upsert(state: VaultState)
}
