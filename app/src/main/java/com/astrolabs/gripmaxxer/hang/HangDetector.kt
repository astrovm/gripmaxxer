package com.astrolabs.gripmaxxer.hang

import com.astrolabs.gripmaxxer.pose.PoseFrame
import com.astrolabs.gripmaxxer.pose.NormalizedLandmark
import com.google.mlkit.vision.pose.PoseLandmark

data class HangDetectionConfig(
    val wristShoulderMargin: Float = 0.06f,
    val missingPoseTimeoutMs: Long = 300L,
    val partialPoseHoldMs: Long = 3000L,
    val shoulderOnlyHoldMs: Long = 900L,
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
    private var partialSinceMs: Long? = null
    private var shoulderOnlySinceMs: Long? = null
    private var candidateState: Boolean? = null
    private var candidateSinceMs: Long = 0L
    private var lastToggleMs: Long = 0L

    fun updateConfig(config: HangDetectionConfig) {
        this.config = config
    }

    fun reset() {
        isHanging = false
        missingSinceMs = null
        partialSinceMs = null
        shoulderOnlySinceMs = null
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
        val shoulderY = frame.averageY(PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER)
        val wristY = frame.averageY(PoseLandmark.LEFT_WRIST, PoseLandmark.RIGHT_WRIST)
        val canComputeHangNow = shoulderY != null && wristY != null && hasReliableUpperBodyPose(frame)

        if (canComputeHangNow) {
            missingSinceMs = null
            partialSinceMs = null
            shoulderOnlySinceMs = null
            return wristY < (shoulderY - config.wristShoulderMargin)
        }

        // At top position, wrists can leave frame on door bars. Keep hanging briefly if upper body is still reliable.
        if (isHanging && hasUpperBodyCore(frame)) {
            if (partialSinceMs == null) partialSinceMs = nowMs
            shoulderOnlySinceMs = null
            val partialDuration = nowMs - (partialSinceMs ?: nowMs)
            if (partialDuration <= config.partialPoseHoldMs) {
                return true
            }
        } else {
            partialSinceMs = null
            if (isHanging && hasShoulderPair(frame)) {
                if (shoulderOnlySinceMs == null) shoulderOnlySinceMs = nowMs
                val shoulderOnlyDuration = nowMs - (shoulderOnlySinceMs ?: nowMs)
                if (shoulderOnlyDuration <= config.shoulderOnlyHoldMs) {
                    return true
                }
            } else {
                shoulderOnlySinceMs = null
            }
        }

        if (missingSinceMs == null) missingSinceMs = nowMs
        val missingDuration = nowMs - (missingSinceMs ?: nowMs)
        if (missingDuration > config.missingPoseTimeoutMs) {
            return false
        }
        return isHanging
    }

    private fun hasUpperBodyCore(frame: PoseFrame): Boolean {
        val leftShoulder = frame.landmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = frame.landmark(PoseLandmark.RIGHT_SHOULDER)
        if (leftShoulder == null && rightShoulder == null) return false

        val hasLeftArmAnchor = frame.landmark(PoseLandmark.LEFT_ELBOW) != null ||
            frame.landmark(PoseLandmark.LEFT_WRIST) != null
        val hasRightArmAnchor = frame.landmark(PoseLandmark.RIGHT_ELBOW) != null ||
            frame.landmark(PoseLandmark.RIGHT_WRIST) != null
        if (!hasLeftArmAnchor && !hasRightArmAnchor) return false

        if (leftShoulder != null && rightShoulder != null) {
            val shoulderWidth = distance(leftShoulder, rightShoulder)
            if (shoulderWidth < 0.08f) return false
        }

        return true
    }

    private fun hasReliableUpperBodyPose(frame: PoseFrame): Boolean {
        val leftShoulder = frame.landmark(PoseLandmark.LEFT_SHOULDER) ?: return false
        val rightShoulder = frame.landmark(PoseLandmark.RIGHT_SHOULDER) ?: return false

        val shoulderWidth = distance(leftShoulder, rightShoulder)
        if (shoulderWidth < 0.10f) return false

        val leftElbow = frame.landmark(PoseLandmark.LEFT_ELBOW)
        val rightElbow = frame.landmark(PoseLandmark.RIGHT_ELBOW)
        val leftWrist = frame.landmark(PoseLandmark.LEFT_WRIST)
        val rightWrist = frame.landmark(PoseLandmark.RIGHT_WRIST)
        if (leftWrist == null && rightWrist == null) return false

        val minArmLengthWithElbow = shoulderWidth * 0.55f
        val minShoulderWristWithoutElbow = shoulderWidth * 0.25f

        val leftArmReliable = isArmReliable(
            shoulder = leftShoulder,
            elbow = leftElbow,
            wrist = leftWrist,
            minArmLengthWithElbow = minArmLengthWithElbow,
            minShoulderWristWithoutElbow = minShoulderWristWithoutElbow,
        )
        val rightArmReliable = isArmReliable(
            shoulder = rightShoulder,
            elbow = rightElbow,
            wrist = rightWrist,
            minArmLengthWithElbow = minArmLengthWithElbow,
            minShoulderWristWithoutElbow = minShoulderWristWithoutElbow,
        )
        if (!leftArmReliable && !rightArmReliable) return false

        if (leftWrist != null && rightWrist != null) {
            val wristWidth = distance(leftWrist, rightWrist)
            if (wristWidth < shoulderWidth * 0.45f || wristWidth > shoulderWidth * 2.8f) return false
        }

        return true
    }

    private fun hasShoulderPair(frame: PoseFrame): Boolean {
        val leftShoulder = frame.landmark(PoseLandmark.LEFT_SHOULDER) ?: return false
        val rightShoulder = frame.landmark(PoseLandmark.RIGHT_SHOULDER) ?: return false
        return distance(leftShoulder, rightShoulder) >= 0.08f
    }

    private fun isArmReliable(
        shoulder: NormalizedLandmark,
        elbow: NormalizedLandmark?,
        wrist: NormalizedLandmark?,
        minArmLengthWithElbow: Float,
        minShoulderWristWithoutElbow: Float,
    ): Boolean {
        wrist ?: return false

        return if (elbow != null) {
            val armLength = distance(shoulder, elbow) + distance(elbow, wrist)
            armLength >= minArmLengthWithElbow
        } else {
            distance(shoulder, wrist) >= minShoulderWristWithoutElbow
        }
    }

    private fun distance(a: NormalizedLandmark, b: NormalizedLandmark): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}
