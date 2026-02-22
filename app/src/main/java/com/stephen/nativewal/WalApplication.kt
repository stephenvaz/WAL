package com.stephen.nativewal

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.stephen.nativewal.data.repository.SettingsRepository
import com.stephen.nativewal.network.NetworkMonitor
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
