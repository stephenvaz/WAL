package com.stephen.nativewal.widget

import android.content.Context
import androidx.core.content.edit

class AutoLoginWidgetStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveWidgetSsid(widgetId: Int, ssid: String) {
        prefs.edit { putString(widgetKey(widgetId), ssid) }
    }

    fun getWidgetSsid(widgetId: Int): String? {
        return prefs.getString(widgetKey(widgetId), null)
    }

    fun removeWidget(widgetId: Int) {
        prefs.edit { remove(widgetKey(widgetId)) }
    }

    fun savePendingSsid(ssid: String) {
        prefs.edit { putString(KEY_PENDING_SSID, ssid) }
    }

    fun getPendingSsid(): String? {
        return prefs.getString(KEY_PENDING_SSID, null)
    }

    fun clearPendingSsid() {
        prefs.edit { remove(KEY_PENDING_SSID) }
    }

    private fun widgetKey(widgetId: Int) = "widget_ssid_$widgetId"

    companion object {
        private const val PREFS_NAME = "auto_login_widgets"
        private const val KEY_PENDING_SSID = "pending_ssid"
    }
}