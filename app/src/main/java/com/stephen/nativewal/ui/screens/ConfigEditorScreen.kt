package com.stephen.nativewal.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stephen.nativewal.data.model.FormActionType
import com.stephen.nativewal.ui.viewmodel.ConfigEditorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigEditorScreen(
    viewModel: ConfigEditorViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current

    // Drag-to-reorder state
    var dragIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val expandedIndices = remember { mutableStateListOf<Int>() }
    val headerItemCount = 2 // items before actions in the LazyColumn

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) onNavigateBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isEditing) "Edit Config" else "New Config") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.save() }) {
                        Icon(Icons.Default.Save, contentDescription = "Save")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    expandedIndices.add(uiState.actions.size)
                    viewModel.addAction()
                },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add Action") }
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            userScrollEnabled = dragIndex == -1,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                Text("Network Settings", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = uiState.ssid,
                    onValueChange = { viewModel.updateSsid(it) },
                    label = { Text("WiFi SSID") },
                    placeholder = { Text("Exact Network Name") },
                    leadingIcon = { Icon(Icons.Default.Wifi, contentDescription = null) },
                    isError = uiState.ssidError != null,
                    supportingText = uiState.ssidError?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = uiState.url,
                    onValueChange = { viewModel.updateUrl(it) },
                    label = { Text("Portal URL") },
                    placeholder = { Text("http://192.168.1.1") },
                    leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                    isError = uiState.urlError != null,
                    supportingText = uiState.urlError?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = uiState.timeoutInSeconds,
                    onValueChange = { viewModel.updateTimeout(it) },
                    label = { Text("Timeout (seconds)") },
                    placeholder = { Text("e.g., 10.0") },
                    leadingIcon = { Icon(Icons.Default.Timer, contentDescription = null) },
                    isError = uiState.timeoutError != null,
                    supportingText = uiState.timeoutError?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Form Actions", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${uiState.actions.size} action(s)",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Actions execute in order. Use CSS selectors or element IDs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
            }

            if (uiState.actions.isEmpty() && !uiState.isLoading) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.TouchApp,
                                contentDescription = null,
                                modifier = Modifier.height(48.dp),
                                tint = Color.Gray
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("No actions defined", color = Color.Gray)
                            Text(
                                "Tap + to add your first action",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            itemsIndexed(uiState.actions, key = { _, action -> action.id }) { index, action ->
                val isDragging = dragIndex == index

                ActionTile(
                    modifier = Modifier
                        .then(
                            if (isDragging) {
                                Modifier
                                    .zIndex(1f)
                                    .graphicsLayer {
                                        translationY = dragOffset
                                        shadowElevation = 8.dp.toPx()
                                    }
                            } else {
                                Modifier.animateItem()
                            }
                        )
                        .pointerInput(Unit) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    dragIndex = index
                                    dragOffset = 0f
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDrag = { change, offset ->
                                    change.consume()
                                    dragOffset += offset.y

                                    val lazyIndex = dragIndex + headerItemCount
                                    val draggingItem = listState.layoutInfo.visibleItemsInfo
                                        .firstOrNull { it.index == lazyIndex }
                                        ?: return@detectDragGesturesAfterLongPress

                                    val draggedCenter =
                                        draggingItem.offset + draggingItem.size / 2 + dragOffset

                                    val targetItem = listState.layoutInfo.visibleItemsInfo
                                        .firstOrNull { info ->
                                            info.index >= headerItemCount &&
                                            info.index != lazyIndex &&
                                            draggedCenter.toInt() in
                                                info.offset..(info.offset + info.size)
                                        }

                                    if (targetItem != null) {
                                        val targetActionIndex =
                                            targetItem.index - headerItemCount
                                        if (targetActionIndex in uiState.actions.indices) {
                                            dragOffset +=
                                                draggingItem.offset - targetItem.offset
                                            viewModel.moveAction(dragIndex, targetActionIndex)
                                            dragIndex = targetActionIndex
                                        }
                                    }
                                },
                                onDragEnd = {
                                    dragIndex = -1
                                    dragOffset = 0f
                                },
                                onDragCancel = {
                                    dragIndex = -1
                                    dragOffset = 0f
                                }
                            )
                        },
                    index = index,
                    isExpanded = index in expandedIndices,
                    onToggleExpand = {
                        if (index in expandedIndices) expandedIndices.remove(index)
                        else expandedIndices.add(index)
                    },
                    actionType = action.type,
                    selector = action.selector,
                    value = action.value ?: "",
                    onTypeChanged = { viewModel.updateActionType(index, it) },
                    onSelectorChanged = { viewModel.updateActionSelector(index, it) },
                    onValueChanged = { viewModel.updateActionValue(index, it) },
                    onDelete = { viewModel.removeAction(action.id) }
                )
                Spacer(Modifier.height(12.dp))
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionTile(
    modifier: Modifier = Modifier,
    index: Int,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    actionType: FormActionType,
    selector: String,
    value: String,
    onTypeChanged: (FormActionType) -> Unit,
    onSelectorChanged: (String) -> Unit,
    onValueChanged: (String) -> Unit,
    onDelete: () -> Unit
) {
    var dropdownExpanded by remember { mutableStateOf(false) }

    val dismissState = rememberSwipeToDismissBoxState()

    // React to completed swipe
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
            onDelete()
            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val isSwiping = direction != SwipeToDismissBoxValue.Settled
            val bgColor = if (isSwiping) MaterialTheme.colorScheme.error else Color.Transparent
            val iconTint = if (isSwiping) MaterialTheme.colorScheme.onError else Color.Transparent
            val alignment = if (direction == SwipeToDismissBoxValue.StartToEnd)
                Alignment.CenterStart else Alignment.CenterEnd

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        color = bgColor,
                        shape = MaterialTheme.shapes.medium
                    )
                    .padding(horizontal = 20.dp),
                contentAlignment = alignment
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = iconTint
                )
            }
        },
        enableDismissFromStartToEnd = true
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleExpand() }
                        .padding(16.dp)
                ) {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.width(24.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (actionType == FormActionType.setValue) "Set Value" else "Click Element",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = selector.ifEmpty { "No selector" },
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        if (isExpanded) Icons.Default.KeyboardArrowUp
                        else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                        ExposedDropdownMenuBox(
                            expanded = dropdownExpanded,
                            onExpandedChange = { dropdownExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = if (actionType == FormActionType.setValue) "Set Value (Fill Field)" else "Click (Submit/Button)",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Action Type") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(dropdownExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            )
                            ExposedDropdownMenu(expanded = dropdownExpanded, onDismissRequest = { dropdownExpanded = false }) {
                                DropdownMenuItem(
                                    text = { Text("Set Value (Fill Field)") },
                                    onClick = {
                                        onTypeChanged(FormActionType.setValue)
                                        dropdownExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Click (Submit/Button)") },
                                    onClick = {
                                        onTypeChanged(FormActionType.click)
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = selector,
                            onValueChange = onSelectorChanged,
                            label = { Text("Selector") },
                            placeholder = { Text("#username, input[name=\"user\"]") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (actionType == FormActionType.setValue) {
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = value,
                                onValueChange = onValueChanged,
                                label = { Text("Value") },
                                placeholder = { Text("Text to enter in the field") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}
