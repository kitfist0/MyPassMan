package my.passman.util

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password-based encryption for backup files (`.pman`) and cloud sync payloads:
 * AES-256-GCM with the key derived from the password by [Argon2id].
 *
 * Output format (integers are big-endian):
 * ```
 * [magic "PMAN" (4)] [version (1)] [memoryKib (4)] [iterations (4)] [parallelism (4)]
 * [salt (16)] [IV (12)] [ciphertext + GCM tag]
 * ```
 * Everything before the ciphertext is passed to GCM as associated data, so a
 * tampered header fails authentication. The Argon2 parameters travel with the
 * file, so they can be raised later without breaking files written with the
 * current ones.
 *
 * Both [encrypt] and [decrypt] run Argon2id: never call them on the main thread.
 */
object CryptoUtils {
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BIT = 128
    private const val TAG_LENGTH_BYTE = TAG_LENGTH_BIT / 8
    private const val IV_LENGTH_BYTE = 12
    private const val SALT_LENGTH_BYTE = 16
    private const val KEY_LENGTH_BYTE = 32

    private val MAGIC = "PMAN".toByteArray(Charsets.US_ASCII)
    private const val FORMAT_VERSION: Byte = 1
    private const val HEADER_LENGTH_BYTE = 4 + 1 + 4 + 4 + 4
    private const val PREFIX_LENGTH_BYTE = HEADER_LENGTH_BYTE + SALT_LENGTH_BYTE + IV_LENGTH_BYTE

    // RFC 9106, second recommended option: 64 MiB, 3 passes, 4 lanes.
    private const val MEMORY_KIB = 65_536
    private const val ITERATIONS = 3
    private const val PARALLELISM = 4

    // Upper bounds for parameters read from a file, so a crafted header cannot make
    // decryption allocate unbounded memory or spin for minutes.
    private const val MAX_MEMORY_KIB = 262_144
    private const val MAX_ITERATIONS = 16
    private const val MAX_PARALLELISM = 16

    private val secureRandom = SecureRandom()

    fun encrypt(
        data: ByteArray,
        password: CharArray,
    ): ByteArray {
        val salt = ByteArray(SALT_LENGTH_BYTE).apply { secureRandom.nextBytes(this) }
        val iv = ByteArray(IV_LENGTH_BYTE).apply { secureRandom.nextBytes(this) }
        val prefix =
            ByteBuffer
                .allocate(PREFIX_LENGTH_BYTE)
                .put(MAGIC)
                .put(FORMAT_VERSION)
                .putInt(MEMORY_KIB)
                .putInt(ITERATIONS)
                .putInt(PARALLELISM)
                .put(salt)
                .put(iv)
                .array()

        val key = Argon2id.derive(password, salt, MEMORY_KIB, ITERATIONS, PARALLELISM, KEY_LENGTH_BYTE)
        val cipher =
            try {
                Cipher.getInstance(ALGORITHM).apply {
                    init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_LENGTH_BIT, iv))
                }
            } finally {
                key.fill(0)
            }
        cipher.updateAAD(prefix)

        return prefix + cipher.doFinal(data)
    }

    /**
     * @throws IllegalArgumentException if [data] is not a supported backup file.
     * @throws javax.crypto.AEADBadTagException if the password is wrong or the file was modified.
     */
    fun decrypt(
        data: ByteArray,
        password: CharArray,
    ): ByteArray {
        require(data.size >= PREFIX_LENGTH_BYTE + TAG_LENGTH_BYTE) { "Not a MyPassMan backup file" }

        val buffer = ByteBuffer.wrap(data)
        val magic = ByteArray(MAGIC.size).also { buffer.get(it) }
        require(magic.contentEquals(MAGIC)) { "Not a MyPassMan backup file" }
        val version = buffer.get()
        require(version == FORMAT_VERSION) { "Unsupported backup format version: $version" }

        val memoryKib = buffer.int
        val iterations = buffer.int
        val parallelism = buffer.int
        require(
            parallelism in 1..MAX_PARALLELISM &&
                memoryKib in 8 * parallelism..MAX_MEMORY_KIB &&
                iterations in 1..MAX_ITERATIONS,
        ) { "Unsupported backup encryption parameters" }

        val salt = ByteArray(SALT_LENGTH_BYTE).also { buffer.get(it) }
        val iv = ByteArray(IV_LENGTH_BYTE).also { buffer.get(it) }

        val key = Argon2id.derive(password, salt, memoryKib, iterations, parallelism, KEY_LENGTH_BYTE)
        val cipher =
            try {
                Cipher.getInstance(ALGORITHM).apply {
                    init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_LENGTH_BIT, iv))
                }
            } finally {
                key.fill(0)
            }
        cipher.updateAAD(data, 0, PREFIX_LENGTH_BYTE)

        return cipher.doFinal(data, PREFIX_LENGTH_BYTE, data.size - PREFIX_LENGTH_BYTE)
    }
}
