package my.passman.data

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordRepository @Inject constructor(
    private val recordDao: RecordDao,
    private val tagDao: TagDao,
    private val changeTracker: VaultChangeTracker,
) {
    fun hasRecords(): Flow<Boolean> = recordDao.hasRecords()

    fun getAllRecords(): Flow<List<RecordWithTag>> = recordDao.getAllRecords()

    fun searchRecords(query: String): Flow<List<RecordWithTag>> = recordDao.searchRecords(query)

    suspend fun getRecordById(id: Long): Record? = recordDao.getRecordById(id)

    /**
     * Saves [record], found by its uuid rather than its id. Sync may replace the whole vault while
     * the record is open for editing, renumbering rows or deleting the record or its tag, so the
     * record keeps whatever id it has now (a deleted one is added back), and a tag that no longer
     * exists is dropped rather than failing the foreign key.
     */
    suspend fun saveRecord(record: Record) {
        changeTracker.recordChange {
            val id = recordDao.getRecordByUuid(record.uuid)?.id ?: 0
            val tagId = record.tagId?.takeIf { tagDao.getTagById(it) != null }
            recordDao.upsertRecord(record.copy(id = id, tagId = tagId))
        }
    }

    /** Deletes by uuid, since sync may have given the record's old id to another record. */
    suspend fun deleteRecord(uuid: String) {
        if (recordDao.getRecordByUuid(uuid) == null) return
        changeTracker.recordChange { recordDao.deleteRecordByUuid(uuid) }
    }
}
