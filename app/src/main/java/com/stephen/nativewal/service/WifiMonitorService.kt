package com.stephen.nativewal.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.stephen.nativewal.MainActivity
import com.stephen.nativewal.R
import com.stephen.nativewal.network.WifiLoginTrigger
import com.stephen.nativewal.util.dLog
import com.stephen.nativewal.util.dLogError

/**
 * Foreground service that keeps a [ConnectivityManager.NetworkCallback]
 * alive across process restarts and swipe-from-recents kills.
 *
 * This is the **only** reliable way on modern Android (API 31+) to get
 * instant WiFi connectivity change notifications when the app is not in
 * the foreground. The persistent notification signals to the OS that the
 * process must not be killed.
 *
 * Flow:
 *   1. User enables the WiFi monitor → [start] is called
 *   2. Service moves to foreground with a notification
 *   3. A WiFi [NetworkCallback] is registered
 *   4. On WiFi connect → [WifiLoginTrigger.onWifiAvailable] runs the auto-login
 *   5. User disables the monitor → [stop] is called
 *
 * The service is restarted on boot via [BootReceiver] and on cold start
 * via [WalApplication].
 */
class WifiMonitorService : Service() {

    companion object {
        private const val TAG = "WifiMonitorService"
        const val CHANNEL_ID = "wifi_monitor"
        private const val NOTIFICATION_ID = 2001

        /** Start the foreground service. Idempotent — safe to call repeatedly. */
        fun start(context: Context) {
            val intent = Intent(context, WifiMonitorService::class.java)
            context.startForegroundService(intent)
        }

        /** Stop the foreground service. */
        fun stop(context: Context) {
            val intent = Intent(context, WifiMonitorService::class.java)
            context.stopService(intent)
        }
    }

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate() {
        super.onCreate()
        dLog(TAG, "Service created.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        dLog(TAG, "onStartCommand (flags=$flags, startId=$startId)")

        ensureNotificationChannel()
        startForeground(
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )

        registerWifiCallback()

        // If the system kills the service, restart it automatically.
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterWifiCallback()
        dLog(TAG, "Service destroyed.")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── WiFi callback ─────────────────────────────────────────────────

    private fun registerWifiCallback() {
        if (networkCallback != null) return             // already registered

        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                dLog(TAG, "NetworkCallback: onAvailable")
                WifiLoginTrigger.onWifiAvailable(applicationContext, source = "service")
            }
        }

        try {
            cm.registerNetworkCallback(request, callback)
            networkCallback = callback
            dLog(TAG, "WiFi NetworkCallback registered inside service.")
        } catch (e: Exception) {
            dLogError(TAG, "Failed to register WiFi NetworkCallback", e)
        }
    }

    private fun unregisterWifiCallback() {
        val cb = networkCallback ?: return

        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        try {
            cm.unregisterNetworkCallback(cb)
            dLog(TAG, "WiFi NetworkCallback unregistered.")
        } catch (e: Exception) {
            dLogError(TAG, "Failed to unregister WiFi NetworkCallback", e)
        }
        networkCallback = null
    }

    // ── Notification ──────────────────────────────────────────────────

    private fun ensureNotificationChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "WiFi Monitor",
            NotificationManager.IMPORTANCE_LOW          // silent, no sound/vibration
        ).apply {
            description = "Keeps the WiFi auto-login monitor running"
        }
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val tapIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val appIcon = android.graphics.BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(appIcon)
            .setContentTitle("WiFi Auto-Login active")
            .setContentText("Monitoring for WiFi connections")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}
