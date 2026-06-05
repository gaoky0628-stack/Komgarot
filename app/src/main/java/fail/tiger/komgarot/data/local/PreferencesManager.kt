package fail.tiger.komgarot.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("settings")

class PreferencesManager(context: Context) {
    private val dataStore = context.dataStore

    companion object {
        val OFFLINE_MODE_KEY = booleanPreferencesKey("offline_mode")
    }

    suspend fun setOfflineMode(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[OFFLINE_MODE_KEY] = enabled
        }
    }

    fun getOfflineModeFlow(): Flow<Boolean> {
        return dataStore.data.map { prefs ->
            prefs[OFFLINE_MODE_KEY] ?: false
        }
    }
}
