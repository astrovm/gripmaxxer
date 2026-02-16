package com.astrolabs.gripmaxxer.reps

import com.astrolabs.gripmaxxer.pose.PoseFrame

interface ModeRepDetector {
    fun reset()
    fun process(
        frame: PoseFrame,
        active: Boolean,
        nowMs: Long = System.currentTimeMillis(),
    ): RepCounterResult
}
