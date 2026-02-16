package com.astrolabs.gripmaxxer.service

import com.astrolabs.gripmaxxer.reps.ExerciseMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MonitoringSnapshot(
    val serviceRunning: Boolean = false,
    val hanging: Boolean = false,
    val reps: Int = 0,
    val elapsedHangMs: Long = 0L,
    val cameraFps: Int = 0,
    val posePresent: Boolean = false,
    val lastFrameAgeMs: Long = Long.MAX_VALUE,
    val mode: ExerciseMode = ExerciseMode.UNKNOWN,
    val hasMediaController: Boolean = false,
    val mediaControllerPackage: String? = null,
)

object MonitoringStateStore {
    private val _snapshot = MutableStateFlow(MonitoringSnapshot())
    val snapshot: StateFlow<MonitoringSnapshot> = _snapshot.asStateFlow()

    fun update(update: (MonitoringSnapshot) -> MonitoringSnapshot) {
        _snapshot.value = update(_snapshot.value)
    }

    fun reset() {
        _snapshot.value = MonitoringSnapshot()
    }
}
