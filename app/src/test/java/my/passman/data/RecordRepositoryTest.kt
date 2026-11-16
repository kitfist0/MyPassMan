package my.passman.data

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class RecordRepositoryTest {
    private val record =
        Record(id = 7, uuid = "uuid-7", created = 1L, modified = 2L, name = "bank", login = "me", secret = "s", comment = "", tagId = 3)

    private val recordDao =
        mockk<RecordDao>(relaxUnitFun = true) {
            coEvery { getRecordById(any()) } returns null
            coEvery { getRecordById(record.id) } returns record
            coEvery { getRecordByUuid(any()) } returns null
            coEvery { getRecordByUuid(record.uuid) } returns record
        }
    private val tagDao =
        mockk<TagDao> {
            coEvery { getTagById(any()) } returns null
            coEvery { getTagById(3) } returns Tag(id = 3, name = "work")
        }
    private val changeTracker =
        mockk<VaultChangeTracker> {
            coEvery { recordChange(any<suspend () -> Any?>()) } coAnswers { firstArg<suspend () -> Any?>().invoke() }
        }

    private val repository = RecordRepository(recordDao, tagDao, changeTracker)

    @Test
    fun reads_delegateToDao() =
        runTest {
            val all = flowOf(emptyList<RecordWithTag>())
            val found = flowOf(emptyList<RecordWithTag>())
            val hasRecords = flowOf(true)
            every { recordDao.getAllRecords() } returns all
            every { recordDao.searchRecords("ba") } returns found
            every { recordDao.hasRecords() } returns hasRecords

            assertSame(all, repository.getAllRecords())
            assertSame(found, repository.searchRecords("ba"))
            assertSame(hasRecords, repository.hasRecords())
            assertEquals(record, repository.getRecordById(record.id))
        }

    @Test
    fun saveRecord_isTrackedAsChange() =
        runTest {
            repository.saveRecord(record)

            coVerifyOrder {
                changeTracker.recordChange(any<suspend () -> Any?>())
                recordDao.upsertRecord(record)
            }
        }

    @Test
    fun saveRecord_newRecord_isInserted() =
        runTest {
            repository.saveRecord(record.copy(id = 0, uuid = "new"))

            coVerify(exactly = 1) { recordDao.upsertRecord(record.copy(id = 0, uuid = "new")) }
        }

    @Test
    fun saveRecord_renumberedBySync_keepsItsCurrentId() =
        runTest {
            // Sync replaced the vault while the record was open: it is now row 12, and row 7 is another record.
            coEvery { recordDao.getRecordByUuid(record.uuid) } returns record.copy(id = 12)

            repository.saveRecord(record.copy(name = "edited"))

            coVerify(exactly = 1) { recordDao.upsertRecord(record.copy(id = 12, name = "edited")) }
        }

    @Test
    fun saveRecord_deletedBySync_isAddedBack() =
        runTest {
            coEvery { recordDao.getRecordByUuid(record.uuid) } returns null

            repository.saveRecord(record)

            coVerify(exactly = 1) { recordDao.upsertRecord(record.copy(id = 0)) }
        }

    @Test
    fun saveRecord_withTagThatNoLongerExists_dropsTheTag() =
        runTest {
            repository.saveRecord(record.copy(tagId = 99))

            coVerify(exactly = 1) { recordDao.upsertRecord(record.copy(tagId = null)) }
        }

    @Test
    fun deleteRecord_isTrackedAsChange() =
        runTest {
            repository.deleteRecord(record.uuid)

            coVerifyOrder {
                changeTracker.recordChange(any<suspend () -> Any?>())
                recordDao.deleteRecordByUuid(record.uuid)
            }
        }

    @Test
    fun deleteRecord_missing_changesNothing() =
        runTest {
            repository.deleteRecord("missing")

            coVerify(exactly = 0) { recordDao.deleteRecordByUuid(any()) }
            coVerify(exactly = 0) { changeTracker.recordChange(any<suspend () -> Any?>()) }
        }
}
