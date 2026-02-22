package com.stephen.nativewal.data.model

import kotlinx.serialization.Serializable

@Serializable
data class WifiConfig(
    val ssid: String,
    val url: String,
    val isEnabled: Boolean = true,
    val actions: List<FormAction> = emptyList(),
    val timeoutInSeconds: Double = 10.0
)
