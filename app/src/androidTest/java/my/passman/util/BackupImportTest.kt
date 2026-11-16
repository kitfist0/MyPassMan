package my.passman.util

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import my.passman.data.AppDatabase
import my.passman.data.Record
import my.passman.data.Tag
import my.passman.data.VaultChangeTracker
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Clock

/**
 * Runs the import merge against a real database: the bugs it guards against came from SQLite's
 * unique and foreign key constraints, which mocks do not have.
 */
@RunWith(AndroidJUnit4::class)
class BackupImportTest {
    private lateinit var db: AppDatabase
    private lateinit var backupManager: BackupManager

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val tracker = VaultChangeTracker(db, db.vaultStateDao(), Clock.System)
        backupManager = BackupManager(db.recordDao(), db.tagDao(), tracker)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun backupFromAnotherDevice_mergesWithoutTouchingUnrelatedLocalRows() =
        runBlocking {
            val workId = db.tagDao().insertTag(Tag(uuid = "local-work", name = "work"))
            db.recordDao().upsertRecord(record(uuid = "mail", name = "mail", tagUuid = "local-work"))
            val mailId = db.recordDao().getRecordByUuid("mail")!!.id

            // The other device numbered its rows independently, so the ids collide with local ones,
            // and it created its own "work" tag under another uuid.
            val backup =
                BackupManager.BackupPayload(
                    tags = listOf(Tag(id = workId, uuid = "bank", name = "bank"), Tag(id = 5, uuid = "other-work", name = "work")),
                    records =
                        listOf(
                            record(id = mailId, uuid = "card", name = "card", tagUuid = "other-work"),
                            record(id = 2, uuid = "loan", name = "loan", tagUuid = "bank"),
                        ),
                )

            import(backup)
            import(backup) // A second import of the same file must not duplicate anything.

            val tags = db.tagDao().getTagsList()
            assertEquals(setOf("work" to "local-work", "bank" to "bank"), tags.map { it.name to it.uuid }.toSet())
            assertEquals(workId, tags.single { it.name == "work" }.id)
            assertEquals(
                mapOf("mail" to "local-work", "card" to "local-work", "loan" to "bank"),
                db.recordDao().getRecordsList().associate { it.name to it.tagUuid },
            )
            assertEquals(mailId, db.recordDao().getRecordByUuid("mail")!!.id)
        }

    @Test
    fun deletingATag_clearsItFromRecords() =
        runBlocking {
            db.tagDao().insertTag(Tag(uuid = "work", name = "work"))
            db.recordDao().upsertRecord(record(uuid = "mail", name = "mail", tagUuid = "work"))

            db.tagDao().deleteTag(db.tagDao().getTagByUuid("work")!!)

            assertEquals(null, db.recordDao().getRecordByUuid("mail")!!.tagUuid)
        }

    private suspend fun import(payload: BackupManager.BackupPayload) {
        val password = "pw".toCharArray()
        backupManager.importDatabase(backupManager.encryptPayload(payload, password), password.copyOf())
    }

    private fun record(
        id: Long = 0,
        uuid: String,
        name: String,
        tagUuid: String?,
    ) = Record(
        id = id,
        uuid = uuid,
        created = 1,
        modified = 1,
        name = name,
        login = "login",
        secret = "secret",
        comment = "",
        tagUuid = tagUuid,
    )
}
