package my.passman.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.ByteBuffer
import javax.crypto.AEADBadTagException

class CryptoUtilsTest {
    private val plainText = "{\"records\":[],\"tags\":[]}".toByteArray()
    private val password = "correct horse".toCharArray()

    @Test
    fun decrypt_returnsOriginalData() {
        val encrypted = CryptoUtils.encrypt(plainText, password)

        assertArrayEquals(plainText, CryptoUtils.decrypt(encrypted, password))
    }

    @Test
    fun encrypt_writesVersionedArgon2Header() {
        val header = ByteBuffer.wrap(CryptoUtils.encrypt(plainText, password))

        assertArrayEquals("PMAN".toByteArray(), ByteArray(4).also { header.get(it) })
        assertArrayEquals(
            intArrayOf(1, 65_536, 3, 4),
            intArrayOf(header.get().toInt(), header.int, header.int, header.int),
        )
    }

    @Test
    fun encrypt_isSaltedPerCall() {
        assertFalse(CryptoUtils.encrypt(plainText, password).contentEquals(CryptoUtils.encrypt(plainText, password)))
    }

    @Test
    fun decrypt_rejectsWrongPassword() {
        val encrypted = CryptoUtils.encrypt(plainText, password)

        assertThrows(AEADBadTagException::class.java) {
            CryptoUtils.decrypt(encrypted, "wrong horse".toCharArray())
        }
    }

    @Test
    fun decrypt_rejectsTamperedHeader() {
        // Lower the iteration count from 3 to 2: still within bounds, but authenticated as associated data.
        val encrypted = CryptoUtils.encrypt(plainText, password).also { it[ITERATIONS_LAST_BYTE] = 2 }

        assertThrows(AEADBadTagException::class.java) { CryptoUtils.decrypt(encrypted, password) }
    }

    @Test
    fun decrypt_rejectsTamperedCiphertext() {
        val encrypted = CryptoUtils.encrypt(plainText, password)
        encrypted[encrypted.lastIndex] = (encrypted.last() + 1).toByte()

        assertThrows(AEADBadTagException::class.java) { CryptoUtils.decrypt(encrypted, password) }
    }

    @Test
    fun decrypt_rejectsForeignFile() {
        assertThrows(IllegalArgumentException::class.java) {
            CryptoUtils.decrypt(ByteArray(128) { it.toByte() }, password)
        }
    }

    @Test
    fun decrypt_rejectsTruncatedFile() {
        val encrypted = CryptoUtils.encrypt(plainText, password)

        assertThrows(IllegalArgumentException::class.java) {
            CryptoUtils.decrypt(encrypted.copyOf(20), password)
        }
    }

    @Test
    fun decrypt_rejectsUnknownVersion() {
        val encrypted = CryptoUtils.encrypt(plainText, password).also { it[VERSION_BYTE] = 2 }

        assertThrows(IllegalArgumentException::class.java) { CryptoUtils.decrypt(encrypted, password) }
    }

    @Test
    fun decrypt_rejectsExcessiveMemoryParameter() {
        // 0x7F at the top of memoryKib asks for ~2 TiB; must be refused before Argon2 allocates anything.
        val encrypted = CryptoUtils.encrypt(plainText, password).also { it[MEMORY_FIRST_BYTE] = 0x7F }

        assertThrows(IllegalArgumentException::class.java) { CryptoUtils.decrypt(encrypted, password) }
    }

    private companion object {
        const val VERSION_BYTE = 4
        const val MEMORY_FIRST_BYTE = 5
        const val ITERATIONS_LAST_BYTE = 12
    }
}
