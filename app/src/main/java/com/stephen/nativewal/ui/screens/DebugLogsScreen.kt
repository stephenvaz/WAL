package com.stephen.nativewal.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stephen.nativewal.ui.viewmodel.DebugViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DebugLogsScreen(
    viewModel: DebugViewModel,
    onNavigateBack: () -> Unit
) {
    val logs by viewModel.logs.collectAsState()
    val context = LocalContext.current
    val logListState = rememberLazyListState()

    // Track size at first composition so we only auto-scroll for *new* logs
    var initialSize by remember { mutableIntStateOf(-1) }
    LaunchedEffect(Unit) {
        initialSize = logs.size
        if (logs.isNotEmpty()) {
            logListState.scrollToItem(logs.size - 1)
        }
    }
    // Only animate-scroll for logs that arrive after the screen opened
    LaunchedEffect(logs.size) {
        if (initialSize >= 0 && logs.size > initialSize && logs.isNotEmpty()) {
            logListState.animateScrollToItem(logs.size - 1)
        }
    }

    val isScrolled by remember {
        derivedStateOf { logListState.firstVisibleItemIndex > 0 || logListState.firstVisibleItemScrollOffset > 0 }
    }
    val appBarContainerColor = if (isScrolled)
        MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
    else
        MaterialTheme.colorScheme.surface

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Debug Logs") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.clearLogs() }) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Clear Logs")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = appBarContainerColor
                )
            )
        },
        floatingActionButton = {
            if (logs.isNotEmpty()) {
                FloatingActionButton(
                    onClick = {
                        val allText = logs.joinToString("\n") { it.toString() }
                        val cm = context.getSystemService(ClipboardManager::class.java)
                        cm.setPrimaryClip(ClipData.newPlainText("logs", allText))
                        Toast.makeText(context, "All logs copied", Toast.LENGTH_SHORT).show()
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy all logs",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    ) { padding ->
        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No logs available.\nWaiting for something to happen…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                state = logListState,
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding()
                ),
                modifier = Modifier
                    .fillMaxSize()
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
        }
    }
}
