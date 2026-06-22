package com.stephen.nativewal.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.stephen.nativewal.MainActivity
import com.stephen.nativewal.R

class AutoLoginWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { appWidgetId ->
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val store = AutoLoginWidgetStore(context)
        appWidgetIds.forEach(store::removeWidget)
    }

    companion object {
        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, AutoLoginWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            ids.forEach { updateWidget(context, manager, it) }
        }

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val store = AutoLoginWidgetStore(context)
            val ssid = store.getWidgetSsid(appWidgetId)
            val configuredSsid = ssid ?: ""
            val views = RemoteViews(context.packageName, R.layout.autologin_widget)

            val hasSsid = ssid != null && ssid.trim().isNotEmpty()
            val title = if (hasSsid) {
                configuredSsid
            } else {
                context.getString(R.string.autologin_widget_not_configured)
            }

            views.setTextViewText(R.id.widgetTitle, title)

            val launchIntent = if (hasSsid) {
                Intent(context, MainActivity::class.java).apply {
                    action = MainActivity.ACTION_START_AUTO_LOGIN
                    putExtra(MainActivity.EXTRA_AUTO_LOGIN_SSID, configuredSsid)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            } else {
                Intent(context, AutoLoginWidgetConfigureActivity::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            views.setOnClickPendingIntent(R.id.widgetRoot, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
