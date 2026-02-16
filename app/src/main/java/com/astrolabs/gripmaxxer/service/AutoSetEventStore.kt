package com.astrolabs.gripmaxxer.service

import com.astrolabs.gripmaxxer.reps.ExerciseMode
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicLong

data class AutoSetEvent(
    val eventId: Long,
    val mode: ExerciseMode,
    val reps: Int,
    val activeMs: Long,
    val timestampMs: Long,
)

object AutoSetEventStore {
    private val eventCounter = AtomicLong(0L)
    private val _events = MutableSharedFlow<AutoSetEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<AutoSetEvent> = _events.asSharedFlow()

    fun emit(
        mode: ExerciseMode,
        reps: Int,
        activeMs: Long,
        timestampMs: Long,
    ) {
        _events.tryEmit(
            AutoSetEvent(
                eventId = eventCounter.incrementAndGet(),
                mode = mode,
                reps = reps,
                activeMs = activeMs,
                timestampMs = timestampMs,
            )
        )
    }
}
