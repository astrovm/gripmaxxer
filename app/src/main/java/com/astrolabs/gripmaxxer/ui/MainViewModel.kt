package com.astrolabs.gripmaxxer.ui

import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.astrolabs.gripmaxxer.datastore.AppSettings
import com.astrolabs.gripmaxxer.datastore.SettingsRepository
import com.astrolabs.gripmaxxer.datastore.WorkoutHistory
import com.astrolabs.gripmaxxer.media.MediaControlManager
import com.astrolabs.gripmaxxer.overlay.OverlayTimerManager
import com.astrolabs.gripmaxxer.reps.ExerciseMode
import com.astrolabs.gripmaxxer.service.DebugPreviewFrame
import com.astrolabs.gripmaxxer.service.DebugPreviewStore
import com.astrolabs.gripmaxxer.service.HangCamService
import com.astrolabs.gripmaxxer.service.MonitoringSnapshot
import com.astrolabs.gripmaxxer.service.MonitoringStateStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

data class PermissionSnapshot(
    val cameraGranted: Boolean = false,
    val notificationAccessEnabled: Boolean = false,
    val overlayPermissionGranted: Boolean = false,
)

data class MainUiState(
    val settings: AppSettings = AppSettings(),
    val history: WorkoutHistory = WorkoutHistory(),
    val permissions: PermissionSnapshot = PermissionSnapshot(),
    val monitoring: MonitoringSnapshot = MonitoringSnapshot(),
    val showCameraPreview: Boolean = true,
    val cameraPreviewFrame: DebugPreviewFrame? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val settingsRepository = SettingsRepository(appContext)

    private val permissionsState = MutableStateFlow(readPermissions())
    private val showCameraPreviewState = MutableStateFlow(true)

    init {
        DebugPreviewStore.enabled.value = true
    }

    private val baseUiState = combine(
        settingsRepository.settingsFlow,
        settingsRepository.historyFlow,
        permissionsState,
        MonitoringStateStore.snapshot,
        showCameraPreviewState,
    ) { settings, history, permissions, monitoring, showCameraPreview ->
        MainUiState(
            settings = settings,
            history = history,
            permissions = permissions,
            monitoring = monitoring,
            showCameraPreview = showCameraPreview,
        )
    }

    val uiState: StateFlow<MainUiState> = combine(
        baseUiState,
        DebugPreviewStore.frame,
    ) { base, debugPreviewFrame ->
        base.copy(
            cameraPreviewFrame = if (base.showCameraPreview) debugPreviewFrame else null,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainUiState(),
    )

    fun refreshPermissionState() {
        permissionsState.value = readPermissions()
    }

    fun setShowCameraPreview(enabled: Boolean) {
        showCameraPreviewState.value = enabled
        DebugPreviewStore.enabled.value = enabled
        if (!enabled) {
            DebugPreviewStore.clear()
        }
    }

    fun setOverlayEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setOverlayEnabled(enabled)
        }
    }

    fun setMediaControlEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setMediaControlEnabled(enabled)
        }
    }

    fun setSelectedExerciseMode(mode: ExerciseMode) {
        viewModelScope.launch {
            settingsRepository.setSelectedExerciseMode(mode)
        }
    }

    fun startMonitoring() {
        HangCamService.start(appContext)
    }

    fun stopMonitoring() {
        HangCamService.stop(appContext)
    }

    private fun readPermissions(): PermissionSnapshot {
        val cameraGranted = ContextCompat.checkSelfPermission(
            appContext,
            android.Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED

        return PermissionSnapshot(
            cameraGranted = cameraGranted,
            notificationAccessEnabled = MediaControlManager.isNotificationAccessEnabled(appContext),
            overlayPermissionGranted = OverlayTimerManager.isOverlayPermissionGranted(appContext),
        )
    }
}
