package com.stephen.nativewal

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.stephen.nativewal.data.repository.SettingsRepository
import com.stephen.nativewal.data.repository.WifiConfigRepository
import com.stephen.nativewal.network.NetworkMonitor
import com.stephen.nativewal.shortcut.AppShortcutManager
import com.stephen.nativewal.util.LogService
import com.stephen.nativewal.util.dLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WalApplication : Application() {

    companion object {
        private const val TAG = "WalApplication"
    }

    /** Application-scoped coroutine scope (lives until process death). */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        LogService.initialize(this)
        createNotificationChannels()
        restoreWifiMonitor()
        migrateCreatedAtTimestamps()
        updateAppShortcuts()
        dLog(TAG, "WAL Application initialized")
    }

    /**
     * If the WiFi monitor was previously enabled, restart the foreground
     * service on every cold start.  The service uses START_STICKY so the
     * OS will also re-create it after low-memory kills, but an explicit
     * start here covers app-update and force-stop scenarios.
     */
    private fun restoreWifiMonitor() {
        val settings = SettingsRepository(this)
        appScope.launch {
            val registered = settings.getBoolean(
                SettingsRepository.KEY_NETWORK_MONITOR_REGISTERED, false
            )
            if (!registered) return@launch

            NetworkMonitor.register(this@WalApplication)
            dLog(TAG, "Restored WiFi monitor service on process start.")
        }
    }

    private fun migrateCreatedAtTimestamps() {
        val settings = SettingsRepository(this)
        appScope.launch {
            val done = settings.getBoolean(SettingsRepository.KEY_SHORTCUT_MIGRATION_DONE, false)
            if (done) return@launch

            val repository = WifiConfigRepository(this@WalApplication)
            val configs = repository.getAllConfigs()
            if (configs.isEmpty()) {
                settings.setBoolean(SettingsRepository.KEY_SHORTCUT_MIGRATION_DONE, true)
                return@launch
            }

            val sorted = configs.sortedBy { it.ssid }
            val now = System.currentTimeMillis()
            sorted.forEachIndexed { index, config ->
                repository.saveConfig(config.copy(
                    updatedAt = now - ((sorted.size - index) * 1000)
                ))
            }

            settings.setBoolean(SettingsRepository.KEY_SHORTCUT_MIGRATION_DONE, true)
            dLog(TAG, "Migrated ${sorted.size} configs with updatedAt timestamps")
        }
    }

    private fun updateAppShortcuts() {
        appScope.launch {
            AppShortcutManager(this@WalApplication).updateShortcuts()
            dLog(TAG, "App shortcuts updated on startup")
        }
    }

    private fun createNotificationChannels() {
        val nm = getSystemService(NotificationManager::class.java)

        val resultChannel = NotificationChannel(
            "auto_login_result",
            "AutoLogin Results",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Success/failure notifications for WiFi login attempts"
        }

        nm.createNotificationChannel(resultChannel)
    }
}
