package com.stephen.nativewal.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.stephen.nativewal.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val Context.logDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "debug_logs"
)

data class LogEntry(
    val timestamp: Instant,
    val message: String
) {
    private val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        .withZone(ZoneId.systemDefault())

    override fun toString(): String {
        return "[${formatter.format(timestamp)}] $message"
    }
}

/**
 * Singleton service that stores debug log entries in memory and persists
 * them to DataStore. Exposes a StateFlow for the UI to observe.
 * Mirrors the Flutter LogService behavior.
 */
object LogService {

    private const val MAX_BUFFER_SIZE = 2000
    private val KEY_LOG_BUFFER = stringPreferencesKey("debug_logs_buffer")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private var _enabled = false
    val enabled: Boolean get() = _enabled

    private var context: Context? = null
    private var settingsRepository: SettingsRepository? = null
    private var initialized = false

    /**
     * Must be called once at app startup (e.g. in WalApplication.onCreate()).
     * Loads persisted logs and the enabled flag from DataStore.
     */
    fun initialize(appContext: Context) {
        if (initialized) return
        context = appContext.applicationContext
        settingsRepository = SettingsRepository(appContext.applicationContext)

        scope.launch {
            _enabled = settingsRepository!!.getBoolean(
                SettingsRepository.KEY_DEBUG_LOGS_ENABLED, false
            )

            // Restore persisted logs
            val prefs = appContext.logDataStore.data.first()
            val raw = prefs[KEY_LOG_BUFFER]
            if (!raw.isNullOrBlank()) {
                val restored = raw.split("\n").mapNotNull { line ->
                    val idx = line.indexOf('|')
                    if (idx > 0) {
                        val ts = try {
                            Instant.parse(line.substring(0, idx))
                        } catch (_: Exception) {
                            Instant.now()
                        }
                        val msg = line.substring(idx + 1)
                        LogEntry(ts, msg)
                    } else null
                }
                _logs.value = restored
            }
            initialized = true
        }
    }

    fun setEnabled(enabled: Boolean) {
        _enabled = enabled
        scope.launch {
            settingsRepository?.setBoolean(
                SettingsRepository.KEY_DEBUG_LOGS_ENABLED, enabled
            )
        }
    }

    fun append(message: String) {
        if (!_enabled) return
        val entry = LogEntry(Instant.now(), message)
        val updated = (_logs.value + entry).let { list ->
            if (list.size > MAX_BUFFER_SIZE) list.takeLast(MAX_BUFFER_SIZE) else list
        }
        _logs.value = updated
        persistLogs(updated)
    }

    fun clear() {
        _logs.value = emptyList()
        val ctx = context ?: return
        scope.launch {
            ctx.logDataStore.edit { prefs ->
                prefs.remove(KEY_LOG_BUFFER)
            }
        }
    }

    private fun persistLogs(entries: List<LogEntry>) {
        val ctx = context ?: return
        scope.launch {
            val serialized = entries.joinToString("\n") { entry ->
                "${entry.timestamp}|${entry.message}"
            }
            ctx.logDataStore.edit { prefs ->
                prefs[KEY_LOG_BUFFER] = serialized
            }
        }
    }
}
