package my.passman.util

import java.security.SecureRandom
import java.security.spec.KeySpec
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object CryptoUtils {
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BIT = 128
    private const val IV_LENGTH_BYTE = 12
    private const val SALT_LENGTH_BYTE = 16
    private const val PBKDF2_ITERATIONS = 10000
    private const val KEY_LENGTH_BIT = 256

    fun encrypt(data: ByteArray, password: CharArray): ByteArray {
        val salt = ByteArray(SALT_LENGTH_BYTE).apply { SecureRandom().nextBytes(this) }
        val iv = ByteArray(IV_LENGTH_BYTE).apply { SecureRandom().nextBytes(this) }

        val secretKey = deriveKey(password, salt)
        val cipher = Cipher.getInstance(ALGORITHM).apply {
            init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH_BIT, iv))
        }

        val encryptedData = cipher.doFinal(data)
        
        // Output format: [Salt (16)] + [IV (12)] + [Encrypted Data]
        return salt + iv + encryptedData
    }

    fun decrypt(data: ByteArray, password: CharArray): ByteArray {
        if (data.size < SALT_LENGTH_BYTE + IV_LENGTH_BYTE) {
            throw IllegalArgumentException("Data too short")
        }

        val salt = data.sliceArray(0 until SALT_LENGTH_BYTE)
        val iv = data.sliceArray(SALT_LENGTH_BYTE until SALT_LENGTH_BYTE + IV_LENGTH_BYTE)
        val encryptedData = data.sliceArray(SALT_LENGTH_BYTE + IV_LENGTH_BYTE until data.size)

        val secretKey = deriveKey(password, salt)
        val cipher = Cipher.getInstance(ALGORITHM).apply {
            init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH_BIT, iv))
        }

        return cipher.doFinal(encryptedData)
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKey {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec: KeySpec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BIT)
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }
}
