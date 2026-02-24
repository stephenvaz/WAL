package com.stephen.nativewal.worker

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.webkit.SslErrorHandler
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.stephen.nativewal.R
import com.stephen.nativewal.data.model.FormAction
import com.stephen.nativewal.data.model.FormActionType
import com.stephen.nativewal.data.model.WifiConfig
import com.stephen.nativewal.data.repository.SettingsRepository
import com.stephen.nativewal.data.repository.WifiConfigRepository
import com.stephen.nativewal.util.dLog
import com.stephen.nativewal.util.dLogError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers.IO
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class AutoLoginWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_SSID = "ssid"
        private const val TAG = "AutoLoginWorker"
        private const val NOTIFICATION_CHANNEL_ID = "auto_login_result"
        private const val NOTIFICATION_CHANNEL_NAME = "AutoLogin Results"
    }

    private val repository = WifiConfigRepository(appContext)
    private val settingsRepository = SettingsRepository(appContext)

    override suspend fun doWork(): Result {
        val ssid = inputData.getString(KEY_SSID)
        if (ssid.isNullOrBlank()) {
            dLog(TAG, "No SSID provided, aborting.")
            return Result.failure()
        }

        dLog(TAG, "Worker started for SSID: $ssid")

        val config = repository.getConfig(ssid)
        if (config == null) {
            dLog(TAG, "No config found for SSID: $ssid")
            return Result.success()
        }

        if (!config.isEnabled) {
            dLog(TAG, "Config disabled for SSID: $ssid")
            return Result.success()
        }

        val bound = bindProcessToWifi()
        if (!bound) {
            dLogError(TAG, "Could not bind to WiFi network")
            showNotification(ssid, success = false, message = "Could not bind to WiFi network")
            return Result.failure()
        }

        // ── Pre-login connectivity check ──────────────────────────────
        // If the setting is enabled, probe the network first. When
        // internet is already reachable (HTTP 204), the captive portal
        // has already been resolved — skip the WebView login entirely.
        val skipLogin = settingsRepository.getBoolean(
            SettingsRepository.KEY_CONNECTIVITY_CHECK, default = false
        ) && isInternetReachable()

        if (skipLogin) {
            dLog(TAG, "Internet already reachable on $ssid — skipping portal login.")
            val validated = validateWifiNetwork()
            dLog(TAG, "Portal validation (pre-connected): $validated")
            unbindProcess()
            showNotification(ssid, success = true, message = "Internet Already Reachable on $ssid | Skipping Login")
            return Result.success()
        }

        val success = performWebViewLogin(config)

        // Trigger captive portal validation before unbinding
        var validated = false
        if (success) {
            validated = validateWifiNetwork()
            dLog(TAG, "Portal validation: $validated")
        }

        unbindProcess()

        val finalSuccess = success && validated
        showNotification(ssid, finalSuccess)

        return if (finalSuccess) Result.success() else Result.retry()
    }

    /**
     * Find the WiFi [Network] from all available networks.
     * Unlike cm.activeNetwork, this works even when mobile data is the default route.
     */
    @Suppress("DEPRECATION") // No non-deprecated replacement for enumerating all networks
    private fun findWifiNetwork(): android.net.Network? {
        val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.allNetworks.firstOrNull { network ->
            val caps = cm.getNetworkCapabilities(network)
            caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        }
    }

    private fun bindProcessToWifi(): Boolean {
        val wifiNetwork = findWifiNetwork() ?: return false
        val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.bindProcessToNetwork(wifiNetwork)
    }

    private fun unbindProcess() {
        val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.bindProcessToNetwork(null)
    }

    /**
     * Quick connectivity probe over the bound WiFi network.
     * Returns true when internet is already available (HTTP 204
     * from Google's connectivity-check endpoint), meaning no captive
     * portal login is required.
     */
    private suspend fun isInternetReachable(): Boolean = withContext(IO) {
        val wifiNetwork = findWifiNetwork() ?: return@withContext false

        val checkUrl = "http://connectivitycheck.gstatic.com/generate_204"
        var connection: HttpURLConnection? = null
        try {
            connection = wifiNetwork.openConnection(URL(checkUrl)) as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.useCaches = false
            connection.connect()

            val code = connection.responseCode
            // generate_204 returns exactly 204 when internet is available.
            // HTTP 200 could means a captive portal intercepted the request.
            val reachable = code == 204
            dLog(TAG, "Pre-login connectivity check: HTTP $code → reachable=$reachable")
            reachable
        } catch (e: Exception) {
            dLog(TAG, "Pre-login connectivity check failed: ${e.message}")
            false
        } finally {
            try { connection?.disconnect() } catch (_: Exception) {}
        }
    }

    /**
     * Hits Google's connectivity check endpoint through the WiFi network.
     * Then calls reportNetworkConnectivity() to tell Android the captive portal
     * is resolved — this clears the "Sign in to Wi-Fi" notification and
     * switches the default route back to WiFi.
     */
    private suspend fun validateWifiNetwork(): Boolean = withContext(IO) {
        val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val wifiNetwork = findWifiNetwork() ?: return@withContext false

        val checkUrl = "http://connectivitycheck.gstatic.com/generate_204"
        var connection: HttpURLConnection? = null
        try {
            connection = wifiNetwork.openConnection(URL(checkUrl)) as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.useCaches = false
            connection.connect()

            val code = connection.responseCode
            // Only 204 means real internet. 200 = captive portal intercept.
            val success = code == 204
            dLog(TAG, "Validation HTTP $code — reporting connectivity: $success")
            cm.reportNetworkConnectivity(wifiNetwork, success)
            success
        } catch (e: Exception) {
            dLogError(TAG, "Validation failed", e)
            try { cm.reportNetworkConnectivity(wifiNetwork, false) } catch (_: Exception) {}
            false
        } finally {
            try { connection?.disconnect() } catch (_: Exception) {}
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun performWebViewLogin(config: WifiConfig): Boolean {
        val result = CompletableDeferred<Boolean>()
        var hasInjected = false
        var hasCriticalError = false
        var webView: WebView? = null
        val mainHandler = Handler(Looper.getMainLooper())
        var completionRunnable: Runnable? = null

        val totalTimeoutMs = (config.timeoutInSeconds * 3000).toLong().coerceAtLeast(15000L)
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

                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            dLog(TAG, "Page loading: $url")
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            dLog(TAG, "Page loaded: $url")

                            // If we hit a critical error (DNS, connection refused, etc.)
                            // don't bother injecting into the error page.
                            if (hasCriticalError) {
                                dLog(TAG, "Skipping injection — critical error occurred.")
                                return
                            }

                            // Log page content before injection
                            view?.evaluateJavascript("document.body.innerText") { rawContent ->
                                val content = rawContent
                                    ?.removeSurrounding("\"")
                                    ?.replace("\\n", "\n")
                                    ?.replace("\\t", "\t")
                                    ?: "(empty)"
                                val label = if (hasInjected) "After Injection" else "Before Injection"
                                dLog(TAG, "PAGE CONTENT ($label):\n$content")
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

                            hasInjected = true
                            val jsCode = generateJsFromActions(config.actions)
                            dLog(TAG, "Executing JS:\n$jsCode")
                            view?.evaluateJavascript(jsCode) { _ ->
                                dLog(TAG, "JS injection complete. Waiting ${cleanupDelayMs}ms.")
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

                        @Suppress("OVERRIDE_DEPRECATION")
                        override fun onReceivedError(
                            view: WebView?,
                            errorCode: Int,
                            description: String?,
                            failingUrl: String?
                        ) {
                            dLogError(TAG, "WebView error: $errorCode - $description")
                            // Fail on critical errors (DNS, connection refused, timeout, etc.)
                            // Error codes: -2 = NAME_NOT_RESOLVED, -6 = CONNECTION_REFUSED,
                            //              -8 = TIMEOUT, -1 = GENERIC
                            if (errorCode <= -1) {
                                hasCriticalError = true
                                completionRunnable?.let { mainHandler.removeCallbacks(it) }
                                if (!result.isCompleted) {
                                    result.complete(false)
                                }
                            }
                        }

                        @SuppressLint("WebViewClientOnReceivedSslError")
                        override fun onReceivedSslError(
                            view: WebView?,
                            handler: SslErrorHandler?,
                            error: android.net.http.SslError?
                        ) {
                            handler?.proceed()
                        }
                    }

                    loadUrl(config.url)
                }
            }

            val success = withTimeoutOrNull(totalTimeoutMs) {
                result.await()
            } ?: false

            withContext(Dispatchers.Main) {
                webView?.apply {
                    stopLoading()
                    clearHistory()
                    clearCache(true)
                    destroy()
                }
                webView = null
            }

            return success
        } catch (e: Exception) {
            dLogError(TAG, "WebView login failed", e)
            withContext(Dispatchers.Main) {
                webView?.destroy()
            }
            return false
        }
    }

    private fun generateJsFromActions(actions: List<FormAction>): String {
        val sb = StringBuilder()
        sb.appendLine("try {")

        val sorted = actions.sortedBy { it.order }
        for (action in sorted) {
            val selector = action.selector.replace("'", "\\'")
            when (action.type) {
                FormActionType.setValue -> {
                    val value = (action.value ?: "").replace("'", "\\'")
                    sb.appendLine("  var elem_${action.order} = document.querySelector('$selector');")
                    sb.appendLine("  if (elem_${action.order}) {")
                    sb.appendLine("    elem_${action.order}.value = '$value';")
                    sb.appendLine("    elem_${action.order}.dispatchEvent(new Event('input', { bubbles: true }));")
                    sb.appendLine("  }")
                }
                FormActionType.click -> {
                    sb.appendLine("  var btn_${action.order} = document.querySelector('$selector');")
                    sb.appendLine("  if (btn_${action.order}) {")
                    sb.appendLine("    btn_${action.order}.disabled = false;")
                    sb.appendLine("    btn_${action.order}.click();")
                    sb.appendLine("  }")
                }
            }
        }

        sb.appendLine("} catch(e) { console.log('AutoLogin Error:', e); }")
        return sb.toString()
    }

    private fun showNotification(ssid: String, success: Boolean, message: String? = null) {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Success/failure notifications for WiFi login attempts"
        }
        nm.createNotificationChannel(channel)

        val title = if (success) "WiFi Login Success" else "WiFi Login Failed"
        val body = message ?: if (success) {
            "Connected on $ssid."
        } else {
            "Unable to login on $ssid."
        }

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
}
