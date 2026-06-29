package com.stephen.nativewal.util

import android.util.Log

/**
 * Global debug log function that mirrors Flutter's dPrint().
 * - Always calls android.util.Log.d() for logcat output
 * - Also appends to LogService so logs appear in the Debug screen
 */
fun dLog(tag: String, message: String) {
    Log.d(tag, message)
    LogService.append("[$tag] $message")
}

/**
 * Overload for error-level logs.
 */
fun dLogError(tag: String, message: String, throwable: Throwable? = null) {
    if (throwable != null) {
        Log.e(tag, message, throwable)
        LogService.append("[$tag] ERROR: $message — ${throwable.message}")
    } else {
        Log.e(tag, message)
        LogService.append("[$tag] ERROR: $message")
    }
}
