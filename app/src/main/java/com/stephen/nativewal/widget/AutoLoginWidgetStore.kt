package com.stephen.nativewal.widget

import android.content.Context

class AutoLoginWidgetStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveWidgetSsid(widgetId: Int, ssid: String) {
        prefs.edit().putString(widgetKey(widgetId), ssid).apply()
    }

    fun getWidgetSsid(widgetId: Int): String? {
        return prefs.getString(widgetKey(widgetId), null)
    }

    fun removeWidget(widgetId: Int) {
        prefs.edit().remove(widgetKey(widgetId)).apply()
    }

    private fun widgetKey(widgetId: Int) = "widget_ssid_$widgetId"

    companion object {
        private const val PREFS_NAME = "auto_login_widgets"
    }
}