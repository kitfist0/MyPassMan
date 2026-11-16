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
        Record(id = 7, uuid = "uuid-7", created = 1L, modified = 2L, name = "bank", login = "me", secret = "s", comment = "")

    private val recordDao =
        mockk<RecordDao>(relaxUnitFun = true) {
            coEvery { getRecordById(any()) } returns null
            coEvery { getRecordById(record.id) } returns record
        }
    private val changeTracker =
        mockk<VaultChangeTracker> {
            coEvery { recordChange(any<suspend () -> Any?>()) } coAnswers { firstArg<suspend () -> Any?>().invoke() }
        }

    private val repository = RecordRepository(recordDao, changeTracker)

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
    fun deleteRecord_isTrackedAsChange() =
        runTest {
            repository.deleteRecord(record.id)

            coVerifyOrder {
                changeTracker.recordChange(any<suspend () -> Any?>())
                recordDao.deleteRecord(record)
            }
        }

    @Test
    fun deleteRecord_missing_changesNothing() =
        runTest {
            repository.deleteRecord(99)

            coVerify(exactly = 0) { recordDao.deleteRecord(any()) }
            coVerify(exactly = 0) { changeTracker.recordChange(any<suspend () -> Any?>()) }
        }
}
