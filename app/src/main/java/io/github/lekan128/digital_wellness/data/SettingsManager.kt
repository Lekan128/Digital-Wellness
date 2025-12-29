package io.github.lekan128.digital_wellness.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsManager(private val context: Context) {

    val notificationPeriod: Flow<Float> = context.dataStore.data
        .map { preferences ->
            preferences[NOTIFICATION_PERIOD] ?: 20f // Default 20 mins
        }

    val isMonitoringEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[IS_MONITORING] ?: false
        }

    val selectedApps: Flow<Set<String>> = context.dataStore.data
        .map { preferences ->
            preferences[SELECTED_APPS] ?: emptySet()
        }

    val soundEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[SOUND_ENABLED] ?: true
        }

    val vibrationEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[VIBRATION_ENABLED] ?: true
        }

    suspend fun saveNotificationPeriod(period: Float) {
        context.dataStore.edit { preferences ->
            preferences[NOTIFICATION_PERIOD] = period
        }
    }

    suspend fun setMonitoringEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_MONITORING] = enabled
        }
    }

    suspend fun saveSelectedApps(packageNames: Set<String>) {
        context.dataStore.edit { preferences ->
            preferences[SELECTED_APPS] = packageNames
        }
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SOUND_ENABLED] = enabled
        }
    }

    suspend fun setVibrationEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[VIBRATION_ENABLED] = enabled
        }
    }
    
    companion object {
        val NOTIFICATION_PERIOD = floatPreferencesKey("notification_period")
        val SELECTED_APPS = stringSetPreferencesKey("selected_apps")
        val IS_MONITORING = booleanPreferencesKey("is_monitoring")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
    }
}
