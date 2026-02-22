package com.stephen.nativewal.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "app_settings"
)

class SettingsRepository(private val context: Context) {

    companion object {
        val KEY_DEBUG_LOGS_ENABLED = booleanPreferencesKey("debug_logs_enabled")
        val KEY_CONNECTIVITY_CHECK = booleanPreferencesKey("connectivity_check_enabled")
        val KEY_NETWORK_MONITOR_REGISTERED = booleanPreferencesKey("network_monitor_registered")
    }

    suspend fun setBoolean(key: Preferences.Key<Boolean>, value: Boolean) {
        context.settingsDataStore.edit { it[key] = value }
    }

    suspend fun getBoolean(key: Preferences.Key<Boolean>, default: Boolean = false): Boolean {
        return context.settingsDataStore.data.first()[key] ?: default
    }

    fun getBooleanFlow(key: Preferences.Key<Boolean>, default: Boolean = false): Flow<Boolean> {
        return context.settingsDataStore.data.map { it[key] ?: default }
    }
}
