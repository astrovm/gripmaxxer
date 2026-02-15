package com.astrolabs.hangmaxxer.hang

import com.astrolabs.hangmaxxer.pose.PoseFrame
import com.google.mlkit.vision.pose.PoseLandmark

data class HangDetectionConfig(
    val wristShoulderMargin: Float = 0.06f,
    val missingPoseTimeoutMs: Long = 300L,
    val stableSwitchMs: Long = 500L,
    val minToggleIntervalMs: Long = 1500L,
)

data class HangDetectionResult(
    val isHanging: Boolean,
    val transitioned: Boolean,
)

class HangDetector(
    private var config: HangDetectionConfig = HangDetectionConfig(),
) {

    private var isHanging = false
    private var missingSinceMs: Long? = null
    private var candidateState: Boolean? = null
    private var candidateSinceMs: Long = 0L
    private var lastToggleMs: Long = 0L

    fun updateConfig(config: HangDetectionConfig) {
        this.config = config
    }

    fun reset() {
        isHanging = false
        missingSinceMs = null
        candidateState = null
        candidateSinceMs = 0L
        lastToggleMs = 0L
    }

    fun process(frame: PoseFrame, nowMs: Long = System.currentTimeMillis()): HangDetectionResult {
        val rawHanging = rawHangingState(frame, nowMs)

        if (rawHanging == isHanging) {
            candidateState = null
            return HangDetectionResult(isHanging = isHanging, transitioned = false)
        }

        if (candidateState != rawHanging) {
            candidateState = rawHanging
            candidateSinceMs = nowMs
            return HangDetectionResult(isHanging = isHanging, transitioned = false)
        }

        val stableForMs = nowMs - candidateSinceMs
        val sinceLastToggle = nowMs - lastToggleMs
        if (stableForMs >= config.stableSwitchMs && sinceLastToggle >= config.minToggleIntervalMs) {
            isHanging = rawHanging
            lastToggleMs = nowMs
            candidateState = null
            return HangDetectionResult(isHanging = isHanging, transitioned = true)
        }

        return HangDetectionResult(isHanging = isHanging, transitioned = false)
    }

    private fun rawHangingState(frame: PoseFrame, nowMs: Long): Boolean {
        if (!frame.posePresent) {
            if (missingSinceMs == null) missingSinceMs = nowMs
            val missingDuration = nowMs - (missingSinceMs ?: nowMs)
            if (missingDuration > config.missingPoseTimeoutMs) {
                return false
            }
            return isHanging
        }
        missingSinceMs = null

        val wristY = frame.averageY(PoseLandmark.LEFT_WRIST, PoseLandmark.RIGHT_WRIST) ?: return false
        val shoulderY = frame.averageY(PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER) ?: return false

        return wristY < (shoulderY - config.wristShoulderMargin)
    }
}
