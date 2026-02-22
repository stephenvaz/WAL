package com.stephen.nativewal

import android.Manifest
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
import com.stephen.nativewal.ui.navigation.WalNavHost
import com.stephen.nativewal.ui.theme.WALTheme

class MainActivity : ComponentActivity() {

    private val handler = Handler(Looper.getMainLooper())

    // ── Permission launchers (registered in onCreate, before STARTED) ──

    private lateinit var backgroundLocationLauncher: ActivityResultLauncher<String>
    private lateinit var locationLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var notificationLauncher: ActivityResultLauncher<String>
    private lateinit var locationSettingsLauncher: ActivityResultLauncher<IntentSenderRequest>

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

        // Register launchers BEFORE setContent (must be before STARTED state)
        locationSettingsLauncher = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            Log.d("PermissionFlow", "Location settings result: ${result.resultCode}")
            // ON_RESUME lifecycle observer will refresh the banner
        }

        backgroundLocationLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            Log.d("PermissionFlow", "Background location result: $granted")
            // Done — the ON_RESUME lifecycle observer in HomeScreen will refresh state
        }

        locationLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { grants ->
            val fineGranted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
            Log.d("PermissionFlow", "Location result: fine=$fineGranted")
            if (fineGranted) {
                val hasBackground = ContextCompat.checkSelfPermission(
                    this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                if (!hasBackground) {
                    handler.postDelayed({
                        Log.d("PermissionFlow", "Launching background location request")
                        backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    }, 800)
                }
            }
        }

        notificationLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            Log.d("PermissionFlow", "Notification result: $granted")
            val needsLocation = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
            if (needsLocation) {
                handler.postDelayed({
                    Log.d("PermissionFlow", "Launching location request")
                    locationLauncher.launch(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                    )
                }, 200)
            } else {
                // Foreground already granted, chain to background
                val hasBackground = ContextCompat.checkSelfPermission(
                    this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                if (!hasBackground) {
                    handler.postDelayed({
                        Log.d("PermissionFlow", "Launching background location request (skip location)")
                        backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    }, 500)
                }
            }
        }

        enableEdgeToEdge()
        setContent {
            WALTheme {
                Surface {
                    val navController = rememberNavController()
                    WalNavHost(navController = navController)
                }
            }
        }

        // Kick off the permission chain after the Activity is fully created
        startPermissionChain()
    }

    private fun startPermissionChain() {
        val needsNotification = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED

        val needsLocation = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED

        val needsBackground = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) != PackageManager.PERMISSION_GRANTED

        Log.d("PermissionFlow", "Start chain: notification=$needsNotification, location=$needsLocation, background=$needsBackground")

        when {
            needsNotification -> notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            needsLocation -> locationLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
            needsBackground -> backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }
}
