package com.astrolabs.gripmaxxer.reps

import com.astrolabs.gripmaxxer.hang.HangDetectionConfig
import com.astrolabs.gripmaxxer.hang.HangDetector
import com.astrolabs.gripmaxxer.pose.PoseFrame

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
