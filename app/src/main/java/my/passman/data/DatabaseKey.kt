package my.passman.data

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.security.SecureRandom

/**
 * The SQLCipher key for the local database: 32 random bytes, persisted only in
 * wrapped form (see [DeviceBoundCipher.DATABASE_KEY_ALIAS]) inside
 * `noBackupFilesDir`, so neither the key file nor the Keystore key ever leave
 * this device.
 */
class DatabaseKey private constructor(
    private val raw: ByteArray,
) {
    /** SQLCipher raw-key literal (`x'<64 hex>'`), which skips its own PBKDF2 pass. */
    fun toPassphrase(): ByteArray = "x'${raw.toHex()}'".toByteArray(Charsets.US_ASCII)

    companion object {
        private const val KEY_FILE_NAME = "database.key"
        private const val KEY_LENGTH_BYTE = 32

        /** Returns the stored key, or `null` if there is none or it can no longer be unwrapped. */
        fun load(context: Context): DatabaseKey? {
            val file = keyFile(context)
            if (!file.baseFile.exists()) return null
            return try {
                DeviceBoundCipher
                    .decrypt(file.readFully(), DeviceBoundCipher.DATABASE_KEY_ALIAS)
                    .takeIf { it.size == KEY_LENGTH_BYTE }
                    ?.let(::DatabaseKey)
            } catch (_: Exception) {
                // The Keystore key is gone (e.g. lock screen reset on some OEMs) or the
                // file is corrupt — either way the old key is unrecoverable.
                null
            }
        }

        /** Generates a fresh key and persists it, replacing any stored one. */
        fun create(context: Context): DatabaseKey {
            val file = keyFile(context)
            val raw = ByteArray(KEY_LENGTH_BYTE).apply { SecureRandom().nextBytes(this) }
            val wrapped = DeviceBoundCipher.encrypt(raw, DeviceBoundCipher.DATABASE_KEY_ALIAS)
            val stream = file.startWrite()
            try {
                stream.write(wrapped)
                file.finishWrite(stream)
            } catch (e: Exception) {
                file.failWrite(stream)
                throw e
            }
            return DatabaseKey(raw)
        }

        private fun keyFile(context: Context) = AtomicFile(File(context.noBackupFilesDir, KEY_FILE_NAME))

        private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
    }
}
