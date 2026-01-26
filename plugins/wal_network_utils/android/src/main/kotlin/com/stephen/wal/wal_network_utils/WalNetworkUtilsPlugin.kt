package com.stephen.wal.wal_network_utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/** WalNetworkUtilsPlugin: registers engine-wide MethodChannel and EventChannel for background isolates */
class WalNetworkUtilsPlugin : FlutterPlugin, MethodCallHandler {
    private lateinit var channel: MethodChannel
    private lateinit var wifiStateChannel: EventChannel
    private lateinit var appContext: Context
    private var wifiStateReceiver: WifiBroadcastReceiver? = null
    private var eventSink: EventChannel.EventSink? = null

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        appContext = flutterPluginBinding.applicationContext
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, CHANNEL)
        channel.setMethodCallHandler(this)
        
        // Set up EventChannel for Wi-Fi state monitoring
        wifiStateChannel = EventChannel(flutterPluginBinding.binaryMessenger, WIFI_STATE_CHANNEL)
        wifiStateChannel.setStreamHandler(object : EventChannel.StreamHandler {
            override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                eventSink = events
                if (wifiStateReceiver == null) {
                    wifiStateReceiver = WifiBroadcastReceiver(eventSink!!)
                    val intentFilter = IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        appContext.registerReceiver(wifiStateReceiver, intentFilter, Context.RECEIVER_EXPORTED)
                    } else {
                        @Suppress("UnspecifiedRegisterReceiverFlag")
                        appContext.registerReceiver(wifiStateReceiver, intentFilter)
                    }
                }
            }

            override fun onCancel(arguments: Any?) {
                if (wifiStateReceiver != null) {
                    appContext.unregisterReceiver(wifiStateReceiver)
                    wifiStateReceiver = null
                }
                eventSink = null
            }
        })
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "bindProcessToWifi" -> result.success(bindProcessToWifi())
            "unbindProcess" -> result.success(unbindProcess())
            "helloWorld" -> result.success("Hello from WalNetworkUtilsPlugin")
            "getConnectedWifiSSID" -> result.success(getConnectedWifiSSID())
            "initWiFiStateListener" -> result.success(true) // EventChannel already set up
            "validateWifiNetwork" -> {
                val url = call.argument<String>("url")
                thread {
                    val success = performWifiValidation(url)
                    Handler(Looper.getMainLooper()).post { result.success(success) }
                }
            }
            // Activity-only operations should be exposed elsewhere (e.g., separate channel)
            else -> result.notImplemented()
        }
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
        if (wifiStateReceiver != null) {
            appContext.unregisterReceiver(wifiStateReceiver)
            wifiStateReceiver = null
        }
    }

    private fun bindProcessToWifi(): Boolean {
        val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val wifiNetwork = connectivityManager.allNetworks.firstOrNull { network ->
            val caps = connectivityManager.getNetworkCapabilities(network)
            caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        }
        return if (wifiNetwork != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                connectivityManager.bindProcessToNetwork(wifiNetwork)
            } else {
                @Suppress("DEPRECATION")
                ConnectivityManager.setProcessDefaultNetwork(wifiNetwork)
            }
            true
        } else {
            false
        }
    }

    private fun unbindProcess(): Boolean {
        val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            connectivityManager.bindProcessToNetwork(null)
        } else {
            @Suppress("DEPRECATION")
            ConnectivityManager.setProcessDefaultNetwork(null)
        }
        return true
    }

    private fun getConnectedWifiSSID(): String? {
        val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val wifiNetwork = connectivityManager.allNetworks.firstOrNull { network ->
            val caps = connectivityManager.getNetworkCapabilities(network)
            caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        }
        return if (wifiNetwork != null) {
            try {
                val wifiManager = appContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                
                // Try getting SSID from connectionInfo first (requires location permission)
                var ssid = wifiManager.connectionInfo?.ssid?.trim('"')
                
                // If that fails or is empty, try getting from scan results
                if (ssid.isNullOrEmpty()) {
                    ssid = wifiManager.scanResults?.firstOrNull()?.SSID
                }
                
                ssid.takeIf { !it.isNullOrEmpty() }
            } catch (e: Exception) {
                android.util.Log.e("WalNetworkUtilsPlugin", "Error getting SSID: ${e.message}")
                null
            }
        } else {
            null
        }
    }

    private fun performWifiValidation(url: String?): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false

        val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val wifiNetwork = connectivityManager.allNetworks.firstOrNull { network ->
            val caps = connectivityManager.getNetworkCapabilities(network)
            caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } ?: return false

        val checkUrl = url ?: "http://connectivitycheck.gstatic.com/generate_204"
        var connection: HttpURLConnection? = null
        return try {
            connection = wifiNetwork.openConnection(URL(checkUrl)) as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.useCaches = false
            connection.connect()

            val code = connection.responseCode
            val success = code == 204 || code == 200
            connectivityManager.reportNetworkConnectivity(wifiNetwork, success)
            success
        } catch (e: Exception) {
            android.util.Log.e("WalNetworkUtilsPlugin", "Validation error: ${e.message}")
            try {
                connectivityManager.reportNetworkConnectivity(wifiNetwork, false)
            } catch (_: Exception) {
                // Ignore
            }
            false
        } finally {
            try {
                connection?.disconnect()
            } catch (_: Exception) {
                // Ignore
            }
        }
    }

    companion object {
        private const val CHANNEL = "com.stephen.wal/network_utils"
        private const val WIFI_STATE_CHANNEL = "com.stephen.wal/wifi_state"
    }
}

/** BroadcastReceiver for Wi-Fi state changes */
class WifiBroadcastReceiver(private val eventSink: EventChannel.EventSink) : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == WifiManager.NETWORK_STATE_CHANGED_ACTION) {
            val networkInfo = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO) as? android.net.NetworkInfo
            val wifiManager = context?.applicationContext?.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            
            val isConnected = networkInfo?.isConnected == true
            val ssid = if (isConnected) {
                try {
                    // Try getting SSID from connectionInfo first (requires location permission)
                    var result = wifiManager?.connectionInfo?.ssid?.trim('"')
                    
                    // If that fails or is empty, try getting from scan results
                    if (result.isNullOrEmpty()) {
                        val scanResults = wifiManager?.scanResults
                        result = scanResults?.firstOrNull()?.SSID
                    }
                    
                    // If still empty, fallback to "unknown"
                    result.takeIf { !it.isNullOrEmpty() } ?: "unknown"
                } catch (e: Exception) {
                    android.util.Log.e("WifiBroadcastReceiver", "Error getting SSID: ${e.message}")
                    "unknown"
                }
            } else {
                null
            }
            
            val state = if (isConnected) "CONNECTED" else "DISCONNECTED"
            val event = mapOf(
                "ssid" to ssid,
                "state" to state
            )
            
            eventSink.success(event)
        }
    }
}

