package my.passman.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Wraps small secrets (e.g. the Drive sync passphrase) with a non-exportable
 * Android Keystore AES key so they can be persisted at rest on this device
 * without prompting the user again. Unlike [my.passman.util.CryptoUtils], the
 * resulting ciphertext cannot be decrypted on another device or after the key
 * is lost (e.g. app data cleared) — it is not meant to be portable.
 *
 * Each [keyAlias] is a separate Keystore key, so clearing one (e.g. on sync
 * sign-out) does not invalidate secrets wrapped under another.
 */
object DeviceBoundCipher {
    const val SYNC_KEY_ALIAS = "sync_passphrase_key"
    const val DATABASE_KEY_ALIAS = "database_key"

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BIT = 128
    private const val IV_LENGTH_BYTE = 12

    fun encryptToString(
        plainText: String,
        keyAlias: String = SYNC_KEY_ALIAS,
    ): String {
        val payload = encrypt(plainText.toByteArray(Charsets.UTF_8), keyAlias)
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    fun decryptFromString(
        encoded: String,
        keyAlias: String = SYNC_KEY_ALIAS,
    ): String {
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        return decrypt(payload, keyAlias).toString(Charsets.UTF_8)
    }

    /** Output format: [IV (12)] + [Encrypted Data]. */
    fun encrypt(
        data: ByteArray,
        keyAlias: String = SYNC_KEY_ALIAS,
    ): ByteArray {
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey(keyAlias))
        return cipher.iv + cipher.doFinal(data)
    }

    fun decrypt(
        payload: ByteArray,
        keyAlias: String = SYNC_KEY_ALIAS,
    ): ByteArray {
        val iv = payload.sliceArray(0 until IV_LENGTH_BYTE)
        val encrypted = payload.sliceArray(IV_LENGTH_BYTE until payload.size)
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(keyAlias),
            GCMParameterSpec(TAG_LENGTH_BIT, iv),
        )
        return cipher.doFinal(encrypted)
    }

    fun clearKey(keyAlias: String = SYNC_KEY_ALIAS) {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(keyAlias)) {
            keyStore.deleteEntry(keyAlias)
        }
    }

    private fun getOrCreateKey(keyAlias: String): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        (keyStore.getKey(keyAlias, null) as? SecretKey)
            ?.let { return it }

        val keyGenerator =
            KeyGenerator
                .getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec =
            KeyGenParameterSpec
                .Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }
}
