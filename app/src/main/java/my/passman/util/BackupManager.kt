package my.passman.util

import kotlinx.serialization.json.Json
import my.passman.data.Record
import my.passman.data.RecordDao
import java.io.InputStream
import java.io.OutputStream

class BackupManager(
    private val recordDao: RecordDao,
) {
    suspend fun exportDatabase(
        outputStream: OutputStream,
        password: CharArray,
    ) {
        val records = recordDao.getRecordsList()
        val json = Json.encodeToString(records)
        val encryptedData = CryptoUtils.encrypt(json.toByteArray(Charsets.UTF_8), password)
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
        val decryptedData = CryptoUtils.decrypt(encryptedData, password)
        val json = decryptedData.toString(Charsets.UTF_8)
        val records = Json.decodeFromString<List<Record>>(json)
        recordDao.importRecords(records)
    }
}
