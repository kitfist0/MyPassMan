package my.passman.util

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import my.passman.data.Record
import my.passman.data.RecordDao
import my.passman.data.Tag
import my.passman.data.TagDao
import my.passman.data.VaultChangeTracker
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import javax.crypto.BadPaddingException

class BackupManagerTest {
    private val payload =
        BackupManager.BackupPayload(
            records = listOf(Record(id = 1, created = 1L, modified = 2L, name = "Банк", login = "me", secret = "p@ss", comment = "")),
            tags = listOf(Tag(id = 1, name = "work")),
        )

    private val recordDao =
        mockk<RecordDao>(relaxUnitFun = true) {
            coEvery { getRecordsList() } returns payload.records
        }
    private val tagDao =
        mockk<TagDao>(relaxUnitFun = true) {
            coEvery { getTagsList() } returns payload.tags
        }
    private val changeTracker =
        mockk<VaultChangeTracker> {
            coEvery { recordChange(any<suspend () -> Any?>()) } coAnswers { firstArg<suspend () -> Any?>().invoke() }
            coEvery { applySyncedChange(any(), any<suspend () -> Any?>()) } coAnswers {
                secondArg<suspend () -> Any?>().invoke()
            }
        }

    private val backupManager = BackupManager(recordDao, tagDao, changeTracker)

    @Test
    fun exportedPayload_decryptsBackIdentically() =
        runTest {
            val encrypted = backupManager.encryptPayload(backupManager.exportPayload(), PASSWORD.toCharArray())

            assertEquals(payload, backupManager.decryptPayload(encrypted, PASSWORD.toCharArray()))
        }

    @Test
    fun importDatabase_mergesAndIsTrackedAsChange() =
        runTest {
            val encrypted = backupManager.encryptPayload(payload, PASSWORD.toCharArray())

            backupManager.importDatabase(encrypted, PASSWORD.toCharArray())

            coVerifyOrder {
                changeTracker.recordChange(any<suspend () -> Any?>())
                tagDao.upsertTags(payload.tags)
                recordDao.importRecords(payload.records)
            }
        }

    @Test
    fun importDatabase_wrongPassword_changesNothing() =
        runTest {
            val encrypted = backupManager.encryptPayload(payload, PASSWORD.toCharArray())

            assertThrows<BadPaddingException> { backupManager.importDatabase(encrypted, "wrong".toCharArray()) }

            coVerify(exactly = 0) { changeTracker.recordChange(any<suspend () -> Any?>()) }
            coVerify(exactly = 0) { recordDao.importRecords(any()) }
        }

    @Test
    fun replaceVault_replacesEverything_withTheSyncedTimestamp() =
        runTest {
            backupManager.replaceVault(payload, changedAt = 1234L)

            coVerifyOrder {
                changeTracker.applySyncedChange(1234L, any<suspend () -> Any?>())
                recordDao.deleteAllRecords()
                tagDao.deleteAllTags()
                tagDao.upsertTags(payload.tags)
                recordDao.upsertRecords(payload.records)
            }
            coVerify(exactly = 0) { changeTracker.recordChange(any<suspend () -> Any?>()) }
        }

    private companion object {
        const val PASSWORD = "correct horse"
    }
}
