package com.astrovm.gripmaxxer.testutil

import com.astrovm.gripmaxxer.pose.NormalizedLandmark
import com.astrovm.gripmaxxer.pose.PoseFrame
import com.google.mlkit.vision.pose.PoseLandmark

fun lm(x: Float, y: Float) = NormalizedLandmark(x, y)

fun frameOf(
    vararg landmarks: Pair<Int, NormalizedLandmark>,
    timestampMs: Long = 0L,
    posePresent: Boolean = true,
): PoseFrame = PoseFrame(landmarks = mapOf(*landmarks), timestampMs = timestampMs, posePresent = posePresent)

/** Frame builder with the landmarks used by the detectors, all optional. */
data class Body(
    val nose: NormalizedLandmark? = null,
    val leftMouth: NormalizedLandmark? = null,
    val rightMouth: NormalizedLandmark? = null,
    val leftShoulder: NormalizedLandmark? = null,
    val rightShoulder: NormalizedLandmark? = null,
    val leftElbow: NormalizedLandmark? = null,
    val rightElbow: NormalizedLandmark? = null,
    val leftWrist: NormalizedLandmark? = null,
    val rightWrist: NormalizedLandmark? = null,
    val leftHip: NormalizedLandmark? = null,
    val rightHip: NormalizedLandmark? = null,
    val leftKnee: NormalizedLandmark? = null,
    val rightKnee: NormalizedLandmark? = null,
    val leftAnkle: NormalizedLandmark? = null,
    val rightAnkle: NormalizedLandmark? = null,
) {
    fun frame(timestampMs: Long = 0L): PoseFrame {
        val map = buildMap {
            nose?.let { put(PoseLandmark.NOSE, it) }
            leftMouth?.let { put(PoseLandmark.LEFT_MOUTH, it) }
            rightMouth?.let { put(PoseLandmark.RIGHT_MOUTH, it) }
            leftShoulder?.let { put(PoseLandmark.LEFT_SHOULDER, it) }
            rightShoulder?.let { put(PoseLandmark.RIGHT_SHOULDER, it) }
            leftElbow?.let { put(PoseLandmark.LEFT_ELBOW, it) }
            rightElbow?.let { put(PoseLandmark.RIGHT_ELBOW, it) }
            leftWrist?.let { put(PoseLandmark.LEFT_WRIST, it) }
            rightWrist?.let { put(PoseLandmark.RIGHT_WRIST, it) }
            leftHip?.let { put(PoseLandmark.LEFT_HIP, it) }
            rightHip?.let { put(PoseLandmark.RIGHT_HIP, it) }
            leftKnee?.let { put(PoseLandmark.LEFT_KNEE, it) }
            rightKnee?.let { put(PoseLandmark.RIGHT_KNEE, it) }
            leftAnkle?.let { put(PoseLandmark.LEFT_ANKLE, it) }
            rightAnkle?.let { put(PoseLandmark.RIGHT_ANKLE, it) }
        }
        return PoseFrame(landmarks = map, timestampMs = timestampMs, posePresent = map.isNotEmpty())
    }
}

object Poses {
    /** Straight arms above the shoulders: a dead hang (elbow angle 180). */
    val deadHang = Body(
        nose = lm(0.5f, 0.45f),
        leftShoulder = lm(0.4f, 0.5f),
        rightShoulder = lm(0.6f, 0.5f),
        leftElbow = lm(0.4f, 0.4f),
        rightElbow = lm(0.6f, 0.4f),
        leftWrist = lm(0.4f, 0.3f),
        rightWrist = lm(0.6f, 0.3f),
    )

    /** Arms bent to 90 degrees while still gripping above the shoulders. */
    val bentHang = deadHang.copy(
        leftElbow = lm(0.3f, 0.4f),
        rightElbow = lm(0.7f, 0.4f),
    )

    /** Standing with the hands at the hips. */
    val standing = Body(
        nose = lm(0.5f, 0.2f),
        leftShoulder = lm(0.4f, 0.3f),
        rightShoulder = lm(0.6f, 0.3f),
        leftElbow = lm(0.4f, 0.45f),
        rightElbow = lm(0.6f, 0.45f),
        leftWrist = lm(0.4f, 0.6f),
        rightWrist = lm(0.6f, 0.6f),
    )
}
