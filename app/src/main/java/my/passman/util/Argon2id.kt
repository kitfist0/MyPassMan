package my.passman.util

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters

/**
 * Argon2id (RFC 9106, version 0x13) via BouncyCastle. Memory-hard, so every
 * password guess costs [memoryKib] of RAM, which blunts GPU/ASIC brute force.
 *
 * This is CPU- and memory-heavy by design: never call it on the main thread.
 */
object Argon2id {
    const val VERSION = Argon2Parameters.ARGON2_VERSION_13

    fun derive(
        password: CharArray,
        salt: ByteArray,
        memoryKib: Int,
        iterations: Int,
        parallelism: Int,
        length: Int,
    ): ByteArray {
        val parameters =
            Argon2Parameters
                .Builder(Argon2Parameters.ARGON2_id)
                .withVersion(VERSION)
                .withSalt(salt)
                .withMemoryAsKB(memoryKib)
                .withIterations(iterations)
                .withParallelism(parallelism)
                .build()
        val generator = Argon2BytesGenerator().apply { init(parameters) }
        return ByteArray(length).also { generator.generateBytes(password, it) }
    }
}
