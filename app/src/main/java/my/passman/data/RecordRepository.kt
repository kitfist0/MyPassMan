package my.passman.data

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordRepository @Inject constructor(
    private val recordDao: RecordDao,
    private val changeTracker: VaultChangeTracker,
) {
    fun hasRecords(): Flow<Boolean> = recordDao.hasRecords()

    fun getAllRecords(): Flow<List<RecordWithTag>> = recordDao.getAllRecords()

    fun searchRecords(query: String): Flow<List<RecordWithTag>> = recordDao.searchRecords(query)

    suspend fun getRecordById(id: Long): Record? = recordDao.getRecordById(id)

    suspend fun saveRecord(record: Record) {
        changeTracker.recordChange { recordDao.upsertRecord(record) }
    }

    suspend fun deleteRecord(id: Long) {
        val record = recordDao.getRecordById(id) ?: return
        changeTracker.recordChange { recordDao.deleteRecord(record) }
    }
}
