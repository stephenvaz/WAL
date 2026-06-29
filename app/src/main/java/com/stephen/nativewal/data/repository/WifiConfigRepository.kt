package com.stephen.nativewal.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.stephen.nativewal.data.model.WifiConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.wifiConfigDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "wifi_configs"
)

class WifiConfigRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private fun configKey(ssid: String) = stringPreferencesKey("config_$ssid")

    suspend fun saveConfig(config: WifiConfig) {
        context.wifiConfigDataStore.edit { prefs ->
            prefs[configKey(config.ssid)] = json.encodeToString(config)
        }
    }

    suspend fun getConfig(ssid: String): WifiConfig? {
        val prefs = context.wifiConfigDataStore.data.first()
        val raw = prefs[configKey(ssid)] ?: return null
        return try {
            json.decodeFromString<WifiConfig>(raw)
        } catch (_: Exception) {
            null
        }
    }

    fun getAllConfigsFlow(): Flow<List<WifiConfig>> {
        return context.wifiConfigDataStore.data.map { prefs ->
            prefs.asMap()
                .filter { (key, _) -> key.name.startsWith("config_") }
                .mapNotNull { (_, value) ->
                    try {
                        json.decodeFromString<WifiConfig>(value as String)
                    } catch (_: Exception) {
                        null
                    }
                }
        }
    }

    suspend fun getAllConfigs(): List<WifiConfig> {
        return getAllConfigsFlow().first()
    }

    suspend fun deleteConfig(ssid: String) {
        context.wifiConfigDataStore.edit { prefs ->
            prefs.remove(configKey(ssid))
        }
    }
}
