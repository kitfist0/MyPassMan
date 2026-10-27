package my.passman.data

import my.passman.util.Argon2id
import org.bouncycastle.util.encoders.Base64
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Hashes the unlock PIN with Argon2id and a random per-PIN salt. A PIN has at
 * most ~1.1M combinations, so a fast hash like SHA-256 is brute-forced offline
 * in seconds; Argon2id makes every guess cost [MEMORY_KIB] of RAM and real CPU time.
 *
 * The result is stored as a PHC-style string
 * (`$argon2id$v=19$m=19456,t=2,p=1$<salt>$<hash>`), so the parameters can be
 * raised later without breaking PINs hashed with the old ones.
 */
object PinHasher {
    private const val ALGORITHM = "argon2id"
    private const val MEMORY_KIB = 19_456
    private const val ITERATIONS = 2
    private const val PARALLELISM = 1
    private const val SALT_LENGTH_BYTE = 16
    private const val HASH_LENGTH_BYTE = 32
    private const val LEGACY_SHA256_HEX_LENGTH = 64

    private val secureRandom = SecureRandom()

    fun hash(pin: String): String {
        val salt = ByteArray(SALT_LENGTH_BYTE).also { secureRandom.nextBytes(it) }
        val hash = argon2(pin, salt, MEMORY_KIB, ITERATIONS, PARALLELISM, HASH_LENGTH_BYTE)
        return "\$$ALGORITHM\$v=${Argon2id.VERSION}" +
            "\$m=$MEMORY_KIB,t=$ITERATIONS,p=$PARALLELISM" +
            "\$${Base64.toBase64String(salt)}\$${Base64.toBase64String(hash)}"
    }

    fun verify(
        pin: String,
        encoded: String,
    ): Boolean {
        if (isLegacy(encoded)) {
            return MessageDigest.isEqual(legacySha256(pin).toByteArray(), encoded.toByteArray())
        }
        val parts = encoded.split('$')
        if (parts.size != 6 || parts[1] != ALGORITHM) return false
        // A corrupted stored hash must fail verification, not crash the unlock screen.
        return runCatching {
            val params =
                parts[3].split(',').associate { param ->
                    val (key, value) = param.split('=', limit = 2)
                    key to value.toInt()
                }
            val salt = Base64.decode(parts[4])
            val expected = Base64.decode(parts[5])
            val actual =
                argon2(
                    pin = pin,
                    salt = salt,
                    memoryKib = params.getValue("m"),
                    iterations = params.getValue("t"),
                    parallelism = params.getValue("p"),
                    length = expected.size,
                )
            MessageDigest.isEqual(actual, expected)
        }.getOrDefault(false)
    }

    /** True for an unsalted SHA-256 hex digest stored before PINs were hashed with Argon2. */
    fun isLegacy(encoded: String): Boolean =
        encoded.length == LEGACY_SHA256_HEX_LENGTH && encoded.all { it in '0'..'9' || it in 'a'..'f' }

    private fun argon2(
        pin: String,
        salt: ByteArray,
        memoryKib: Int,
        iterations: Int,
        parallelism: Int,
        length: Int,
    ): ByteArray = Argon2id.derive(pin.toCharArray(), salt, memoryKib, iterations, parallelism, length)

    private fun legacySha256(pin: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(pin.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
