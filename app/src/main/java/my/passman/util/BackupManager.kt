package my.passman.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import my.passman.data.Record
import my.passman.data.RecordDao
import my.passman.data.Tag
import my.passman.data.TagDao
import my.passman.data.VaultChangeTracker
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupManager @Inject constructor(
    private val recordDao: RecordDao,
    private val tagDao: TagDao,
    private val changeTracker: VaultChangeTracker,
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
        changeTracker.recordChange { mergeIntoVault(payload) }
    }

    /**
     * Merges a backup into the vault. Row ids only mean something on the device that made them, so
     * nothing is matched by id: tags are matched by their unique name (an existing tag keeps its
     * color) and records by [Record.uuid], where the newer [Record.modified] wins. Imported records
     * are pointed at the local ids of their tags.
     */
    private suspend fun mergeIntoVault(payload: BackupPayload) {
        val localTagIds =
            payload.tags.associate { tag ->
                tag.id to (tagDao.getTagByName(tag.name)?.id ?: tagDao.insertTag(tag.copy(id = 0)))
            }
        payload.records.forEach { record ->
            val existing = recordDao.getRecordByUuid(record.uuid)
            if (existing != null && record.modified <= existing.modified) return@forEach
            recordDao.upsertRecord(
                record.copy(id = existing?.id ?: 0, tagId = record.tagId?.let(localTagIds::get)),
            )
        }
    }

    /**
     * Replaces the whole vault with a synced copy. Unlike [importDatabase] nothing is merged, and the
     * vault takes [changedAt] from the copy rather than counting as a fresh local edit.
     */
    suspend fun replaceVault(
        payload: BackupPayload,
        changedAt: Long,
    ) {
        changeTracker.applySyncedChange(changedAt) {
            recordDao.deleteAllRecords()
            tagDao.deleteAllTags()
            tagDao.upsertTags(payload.tags)
            recordDao.upsertRecords(payload.records)
        }
    }
}
