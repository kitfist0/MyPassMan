package my.passman.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import my.passman.util.KeystoreCipher
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val sortOrder: Flow<SortOrder> =
        dataStore.data
            .map { preferences ->
                val sortOrderName = preferences[SORT_ORDER] ?: SortOrder.BY_NAME.name
                SortOrder.valueOf(sortOrderName)
            }

    val appTheme: Flow<AppTheme> =
        dataStore.data
            .map { preferences ->
                val themeName = preferences[APP_THEME] ?: AppTheme.SYSTEM.name
                AppTheme.valueOf(themeName)
            }

    val pinHash: Flow<String?> =
        dataStore.data
            .map { preferences -> preferences[PIN_HASH] }

    val pinLength: Flow<Int> =
        dataStore.data
            .map { preferences -> preferences[PIN_LENGTH] ?: 4 }

    val fingerprintEnabled: Flow<Boolean> =
        dataStore.data
            .map { preferences -> preferences[FINGERPRINT_ENABLED] ?: false }

    val syncProvider: Flow<SyncProvider> =
        dataStore.data
            .map { preferences ->
                preferences[SYNC_PROVIDER]?.let { name ->
                    runCatching { SyncProvider.valueOf(name) }.getOrNull()
                } ?: SyncProvider.NONE
            }

    val hasSyncPassphrase: Flow<Boolean> =
        dataStore.data
            .map { preferences -> preferences[SYNC_PASSPHRASE] != null }

    val lastSyncedAt: Flow<Long?> =
        dataStore.data
            .map { preferences -> preferences[LAST_SYNCED_AT] }

    val lastSyncedLocalChangedAt: Flow<Long?> =
        dataStore.data
            .map { preferences -> preferences[LAST_SYNCED_LOCAL_CHANGED_AT] }

    val lastSyncedContentTimestamp: Flow<Long?> =
        dataStore.data
            .map { preferences -> preferences[LAST_SYNCED_CONTENT_TIMESTAMP] }

    suspend fun setSyncProvider(provider: SyncProvider) {
        dataStore.edit { preferences ->
            preferences[SYNC_PROVIDER] = provider.name
        }
    }

    suspend fun setYandexAccessToken(token: String) {
        dataStore.edit { preferences ->
            preferences[YANDEX_ACCESS_TOKEN] = KeystoreCipher.encryptToString(token)
        }
    }

    suspend fun getYandexAccessToken(): String? {
        val encrypted =
            dataStore.data
                .map { it[YANDEX_ACCESS_TOKEN] }
                .first() ?: return null
        return try {
            KeystoreCipher.decryptFromString(encrypted)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun setSyncPassphrase(passphrase: String) {
        dataStore.edit { preferences ->
            preferences[SYNC_PASSPHRASE] = KeystoreCipher.encryptToString(passphrase)
        }
    }

    suspend fun getSyncPassphrase(): CharArray? {
        val encrypted =
            dataStore.data
                .map { it[SYNC_PASSPHRASE] }
                .first() ?: return null
        return try {
            KeystoreCipher.decryptFromString(encrypted).toCharArray()
        } catch (_: Exception) {
            // The Keystore key is device-local and never backed up; if this value was
            // restored from a backup onto a new device/install, the key won't exist.
            null
        }
    }

    suspend fun clearSyncState() {
        KeystoreCipher.clearKey()
        dataStore.edit { preferences ->
            preferences.remove(SYNC_PROVIDER)
            preferences.remove(SYNC_PASSPHRASE)
            preferences.remove(YANDEX_ACCESS_TOKEN)
            preferences.remove(LAST_SYNCED_AT)
            preferences.remove(LAST_SYNCED_LOCAL_CHANGED_AT)
            preferences.remove(LAST_SYNCED_CONTENT_TIMESTAMP)
        }
    }

    suspend fun recordSyncSuccess(
        localChangedAt: Long,
        contentTimestamp: Long,
    ) {
        dataStore.edit { preferences ->
            preferences[LAST_SYNCED_AT] = System.currentTimeMillis()
            preferences[LAST_SYNCED_LOCAL_CHANGED_AT] = localChangedAt
            preferences[LAST_SYNCED_CONTENT_TIMESTAMP] = contentTimestamp
        }
    }

    suspend fun setSortOrder(sortOrder: SortOrder) {
        dataStore.edit { preferences ->
            preferences[SORT_ORDER] = sortOrder.name
        }
    }

    suspend fun setAppTheme(theme: AppTheme) {
        dataStore.edit { preferences ->
            preferences[APP_THEME] = theme.name
        }
    }

    suspend fun setPin(pin: String) {
        val hash = hashPin(pin)
        dataStore.edit { preferences ->
            preferences[PIN_HASH] = hash
            preferences[PIN_LENGTH] = pin.length
        }
    }

    suspend fun clearPin() {
        dataStore.edit { preferences ->
            preferences.remove(PIN_HASH)
            preferences.remove(PIN_LENGTH)
            preferences.remove(FINGERPRINT_ENABLED)
        }
    }

    suspend fun setFingerprintEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[FINGERPRINT_ENABLED] = enabled
        }
    }

    fun hashPin(pin: String): String {
        val bytes = pin.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        val SORT_ORDER = stringPreferencesKey("sort_order")
        val APP_THEME = stringPreferencesKey("app_theme")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_LENGTH = intPreferencesKey("pin_length")
        val FINGERPRINT_ENABLED = booleanPreferencesKey("fingerprint_enabled")
        val SYNC_PROVIDER = stringPreferencesKey("sync_provider")
        val SYNC_PASSPHRASE = stringPreferencesKey("sync_passphrase")
        val YANDEX_ACCESS_TOKEN = stringPreferencesKey("yandex_access_token")
        val LAST_SYNCED_AT = longPreferencesKey("last_synced_at")
        val LAST_SYNCED_LOCAL_CHANGED_AT = longPreferencesKey("last_synced_local_changed_at")
        val LAST_SYNCED_CONTENT_TIMESTAMP = longPreferencesKey("last_synced_content_timestamp")
    }
}
