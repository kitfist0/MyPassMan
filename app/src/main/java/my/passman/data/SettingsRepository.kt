package my.passman.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import my.passman.util.KeystoreCipher
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        val sortOrder: Flow<SortOrder> =
            context.dataStore.data
                .map { preferences ->
                    val sortOrderName = preferences[SORT_ORDER] ?: SortOrder.BY_NAME.name
                    SortOrder.valueOf(sortOrderName)
                }

        val appTheme: Flow<AppTheme> =
            context.dataStore.data
                .map { preferences ->
                    val themeName = preferences[APP_THEME] ?: AppTheme.SYSTEM.name
                    AppTheme.valueOf(themeName)
                }

        val pinHash: Flow<String?> =
            context.dataStore.data
                .map { preferences -> preferences[PIN_HASH] }

        val pinLength: Flow<Int> =
            context.dataStore.data
                .map { preferences -> preferences[PIN_LENGTH] ?: 4 }

        val driveSyncEnabled: Flow<Boolean> =
            context.dataStore.data
                .map { preferences -> preferences[DRIVE_SYNC_ENABLED] ?: false }

        val hasSyncPassphrase: Flow<Boolean> =
            context.dataStore.data
                .map { preferences -> preferences[SYNC_PASSPHRASE] != null }

        val lastSyncedAt: Flow<Long?> =
            context.dataStore.data
                .map { preferences -> preferences[LAST_SYNCED_AT] }

        val lastSyncedLocalChangedAt: Flow<Long?> =
            context.dataStore.data
                .map { preferences -> preferences[LAST_SYNCED_LOCAL_CHANGED_AT] }

        val lastSyncedContentTimestamp: Flow<Long?> =
            context.dataStore.data
                .map { preferences -> preferences[LAST_SYNCED_CONTENT_TIMESTAMP] }

        suspend fun setDriveSyncEnabled(enabled: Boolean) {
            context.dataStore.edit { preferences ->
                preferences[DRIVE_SYNC_ENABLED] = enabled
            }
        }

        suspend fun setSyncPassphrase(passphrase: String) {
            context.dataStore.edit { preferences ->
                preferences[SYNC_PASSPHRASE] = KeystoreCipher.encryptToString(passphrase)
            }
        }

        suspend fun getSyncPassphrase(): CharArray? {
            val encrypted =
                context.dataStore.data
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
            context.dataStore.edit { preferences ->
                preferences.remove(DRIVE_SYNC_ENABLED)
                preferences.remove(SYNC_PASSPHRASE)
                preferences.remove(LAST_SYNCED_AT)
                preferences.remove(LAST_SYNCED_LOCAL_CHANGED_AT)
                preferences.remove(LAST_SYNCED_CONTENT_TIMESTAMP)
            }
        }

        suspend fun recordSyncSuccess(
            localChangedAt: Long,
            contentTimestamp: Long,
        ) {
            context.dataStore.edit { preferences ->
                preferences[LAST_SYNCED_AT] = System.currentTimeMillis()
                preferences[LAST_SYNCED_LOCAL_CHANGED_AT] = localChangedAt
                preferences[LAST_SYNCED_CONTENT_TIMESTAMP] = contentTimestamp
            }
        }

        suspend fun setSortOrder(sortOrder: SortOrder) {
            context.dataStore.edit { preferences ->
                preferences[SORT_ORDER] = sortOrder.name
            }
        }

        suspend fun setAppTheme(theme: AppTheme) {
            context.dataStore.edit { preferences ->
                preferences[APP_THEME] = theme.name
            }
        }

        suspend fun setPin(pin: String) {
            val hash = hashPin(pin)
            context.dataStore.edit { preferences ->
                preferences[PIN_HASH] = hash
                preferences[PIN_LENGTH] = pin.length
            }
        }

        suspend fun clearPin() {
            context.dataStore.edit { preferences ->
                preferences.remove(PIN_HASH)
                preferences.remove(PIN_LENGTH)
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
            val PIN_LENGTH =
                androidx.datastore.preferences.core
                    .intPreferencesKey("pin_length")
            val DRIVE_SYNC_ENABLED = booleanPreferencesKey("drive_sync_enabled")
            val SYNC_PASSPHRASE = stringPreferencesKey("sync_passphrase")
            val LAST_SYNCED_AT = longPreferencesKey("last_synced_at")
            val LAST_SYNCED_LOCAL_CHANGED_AT = longPreferencesKey("last_synced_local_changed_at")
            val LAST_SYNCED_CONTENT_TIMESTAMP = longPreferencesKey("last_synced_content_timestamp")
        }
    }
