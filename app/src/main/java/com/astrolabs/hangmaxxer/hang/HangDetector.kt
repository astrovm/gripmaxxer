package com.astrolabs.hangmaxxer.hang

import com.astrolabs.hangmaxxer.pose.PoseFrame
import com.astrolabs.hangmaxxer.pose.NormalizedLandmark
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
        if (!hasReliableUpperBodyPose(frame)) return false

        val wristY = frame.averageY(PoseLandmark.LEFT_WRIST, PoseLandmark.RIGHT_WRIST) ?: return false
        val shoulderY = frame.averageY(PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER) ?: return false

        return wristY < (shoulderY - config.wristShoulderMargin)
    }

    private fun hasReliableUpperBodyPose(frame: PoseFrame): Boolean {
        val leftShoulder = frame.landmark(PoseLandmark.LEFT_SHOULDER) ?: return false
        val rightShoulder = frame.landmark(PoseLandmark.RIGHT_SHOULDER) ?: return false
        val leftElbow = frame.landmark(PoseLandmark.LEFT_ELBOW) ?: return false
        val rightElbow = frame.landmark(PoseLandmark.RIGHT_ELBOW) ?: return false
        val leftWrist = frame.landmark(PoseLandmark.LEFT_WRIST) ?: return false
        val rightWrist = frame.landmark(PoseLandmark.RIGHT_WRIST) ?: return false

        val shoulderWidth = distance(leftShoulder, rightShoulder)
        if (shoulderWidth < 0.12f) return false

        val wristWidth = distance(leftWrist, rightWrist)
        if (wristWidth < shoulderWidth * 0.55f || wristWidth > shoulderWidth * 2.6f) return false

        val leftArmLength = distance(leftShoulder, leftElbow) + distance(leftElbow, leftWrist)
        val rightArmLength = distance(rightShoulder, rightElbow) + distance(rightElbow, rightWrist)
        val minArmLength = shoulderWidth * 0.9f
        if (leftArmLength < minArmLength || rightArmLength < minArmLength) return false

        return true
    }

    private fun distance(a: NormalizedLandmark, b: NormalizedLandmark): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}
