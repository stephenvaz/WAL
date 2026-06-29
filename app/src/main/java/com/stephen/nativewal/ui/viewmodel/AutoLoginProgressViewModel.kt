package com.stephen.nativewal.ui.viewmodel

import android.content.Context
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.stephen.nativewal.data.model.AutoLoginStep
import com.stephen.nativewal.network.WifiLoginTrigger
import com.stephen.nativewal.worker.AutoLoginWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AutoLoginProgressUiState(
    val ssid: String,
    val currentStep: AutoLoginStep = AutoLoginStep.REQUEST_RECEIVED,
    val statusMessage: String = "Waiting to start",
    val errorMessage: String? = null,
    val isRunning: Boolean = true,
    val isFailed: Boolean = false,
    val isLocationError: Boolean = false,
    val canRetry: Boolean = false,
    val sessionId: String? = null
)

class AutoLoginProgressViewModel(
    private val context: Context,
    private val ssid: String
) : ViewModel() {

    private val workManager = WorkManager.getInstance(context)
    private val workName = "auto_login_$ssid"
    private val workInfosLiveData = workManager.getWorkInfosForUniqueWorkLiveData(workName)

    private val _uiState = MutableStateFlow(AutoLoginProgressUiState(ssid = ssid))
    val uiState: StateFlow<AutoLoginProgressUiState> = _uiState.asStateFlow()

    private val workInfoObserver = Observer<List<WorkInfo>> { workInfos ->
        val workInfo = if (workInfos.isEmpty()) null else workInfos[0]
        if (workInfo == null) {
            _uiState.update {
                it.copy(
                    currentStep = AutoLoginStep.REQUEST_RECEIVED,
                    statusMessage = "Waiting for login run",
                    errorMessage = null,
                    isRunning = false,
                    isFailed = false,
                    isLocationError = false,
                    canRetry = true,
                    sessionId = null
                )
            }
            return@Observer
        }

        // Extract ID to detect new runs
        val currentSessionId = workInfo.progress.getString(AutoLoginWorker.KEY_SESSION_ID)
            ?: workInfo.outputData.getString(AutoLoginWorker.KEY_SESSION_ID)

        val stepName = workInfo.progress.getString(AutoLoginWorker.KEY_PROGRESS_STEP)
            ?: workInfo.outputData.getString(AutoLoginWorker.KEY_PROGRESS_STEP)

        val progressError = workInfo.progress.getString(AutoLoginWorker.KEY_PROGRESS_ERROR)
            ?: workInfo.outputData.getString(AutoLoginWorker.KEY_PROGRESS_ERROR)

        val isLocErr = workInfo.progress.getBoolean(AutoLoginWorker.KEY_IS_LOCATION_ERROR, false) ||
                       workInfo.outputData.getBoolean(AutoLoginWorker.KEY_IS_LOCATION_ERROR, false)

        val message = workInfo.progress.getString(AutoLoginWorker.KEY_PROGRESS_MESSAGE)
            ?: workInfo.outputData.getString(AutoLoginWorker.KEY_PROGRESS_MESSAGE)
            ?: when (workInfo.state) {
                WorkInfo.State.ENQUEUED -> "Queued"
                WorkInfo.State.RUNNING -> "Running"
                WorkInfo.State.SUCCEEDED -> "Completed"
                WorkInfo.State.FAILED -> "Failed"
                else -> "Stopped"
            }

        val step = stepName?.let { runCatching { AutoLoginStep.valueOf(it) }.getOrNull() }
            ?: when (workInfo.state) {
                WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING -> AutoLoginStep.REQUEST_RECEIVED
                WorkInfo.State.SUCCEEDED -> AutoLoginStep.COMPLETED
                else -> AutoLoginStep.FAILED
            }

        val isRunning = workInfo.state == WorkInfo.State.ENQUEUED || workInfo.state == WorkInfo.State.RUNNING
        val isFailed = workInfo.state == WorkInfo.State.FAILED || workInfo.state == WorkInfo.State.CANCELLED

        _uiState.update { currentState ->
            // If it's a NEW session, we must clear old errors and reset steps
            val isNewSession = currentSessionId != null && currentSessionId != currentState.sessionId

            val nextStep = if (isNewSession) step else {
                if (step == AutoLoginStep.FAILED) currentState.currentStep else step
            }

            val nextError = if (isNewSession) progressError else (progressError ?: if (isFailed) currentState.errorMessage else null)
            val nextLocErr = if (isNewSession) isLocErr else (if (isLocErr) true else (if (isFailed) currentState.isLocationError else false))

            currentState.copy(
                currentStep = nextStep,
                statusMessage = message,
                errorMessage = nextError,
                isRunning = isRunning,
                isFailed = isFailed,
                isLocationError = nextLocErr,
                canRetry = workInfo.state.isFinished && workInfo.state != WorkInfo.State.SUCCEEDED,
                sessionId = currentSessionId ?: currentState.sessionId
            )
        }
    }

    init {
        workInfosLiveData.observeForever(workInfoObserver)
    }

    fun retry() {
        viewModelScope.launch {
            WifiLoginTrigger.enqueueAutoLogin(context, ssid, source = "retry")
        }
    }

    override fun onCleared() {
        workInfosLiveData.removeObserver(workInfoObserver)
        super.onCleared()
    }

    class Factory(
        private val appContext: Context,
        private val ssid: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AutoLoginProgressViewModel(appContext, ssid) as T
        }
    }
}
