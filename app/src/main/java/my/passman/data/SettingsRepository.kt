package my.passman.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    val sortOrder: Flow<SortOrder> = context.dataStore.data
        .map { preferences ->
            val sortOrderName = preferences[SORT_ORDER] ?: SortOrder.BY_NAME.name
            SortOrder.valueOf(sortOrderName)
        }

    suspend fun setSortOrder(sortOrder: SortOrder) {
        context.dataStore.edit { preferences ->
            preferences[SORT_ORDER] = sortOrder.name
        }
    }

    private companion object {
        val SORT_ORDER = stringPreferencesKey("sort_order")
    }
}
