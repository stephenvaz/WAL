package com.stephen.nativewal.network

import android.content.Context
import com.stephen.nativewal.service.WifiMonitorService
import com.stephen.nativewal.util.dLog

/**
 * Thin façade for starting / stopping the WiFi monitor.
 *
 * All actual network monitoring happens inside [WifiMonitorService],
 * a foreground service that keeps the process alive so its
 * [ConnectivityManager.NetworkCallback] fires reliably — even after
 * swipe-from-recents, screen-off, or Doze.
 */
object NetworkMonitor {

    private const val TAG = "NetworkMonitor"

    /**
     * Start the WiFi monitor foreground service.
     * Idempotent — safe to call on every cold start / boot.
     */
    fun register(context: Context) {
        dLog(TAG, "Starting WifiMonitorService…")
        WifiMonitorService.start(context.applicationContext)
    }

    /**
     * Stop the WiFi monitor foreground service.
     */
    fun unregister(context: Context) {
        dLog(TAG, "Stopping WifiMonitorService…")
        WifiMonitorService.stop(context.applicationContext)
    }
}
