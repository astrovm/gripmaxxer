package com.astrolabs.gripmaxxer.reps

import com.astrolabs.gripmaxxer.pose.PoseFrame

class HoldRepDetector : ModeRepDetector {

    override fun reset() = Unit

    override fun process(
        frame: PoseFrame,
        active: Boolean,
        nowMs: Long,
    ): RepCounterResult {
        return RepCounterResult(
            reps = 0,
            repEvent = false,
        )
    }
}
