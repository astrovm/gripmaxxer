package com.astrolabs.hangmaxxer.service

import android.graphics.Bitmap
import com.astrolabs.hangmaxxer.pose.NormalizedLandmark
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DebugPreviewFrame(
    val bitmap: Bitmap,
    val landmarks: Map<Int, NormalizedLandmark>,
    val timestampMs: Long,
)

object DebugPreviewStore {
    val enabled = MutableStateFlow(false)

    private val _frame = MutableStateFlow<DebugPreviewFrame?>(null)
    val frame: StateFlow<DebugPreviewFrame?> = _frame.asStateFlow()

    fun publish(frame: DebugPreviewFrame) {
        _frame.value = frame
    }

    fun clear() {
        _frame.value = null
    }
}
