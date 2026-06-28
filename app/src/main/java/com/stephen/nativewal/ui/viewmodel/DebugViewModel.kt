package com.stephen.nativewal.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stephen.nativewal.data.model.WifiConfig
import com.stephen.nativewal.data.repository.SettingsRepository
import com.stephen.nativewal.data.repository.WifiConfigRepository
import com.stephen.nativewal.util.LogEntry
import com.stephen.nativewal.util.LogService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class BackupPayload(
    val version: Int = 1,
    val exportedAt: String,
    val configs: List<WifiConfig>
)

data class DebugUiState(
    val debugLogsEnabled: Boolean = false,
    val connectivityCheckEnabled: Boolean = false,
    val autoConnectEnabled: Boolean = false,
    val toastMessage: String? = null
)

class DebugViewModel(
    private val context: Context,
    private val repository: WifiConfigRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DebugUiState())
    val uiState: StateFlow<DebugUiState> = _uiState.asStateFlow()

    /** Live log entries from LogService, observed by the UI. */
    val logs: StateFlow<List<LogEntry>> = LogService.logs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LogService.logs.value)

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val debugEnabled = settingsRepository.getBoolean(SettingsRepository.KEY_DEBUG_LOGS_ENABLED)
            val connectivityEnabled = settingsRepository.getBoolean(SettingsRepository.KEY_CONNECTIVITY_CHECK)
            val autoConnectEnabled = settingsRepository.getBoolean(SettingsRepository.KEY_AUTO_CONNECT_ENABLED)
            _uiState.update {
                it.copy(
                    debugLogsEnabled = debugEnabled,
                    connectivityCheckEnabled = connectivityEnabled,
                    autoConnectEnabled = autoConnectEnabled
                )
            }
        }
    }

    fun setDebugLogsEnabled(enabled: Boolean) {
        LogService.setEnabled(enabled)
        viewModelScope.launch {
            settingsRepository.setBoolean(SettingsRepository.KEY_DEBUG_LOGS_ENABLED, enabled)
            _uiState.update { it.copy(debugLogsEnabled = enabled) }
        }
    }

    fun clearLogs() {
        LogService.clear()
    }

    fun setConnectivityCheckEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setBoolean(SettingsRepository.KEY_CONNECTIVITY_CHECK, enabled)
            _uiState.update { it.copy(connectivityCheckEnabled = enabled) }
        }
    }

    fun setAutoConnectEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setBoolean(SettingsRepository.KEY_AUTO_CONNECT_ENABLED, enabled)
            _uiState.update { it.copy(autoConnectEnabled = enabled) }
        }
    }

    fun backupConfigs(writeToUri: suspend (String) -> Boolean) {
        viewModelScope.launch {
            try {
                val configs = repository.getAllConfigs()
                val payload = BackupPayload(
                    exportedAt = java.time.Instant.now().toString(),
                    configs = configs
                )
                val jsonString = json.encodeToString(payload)
                val success = writeToUri(jsonString)
                if (success) {
                    _uiState.update { it.copy(toastMessage = "Backup saved successfully") }
                } else {
                    _uiState.update { it.copy(toastMessage = "Backup cancelled") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(toastMessage = "Backup failed: ${e.message}") }
            }
        }
    }

    fun restoreConfigs(readFromUri: suspend () -> String?) {
        viewModelScope.launch {
            try {
                val raw = readFromUri()
                if (raw == null) {
                    _uiState.update { it.copy(toastMessage = "No file selected") }
                    return@launch
                }
                val payload = json.decodeFromString<BackupPayload>(raw)
                var restored = 0
                for (config in payload.configs) {
                    if (config.ssid.isNotBlank()) {
                        repository.saveConfig(config)
                        restored++
                    }
                }
                _uiState.update {
                    it.copy(
                        toastMessage = if (restored > 0) "Restored $restored config(s)"
                        else "No configs restored"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(toastMessage = "Restore failed: ${e.message}") }
            }
        }
    }

    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    class Factory(private val appContext: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DebugViewModel(
                context = appContext,
                repository = WifiConfigRepository(appContext),
                settingsRepository = SettingsRepository(appContext)
            ) as T
        }
    }
}
