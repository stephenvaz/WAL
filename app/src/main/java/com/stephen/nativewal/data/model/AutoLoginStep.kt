package com.stephen.nativewal.data.model

enum class AutoLoginStep(
    val displayLabel: String,
    val failureLabel: String
) {
    REQUEST_RECEIVED("Request received", "Request processing failed"),
    CHECKING_CONFIG("Checking saved config", "Configuration not found"),
    VERIFYING_SSID("Verifying WiFi connection", "WiFi connection check failed"),
    BINDING_WIFI_NETWORK("Latching to WiFi network", "Network binding failed"),
    LOADING_WEBPAGE("Loading webpage", "Portal page loading failed"),
    APPLYING_ACTIONS("Applying your steps", "Web actions failed"),
    VALIDATING_CONNECTION("Validating connection", "Connection validation failed"),
    COMPLETED("Successfully logged in", "Login completion failed"),
    FAILED("Failed", "Auto-login failed")
}
