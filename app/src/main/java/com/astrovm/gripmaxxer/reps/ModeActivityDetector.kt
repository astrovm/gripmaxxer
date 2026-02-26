package com.astrovm.gripmaxxer.reps

import com.astrovm.gripmaxxer.pose.PoseFrame

interface ModeActivityDetector {
    fun reset()
    fun process(
        frame: PoseFrame,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean
}
