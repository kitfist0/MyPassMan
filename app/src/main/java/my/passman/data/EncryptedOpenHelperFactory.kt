package my.passman.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Opens the Room database through SQLCipher, keyed with [DatabaseKey].
 *
 * Room creates its open helper while building the database (often on the main
 * thread), so the real helper and its Keystore access are deferred until Room
 * first needs a connection, which happens on its query executor.
 */
class EncryptedOpenHelperFactory(
    context: Context,
) : SupportSQLiteOpenHelper.Factory {
    private val appContext = context.applicationContext

    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper =
        LazyOpenHelper(configuration.name) {
            val passphrase = prepareDatabase(appContext, configuration.name)
            SupportOpenHelperFactory(passphrase).create(configuration)
        }

    private class LazyOpenHelper(
        override val databaseName: String?,
        private val factory: () -> SupportSQLiteOpenHelper,
    ) : SupportSQLiteOpenHelper {
        private var delegate: SupportSQLiteOpenHelper? = null
        private var writeAheadLoggingEnabled: Boolean? = null

        @Synchronized
        private fun delegate(): SupportSQLiteOpenHelper =
            delegate ?: factory().also { helper ->
                writeAheadLoggingEnabled?.let(helper::setWriteAheadLoggingEnabled)
                delegate = helper
            }

        @Synchronized
        override fun setWriteAheadLoggingEnabled(enabled: Boolean) {
            writeAheadLoggingEnabled = enabled
            delegate?.setWriteAheadLoggingEnabled(enabled)
        }

        override val writableDatabase: SupportSQLiteDatabase
            get() = delegate().writableDatabase

        override val readableDatabase: SupportSQLiteDatabase
            get() = delegate().readableDatabase

        @Synchronized
        override fun close() {
            delegate?.close()
        }
    }

    private companion object {
        /**
         * Returns the SQLCipher passphrase for [name]. A database left without a
         * usable key (Keystore key lost, or a plaintext file from a pre-encryption
         * dev build) can never be opened, so it is discarded.
         */
        fun prepareDatabase(
            context: Context,
            name: String?,
        ): ByteArray {
            System.loadLibrary("sqlcipher")
            val key =
                DatabaseKey.load(context) ?: run {
                    // Discard the database before persisting the new key: if the process
                    // dies in between, the next launch still sees no key and retries,
                    // instead of pairing the new key with an unreadable file.
                    // In-memory databases have no name and nothing on disk to discard.
                    if (name != null) context.deleteDatabase(name)
                    DatabaseKey.create(context)
                }
            return key.toPassphrase()
        }
    }
}
