package com.example.hangplaycam.ui

import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.hangplaycam.datastore.AppSettings
import com.example.hangplaycam.datastore.SettingsRepository
import com.example.hangplaycam.media.MediaControlManager
import com.example.hangplaycam.overlay.OverlayTimerManager
import com.example.hangplaycam.reps.ExerciseMode
import com.example.hangplaycam.service.HangCamService
import com.example.hangplaycam.service.MonitoringSnapshot
import com.example.hangplaycam.service.MonitoringStateStore
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
    val permissions: PermissionSnapshot = PermissionSnapshot(),
    val monitoring: MonitoringSnapshot = MonitoringSnapshot(),
    val selectedMode: ExerciseMode = ExerciseMode.PULL_UP,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val settingsRepository = SettingsRepository(appContext)

    private val permissionsState = MutableStateFlow(readPermissions())
    private val selectedModeState = MutableStateFlow(ExerciseMode.PULL_UP)

    val uiState: StateFlow<MainUiState> = combine(
        settingsRepository.settingsFlow,
        permissionsState,
        MonitoringStateStore.snapshot,
        selectedModeState,
    ) { settings, permissions, monitoring, selectedMode ->
        MainUiState(
            settings = settings,
            permissions = permissions,
            monitoring = monitoring,
            selectedMode = selectedMode,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainUiState(),
    )

    fun refreshPermissionState() {
        permissionsState.value = readPermissions()
    }

    fun setMode(mode: ExerciseMode) {
        selectedModeState.value = mode
    }

    fun setOverlayEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setOverlayEnabled(enabled)
        }
    }

    fun setPoseModeAccurate(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setPoseModeAccurate(enabled)
        }
    }

    fun setWristShoulderMargin(value: Float) {
        viewModelScope.launch {
            settingsRepository.setWristShoulderMargin(value)
        }
    }

    fun setMissingPoseTimeoutMs(value: Long) {
        viewModelScope.launch {
            settingsRepository.setMissingPoseTimeoutMs(value)
        }
    }

    fun setMarginUp(value: Float) {
        viewModelScope.launch {
            settingsRepository.setMarginUp(value)
        }
    }

    fun setMarginDown(value: Float) {
        viewModelScope.launch {
            settingsRepository.setMarginDown(value)
        }
    }

    fun setElbowUpAngle(value: Float) {
        viewModelScope.launch {
            settingsRepository.setElbowUpAngle(value)
        }
    }

    fun setElbowDownAngle(value: Float) {
        viewModelScope.launch {
            settingsRepository.setElbowDownAngle(value)
        }
    }

    fun startMonitoring() {
        HangCamService.start(appContext, selectedModeState.value)
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
