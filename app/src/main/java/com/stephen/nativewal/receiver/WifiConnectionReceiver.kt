// Commenting out this file as we are unable to reliably use Pending Intent
// to trigger the auto-login, but should be explored for better performance and battery life

//package com.stephen.nativewal.receiver
//import android.content.BroadcastReceiver
//import android.content.Context
//import android.content.Intent
//import android.net.wifi.SupplicantState
//import android.net.wifi.WifiManager
//import com.stephen.nativewal.network.WifiLoginTrigger
//import com.stephen.nativewal.util.dLog
//
///**
// * BroadcastReceiver triggered by the OS when a network event matches our
// * registered NetworkRequest via the PendingIntent.
// *
// * IMPORTANT: The PendingIntent fires for ALL events (onAvailable, onLost,
// * onCapabilitiesChanged, etc.), not just onAvailable. We check the WiFi
// * supplicant state via WifiManager to determine if WiFi is actually connected.
// *
// * On every fire we also re-register the PendingIntent synchronously to
// * guarantee it stays alive across process restarts and OEM-specific
// * behaviour that can drop registrations.
// */
//class WifiConnectionReceiver : BroadcastReceiver() {
//
//    companion object {
//        private const val TAG = "WifiConnectionReceiver"
//    }
//
//    @Suppress("DEPRECATION")
//    override fun onReceive(context: Context, intent: Intent) {
//        dLog(TAG, "PendingIntent fired (action=${intent.action})")
//
//        // Check if WiFi is actually connected.
//        // Use WifiManager supplicant state instead of NetworkCapabilities
//        // because getNetworkCapabilities() can return null for a
//        // freshly-available network (race condition).
//        val wifiManager = context.applicationContext
//            .getSystemService(Context.WIFI_SERVICE) as WifiManager
//        val connInfo = wifiManager.connectionInfo
//        if (connInfo == null || connInfo.supplicantState != SupplicantState.COMPLETED) {
//            dLog(TAG, "WiFi not fully connected (state=${connInfo?.supplicantState}), ignoring.")
//            return
//        }
//
//        val rawSsid = connInfo.ssid ?: "<unknown ssid>"
//        val ssid = rawSsid.removeSurrounding("\"")
//        if (ssid == "<unknown ssid>" || ssid.isBlank()) {
//            dLog(TAG, "SSID unknown or blank, ignoring.")
//            return
//        }
//
//        dLog(TAG, "WiFi connected: $ssid")
//        WifiLoginTrigger.onWifiAvailable(context, source = "PendingIntent")
//    }
//}
