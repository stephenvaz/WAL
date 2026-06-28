package com.stephen.nativewal.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "app_settings"
)

class SettingsRepository(private val context: Context) {

    companion object {
        val KEY_DEBUG_LOGS_ENABLED = booleanPreferencesKey("debug_logs_enabled")
        val KEY_CONNECTIVITY_CHECK = booleanPreferencesKey("connectivity_check_enabled")
        val KEY_NETWORK_MONITOR_REGISTERED = booleanPreferencesKey("network_monitor_registered")
        val KEY_BACKGROUND_LOCATION_BANNER_DISMISSED = booleanPreferencesKey("bg_location_banner_dismissed")
        val KEY_SHORTCUT_MIGRATION_DONE = booleanPreferencesKey("shortcut_migration_done")
        val KEY_AUTO_CONNECT_ENABLED = booleanPreferencesKey("auto_connect_enabled")
    }

    suspend fun setBoolean(key: Preferences.Key<Boolean>, value: Boolean) {
        context.settingsDataStore.edit { it[key] = value }
    }

    suspend fun getBoolean(key: Preferences.Key<Boolean>, default: Boolean = false): Boolean {
        return context.settingsDataStore.data.first()[key] ?: default
    }
}
