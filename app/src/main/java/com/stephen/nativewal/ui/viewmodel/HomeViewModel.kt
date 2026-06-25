package com.stephen.nativewal.ui.viewmodel

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stephen.nativewal.data.model.WifiConfig
import com.stephen.nativewal.data.repository.SettingsRepository
import com.stephen.nativewal.data.repository.WifiConfigRepository
import com.stephen.nativewal.network.NetworkMonitor
import com.stephen.nativewal.util.dLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val configs: List<WifiConfig> = emptyList(),
    val isLoading: Boolean = true,
    val isLocationGranted: Boolean = true,
    val isBackgroundLocationGranted: Boolean = true,
    val isNotificationGranted: Boolean = true,
    val isLocationServiceEnabled: Boolean = true,
    val isNetworkMonitorRegistered: Boolean = false,
    val isBackgroundLocationBannerDismissed: Boolean = false
)

class HomeViewModel(
    private val context: Context,
    private val repository: WifiConfigRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadConfigs()
        checkPermissions()
        checkNetworkMonitorStatus()
        loadBannerDismissState()
    }

    fun loadConfigs() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.getAllConfigsFlow().collect { configs ->
                _uiState.update { it.copy(configs = configs, isLoading = false) }
            }
        }
    }

    fun deleteConfig(ssid: String) {
        viewModelScope.launch {
            repository.deleteConfig(ssid)
        }
    }

    fun toggleConfigEnabled(config: WifiConfig) {
        viewModelScope.launch {
            repository.saveConfig(config.copy(isEnabled = !config.isEnabled))
        }
    }

    fun checkPermissions() {
        val locationGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val backgroundLocationGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val notificationGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)

        _uiState.update {
            it.copy(
                isLocationGranted = locationGranted,
                isBackgroundLocationGranted = backgroundLocationGranted,
                isNotificationGranted = notificationGranted,
                isLocationServiceEnabled = gpsEnabled
            )
        }
    }

    private fun loadBannerDismissState() {
        viewModelScope.launch {
            val dismissed = settingsRepository.getBoolean(
                SettingsRepository.KEY_BACKGROUND_LOCATION_BANNER_DISMISSED, false
            )
            _uiState.update { it.copy(isBackgroundLocationBannerDismissed = dismissed) }
        }
    }

    fun dismissBackgroundLocationBanner() {
        viewModelScope.launch {
            settingsRepository.setBoolean(SettingsRepository.KEY_BACKGROUND_LOCATION_BANNER_DISMISSED, true)
            _uiState.update { it.copy(isBackgroundLocationBannerDismissed = true) }
        }
    }

    private fun checkNetworkMonitorStatus() {
        viewModelScope.launch {
            val registered = settingsRepository.getBoolean(
                SettingsRepository.KEY_NETWORK_MONITOR_REGISTERED, false
            )
            _uiState.update { it.copy(isNetworkMonitorRegistered = registered) }
        }
    }

    fun registerNetworkMonitor() {
        viewModelScope.launch {
            settingsRepository.setBoolean(SettingsRepository.KEY_NETWORK_MONITOR_REGISTERED, true)
            NetworkMonitor.register(context)
            dLog("HomeViewModel", "WiFi monitor service started")
            _uiState.update { it.copy(isNetworkMonitorRegistered = true) }

            // If background location is not granted, re-show the banner so user is prompted
            val bgLocGranted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (!bgLocGranted) {
                settingsRepository.setBoolean(SettingsRepository.KEY_BACKGROUND_LOCATION_BANNER_DISMISSED, false)
                _uiState.update { it.copy(isBackgroundLocationBannerDismissed = false) }
            }
        }
    }

    fun unregisterNetworkMonitor() {
        viewModelScope.launch {
            settingsRepository.setBoolean(SettingsRepository.KEY_NETWORK_MONITOR_REGISTERED, false)
            NetworkMonitor.unregister(context)
            dLog("HomeViewModel", "WiFi monitor service stopped")
            _uiState.update { it.copy(isNetworkMonitorRegistered = false) }
        }
    }
}

class HomeViewModelFactory(private val appContext: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return HomeViewModel(
            context = appContext,
            repository = WifiConfigRepository(appContext),
            settingsRepository = SettingsRepository(appContext)
        ) as T
    }
}
