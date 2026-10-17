package my.passman.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class EncryptedDatabaseTest {
    private lateinit var context: Context
    private lateinit var dbFile: File

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(DB_NAME)
        dbFile = context.getDatabasePath(DB_NAME)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun newDatabaseIsEncrypted() =
        runBlocking {
            encryptedDatabase().use { db -> db.recordDao().upsertRecord(record("new")) }

            assertTrue(dbFile.exists())
            assertFalse(dbFile.hasPlaintextHeader())
            encryptedDatabase().use { db ->
                assertEquals(listOf("new"), db.recordDao().getRecordsList().map { it.name })
            }
        }

    private fun encryptedDatabase(): AppDatabase =
        Room
            .databaseBuilder(context, AppDatabase::class.java, DB_NAME)
            .openHelperFactory(EncryptedOpenHelperFactory(context))
            .build()

    private fun record(name: String) =
        Record(created = 1, modified = 1, name = name, login = "login", secret = "secret", comment = "")

    private fun File.hasPlaintextHeader(): Boolean = readBytes().copyOf(16).decodeToString() == "SQLite format 3\u0000"

    private inline fun <T> AppDatabase.use(block: (AppDatabase) -> T): T =
        try {
            block(this)
        } finally {
            close()
        }

    private companion object {
        const val DB_NAME = "encrypted_database_test"
    }
}
