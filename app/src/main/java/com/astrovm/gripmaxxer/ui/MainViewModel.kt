package com.astrovm.gripmaxxer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astrovm.gripmaxxer.AppContainer
import com.astrovm.gripmaxxer.data.Accent
import com.astrovm.gripmaxxer.data.ExerciseStats
import com.astrovm.gripmaxxer.data.Settings
import com.astrovm.gripmaxxer.data.Workout
import com.astrovm.gripmaxxer.data.WorkoutSet
import com.astrovm.gripmaxxer.tracking.Exercise
import com.astrovm.gripmaxxer.tracking.LiveState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class Tab { WORKOUT, HISTORY, PROFILE }

/** Which special-access permissions the app has. Refreshed whenever the app comes back. */
data class Access(
    val camera: Boolean = false,
    val notifications: Boolean = false,
    val overlay: Boolean = false,
)

/** Database state is still loading. */
sealed interface Loadable<out T> {
    data object Loading : Loadable<Nothing>
    data class Ready<T>(val value: T) : Loadable<T>
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(private val app: AppContainer, private val readAccess: () -> Access) : ViewModel() {

    private val _tab = MutableStateFlow(Tab.WORKOUT)
    val tab: StateFlow<Tab> = _tab.asStateFlow()

    private val _openWorkoutId = MutableStateFlow<Long?>(null)
    val openWorkoutId: StateFlow<Long?> = _openWorkoutId.asStateFlow()

    private val _access = MutableStateFlow(readAccess())
    val access: StateFlow<Access> = _access.asStateFlow()

    val settings: StateFlow<Settings?> = app.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val activeWorkout: StateFlow<Loadable<Workout?>> = app.workouts.active
        .map { Loadable.Ready(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Loadable.Loading)

    val history: StateFlow<Loadable<List<Workout>>> = app.workouts.history
        .map { Loadable.Ready(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Loadable.Loading)

    val stats: StateFlow<List<ExerciseStats>> =
        app.workouts.stats.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val openWorkout: StateFlow<Workout?> = _openWorkoutId
        .flatMapLatest { id -> if (id == null) flowOf(null) else app.workouts.workout(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Wall clock that ticks every second while someone is watching it. */
    val clock: StateFlow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), System.currentTimeMillis())

    val live: StateFlow<LiveState> = app.controller.live
    val preview = app.preview

    fun selectTab(tab: Tab) {
        _tab.value = tab
    }

    fun openWorkout(id: Long?) {
        _openWorkoutId.value = id
    }

    fun refreshAccess() {
        _access.value = readAccess()
    }

    fun setPreviewVisible(visible: Boolean) {
        app.previewVisible.value = visible
        if (!visible) app.preview.value = null
    }

    fun startWorkout(exercise: Exercise) = launch { app.controller.start(exercise) }

    fun resumeTracking() = launch { app.controller.resume() }

    fun switchExercise(exercise: Exercise) = launch { app.controller.switchExercise(exercise) }

    fun finishWorkout() = launch { app.controller.finish() }

    /** [completedAtMs] places a set added to a past workout. Null means now. */
    fun addSet(workoutId: Long, exercise: Exercise, reps: Int, durationMs: Long, completedAtMs: Long? = null) =
        launch { app.workouts.addSet(workoutId, exercise, reps, durationMs, completedAtMs) }

    fun updateSet(setId: Long, reps: Int, durationMs: Long) = launch { app.workouts.updateSet(setId, reps, durationMs) }

    fun deleteSet(setId: Long) = launch { app.workouts.deleteSet(setId) }

    /** Undoes [deleteSet]. Does nothing if the workout is gone too. */
    fun restoreSet(workoutId: Long, set: WorkoutSet) = launch { runCatching { app.workouts.restoreSet(workoutId, set) } }

    fun deleteWorkout(id: Long) = launch {
        _openWorkoutId.value = null
        app.workouts.delete(id)
    }

    fun setMediaControl(on: Boolean) = launch { app.settings.setMediaControl(on) }
    fun setOverlay(on: Boolean) = launch { app.settings.setOverlay(on) }
    fun setRepSound(on: Boolean) = launch { app.settings.setRepSound(on) }
    fun setVoiceCues(on: Boolean) = launch { app.settings.setVoiceCues(on) }
    fun setAccent(accent: Accent) = launch { app.settings.setAccent(accent) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
