package com.stephen.nativewal

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Surface
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import com.stephen.nativewal.network.WifiLoginTrigger
import com.stephen.nativewal.ui.navigation.Screen
import com.stephen.nativewal.ui.navigation.WalNavHost
import com.stephen.nativewal.ui.theme.WALTheme
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

class MainActivity : ComponentActivity() {

    companion object {
        const val ACTION_START_AUTO_LOGIN = "com.stephen.nativewal.action.START_AUTO_LOGIN"
        const val EXTRA_AUTO_LOGIN_SSID = "extra_auto_login_ssid"
    }

    private val handler = Handler(Looper.getMainLooper())
    private val pendingNavigationRoute = MutableStateFlow<String?>(null)

    // ── Permission launchers ──

    private lateinit var backgroundLocationLauncher: ActivityResultLauncher<String>
    private lateinit var locationLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var notificationLauncher: ActivityResultLauncher<String>
    private lateinit var locationSettingsLauncher: ActivityResultLauncher<IntentSenderRequest>

    private fun handleLaunchIntent(intent: Intent?) {
        val ssid = intent?.getStringExtra(EXTRA_AUTO_LOGIN_SSID)
        if (ssid != null && ssid.trim().isNotEmpty() && intent.action == ACTION_START_AUTO_LOGIN) {
            // Trigger the auto-login worker
            WifiLoginTrigger.enqueueAutoLogin(this, ssid, source = "widget")
            // Schedule navigation to the progress screen
            pendingNavigationRoute.value = Screen.AutoLoginProgress.createRoute(ssid)
        }
    }

    /** Call from Compose to re-request location permission from the banner. */
    fun requestLocationPermission() {
        locationLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    /** Call from Compose to re-request notification permission from the banner. */
    fun requestNotificationPermission() {
        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    /** Call from Compose to request background location from the banner. */
    fun requestBackgroundLocationPermission() {
        backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }

    /** Show an in-app system dialog to enable device location (GPS). */
    fun enableLocationService() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10_000L).build()
        val settingsRequest = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
            .setAlwaysShow(true)
            .build()

        LocationServices.getSettingsClient(this)
            .checkLocationSettings(settingsRequest)
            .addOnFailureListener { exception ->
                if (exception is ResolvableApiException) {
                    val intentSender = IntentSenderRequest.Builder(exception.resolution).build()
                    locationSettingsLauncher.launch(intentSender)
                }
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleLaunchIntent(intent)

        locationSettingsLauncher = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            Log.d("PermissionFlow", "Location settings result: ${result.resultCode}")
        }

        backgroundLocationLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            Log.d("PermissionFlow", "Background location result: $granted")
        }

        locationLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { grants ->
            Log.d("PermissionFlow", "Location result: $grants")
        }

        notificationLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { _ ->
            val needsLocation = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
            if (needsLocation) {
                handler.postDelayed({
                    locationLauncher.launch(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                    )
                }, 200)
            }
        }

        enableEdgeToEdge()
        setContent {
            WALTheme {
                Surface {
                    val navController = rememberNavController()
                    val navigationRoute by pendingNavigationRoute.collectAsState()

                    LaunchedEffect(navigationRoute) {
                        navigationRoute?.let { route ->
                            navController.navigate(route) {
                                // If we are already on a progress screen, or coming from Home,
                                // ensure we don't stack multiple progress screens.
                                if (route.contains("auto_login_progress")) {
                                    popUpTo(Screen.Home.route) { inclusive = false }
                                }
                                launchSingleTop = true
                            }
                            // Clear the pending route so we don't navigate again on recomposition
                            pendingNavigationRoute.value = null
                        }
                    }

                    WalNavHost(navController = navController)
                }
            }
        }

        startPermissionChain()
    }

    private fun startPermissionChain() {
        val needsNotification = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED

        val needsLocation = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED

        when {
            needsNotification -> notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            needsLocation -> locationLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunchIntent(intent)
    }
}
