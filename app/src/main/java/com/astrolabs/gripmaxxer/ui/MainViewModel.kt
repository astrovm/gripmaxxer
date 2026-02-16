package com.astrolabs.gripmaxxer.ui

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.astrolabs.gripmaxxer.datastore.AppSettings
import com.astrolabs.gripmaxxer.datastore.ColorPalette
import com.astrolabs.gripmaxxer.datastore.GripmaxxerDatabase
import com.astrolabs.gripmaxxer.datastore.SettingsRepository
import com.astrolabs.gripmaxxer.media.MediaControlManager
import com.astrolabs.gripmaxxer.overlay.OverlayTimerManager
import com.astrolabs.gripmaxxer.reps.ExerciseMode
import com.astrolabs.gripmaxxer.service.AutoSetEvent
import com.astrolabs.gripmaxxer.service.AutoSetEventStore
import com.astrolabs.gripmaxxer.service.DebugPreviewFrame
import com.astrolabs.gripmaxxer.service.DebugPreviewStore
import com.astrolabs.gripmaxxer.service.HangCamService
import com.astrolabs.gripmaxxer.service.MonitoringSnapshot
import com.astrolabs.gripmaxxer.service.MonitoringStateStore
import com.astrolabs.gripmaxxer.workout.ActiveWorkoutState
import com.astrolabs.gripmaxxer.workout.CalendarDaySummary
import com.astrolabs.gripmaxxer.workout.CompletedWorkoutDetail
import com.astrolabs.gripmaxxer.workout.ProfileStats
import com.astrolabs.gripmaxxer.workout.RoomWorkoutRepository
import com.astrolabs.gripmaxxer.workout.WorkoutFeedItem
import com.astrolabs.gripmaxxer.workout.WorkoutSetState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

enum class RootTab {
    LOG,
    WORKOUT,
    PROFILE,
}

data class PermissionSnapshot(
    val cameraGranted: Boolean = false,
    val notificationAccessEnabled: Boolean = false,
    val overlayPermissionGranted: Boolean = false,
)

data class LiveSetUiState(
    val active: Boolean = false,
    val reps: Int = 0,
    val durationMs: Long = 0L,
)

data class SessionEditorUiState(
    val sets: List<WorkoutSetState> = emptyList(),
)

data class WorkoutSessionUiState(
    val workoutId: Long,
    val mode: ExerciseMode,
    val elapsedMs: Long,
    val paused: Boolean,
    val completedSetCount: Int,
    val liveSet: LiveSetUiState,
    val editor: SessionEditorUiState,
)

data class MainUiState(
    val selectedTab: RootTab = RootTab.WORKOUT,
    val settings: AppSettings = AppSettings(),
    val permissions: PermissionSnapshot = PermissionSnapshot(),
    val monitoring: MonitoringSnapshot = MonitoringSnapshot(),
    val completedWorkouts: List<WorkoutFeedItem> = emptyList(),
    val calendarDays: List<CalendarDaySummary> = emptyList(),
    val profileStats: ProfileStats = ProfileStats(
        totalWorkouts = 0,
        maxReps = 0,
        maxHoldMs = 0L,
    ),
    val selectedWorkoutDetail: CompletedWorkoutDetail? = null,
    val workoutSession: WorkoutSessionUiState? = null,
    val showCameraPreview: Boolean = true,
    val cameraPreviewFrame: DebugPreviewFrame? = null,
    val workoutMessage: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val settingsRepository = SettingsRepository(appContext)
    private val database = GripmaxxerDatabase.getInstance(appContext)
    private val workoutRepository = RoomWorkoutRepository(database)

    private val permissionsState = MutableStateFlow(readPermissions())
    private val selectedTabState = MutableStateFlow(RootTab.WORKOUT)
    private val showCameraPreviewState = MutableStateFlow(true)
    private val selectedWorkoutDetailState = MutableStateFlow<CompletedWorkoutDetail?>(null)
    private val workoutMessageState = MutableStateFlow<String?>(null)
    private val nowMsState = MutableStateFlow(System.currentTimeMillis())

    private val settingsState = settingsRepository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings(),
    )

    private val completedWorkoutsState = workoutRepository.completedWorkoutFeedFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    private val calendarDaysState = workoutRepository.calendarSummaryFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    private val profileStatsState = workoutRepository.profileStatsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProfileStats(
            totalWorkouts = 0,
            maxReps = 0,
            maxHoldMs = 0L,
        ),
    )

    private val activeWorkoutState = workoutRepository.activeWorkoutFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    init {
        DebugPreviewStore.enabled.value = true

        viewModelScope.launch {
            AutoSetEventStore.events.collect { event ->
                handleAutoSetEvent(event)
            }
        }

        viewModelScope.launch {
            while (true) {
                nowMsState.value = System.currentTimeMillis()
                delay(1000L)
            }
        }
    }

    val uiState: StateFlow<MainUiState> = combine(
        selectedTabState,
        settingsState,
        permissionsState,
        MonitoringStateStore.snapshot,
        completedWorkoutsState,
        calendarDaysState,
        profileStatsState,
        selectedWorkoutDetailState,
        activeWorkoutState,
        showCameraPreviewState,
        workoutMessageState,
        nowMsState,
        DebugPreviewStore.frame,
    ) { values ->
        val selectedTab = values[0] as RootTab
        val settings = values[1] as AppSettings
        val permissions = values[2] as PermissionSnapshot
        val monitoring = values[3] as MonitoringSnapshot
        val completedWorkouts = values[4] as List<WorkoutFeedItem>
        val calendarDays = values[5] as List<CalendarDaySummary>
        val profileStats = values[6] as ProfileStats
        val selectedWorkoutDetail = values[7] as CompletedWorkoutDetail?
        val activeWorkout = values[8] as ActiveWorkoutState?
        val showCameraPreview = values[9] as Boolean
        val workoutMessage = values[10] as String?
        val nowMs = values[11] as Long
        val debugFrame = values[12] as DebugPreviewFrame?

        MainUiState(
            selectedTab = selectedTab,
            settings = settings,
            permissions = permissions,
            monitoring = monitoring,
            completedWorkouts = completedWorkouts,
            calendarDays = calendarDays,
            profileStats = profileStats,
            selectedWorkoutDetail = selectedWorkoutDetail,
            workoutSession = activeWorkout?.toWorkoutSessionUiState(monitoring = monitoring, nowMs = nowMs),
            showCameraPreview = showCameraPreview,
            cameraPreviewFrame = if (showCameraPreview) debugFrame else null,
            workoutMessage = workoutMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainUiState(),
    )

    fun selectTab(tab: RootTab) {
        selectedTabState.value = tab
    }

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

    fun setColorPalette(palette: ColorPalette) {
        viewModelScope.launch {
            settingsRepository.setColorPalette(palette)
        }
    }

    fun setSelectedExerciseMode(mode: ExerciseMode) {
        viewModelScope.launch {
            settingsRepository.setSelectedExerciseMode(mode)
        }
    }

    fun startWorkout(mode: ExerciseMode = settingsState.value.selectedExerciseMode) {
        viewModelScope.launch {
            val existing = activeWorkoutState.value
            if (existing != null) {
                selectedTabState.value = RootTab.WORKOUT
                clearWorkoutMessageLater("Workout already running")
                return@launch
            }
            settingsRepository.setSelectedExerciseMode(mode)
            workoutRepository.startWorkout(mode)
            startMonitoringWithMode(mode)
            selectedTabState.value = RootTab.WORKOUT
            clearWorkoutMessageLater("Started ${mode.label}")
        }
    }

    fun pauseWorkout() {
        viewModelScope.launch {
            val active = activeWorkoutState.value ?: return@launch
            if (workoutRepository.pauseWorkout(active.id)) {
                stopMonitoring()
                clearWorkoutMessageLater("Workout paused")
            }
        }
    }

    fun resumeWorkout() {
        viewModelScope.launch {
            val active = activeWorkoutState.value ?: return@launch
            if (workoutRepository.resumeWorkout(active.id)) {
                startMonitoringWithMode(active.mode)
                clearWorkoutMessageLater("Workout resumed")
            }
        }
    }

    fun endWorkout() {
        viewModelScope.launch {
            val active = activeWorkoutState.value ?: return@launch
            if (workoutRepository.endWorkout(active.id)) {
                stopMonitoring()
                clearWorkoutMessageLater("Workout ended")
            }
        }
    }

    fun editActiveSet(setId: Long, reps: Int, durationMs: Long) {
        viewModelScope.launch {
            workoutRepository.editSet(setId = setId, reps = reps, durationMs = durationMs)
        }
    }

    fun deleteActiveSet(setId: Long) {
        viewModelScope.launch {
            workoutRepository.deleteSet(setId)
        }
    }

    fun openWorkoutDetail(workoutId: Long) {
        viewModelScope.launch {
            selectedWorkoutDetailState.value = workoutRepository.getCompletedWorkoutDetail(workoutId)
        }
    }

    fun closeWorkoutDetail() {
        selectedWorkoutDetailState.value = null
    }

    fun editDetailSet(setId: Long, reps: Int, durationMs: Long) {
        viewModelScope.launch {
            val success = workoutRepository.editSet(setId, reps, durationMs)
            if (success) {
                refreshOpenDetail()
            }
        }
    }

    fun deleteDetailSet(setId: Long) {
        viewModelScope.launch {
            val success = workoutRepository.deleteSet(setId)
            if (success) {
                refreshOpenDetail()
            }
        }
    }

    fun clearWorkoutMessage() {
        workoutMessageState.value = null
    }

    private suspend fun refreshOpenDetail() {
        val workoutId = selectedWorkoutDetailState.value?.workoutId ?: return
        selectedWorkoutDetailState.value = workoutRepository.getCompletedWorkoutDetail(workoutId)
    }

    private suspend fun handleAutoSetEvent(event: AutoSetEvent) {
        val active = activeWorkoutState.value ?: return
        if (active.paused) return
        if (active.mode != event.mode) return

        val set = workoutRepository.appendAutoSet(active.id, event)
        if (set != null) {
            val message = if (active.mode.isHangMode()) {
                "Set ${set.setNumber}: ${formatDurationShort(set.durationMs)} hold"
            } else {
                "Set ${set.setNumber}: ${set.reps} reps"
            }
            clearWorkoutMessageLater(message)
        }
    }

    private suspend fun startMonitoringWithMode(mode: ExerciseMode) {
        settingsRepository.setSelectedExerciseMode(mode)
        HangCamService.start(appContext)
    }

    private fun stopMonitoring() {
        HangCamService.stop(appContext)
    }

    private fun clearWorkoutMessageLater(message: String) {
        viewModelScope.launch {
            workoutMessageState.value = message
            delay(1800L)
            if (workoutMessageState.value == message) {
                workoutMessageState.value = null
            }
        }
    }

    private fun formatDurationShort(ms: Long): String {
        val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return String.format(Locale.US, "%d:%02d", minutes, seconds)
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

private fun ActiveWorkoutState.toWorkoutSessionUiState(
    monitoring: MonitoringSnapshot,
    nowMs: Long,
): WorkoutSessionUiState {
    val computedElapsed = if (paused) {
        elapsedMs
    } else {
        elapsedMs + (nowMs - elapsedComputedAtMs).coerceAtLeast(0L)
    }

    val liveSet = if (!paused && monitoring.mode == mode) {
        LiveSetUiState(
            active = monitoring.hanging,
            reps = monitoring.reps,
            durationMs = monitoring.elapsedHangMs,
        )
    } else {
        LiveSetUiState()
    }

    return WorkoutSessionUiState(
        workoutId = id,
        mode = mode,
        elapsedMs = computedElapsed.coerceAtLeast(0L),
        paused = paused,
        completedSetCount = sets.size,
        liveSet = liveSet,
        editor = SessionEditorUiState(sets = sets),
    )
}

private fun ExerciseMode.isHangMode(): Boolean {
    return this == ExerciseMode.DEAD_HANG || this == ExerciseMode.ACTIVE_HANG
}
