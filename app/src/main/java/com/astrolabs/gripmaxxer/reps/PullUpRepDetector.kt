package com.astrolabs.gripmaxxer.reps

import com.astrolabs.gripmaxxer.pose.PoseFeatureExtractor
import com.astrolabs.gripmaxxer.pose.PoseFrame

class PullUpRepDetector(
    featureExtractor: PoseFeatureExtractor,
) : ModeRepDetector {

    private val repCounter = RepCounter(featureExtractor)

    fun updateConfig(config: RepCounterConfig) {
        repCounter.updateConfig(config)
    }

    override fun reset() {
        repCounter.reset()
    }

    override fun process(
        frame: PoseFrame,
        active: Boolean,
        nowMs: Long,
    ): RepCounterResult {
        return repCounter.process(frame = frame, hanging = active, nowMs = nowMs)
    }
}
