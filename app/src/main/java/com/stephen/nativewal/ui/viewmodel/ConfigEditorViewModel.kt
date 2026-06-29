package com.stephen.nativewal.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stephen.nativewal.data.model.FormAction
import com.stephen.nativewal.data.model.FormActionType
import com.stephen.nativewal.data.model.WifiConfig
import com.stephen.nativewal.data.repository.WifiConfigRepository
import com.stephen.nativewal.shortcut.AppShortcutManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConfigEditorUiState(
    val ssid: String = "",
    val url: String = "",
    val timeoutInSeconds: String = "10.0",
    val isEnabled: Boolean = true,
    val actions: List<FormAction> = emptyList(),
    val isEditing: Boolean = false,
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val ssidError: String? = null,
    val urlError: String? = null,
    val timeoutError: String? = null
)

class ConfigEditorViewModel(
    private val context: Context,
    private val repository: WifiConfigRepository,
    private val editingSsid: String?
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConfigEditorUiState())
    val uiState: StateFlow<ConfigEditorUiState> = _uiState.asStateFlow()

    init {
        if (editingSsid != null) {
            _uiState.update { it.copy(isEditing = true) }
            loadExistingConfig(editingSsid)
        }
    }

    private fun loadExistingConfig(ssid: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val config = repository.getConfig(ssid)
            if (config != null) {
                _uiState.update {
                    it.copy(
                        ssid = config.ssid,
                        url = config.url,
                        timeoutInSeconds = config.timeoutInSeconds.toString(),
                        isEnabled = config.isEnabled,
                        actions = config.actions,
                        isEditing = true,
                        isLoading = false
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun updateSsid(value: String) {
        _uiState.update { it.copy(ssid = value, ssidError = null) }
    }

    fun updateUrl(value: String) {
        _uiState.update { it.copy(url = value, urlError = null) }
    }

    fun updateTimeout(value: String) {
        _uiState.update { it.copy(timeoutInSeconds = value, timeoutError = null) }
    }

    fun addAction() {
        _uiState.update { state ->
            val newAction = FormAction(
                type = FormActionType.setValue,
                selector = "",
                order = state.actions.size
            )
            state.copy(actions = state.actions + newAction)
        }
    }

    fun removeAction(id: String) {
        _uiState.update { state ->
            val updated = state.actions.filter { it.id != id }
            val reordered = updated.mapIndexed { i, a -> a.copy(order = i) }
            state.copy(actions = reordered)
        }
    }

    fun updateActionType(index: Int, type: FormActionType) {
        _uiState.update { state ->
            val updated = state.actions.toMutableList()
            updated[index] = updated[index].copy(type = type)
            state.copy(actions = updated)
        }
    }

    fun updateActionSelector(index: Int, selector: String) {
        _uiState.update { state ->
            val updated = state.actions.toMutableList()
            updated[index] = updated[index].copy(selector = selector)
            state.copy(actions = updated)
        }
    }

    fun updateActionValue(index: Int, value: String) {
        _uiState.update { state ->
            val updated = state.actions.toMutableList()
            updated[index] = updated[index].copy(value = value)
            state.copy(actions = updated)
        }
    }

    fun moveAction(fromIndex: Int, toIndex: Int) {
        _uiState.update { state ->
            val list = state.actions.toMutableList()
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            val reordered = list.mapIndexed { i, a -> a.copy(order = i) }
            state.copy(actions = reordered)
        }
    }

    fun save(): Boolean {
        val state = _uiState.value

        var hasError = false
        var ssidError: String? = null
        var urlError: String? = null
        var timeoutError: String? = null

        if (state.ssid.isBlank()) {
            ssidError = "SSID required"
            hasError = true
        }
        if (state.url.isBlank()) {
            urlError = "URL required"
            hasError = true
        } else if (!state.url.startsWith("http")) {
            urlError = "Must start with http:// or https://"
            hasError = true
        }
        val timeout = state.timeoutInSeconds.toDoubleOrNull()
        if (timeout == null || timeout <= 0) {
            timeoutError = "Enter a valid positive number"
            hasError = true
        }

        if (hasError) {
            _uiState.update {
                it.copy(ssidError = ssidError, urlError = urlError, timeoutError = timeoutError)
            }
            return false
        }

        viewModelScope.launch {
            val config = WifiConfig(
                ssid = state.ssid.trim(),
                url = state.url.trim(),
                isEnabled = state.isEnabled,
                actions = state.actions,
                timeoutInSeconds = timeout ?: 10.0,
                updatedAt = System.currentTimeMillis()
            )
            repository.saveConfig(config)
            AppShortcutManager(context).updateShortcuts()
            _uiState.update { it.copy(isSaved = true) }
        }
        return true
    }

    class Factory(
        private val appContext: Context,
        private val editingSsid: String?
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ConfigEditorViewModel(
                context = appContext,
                repository = WifiConfigRepository(appContext),
                editingSsid = editingSsid
            ) as T
        }
    }
}
