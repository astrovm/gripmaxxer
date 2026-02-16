package com.astrolabs.gripmaxxer.ui

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.astrolabs.gripmaxxer.datastore.AppSettings
import com.astrolabs.gripmaxxer.datastore.GripmaxxerDatabase
import com.astrolabs.gripmaxxer.datastore.SettingsRepository
import com.astrolabs.gripmaxxer.datastore.WeightUnit
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
import com.astrolabs.gripmaxxer.workout.DefaultExerciseLibrary
import com.astrolabs.gripmaxxer.workout.ExerciseTemplate
import com.astrolabs.gripmaxxer.workout.ProfileStats
import com.astrolabs.gripmaxxer.workout.RoomRoutineRepository
import com.astrolabs.gripmaxxer.workout.RoomWorkoutRepository
import com.astrolabs.gripmaxxer.workout.Routine
import com.astrolabs.gripmaxxer.workout.RoutineExerciseTemplateInput
import com.astrolabs.gripmaxxer.workout.WorkoutFeedItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

enum class RootTab {
    HOME,
    WORKOUT,
    PROFILE,
}

data class PermissionSnapshot(
    val cameraGranted: Boolean = false,
    val notificationAccessEnabled: Boolean = false,
    val overlayPermissionGranted: Boolean = false,
)

data class RestTimerUiState(
    val running: Boolean = false,
    val remainingSeconds: Int = 0,
    val exerciseName: String? = null,
    val completionNotice: String? = null,
)

data class MainUiState(
    val selectedTab: RootTab = RootTab.WORKOUT,
    val settings: AppSettings = AppSettings(),
    val permissions: PermissionSnapshot = PermissionSnapshot(),
    val monitoring: MonitoringSnapshot = MonitoringSnapshot(),
    val routines: List<Routine> = emptyList(),
    val completedWorkouts: List<WorkoutFeedItem> = emptyList(),
    val calendarDays: List<CalendarDaySummary> = emptyList(),
    val profileStats: ProfileStats = ProfileStats(
        totalWorkouts = 0,
        totalSets = 0,
        totalVolumeKg = 0f,
        currentWeekCount = 0,
        streakDays = 0,
        maxReps = 0,
        maxActiveMs = 0L,
        records = emptyList(),
    ),
    val activeWorkout: ActiveWorkoutState? = null,
    val selectedWorkoutDetail: CompletedWorkoutDetail? = null,
    val showCameraPreview: Boolean = true,
    val cameraPreviewFrame: DebugPreviewFrame? = null,
    val routinesExpanded: Boolean = true,
    val exploreVisible: Boolean = false,
    val exploreQuery: String = "",
    val exploreResults: List<ExerciseTemplate> = DefaultExerciseLibrary,
    val pendingAutoEvents: List<AutoSetEvent> = emptyList(),
    val restTimer: RestTimerUiState = RestTimerUiState(),
    val workoutMessage: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val settingsRepository = SettingsRepository(appContext)
    private val database = GripmaxxerDatabase.getInstance(appContext)
    private val routineRepository = RoomRoutineRepository(database)
    private val workoutRepository = RoomWorkoutRepository(database)

    private val permissionsState = MutableStateFlow(readPermissions())
    private val selectedTabState = MutableStateFlow(RootTab.WORKOUT)
    private val showCameraPreviewState = MutableStateFlow(true)
    private val routinesExpandedState = MutableStateFlow(true)
    private val exploreVisibleState = MutableStateFlow(false)
    private val exploreQueryState = MutableStateFlow("")
    private val selectedWorkoutDetailState = MutableStateFlow<CompletedWorkoutDetail?>(null)
    private val pendingAutoEventsState = MutableStateFlow<List<AutoSetEvent>>(emptyList())
    private val restTimerState = MutableStateFlow(RestTimerUiState())
    private val workoutMessageState = MutableStateFlow<String?>(null)
    private val autoTrackExerciseIdState = MutableStateFlow<Long?>(null)
    private val nowMsState = MutableStateFlow(System.currentTimeMillis())

    private var restTimerJob: Job? = null

    private val settingsState = settingsRepository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings(),
    )

    private val routinesState = routineRepository.routinesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
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
            totalSets = 0,
            totalVolumeKg = 0f,
            currentWeekCount = 0,
            streakDays = 0,
            maxReps = 0,
            maxActiveMs = 0L,
            records = emptyList(),
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
            migrateLegacyHistoryIfNeeded()
        }

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
        routinesState,
        completedWorkoutsState,
        calendarDaysState,
        profileStatsState,
        activeWorkoutState,
        showCameraPreviewState,
        routinesExpandedState,
        exploreVisibleState,
        exploreQueryState,
        selectedWorkoutDetailState,
        pendingAutoEventsState,
        restTimerState,
        workoutMessageState,
        nowMsState,
        DebugPreviewStore.frame,
    ) { values ->
        val selectedTab = values[0] as RootTab
        val settings = values[1] as AppSettings
        val permissions = values[2] as PermissionSnapshot
        val monitoring = values[3] as MonitoringSnapshot
        val routines = values[4] as List<Routine>
        val completedWorkouts = values[5] as List<WorkoutFeedItem>
        val calendarDays = values[6] as List<CalendarDaySummary>
        val profileStats = values[7] as ProfileStats
        val activeWorkout = values[8] as ActiveWorkoutState?
        val showCameraPreview = values[9] as Boolean
        val routinesExpanded = values[10] as Boolean
        val exploreVisible = values[11] as Boolean
        val exploreQuery = values[12] as String
        val selectedWorkoutDetail = values[13] as CompletedWorkoutDetail?
        val pendingAutoEvents = values[14] as List<AutoSetEvent>
        val restTimer = values[15] as RestTimerUiState
        val workoutMessage = values[16] as String?
        val nowMs = values[17] as Long
        val debugFrame = values[18] as DebugPreviewFrame?

        val exploreResults = filterExerciseLibrary(exploreQuery)
        val renderedActiveWorkout = activeWorkout?.copy(
            elapsedMs = (nowMs - activeWorkout.startedAtMs).coerceAtLeast(0L),
        )

        MainUiState(
            selectedTab = selectedTab,
            settings = settings,
            permissions = permissions,
            monitoring = monitoring,
            routines = routines,
            completedWorkouts = completedWorkouts,
            calendarDays = calendarDays,
            profileStats = profileStats,
            activeWorkout = renderedActiveWorkout,
            selectedWorkoutDetail = selectedWorkoutDetail,
            showCameraPreview = showCameraPreview,
            cameraPreviewFrame = if (showCameraPreview) debugFrame else null,
            routinesExpanded = routinesExpanded,
            exploreVisible = exploreVisible,
            exploreQuery = exploreQuery,
            exploreResults = exploreResults,
            pendingAutoEvents = pendingAutoEvents,
            restTimer = restTimer,
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

    fun setWeightUnit(unit: WeightUnit) {
        viewModelScope.launch {
            settingsRepository.setWeightUnit(unit)
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

    fun startMonitoringWithMode(mode: ExerciseMode) {
        viewModelScope.launch {
            settingsRepository.setSelectedExerciseMode(mode)
            HangCamService.start(appContext)
        }
    }

    fun stopMonitoring() {
        HangCamService.stop(appContext)
    }

    fun startEmptyWorkout() {
        viewModelScope.launch {
            val title = "Workout ${formatTimestamp(System.currentTimeMillis())}"
            workoutRepository.startEmptyWorkout(title)
            selectedTabState.value = RootTab.WORKOUT
            clearWorkoutMessageLater("Empty workout started")
        }
    }

    fun startRoutineWorkout(routineId: Long) {
        viewModelScope.launch {
            val id = workoutRepository.startWorkoutFromRoutine(routineId)
            if (id != null) {
                clearWorkoutMessageLater("Routine started")
            } else {
                clearWorkoutMessageLater("Could not start routine")
            }
        }
    }

    fun finishActiveWorkout() {
        viewModelScope.launch {
            val activeId = activeWorkoutState.value?.id ?: return@launch
            if (workoutRepository.finishWorkout(activeId)) {
                stopMonitoring()
                clearWorkoutMessageLater("Workout finished")
            }
        }
    }

    fun updateActiveWorkoutTitle(title: String) {
        viewModelScope.launch {
            val activeId = activeWorkoutState.value?.id ?: return@launch
            workoutRepository.updateWorkoutTitle(activeId, title)
        }
    }

    fun addExerciseToActiveWorkout(template: ExerciseTemplate) {
        viewModelScope.launch {
            val activeWorkoutId = activeWorkoutState.value?.id ?: workoutRepository.startEmptyWorkout(
                title = "Workout ${formatTimestamp(System.currentTimeMillis())}"
            )
            workoutRepository.addExerciseToWorkout(
                workoutId = activeWorkoutId,
                exerciseName = template.name,
                mode = template.mode,
                targetSets = 3,
                targetReps = 8,
                targetWeightKg = 0f,
                restSeconds = 90,
            )
            clearWorkoutMessageLater("Added ${template.name}")
        }
    }

    fun addSet(exerciseId: Long) {
        viewModelScope.launch {
            workoutRepository.addSet(exerciseId)
        }
    }

    fun removeSet(setId: Long) {
        viewModelScope.launch {
            workoutRepository.removeSet(setId)
        }
    }

    fun updateSetWeight(setId: Long, displayWeight: Float) {
        viewModelScope.launch {
            val unit = settingsState.value.weightUnit
            val weightKg = if (unit == WeightUnit.KG) displayWeight else displayWeight / LB_PER_KG
            workoutRepository.updateSetWeight(setId, weightKg)
        }
    }

    fun updateSetReps(setId: Long, reps: Int) {
        viewModelScope.launch {
            workoutRepository.updateSetReps(setId, reps)
        }
    }

    fun toggleSetDone(exerciseId: Long, setId: Long, done: Boolean, restSeconds: Int, exerciseName: String) {
        viewModelScope.launch {
            workoutRepository.toggleSetDone(setId, done)
            if (done) {
                startRestTimer(restSeconds = restSeconds, exerciseName = exerciseName)
            }
        }
    }

    fun startAutoTrackForExercise(exerciseId: Long, mode: ExerciseMode?) {
        if (mode == null) {
            clearWorkoutMessageLater("Auto track is only available for mapped camera exercises")
            return
        }
        autoTrackExerciseIdState.value = exerciseId
        startMonitoringWithMode(mode)
        clearWorkoutMessageLater("Auto tracking ${mode.label}")
    }

    fun applyPendingAutoEvent(eventId: Long) {
        viewModelScope.launch {
            val event = pendingAutoEventsState.value.firstOrNull { it.eventId == eventId } ?: return@launch
            val suggestion = workoutRepository.applyAutoSetEvent(event, autoTrackExerciseIdState.value)
            pendingAutoEventsState.update { pending -> pending.filterNot { it.eventId == eventId } }
            if (suggestion != null) {
                val message = if (suggestion.mode.isHangMode()) {
                    "Applied hold ${formatDurationShort(suggestion.durationMs)} to ${suggestion.exerciseName}"
                } else {
                    "Applied auto reps ${suggestion.reps} to ${suggestion.exerciseName}"
                }
                clearWorkoutMessageLater(message)
            }
        }
    }

    fun discardPendingAutoEvent(eventId: Long) {
        pendingAutoEventsState.update { pending -> pending.filterNot { it.eventId == eventId } }
    }

    fun clearWorkoutMessage() {
        workoutMessageState.value = null
    }

    fun toggleRoutinesExpanded() {
        routinesExpandedState.update { !it }
    }

    fun setExploreVisible(visible: Boolean) {
        exploreVisibleState.value = visible
    }

    fun setExploreQuery(query: String) {
        exploreQueryState.value = query
    }

    fun createRoutine(name: String, exercisesCsv: String) {
        viewModelScope.launch {
            val parsed = parseExercisesCsv(exercisesCsv)
            val routineName = name.ifBlank { "Routine ${formatTimestamp(System.currentTimeMillis())}" }
            routineRepository.createRoutine(
                name = routineName,
                exercises = if (parsed.isNotEmpty()) parsed else defaultRoutineInputs(),
            )
            clearWorkoutMessageLater("Routine created")
        }
    }

    fun quickCreateRoutine() {
        viewModelScope.launch {
            routineRepository.createRoutine(
                name = "Quick Routine ${formatTimestamp(System.currentTimeMillis())}",
                exercises = defaultRoutineInputs(),
            )
            clearWorkoutMessageLater("Quick routine created")
        }
    }

    fun renameRoutine(routineId: Long, name: String) {
        viewModelScope.launch {
            if (name.isNotBlank()) {
                routineRepository.renameRoutine(routineId, name.trim())
            }
        }
    }

    fun duplicateRoutine(routineId: Long) {
        viewModelScope.launch {
            routineRepository.duplicateRoutine(routineId)
            clearWorkoutMessageLater("Routine duplicated")
        }
    }

    fun deleteRoutine(routineId: Long) {
        viewModelScope.launch {
            routineRepository.deleteRoutine(routineId)
            clearWorkoutMessageLater("Routine deleted")
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

    fun clearRestTimerNotice() {
        restTimerState.update { it.copy(completionNotice = null) }
    }

    fun displayWeight(kg: Float): String {
        return if (settingsState.value.weightUnit == WeightUnit.KG) {
            formatWeight(kg)
        } else {
            formatWeight(kg * LB_PER_KG)
        }
    }

    private suspend fun migrateLegacyHistoryIfNeeded() {
        val migrated = settingsRepository.isRoomHistoryMigrated()
        if (migrated) return
        val legacySessions = settingsRepository.readLegacySessions()
        runCatching {
            workoutRepository.importLegacySessionsIfNeeded(legacySessions)
        }
        settingsRepository.markRoomHistoryMigrated()
    }

    private suspend fun handleAutoSetEvent(event: AutoSetEvent) {
        val active = activeWorkoutState.value
        if (active == null) {
            pendingAutoEventsState.update { (it + event).takeLast(MAX_PENDING_AUTO_EVENTS) }
            return
        }
        val suggestion = workoutRepository.applyAutoSetEvent(event, autoTrackExerciseIdState.value)
        if (suggestion == null) {
            pendingAutoEventsState.update { (it + event).takeLast(MAX_PENDING_AUTO_EVENTS) }
            return
        }
        val message = if (suggestion.mode.isHangMode()) {
            "Auto set ready for ${suggestion.exerciseName}: ${formatDurationShort(suggestion.durationMs)} hold"
        } else {
            "Auto set ready for ${suggestion.exerciseName}: ${suggestion.reps} reps"
        }
        clearWorkoutMessageLater(message)
    }

    private fun startRestTimer(restSeconds: Int, exerciseName: String) {
        restTimerJob?.cancel()
        restTimerJob = viewModelScope.launch {
            var remaining = restSeconds.coerceAtLeast(15)
            restTimerState.value = RestTimerUiState(
                running = true,
                remainingSeconds = remaining,
                exerciseName = exerciseName,
                completionNotice = null,
            )
            while (remaining > 0) {
                delay(1000L)
                remaining -= 1
                restTimerState.value = restTimerState.value.copy(
                    running = remaining > 0,
                    remainingSeconds = remaining.coerceAtLeast(0),
                )
            }
            vibrateAlert()
            restTimerState.value = RestTimerUiState(
                running = false,
                remainingSeconds = 0,
                exerciseName = exerciseName,
                completionNotice = "Rest finished for $exerciseName",
            )
        }
    }

    private fun vibrateAlert() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(250L, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(250L)
        }
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

    private fun parseExercisesCsv(input: String): List<RoutineExerciseTemplateInput> {
        return input.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { name ->
                val template = DefaultExerciseLibrary.firstOrNull { it.name.equals(name, ignoreCase = true) }
                RoutineExerciseTemplateInput(
                    exerciseName = template?.name ?: name,
                    mode = template?.mode,
                    targetSets = 3,
                    targetReps = 8,
                    targetWeightKg = 0f,
                    restSeconds = 90,
                )
            }
    }

    private fun defaultRoutineInputs(): List<RoutineExerciseTemplateInput> {
        return listOf(
            RoutineExerciseTemplateInput(
                exerciseName = "Pull-up",
                mode = ExerciseMode.PULL_UP,
                targetSets = 3,
                targetReps = 8,
                targetWeightKg = 0f,
                restSeconds = 90,
            ),
            RoutineExerciseTemplateInput(
                exerciseName = "Push-up",
                mode = ExerciseMode.PUSH_UP,
                targetSets = 3,
                targetReps = 12,
                targetWeightKg = 0f,
                restSeconds = 75,
            ),
        )
    }

    private fun filterExerciseLibrary(query: String): List<ExerciseTemplate> {
        val q = query.trim().lowercase(Locale.US)
        if (q.isBlank()) return DefaultExerciseLibrary
        return DefaultExerciseLibrary.filter { it.name.lowercase(Locale.US).contains(q) }
    }

    private fun formatWeight(value: Float): String {
        val asInt = value.toInt()
        return if (asInt.toFloat() == value) {
            asInt.toString()
        } else {
            String.format(Locale.US, "%.1f", value)
        }
    }

    private fun formatTimestamp(timestampMs: Long): String {
        val date = java.util.Date(timestampMs)
        val formatter = java.text.SimpleDateFormat("MMM d", Locale.US)
        return formatter.format(date)
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

    companion object {
        private const val LB_PER_KG = 2.20462f
        private const val MAX_PENDING_AUTO_EVENTS = 20
    }
}

private fun ExerciseMode.isHangMode(): Boolean {
    return this == ExerciseMode.DEAD_HANG || this == ExerciseMode.ACTIVE_HANG
}
