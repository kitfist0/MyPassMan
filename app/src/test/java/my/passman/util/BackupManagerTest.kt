package my.passman.util

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import my.passman.data.Record
import my.passman.data.RecordDao
import my.passman.data.Tag
import my.passman.data.TagColor
import my.passman.data.TagDao
import my.passman.data.VaultChangeTracker
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import javax.crypto.BadPaddingException

class BackupManagerTest {
    private val payload =
        BackupManager.BackupPayload(
            records = listOf(record(id = 1, uuid = "bank", modified = 2L, tagUuid = WORK)),
            tags = listOf(Tag(id = 1, uuid = WORK, name = "work")),
        )

    private val recordDao =
        mockk<RecordDao>(relaxUnitFun = true) {
            coEvery { getRecordsList() } returns payload.records
            coEvery { getRecordByUuid(any()) } returns null
        }
    private val tagDao =
        mockk<TagDao>(relaxUnitFun = true) {
            coEvery { getTagsList() } returns payload.tags
            coEvery { insertTag(any()) } returns NEW_TAG_ID
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

    @Nested
    inner class Import {
        @BeforeEach
        fun noLocalTags() {
            coEvery { tagDao.getTagsList() } returns emptyList()
        }

        private suspend fun import(payload: BackupManager.BackupPayload) {
            backupManager.importDatabase(backupManager.encryptPayload(payload, PASSWORD.toCharArray()), PASSWORD.toCharArray())
        }

        @Test
        fun isTrackedAsChange() =
            runTest {
                import(payload)

                coVerifyOrder {
                    changeTracker.recordChange(any<suspend () -> Any?>())
                    tagDao.insertTag(any())
                    recordDao.upsertRecord(any())
                }
            }

        @Test
        fun wrongPassword_changesNothing() =
            runTest {
                val encrypted = backupManager.encryptPayload(payload, PASSWORD.toCharArray())

                assertThrows<BadPaddingException> { backupManager.importDatabase(encrypted, "wrong".toCharArray()) }

                coVerify(exactly = 0) { changeTracker.recordChange(any<suspend () -> Any?>()) }
                coVerify(exactly = 0) { recordDao.upsertRecord(any()) }
            }

        @Test
        fun newTag_isInsertedUnderAFreshIdKeepingItsUuid() =
            runTest {
                import(payload)

                coVerify(exactly = 1) { tagDao.insertTag(Tag(id = 0, uuid = WORK, name = "work")) }
                coVerify(exactly = 1) { recordDao.upsertRecord(payload.records.single().copy(id = 0)) }
            }

        @Test
        fun tagKnownByUuid_keepsItsLocalNameAndColor() =
            runTest {
                // Renamed and recolored on this device since the backup was made.
                coEvery { tagDao.getTagsList() } returns listOf(Tag(id = 9, uuid = WORK, name = "job", color = TagColor.RED))

                import(payload)

                coVerify(exactly = 0) { tagDao.insertTag(any()) }
                coVerify(exactly = 0) { tagDao.upsertTag(any()) }
                coVerify(exactly = 1) { recordDao.upsertRecord(payload.records.single().copy(id = 0)) }
            }

        @Test
        fun tagWithLocalNameButAnotherUuid_reusesLocalTag() =
            runTest {
                // The same name, in another case, created independently on another device.
                coEvery { tagDao.getTagsList() } returns listOf(Tag(id = 9, uuid = "local-work", name = "Work", color = TagColor.RED))

                import(payload)

                coVerify(exactly = 0) { tagDao.insertTag(any()) }
                coVerify(exactly = 0) { tagDao.upsertTag(any()) }
                coVerify(exactly = 1) { recordDao.upsertRecord(payload.records.single().copy(id = 0, tagUuid = "local-work")) }
            }

        @Test
        fun tagsOfTheSameNameInOneBackup_becomeOneTag() =
            runTest {
                val other = record(id = 2, uuid = "card", modified = 2L, tagUuid = "other-work")

                import(
                    BackupManager.BackupPayload(
                        records = payload.records + other,
                        tags = payload.tags + Tag(id = 2, uuid = "other-work", name = "WORK"),
                    ),
                )

                coVerify(exactly = 1) { tagDao.insertTag(any()) }
                coVerify(exactly = 1) { recordDao.upsertRecord(other.copy(id = 0, tagUuid = WORK)) }
            }

        @Test
        fun recordTaggedWithTagMissingFromBackup_losesTheTag() =
            runTest {
                import(payload.copy(tags = emptyList()))

                coVerify(exactly = 1) { recordDao.upsertRecord(payload.records.single().copy(id = 0, tagUuid = null)) }
            }

        @Test
        fun unknownRecord_isAddedEvenIfItsIdIsTakenLocally() =
            runTest {
                // Local record 1 is a different record; it must not be overwritten.
                coEvery { recordDao.getRecordById(1) } returns record(id = 1, uuid = "mail", modified = 1L)

                import(payload)

                coVerify(exactly = 1) { recordDao.upsertRecord(match { it.id == 0L && it.uuid == "bank" }) }
            }

        @Test
        fun newerCopyOfLocalRecord_updatesItInPlace() =
            runTest {
                coEvery { recordDao.getRecordByUuid("bank") } returns record(id = 5, uuid = "bank", modified = 1L)

                import(payload)

                coVerify(exactly = 1) { recordDao.upsertRecord(payload.records.single().copy(id = 5)) }
            }

        @Test
        fun olderOrSameCopyOfLocalRecord_isSkipped() =
            runTest {
                coEvery { recordDao.getRecordByUuid("bank") } returns record(id = 5, uuid = "bank", modified = 2L)

                import(payload)

                coVerify(exactly = 0) { recordDao.upsertRecord(any()) }
            }
    }

    private companion object {
        const val PASSWORD = "correct horse"
        const val NEW_TAG_ID = 42L
        const val WORK = "work-uuid"

        fun record(
            id: Long,
            uuid: String,
            modified: Long,
            tagUuid: String? = null,
        ) = Record(
            id = id,
            uuid = uuid,
            created = 1L,
            modified = modified,
            name = "Банк",
            login = "me",
            secret = "p@ss",
            comment = "",
            tagUuid = tagUuid,
        )
    }
}
