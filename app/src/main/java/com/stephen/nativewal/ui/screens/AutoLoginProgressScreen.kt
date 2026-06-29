package com.stephen.nativewal.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.location.LocationManager
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.stephen.nativewal.data.model.AutoLoginStep
import com.stephen.nativewal.ui.viewmodel.AutoLoginProgressViewModel
import com.stephen.nativewal.ui.viewmodel.DebugViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AutoLoginProgressScreen(
    viewModel: AutoLoginProgressViewModel,
    debugViewModel: DebugViewModel,
    onNavigateBack: () -> Unit,
    onEnableLocation: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var showDebugSheet by remember { mutableStateOf(false) }

    // Promote SSID and status to the TopAppBar when the main header is scrolled away
    val isScrolled by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 }
    }

    var isLocationNowEnabled by remember { mutableStateOf(checkLocationEnabled(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isLocationNowEnabled = checkLocationEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(uiState.isFailed, uiState.isLocationError) {
        if (uiState.isFailed && uiState.isLocationError) {
            while (true) {
                isLocationNowEnabled = checkLocationEnabled(context)
                if (isLocationNowEnabled) break
                delay(1500)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (isScrolled) uiState.ssid else "Auto-login progress",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isScrolled) {
                            Text(
                                text = uiState.statusMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (uiState.isFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showDebugSheet = true },
                containerColor = Color(0xFFFF9800)
            ) {
                Icon(
                    Icons.Default.BugReport,
                    contentDescription = "Debug Logs",
                    tint = Color.White
                )
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 16.dp, bottom = 48.dp)
        ) {
            // --- Large Header (Scrollable) ---
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.size(12.dp))
                    Column {
                        Text(uiState.ssid, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(
                            text = uiState.statusMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (uiState.isFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // --- Timeline Steps ---
            val steps = AutoLoginStep.entries.filterNot { it == AutoLoginStep.FAILED }
            items(steps) { step ->
                TimelineItem(
                    step = step,
                    currentStep = uiState.currentStep,
                    isFailed = uiState.isFailed,
                    errorMessage = uiState.errorMessage,
                    isLocationError = uiState.isLocationError,
                    isLocationNowEnabled = isLocationNowEnabled,
                    canRetry = uiState.canRetry,
                    onRetry = { viewModel.retry() },
                    onEnableLocation = onEnableLocation
                )
            }
        }
    }

    if (showDebugSheet) {
        DebugLogsBottomSheet(
            debugViewModel = debugViewModel,
            onDismiss = { showDebugSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun DebugLogsBottomSheet(
    debugViewModel: DebugViewModel,
    onDismiss: () -> Unit
) {
    val logs by debugViewModel.logs.collectAsState()
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val logListState = rememberLazyListState()
    var initialSize by remember { mutableIntStateOf(-1) }
    LaunchedEffect(Unit) {
        initialSize = logs.size
        if (logs.isNotEmpty()) {
            logListState.scrollToItem(logs.size - 1)
        }
    }
    LaunchedEffect(logs.size) {
        if (initialSize >= 0 && logs.size > initialSize && logs.isNotEmpty()) {
            logListState.animateScrollToItem(logs.size - 1)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            // Header with close and clear buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Debug Logs",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { debugViewModel.clearLogs() }) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "Clear Logs",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close"
                    )
                }
            }

            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No logs available.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }
            } else {
                LazyColumn(
                    state = logListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                ) {
                    itemsIndexed(logs, key = { index, _ -> index }) { _, entry ->
                        Text(
                            text = entry.toString(),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {},
                                    onLongClick = {
                                        val cm = context.getSystemService(ClipboardManager::class.java)
                                        cm.setPrimaryClip(
                                            ClipData.newPlainText("log", entry.toString())
                                        )
                                        Toast
                                            .makeText(context, "Log copied", Toast.LENGTH_SHORT)
                                            .show()
                                    }
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                // Copy all button
                Button(
                    onClick = {
                        val allText = logs.joinToString("\n") { it.toString() }
                        val cm = context.getSystemService(ClipboardManager::class.java)
                        cm.setPrimaryClip(ClipData.newPlainText("logs", allText))
                        Toast.makeText(context, "All logs copied", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "Copy All Logs",
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

private fun checkLocationEnabled(context: Context): Boolean {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return LocationManagerCompat.isLocationEnabled(lm)
}

@Composable
private fun TimelineItem(
    step: AutoLoginStep,
    currentStep: AutoLoginStep,
    isFailed: Boolean,
    errorMessage: String?,
    isLocationError: Boolean,
    isLocationNowEnabled: Boolean,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onEnableLocation: () -> Unit
) {
    val currentIndex = AutoLoginStep.entries.indexOf(currentStep)
    val stepIndex = AutoLoginStep.entries.indexOf(step)

    val completed = stepIndex < currentIndex || (currentStep == AutoLoginStep.COMPLETED && stepIndex == currentIndex)
    val isCurrent = stepIndex == currentIndex && currentStep != AutoLoginStep.COMPLETED
    val failed = isFailed && isCurrent
    val active = !isFailed && isCurrent

    Surface(
        tonalElevation = if (active || failed) 4.dp else 1.dp,
        shape = MaterialTheme.shapes.large,
        color = when {
            failed -> MaterialTheme.colorScheme.errorContainer
            active -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
            else -> MaterialTheme.colorScheme.surface
        },
        border = if (active) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)) else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when {
                    completed -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    failed -> {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    active -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 3.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    else -> {
                        // Unfilled round checkbox for steps that haven't started yet
                        Icon(
                            imageVector = Icons.Default.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    val titleText = if (failed) step.failureLabel else step.displayLabel

                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (active || failed) FontWeight.Bold else FontWeight.Medium,
                        color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )

                    if (active) {
                        Text(
                            text = "In progress...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else if (failed) {
                        Text(
                            text = errorMessage ?: "An unexpected error occurred.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // --- Inline Actions (Inside the failed step's card) ---
            if (failed) {
                Spacer(Modifier.height(16.dp))
                if (isLocationError && !isLocationNowEnabled) {
                    Button(
                        onClick = onEnableLocation,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Turn on Device Location")
                    }
                } else if (canRetry) {
                    Button(
                        onClick = onRetry,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Retry this step")
                    }
                }
            }
        }
    }
}
