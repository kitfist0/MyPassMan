package my.passman.util

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
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

    fun encryptPayload(
        payload: BackupPayload,
        password: CharArray,
    ): ByteArray {
        val json = Json.encodeToString(BackupPayload.serializer(), payload)
        return CryptoUtils.encrypt(json.toByteArray(Charsets.UTF_8), password)
    }

    fun decryptPayload(
        encryptedData: ByteArray,
        password: CharArray,
    ): BackupPayload {
        val json = CryptoUtils.decrypt(encryptedData, password).toString(Charsets.UTF_8)
        return try {
            Json.decodeFromString(BackupPayload.serializer(), json)
        } catch (_: SerializationException) {
            // Backups created before tags were included in the export are a bare JSON array of records.
            BackupPayload(records = Json.decodeFromString(json), tags = emptyList())
        }
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
