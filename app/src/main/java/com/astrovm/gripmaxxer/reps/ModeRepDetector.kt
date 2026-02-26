package com.astrovm.gripmaxxer.reps

import com.astrovm.gripmaxxer.pose.PoseFrame

interface ModeRepDetector {
    fun reset()
    fun process(
        frame: PoseFrame,
        active: Boolean,
        nowMs: Long = System.currentTimeMillis(),
    ): RepCounterResult
}
