package com.stephen.nativewal.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.stephen.nativewal.data.repository.SettingsRepository
import com.stephen.nativewal.network.NetworkMonitor
import com.stephen.nativewal.util.dLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Restarts the WiFi monitor foreground service after a device reboot.
 *
 * The OS kills all services on shutdown. This receiver listens for
 * [Intent.ACTION_BOOT_COMPLETED] and re-starts the [WifiMonitorService]
 * so that captive-portal auto-login continues to work without the user
 * having to open the app after every reboot.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        dLog(TAG, "BOOT_COMPLETED received – checking WiFi monitor state.")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = SettingsRepository(context)
                val registered = settings.getBoolean(
                    SettingsRepository.KEY_NETWORK_MONITOR_REGISTERED, false
                )

                if (!registered) {
                    dLog(TAG, "WiFi monitor was not enabled, skipping.")
                    return@launch
                }

                NetworkMonitor.register(context)
                dLog(TAG, "WiFi monitor service started after boot.")
            } finally {
                pendingResult.finish()
            }
        }
    }
}
