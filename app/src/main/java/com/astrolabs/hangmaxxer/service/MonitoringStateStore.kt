package com.astrolabs.hangmaxxer.service

import com.astrolabs.hangmaxxer.reps.ExerciseMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MonitoringSnapshot(
    val serviceRunning: Boolean = false,
    val hanging: Boolean = false,
    val reps: Int = 0,
    val elapsedHangMs: Long = 0L,
    val mode: ExerciseMode = ExerciseMode.PULL_UP,
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
