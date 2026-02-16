package com.astrolabs.gripmaxxer.reps

import com.astrolabs.gripmaxxer.pose.PoseFrame

interface ModeActivityDetector {
    fun reset()
    fun process(
        frame: PoseFrame,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean
}
