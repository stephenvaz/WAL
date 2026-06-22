package com.stephen.nativewal.network

import android.content.Context
import android.net.wifi.WifiManager
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.stephen.nativewal.util.dLog
import com.stephen.nativewal.worker.AutoLoginWorker
import java.util.UUID

/**
 * Shared logic for reading the current WiFi SSID and enqueuing
 * AutoLoginWorker. Called by [WifiMonitorService]'s NetworkCallback
 * when a WiFi network becomes available. Uses enqueueUniqueWork to
 * prevent duplicate workers.
 */
object WifiLoginTrigger {

    private const val TAG = "WifiLoginTrigger"

    @Suppress("DEPRECATION")
    fun onWifiAvailable(context: Context, source: String = "unknown") {
        val wifiManager = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as WifiManager
        val connectionInfo = wifiManager.connectionInfo
        val rawSsid = connectionInfo?.ssid ?: "<unknown ssid>"
        val ssid = if (rawSsid.length >= 2 && rawSsid.first() == '"' && rawSsid.last() == '"') {
            rawSsid.substring(1, rawSsid.length - 1)
        } else {
            rawSsid
        }

        if (ssid == "<unknown ssid>" || ssid.trim().isEmpty()) {
            dLog(TAG, "[$source] SSID unknown or blank, ignoring.")
            return
        }

        dLog(TAG, "[$source] WiFi available: $ssid")
        enqueueAutoLogin(context, ssid, source)
    }

    fun enqueueAutoLogin(context: Context, ssid: String, source: String = "manual") {
        val sessionId = UUID.randomUUID().toString()

        val inputData = Data.Builder()
            .putString(AutoLoginWorker.KEY_SSID, ssid)
            .putString(AutoLoginWorker.KEY_SESSION_ID, sessionId)
            .putString(AutoLoginWorker.KEY_TRIGGER_SOURCE, source)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<AutoLoginWorker>()
            .setInputData(inputData)
            .addTag("auto_login_$ssid")
            .addTag("auto_login_session_$sessionId")
            .build()

        // Use REPLACE so that a stale worker (e.g. from an onLost event)
        // never blocks a fresh WiFi-connect worker. If both PendingIntent
        // and live callback fire, the second simply restarts the same work.
        WorkManager.getInstance(context).enqueueUniqueWork(
            "auto_login_$ssid",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
        dLog(TAG, "[$source] Enqueued AutoLoginWorker for SSID: $ssid")
    }
}
