package com.astrovm.gripmaxxer.pose

import com.astrovm.gripmaxxer.reps.ExerciseMode
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark

enum class BodySide {
    LEFT,
    RIGHT,
}

class PoseFeatureExtractor {

    private val trackedLandmarkTypes = listOf(
        PoseLandmark.NOSE,
        PoseLandmark.LEFT_MOUTH,
        PoseLandmark.RIGHT_MOUTH,
        PoseLandmark.LEFT_SHOULDER,
        PoseLandmark.RIGHT_SHOULDER,
        PoseLandmark.LEFT_ELBOW,
        PoseLandmark.RIGHT_ELBOW,
        PoseLandmark.LEFT_WRIST,
        PoseLandmark.RIGHT_WRIST,
        PoseLandmark.LEFT_HIP,
        PoseLandmark.RIGHT_HIP,
        PoseLandmark.LEFT_KNEE,
        PoseLandmark.RIGHT_KNEE,
        PoseLandmark.LEFT_ANKLE,
        PoseLandmark.RIGHT_ANKLE,
    )

    fun toPoseFrame(
        pose: Pose,
        frameWidth: Int,
        frameHeight: Int,
        timestampMs: Long,
    ): PoseFrame {
        val landmarks = buildMap<Int, NormalizedLandmark> {
            trackedLandmarkTypes.forEach { type ->
                val landmark = pose.getPoseLandmark(type) ?: return@forEach
                val normalizedX = (landmark.position.x / frameWidth.toFloat()).coerceIn(0f, 1f)
                val normalizedY = (landmark.position.y / frameHeight.toFloat()).coerceIn(0f, 1f)
                put(type, NormalizedLandmark(normalizedX, normalizedY))
            }
        }

        val bothShouldersPresent = landmarks.containsKey(PoseLandmark.LEFT_SHOULDER) &&
            landmarks.containsKey(PoseLandmark.RIGHT_SHOULDER)
        val leftArmPresent = landmarks.containsKey(PoseLandmark.LEFT_ELBOW) &&
            landmarks.containsKey(PoseLandmark.LEFT_WRIST)
        val rightArmPresent = landmarks.containsKey(PoseLandmark.RIGHT_ELBOW) &&
            landmarks.containsKey(PoseLandmark.RIGHT_WRIST)
        return PoseFrame(
            landmarks = landmarks,
            timestampMs = timestampMs,
            posePresent = bothShouldersPresent && (leftArmPresent || rightArmPresent),
        )
    }

    fun elbowAngleDegrees(frame: PoseFrame): Float? {
        return average(
            elbowAngleDegrees(frame, BodySide.LEFT),
            elbowAngleDegrees(frame, BodySide.RIGHT),
        )
    }

    fun elbowAngleDegrees(frame: PoseFrame, side: BodySide): Float? {
        val (shoulder, elbow, wrist) = when (side) {
            BodySide.LEFT -> Triple(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_WRIST)
            BodySide.RIGHT -> Triple(PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST)
        }
        return computeAngle(frame, shoulder, elbow, wrist)
    }

    fun kneeAngleDegrees(frame: PoseFrame): Float? {
        return average(
            kneeAngleDegrees(frame, BodySide.LEFT),
            kneeAngleDegrees(frame, BodySide.RIGHT),
        )
    }

    fun kneeAngleDegrees(frame: PoseFrame, side: BodySide): Float? {
        val (hip, knee, ankle) = when (side) {
            BodySide.LEFT -> Triple(PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_ANKLE)
            BodySide.RIGHT -> Triple(PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE)
        }
        return computeAngle(frame, hip, knee, ankle)
    }

    fun hipAngleDegrees(frame: PoseFrame): Float? {
        return average(
            hipAngleDegrees(frame, BodySide.LEFT),
            hipAngleDegrees(frame, BodySide.RIGHT),
        )
    }

    fun hipAngleDegrees(frame: PoseFrame, side: BodySide): Float? {
        val (shoulder, hip, knee) = when (side) {
            BodySide.LEFT -> Triple(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE)
            BodySide.RIGHT -> Triple(PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE)
        }
        return computeAngle(frame, shoulder, hip, knee)
    }

    fun inferExerciseMode(frame: PoseFrame): ExerciseMode {
        return ExerciseMode.PULL_UP
    }

    private fun computeAngle(frame: PoseFrame, shoulderType: Int, elbowType: Int, wristType: Int): Float? {
        val shoulder = frame.landmark(shoulderType) ?: return null
        val elbow = frame.landmark(elbowType) ?: return null
        val wrist = frame.landmark(wristType) ?: return null

        val v1x = shoulder.x - elbow.x
        val v1y = shoulder.y - elbow.y
        val v2x = wrist.x - elbow.x
        val v2y = wrist.y - elbow.y

        val dot = (v1x * v2x) + (v1y * v2y)
        val mag1 = kotlin.math.sqrt((v1x * v1x + v1y * v1y).toDouble())
        val mag2 = kotlin.math.sqrt((v2x * v2x + v2y * v2y).toDouble())
        if (mag1 == 0.0 || mag2 == 0.0) return null

        val cosine = (dot / (mag1 * mag2)).coerceIn(-1.0, 1.0)
        return Math.toDegrees(kotlin.math.acos(cosine)).toFloat()
    }

    private fun average(a: Float?, b: Float?): Float? {
        return when {
            a != null && b != null -> (a + b) / 2f
            a != null -> a
            b != null -> b
            else -> null
        }
    }
}
