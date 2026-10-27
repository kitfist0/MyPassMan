package my.passman.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import my.passman.data.Record
import my.passman.data.RecordDao
import my.passman.data.Tag
import my.passman.data.TagDao
import java.io.InputStream
import java.io.OutputStream

class BackupManager(
    private val recordDao: RecordDao,
    private val tagDao: TagDao,
) {
    @Serializable
    data class BackupPayload(
        val records: List<Record>,
        val tags: List<Tag> = emptyList(),
    )

    suspend fun exportPayload(): BackupPayload = BackupPayload(records = recordDao.getRecordsList(), tags = tagDao.getTagsList())

    // Key derivation (Argon2id) is deliberately slow, so keep it off the caller's thread.
    suspend fun encryptPayload(
        payload: BackupPayload,
        password: CharArray,
    ): ByteArray =
        withContext(Dispatchers.Default) {
            val json = Json.encodeToString(BackupPayload.serializer(), payload)
            CryptoUtils.encrypt(json.toByteArray(Charsets.UTF_8), password)
        }

    suspend fun decryptPayload(
        encryptedData: ByteArray,
        password: CharArray,
    ): BackupPayload =
        withContext(Dispatchers.Default) {
            val json = CryptoUtils.decrypt(encryptedData, password).toString(Charsets.UTF_8)
            Json.decodeFromString(BackupPayload.serializer(), json)
        }

    suspend fun exportDatabase(
        outputStream: OutputStream,
        password: CharArray,
    ) {
        val encryptedData = encryptPayload(exportPayload(), password)
        outputStream.use { it.write(encryptedData) }
    }

    suspend fun importDatabase(
        inputStream: InputStream,
        password: CharArray,
    ) {
        val encryptedData = inputStream.use { it.readBytes() }
        importDatabase(encryptedData, password)
    }

    suspend fun importDatabase(
        encryptedData: ByteArray,
        password: CharArray,
    ) {
        val payload = decryptPayload(encryptedData, password)
        tagDao.upsertTags(payload.tags)
        recordDao.importRecords(payload.records)
    }
}
