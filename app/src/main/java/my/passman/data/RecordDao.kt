package my.passman.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordDao {
    @Query("SELECT * FROM records WHERE id = :id")
    suspend fun getRecordById(id: Long): Record?

    @Transaction
    @Query("SELECT * FROM records ORDER BY id DESC")
    fun getAllRecords(): Flow<List<RecordWithTag>>

    @Upsert
    suspend fun upsertRecord(record: Record)

    @Delete
    suspend fun deleteRecord(record: Record)

    @Transaction
    @Query("SELECT * FROM records WHERE name LIKE '%' || :searchQuery || '%' OR login LIKE '%' || :searchQuery || '%'")
    fun searchRecords(searchQuery: String): Flow<List<RecordWithTag>>

    @Query("SELECT * FROM records")
    suspend fun getRecordsList(): List<Record>

    @Query("SELECT MAX(modified) FROM records")
    suspend fun getMaxModified(): Long?

    @Query("DELETE FROM records")
    suspend fun deleteAllRecords()

    @Upsert
    suspend fun upsertRecords(records: List<Record>)

    @Transaction
    suspend fun importRecords(records: List<Record>) {
        records.forEach { record ->
            val existing = getRecordById(record.id)
            if (existing == null || record.modified > existing.modified) {
                upsertRecord(record)
            }
        }
    }
}
