package com.stephen.nativewal.worker

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.location.LocationManager
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Handler
import android.os.Looper
import android.webkit.SslErrorHandler
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.app.NotificationCompat
import androidx.core.location.LocationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.stephen.nativewal.R
import com.stephen.nativewal.data.model.AutoLoginStep
import com.stephen.nativewal.data.model.FormAction
import com.stephen.nativewal.data.model.FormActionType
import com.stephen.nativewal.data.model.WifiConfig
import com.stephen.nativewal.data.repository.SettingsRepository
import com.stephen.nativewal.data.repository.WifiConfigRepository
import com.stephen.nativewal.util.dLog
import com.stephen.nativewal.util.dLogError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

class AutoLoginWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_SSID = "ssid"
        const val KEY_SESSION_ID = "session_id"
        const val KEY_TRIGGER_SOURCE = "trigger_source"
        const val KEY_PROGRESS_STEP = "progress_step"
        const val KEY_PROGRESS_MESSAGE = "progress_message"
        const val KEY_PROGRESS_ERROR = "progress_error"
        const val KEY_IS_LOCATION_ERROR = "is_location_error"
        private const val TAG = "AutoLoginWorker"
        private const val NOTIFICATION_CHANNEL_ID = "auto_login_result"
        private const val NOTIFICATION_CHANNEL_NAME = "AutoLogin Results"
    }

    private val repository = WifiConfigRepository(appContext)
    private val settingsRepository = SettingsRepository(appContext)
    private val progressScope = CoroutineScope(SupervisorJob() + IO)

    override suspend fun doWork(): Result {
        val ssid = inputData.getString(KEY_SSID)
        val sessionId: String = inputData.getString(KEY_SESSION_ID) ?: UUID.randomUUID().toString()

        try {
            // --- Step 1: Request Received ---
            val step1 = AutoLoginStep.REQUEST_RECEIVED
            publishProgress(sessionId, step1.name, "Received auto-login request")

            if (ssid.isNullOrBlank()) {
                val err = "Target SSID is missing."
                val data = createErrorData(sessionId, step1.name, "Request failed", err)
                publishProgress(sessionId, step1.name, "Request failed", err)
                return Result.failure(data)
            }

            // --- Step 2: Checking Config ---
            val step2 = AutoLoginStep.CHECKING_CONFIG
            publishProgress(sessionId, step2.name, "Looking up actions for $ssid")

            val config = repository.getConfig(ssid)
            if (config == null) {
                val err = "No saved actions found for $ssid."
                val data = createErrorData(sessionId, step2.name, "Config not found", err)
                publishProgress(sessionId, step2.name, "Config not found", err)
                return Result.failure(data)
            }

            if (!config.isEnabled) {
                val err = "Auto-login is disabled for $ssid."
                val data = createErrorData(sessionId, step2.name, "Disabled", err)
                publishProgress(sessionId, step2.name, "Disabled", err)
                return Result.failure(data)
            }

            // --- Step 3: Verifying SSID ---
            val step3 = AutoLoginStep.VERIFYING_SSID
            publishProgress(sessionId, step3.name, "Checking current WiFi connection")

            if (!isLocationEnabled()) {
                val err = "Location is OFF. Android requires location to identify WiFi networks."
                val data = createErrorData(sessionId, step3.name, "Location disabled", err, isLocationError = true)
                publishProgress(sessionId, step3.name, "Location disabled", err, isLocationError = true)
                return Result.failure(data)
            }

            val currentSsid = getCurrentWifiSsid()
            if (currentSsid != ssid) {
                val autoConnectEnabled = settingsRepository.getBoolean(SettingsRepository.KEY_AUTO_CONNECT_ENABLED, false)
                if (autoConnectEnabled) {
                    publishProgress(sessionId, step3.name, "Not on $ssid. Attempting to connect...")
                    dLog(TAG, "Auto-connect enabled. Current: $currentSsid, Target: $ssid")
                    val connected = connectToWifi(ssid, sessionId)
                    if (!connected) {
                        val err = "Could not connect to $ssid. Please connect manually."
                        val data = createErrorData(sessionId, step3.name, "Auto-connect failed", err)
                        publishProgress(sessionId, step3.name, "Auto-connect failed", err)
                        return Result.failure(data)
                    }
                    dLog(TAG, "Successfully connected to $ssid via auto-connect")
                } else {
                    val err = if (currentSsid == null) {
                        "Not connected to any WiFi network. Please connect to $ssid and try again."
                    } else {
                        "Connected to \"$currentSsid\" instead of \"$ssid\". Switch networks and retry."
                    }
                    val data = createErrorData(sessionId, step3.name, "Wrong connection", err)
                    publishProgress(sessionId, step3.name, "Wrong connection", err)
                    return Result.failure(data)
                }
            }

            // Check if internet is already reachable on this SSID (Early check)
            val connectivityVerificationEnabled = settingsRepository.getBoolean(SettingsRepository.KEY_CONNECTIVITY_CHECK, false)
            if (connectivityVerificationEnabled && isInternetReachable()) {
                val msg = "Already connected to the internet on $ssid."
                publishProgress(sessionId, AutoLoginStep.COMPLETED.name, msg)
                showNotification(ssid, true, "Already connected on $ssid.")
                return Result.success(workDataOf(
                    KEY_SESSION_ID to sessionId,
                    KEY_PROGRESS_STEP to AutoLoginStep.COMPLETED.name,
                    KEY_PROGRESS_MESSAGE to msg
                ))
            }

            // --- Step 4: Binding Network ---
            val step4 = AutoLoginStep.BINDING_WIFI_NETWORK
            publishProgress(sessionId, step4.name, "Latching to the WiFi network")
            val bound = bindProcessToWifi()
            if (!bound) {
                val err = "System failed to route traffic through WiFi."
                val data = createErrorData(sessionId, step4.name, "Routing failed", err)
                publishProgress(sessionId, step4.name, "Routing failed", err)
                return Result.failure(data)
            }

            // Post-bind connectivity probe: ONLY skip if verification is enabled AND probe succeeds
            if (connectivityVerificationEnabled && isInternetReachable()) {
                val stepValidated = AutoLoginStep.VALIDATING_CONNECTION
                publishProgress(sessionId, stepValidated.name, "Verifying internet access...")
                val validated = validateWifiNetwork()
                if (validated) {
                    publishProgress(sessionId, AutoLoginStep.COMPLETED.name, "Logged in successfully")
                    showNotification(ssid, true, "Already connected to internet.")
                    return Result.success()
                } else {
                    val err = "Connected but internet probe failed."
                    val data = createErrorData(sessionId, stepValidated.name, "Probe failed", err)
                    publishProgress(sessionId, stepValidated.name, "Probe failed", err)
                    return Result.failure(data)
                }
            }

            // --- Step 5: Loading Page & Automating ---
            val step5 = AutoLoginStep.LOADING_WEBPAGE
            publishProgress(sessionId, step5.name, "Loading captive portal page")
            val loginSuccess = performWebViewLogin(config, sessionId)

            // --- Step 6: Final Validating ---
            val step6 = AutoLoginStep.VALIDATING_CONNECTION
            var validated = false
            if (loginSuccess) {
                publishProgress(sessionId, step6.name, "Checking if login succeeded")
                validated = validateWifiNetwork()
            }

            if (loginSuccess && validated) {
                publishProgress(sessionId, AutoLoginStep.COMPLETED.name, "Successfully logged in")
                showNotification(ssid, true)
                return Result.success()
            } else {
                val failedStep = if (!loginSuccess) step5 else step6
                val err = if (!loginSuccess) "Portal page failed to load or actions timed out." else "Portal did not grant internet access after login."
                val data = createErrorData(sessionId, failedStep.name, "Login failed", err)
                publishProgress(sessionId, failedStep.name, "Login failed", err)
                showNotification(ssid, false)
                return Result.failure(data)
            }
        } finally {
            unbindProcess()
        }
    }

    private fun isLocationEnabled(): Boolean {
        val lm = applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return LocationManagerCompat.isLocationEnabled(lm)
    }

    private fun createErrorData(sessionId: String, step: String, message: String, error: String, isLocationError: Boolean = false): Data {
        return workDataOf(
            KEY_SESSION_ID to sessionId,
            KEY_PROGRESS_STEP to step,
            KEY_PROGRESS_MESSAGE to message,
            KEY_PROGRESS_ERROR to error,
            KEY_IS_LOCATION_ERROR to isLocationError
        )
    }

    private fun bindProcessToWifi(): Boolean {
        val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val wifiNetwork = cm.allNetworks.firstOrNull { network ->
            val caps = cm.getNetworkCapabilities(network)
            caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } ?: return false
        return cm.bindProcessToNetwork(wifiNetwork)
    }

    private fun unbindProcess() {
        val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.bindProcessToNetwork(null)
    }

    @Suppress("DEPRECATION")
    private fun getCurrentWifiSsid(): String? {
        return try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val connectionInfo = wifiManager.connectionInfo
            val rawSsid = connectionInfo?.ssid ?: return null
            if (rawSsid == "<unknown ssid>" || rawSsid == "none") return null
            if (rawSsid.length >= 2 && rawSsid.first() == '"' && rawSsid.last() == '"') {
                rawSsid.substring(1, rawSsid.length - 1)
            } else {
                rawSsid
            }
        } catch (e: Exception) {
            null
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun connectToWifi(targetSsid: String, sessionId: String): Boolean {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val suggestion = WifiNetworkSuggestion.Builder()
            .setSsid(targetSsid)
            .build()

        val status = wifiManager.addNetworkSuggestions(listOf(suggestion))
        dLog(TAG, "Auto-connect: addNetworkSuggestions status: $status")

        // Wait briefly for the suggestion to take effect
        kotlinx.coroutines.delay(3000)

        // Check if we connected to the target
        val currentSsid = getCurrentWifiSsid()
        if (currentSsid == targetSsid) {
            dLog(TAG, "Auto-connect: suggestion worked, connected to $targetSsid")
            wifiManager.removeNetworkSuggestions(listOf(suggestion))
            return true
        }

        dLog(TAG, "Auto-connect: suggestion didn't switch, current=$currentSsid, opening WiFi settings")
        publishProgress(sessionId, AutoLoginStep.VERIFYING_SSID.name, "Opening WiFi settings to connect to $targetSsid...")

        // Fall back to opening WiFi settings for the user
        val intent = Intent(android.provider.Settings.ACTION_WIFI_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        applicationContext.startActivity(intent)

        // Poll for up to 30 seconds waiting for the user to connect
        val startTime = System.currentTimeMillis()
        val timeoutMs = 30_000L
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            kotlinx.coroutines.delay(2000)
            val ssid = getCurrentWifiSsid()
            dLog(TAG, "Auto-connect: polling SSID=$ssid")
            if (ssid == targetSsid) {
                dLog(TAG, "Auto-connect: user connected to $targetSsid")
                wifiManager.removeNetworkSuggestions(listOf(suggestion))
                return true
            }
        }

        dLog(TAG, "Auto-connect: timed out waiting for user to connect to $targetSsid")
        wifiManager.removeNetworkSuggestions(listOf(suggestion))
        return false
    }

    private suspend fun isInternetReachable(): Boolean = withContext(IO) {
        val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val wifiNetwork = cm.allNetworks.firstOrNull { network ->
            val caps = cm.getNetworkCapabilities(network)
            caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } ?: return@withContext false

        var connection: HttpURLConnection? = null
        try {
            connection = wifiNetwork.openConnection(URL("http://connectivitycheck.gstatic.com/generate_204")) as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.useCaches = false
            connection.connect()
            connection.responseCode == 204
        } catch (e: Exception) {
            false
        } finally {
            try { connection?.disconnect() } catch (_: Exception) {}
        }
    }

    private suspend fun validateWifiNetwork(): Boolean = withContext(IO) {
        val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val wifiNetwork = cm.allNetworks.firstOrNull { network ->
            val caps = cm.getNetworkCapabilities(network)
            caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } ?: return@withContext false

        var connection: HttpURLConnection? = null
        try {
            connection = wifiNetwork.openConnection(URL("http://connectivitycheck.gstatic.com/generate_204")) as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.useCaches = false
            connection.connect()
            val success = connection.responseCode == 204
            cm.reportNetworkConnectivity(wifiNetwork, success)
            success
        } catch (e: Exception) {
            try { cm.reportNetworkConnectivity(wifiNetwork, false) } catch (_: Exception) {}
            false
        } finally {
            try { connection?.disconnect() } catch (_: Exception) {}
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun performWebViewLogin(config: WifiConfig, sessionId: String): Boolean {
        val result = CompletableDeferred<Boolean>()
        var hasInjected = false
        var hasCriticalError = false
        var webView: WebView? = null
        val mainHandler = Handler(Looper.getMainLooper())
        var completionRunnable: Runnable? = null
        val cleanupDelayMs = (config.timeoutInSeconds * 1000).toLong()

        try {
            withContext(Dispatchers.Main) {
                webView = WebView(applicationContext).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    settings.cacheMode = WebSettings.LOAD_NO_CACHE
                    settings.userAgentString = "WAL-AutoLogin/1.0"

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            dLog(TAG, "WebView finished loading: $url")

                            if (hasCriticalError) {
                                dLog(TAG, "Skipping injection — critical error occurred.")
                                return
                            }

                            if (hasInjected) {
                                dLog(TAG, "Post-injection navigation to: $url — restarting completion timer.")
                                completionRunnable?.let { mainHandler.removeCallbacks(it) }
                                val postRunnable = Runnable {
                                    if (!result.isCompleted) {
                                        result.complete(true)
                                    }
                                }
                                completionRunnable = postRunnable
                                mainHandler.postDelayed(postRunnable, cleanupDelayMs)
                                return
                            }

                            progressScope.launch {
                                publishProgress(sessionId, AutoLoginStep.APPLYING_ACTIONS.name, "Applying saved web actions")
                            }

                            hasInjected = true
                            val jsCode = generateJsFromActions(config.actions)
                            view?.evaluateJavascript(jsCode) { _ ->
                                dLog(TAG, "JS Injection complete. Waiting ${cleanupDelayMs}ms.")
                                completionRunnable?.let { mainHandler.removeCallbacks(it) }
                                val jsRunnable = Runnable {
                                    if (!result.isCompleted) {
                                        result.complete(true)
                                    }
                                }
                                completionRunnable = jsRunnable
                                mainHandler.postDelayed(jsRunnable, cleanupDelayMs)
                            }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                            dLogError(TAG, "WebView error: $errorCode - $description ($failingUrl)")
                            if (errorCode <= -1) {
                                hasCriticalError = true
                                completionRunnable?.let { mainHandler.removeCallbacks(it) }
                                if (!result.isCompleted) {
                                    result.complete(false)
                                }
                            }
                        }

                        @SuppressLint("WebViewClientOnReceivedSslError")
                        override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: android.net.http.SslError?) {
                            handler?.proceed()
                        }
                    }
                    dLog(TAG, "Loading URL: ${config.url}")
                    loadUrl(config.url)
                }
            }
            return withTimeoutOrNull((config.timeoutInSeconds * 3000).toLong().coerceAtLeast(20000L).milliseconds) {
                result.await()
            } ?: false
        } catch (e: Exception) {
            dLogError(TAG, "WebView login failed", e)
            return false
        } finally {
            withContext(Dispatchers.Main) {
                webView?.apply {
                    stopLoading()
                    clearHistory()
                    clearCache(true)
                    destroy()
                }
                webView = null
            }
        }
    }

    private fun generateJsFromActions(actions: List<FormAction>): String {
        val sb = StringBuilder().appendLine("try {")
        for (action in actions.sortedBy { it.order }) {
            val selector = action.selector.replace("'", "\\'")
            if (action.type == FormActionType.setValue) {
                val value = (action.value ?: "").replace("'", "\\'")
                sb.appendLine("  var e${action.order}=document.querySelector('$selector'); if(e${action.order}){e${action.order}.value='$value'; e${action.order}.dispatchEvent(new Event('input',{bubbles:true}));}")
            } else {
                sb.appendLine("  var b${action.order}=document.querySelector('$selector'); if(b${action.order}){b${action.order}.disabled=false; b${action.order}.click();}")
            }
        }
        return sb.appendLine("} catch(e) {}").toString()
    }

    private fun showNotification(ssid: String, success: Boolean, message: String? = null) {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, NOTIFICATION_CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH)
        nm.createNotificationChannel(channel)
        val title = if (success) "WiFi Login Success" else "WiFi Login Failed"
        val body = message ?: if (success) "Connected on $ssid." else "Unable to login on $ssid."
        val appIcon = android.graphics.BitmapFactory.decodeResource(applicationContext.resources, R.mipmap.ic_launcher)
        val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(appIcon)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        nm.notify(ssid.hashCode() and 0x7fffffff, notification)
    }

    private suspend fun publishProgress(sessionId: String, step: String, message: String, error: String? = null, isLocationError: Boolean = false) {
        setProgress(workDataOf(KEY_SESSION_ID to sessionId, KEY_PROGRESS_STEP to step, KEY_PROGRESS_MESSAGE to message, KEY_PROGRESS_ERROR to error, KEY_IS_LOCATION_ERROR to isLocationError))
    }
}
