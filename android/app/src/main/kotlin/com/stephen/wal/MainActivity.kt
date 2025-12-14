package com.stephen.wal

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import androidx.annotation.NonNull
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import android.os.Bundle
import android.view.WindowManager
import io.flutter.plugin.common.StandardMethodCodec

class MainActivity: FlutterActivity() {
    private val ACTIVITY_CHANNEL = "com.stephen.wal/activity_controls"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 1. ALLOW RUNNING ON LOCK SCREEN (Pocket Mode)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        
        // 2. Keep screen awake during the login process
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        val taskQueue = flutterEngine.dartExecutor.binaryMessenger.makeBackgroundTaskQueue()

        // Activity-specific methods via a dedicated channel.
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger,
                      ACTIVITY_CHANNEL,
                      StandardMethodCodec.INSTANCE,
                      taskQueue).setMethodCallHandler { call, result ->
            if (call.method == "minimizeApp") {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                moveTaskToBack(true)
                result.success(true)
            } else {
                result.notImplemented()
            }
        }
    }

    // Network binding is handled by wal_network_utils plugin for all engines
}