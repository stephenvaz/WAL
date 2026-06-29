package com.stephen.nativewal.ui.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object ConfigEditor : Screen("config_editor?ssid={ssid}") {
        fun createRoute(ssid: String? = null): String {
            return if (ssid != null) "config_editor?ssid=$ssid" else "config_editor"
        }
    }
    data object AutoLoginProgress : Screen("auto_login_progress?ssid={ssid}") {
        fun createRoute(ssid: String): String = "auto_login_progress?ssid=$ssid"
    }
    data object Debug : Screen("debug")
    data object DebugLogs : Screen("debug_logs")
}
