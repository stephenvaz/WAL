package com.stephen.nativewal.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationDisabled
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Unpublished
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.stephen.nativewal.MainActivity
import com.stephen.nativewal.data.model.WifiConfig
import com.stephen.nativewal.ui.viewmodel.HomeUiState
import com.stephen.nativewal.ui.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onAddConfig: () -> Unit,
    onEditConfig: (String) -> Unit,
    onOpenDebug: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as MainActivity

    // Re-check permissions when returning from settings / background location page
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WAL - WiFi Auto Login") },
                actions = {
                    IconButton(onClick = onOpenDebug) {
                        Icon(Icons.Default.BugReport, contentDescription = "Debug Logs")
                    }
                }
            )
        },
        floatingActionButton = {
            Row(
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Start/Stop service toggle
                IconButton(
                    onClick = {
                        if (uiState.isNetworkMonitorRegistered) {
                            viewModel.unregisterNetworkMonitor()
                        } else {
                            viewModel.registerNetworkMonitor()
                        }
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (uiState.isNetworkMonitorRegistered)
                            Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                        contentDescription = if (uiState.isNetworkMonitorRegistered)
                            "Stop Monitoring" else "Start Monitoring",
                        tint = if (uiState.isNetworkMonitorRegistered)
                            Color(0xFFFF1744)
                        else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                // Thick separator
                Box(
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .width(3.dp)
                        .height(32.dp)
                        .background(
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(1.5.dp)
                        )
                )
                // Add config button
                IconButton(
                    onClick = onAddConfig,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add Config",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Permission warnings
            PermissionWarningBanner(
                uiState = uiState,
                onRequestBackgroundLocation = {
                    activity.requestBackgroundLocationPermission()
                },
                onEnableLocation = {
                    activity.enableLocationService()
                },
                onRequestNotification = {
                    activity.requestNotificationPermission()
                },
                onRequestLocation = {
                    activity.requestLocationPermission()
                },
                onDismissBackgroundLocation = {
                    viewModel.dismissBackgroundLocationBanner()
                }
            )

            // Content
            when {
                uiState.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                uiState.configs.isEmpty() -> {
                    EmptyState()
                }
                else -> {
                    ConfigList(
                        configs = uiState.configs,
                        onEdit = onEditConfig,
                        onDelete = { viewModel.deleteConfig(it.ssid) },
                        onToggle = { viewModel.toggleConfigEnabled(it) },
                        onAddWidget = { ssid -> viewModel.addWidgetForConfig(ssid) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionWarningBanner(
    uiState: HomeUiState,
    onRequestBackgroundLocation: () -> Unit,
    onEnableLocation: () -> Unit,
    onRequestNotification: () -> Unit,
    onRequestLocation: () -> Unit,
    onDismissBackgroundLocation: () -> Unit
) {
    data class Warning(
        val text: String,
        val icon: @Composable () -> Unit,
        val action: () -> Unit,
        val isDismissible: Boolean = false,
        val onDismiss: () -> Unit = {}
    )

    val showBackgroundLocationWarning = !uiState.isBackgroundLocationGranted &&
            !uiState.isBackgroundLocationBannerDismissed

    val warning: Warning? = when {
        !uiState.isNotificationGranted -> Warning(
            "Notifications disabled. Tap to enable.",
            { Icon(Icons.Default.NotificationsOff, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer) },
            onRequestNotification
        )
        !uiState.isLocationGranted -> Warning(
            "Location denied. Cannot detect WiFi.",
            { Icon(Icons.Default.LocationOff, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer) },
            onRequestLocation
        )
        showBackgroundLocationWarning -> Warning(
            "Background location needed for auto-login in background. Tap to grant.",
            { Icon(Icons.Default.LocationOff, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer) },
            onRequestBackgroundLocation,
            isDismissible = true,
            onDismiss = onDismissBackgroundLocation
        )
        !uiState.isLocationServiceEnabled -> Warning(
            "Location (GPS) is OFF. Tap to enable.",
            { Icon(Icons.Default.LocationDisabled, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer) },
            onEnableLocation
        )
        else -> null
    }

    if (warning != null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.errorContainer)
                .clickable { warning.action() }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            warning.icon()
            Spacer(Modifier.width(12.dp))
            Text(
                text = warning.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            if (warning.isDismissible) {
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = { warning.onDismiss() },
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ConfigList(
    configs: List<WifiConfig>,
    onEdit: (String) -> Unit,
    onDelete: (WifiConfig) -> Unit,
    onToggle: (WifiConfig) -> Unit,
    onAddWidget: (String) -> Boolean
) {
    val context = LocalContext.current
    var configToDelete by remember { mutableStateOf<WifiConfig?>(null) }
    var configForWidget by remember { mutableStateOf<WifiConfig?>(null) }

    configToDelete?.let { config ->
        AlertDialog(
            onDismissRequest = { configToDelete = null },
            title = { Text("Delete Config?") },
            text = { Text("Remove settings for ${config.ssid}?") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(config)
                    configToDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { configToDelete = null }) { Text("Cancel") }
            }
        )
    }

    configForWidget?.let { config ->
        AlertDialog(
            onDismissRequest = { configForWidget = null },
            title = { Text("Add Widget") },
            text = { Text("Add a home screen widget for ${config.ssid}?") },
            confirmButton = {
                TextButton(onClick = {
                    val pinned = onAddWidget(config.ssid)
                    if (pinned) {
                        Toast.makeText(context, "Widget added to home screen", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Long-press home screen > Widgets to add", Toast.LENGTH_LONG).show()
                    }
                    configForWidget = null
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { configForWidget = null }) { Text("Cancel") }
            }
        )
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(configs, key = { it.ssid }) { config ->
            val dismissState = rememberSwipeToDismissBoxState()

            // React to completed swipes
            LaunchedEffect(dismissState.currentValue) {
                when (dismissState.currentValue) {
                    SwipeToDismissBoxValue.StartToEnd -> {
                        onToggle(config)
                        dismissState.snapTo(SwipeToDismissBoxValue.Settled)
                    }
                    SwipeToDismissBoxValue.EndToStart -> {
                        configToDelete = config
                        dismissState.snapTo(SwipeToDismissBoxValue.Settled)
                    }
                    SwipeToDismissBoxValue.Settled -> { /* no-op */ }
                }
            }

            SwipeToDismissBox(
                state = dismissState,
                backgroundContent = {
                    val direction = dismissState.dismissDirection
                    val color = when (direction) {
                        SwipeToDismissBoxValue.StartToEnd -> Color(0xFF4CAF50)
                        SwipeToDismissBoxValue.EndToStart -> Color(0xFFF44336)
                        else -> Color.Transparent
                    }
                    val icon = when (direction) {
                        SwipeToDismissBoxValue.StartToEnd -> {
                            if (config.isEnabled) Icons.Default.Unpublished else Icons.Default.CheckCircle
                        }
                        SwipeToDismissBoxValue.EndToStart -> Icons.Default.Delete
                        else -> Icons.Default.Delete
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(color)
                            .padding(horizontal = 20.dp),
                        contentAlignment = if (direction == SwipeToDismissBoxValue.StartToEnd)
                            Alignment.CenterStart else Alignment.CenterEnd
                    ) {
                        Icon(icon, contentDescription = null, tint = Color.White)
                    }
                }
            ) {
                ListItem(
                    colors = ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier.combinedClickable(
                        onClick = { onEdit(config.ssid) },
                        onLongClick = { configForWidget = config }
                    ),
                    leadingContent = {
                        Icon(
                            Icons.Default.Wifi,
                            contentDescription = null,
                            tint = if (config.isEnabled)
                                MaterialTheme.colorScheme.primary else Color.Gray,
                            modifier = Modifier.size(40.dp)
                        )
                    },
                    headlineContent = {
                        Text(
                            text = config.ssid,
                            textDecoration = if (config.isEnabled) null else TextDecoration.LineThrough,
                            color = if (config.isEnabled)
                                MaterialTheme.colorScheme.onSurface else Color.Gray
                        )
                    },
                    supportingContent = {
                        Text(
                            text = config.url,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.WifiOff,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = Color.Gray
        )
        Spacer(Modifier.height(20.dp))
        Text(
            "No Configs Found",
            style = MaterialTheme.typography.titleLarge,
            color = Color.Gray
        )
        Spacer(Modifier.height(10.dp))
        Text("Tap + to add a new auto-login")
    }
}
