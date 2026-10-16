package my.passman.data

import android.content.Context
import android.util.AtomicFile
import my.passman.util.KeystoreCipher
import java.io.File
import java.security.SecureRandom

/**
 * The SQLCipher key for the local database: 32 random bytes, persisted only in
 * wrapped form (see [KeystoreCipher.DATABASE_KEY_ALIAS]) inside
 * `noBackupFilesDir`, so neither the key file nor the Keystore key ever leave
 * this device.
 *
 * @property isNew `true` when no usable key existed and a fresh one was just
 * generated — any encrypted database already on disk is then unreadable.
 */
class DatabaseKey private constructor(
    private val raw: ByteArray,
    val isNew: Boolean,
) {
    /** SQLCipher raw-key literal (`x'<64 hex>'`), which skips its own PBKDF2 pass. */
    fun toPassphrase(): ByteArray = "x'${raw.toHex()}'".toByteArray(Charsets.US_ASCII)

    companion object {
        private const val KEY_FILE_NAME = "database.key"
        private const val KEY_LENGTH_BYTE = 32

        fun getOrCreate(context: Context): DatabaseKey {
            val file = AtomicFile(File(context.noBackupFilesDir, KEY_FILE_NAME))
            load(file)?.let { return DatabaseKey(it, isNew = false) }

            val raw = ByteArray(KEY_LENGTH_BYTE).apply { SecureRandom().nextBytes(this) }
            val wrapped = KeystoreCipher.encrypt(raw, KeystoreCipher.DATABASE_KEY_ALIAS)
            val stream = file.startWrite()
            try {
                stream.write(wrapped)
                file.finishWrite(stream)
            } catch (e: Exception) {
                file.failWrite(stream)
                throw e
            }
            return DatabaseKey(raw, isNew = true)
        }

        private fun load(file: AtomicFile): ByteArray? {
            if (!file.baseFile.exists()) return null
            return try {
                KeystoreCipher
                    .decrypt(file.readFully(), KeystoreCipher.DATABASE_KEY_ALIAS)
                    .takeIf { it.size == KEY_LENGTH_BYTE }
            } catch (_: Exception) {
                // The Keystore key is gone (e.g. lock screen reset on some OEMs) or the
                // file is corrupt — either way the old key is unrecoverable.
                null
            }
        }

        private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
    }
}
