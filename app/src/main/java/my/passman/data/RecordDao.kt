package my.passman.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordDao {
    @Query("SELECT * FROM records WHERE id = :id")
    suspend fun getRecordById(id: Long): Record?

    @Query("SELECT * FROM records ORDER BY id DESC")
    fun getAllRecords(): Flow<List<Record>>

    @Upsert
    suspend fun upsertRecord(record: Record)

    @Delete
    suspend fun deleteRecord(record: Record)

    @Query("SELECT * FROM records WHERE name LIKE '%' || :searchQuery || '%' OR login LIKE '%' || :searchQuery || '%'")
    fun searchRecords(searchQuery: String): Flow<List<Record>>

    @Query("SELECT * FROM records")
    suspend fun getRecordsList(): List<Record>

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
