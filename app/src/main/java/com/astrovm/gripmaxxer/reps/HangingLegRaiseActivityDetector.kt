package com.astrovm.gripmaxxer.reps

import com.astrovm.gripmaxxer.hang.HangDetectionConfig
import com.astrovm.gripmaxxer.hang.HangDetector
import com.astrovm.gripmaxxer.pose.PoseFrame

class HangingLegRaiseActivityDetector : ModeActivityDetector {

    private val hangDetector = HangDetector()

    fun updateConfig(config: HangDetectionConfig) {
        hangDetector.updateConfig(config)
    }

    override fun reset() {
        hangDetector.reset()
    }

    override fun process(
        frame: PoseFrame,
        nowMs: Long,
    ): Boolean {
        return hangDetector.process(frame = frame, nowMs = nowMs).isHanging
    }
}
