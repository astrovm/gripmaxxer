package com.astrolabs.hangmaxxer.pose

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import com.astrolabs.hangmaxxer.reps.ExerciseMode

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
        val bothWristsPresent = landmarks.containsKey(PoseLandmark.LEFT_WRIST) &&
            landmarks.containsKey(PoseLandmark.RIGHT_WRIST)
        val bothElbowsPresent = landmarks.containsKey(PoseLandmark.LEFT_ELBOW) &&
            landmarks.containsKey(PoseLandmark.RIGHT_ELBOW)
        return PoseFrame(
            landmarks = landmarks,
            timestampMs = timestampMs,
            posePresent = bothShouldersPresent && bothWristsPresent && bothElbowsPresent,
        )
    }

    fun elbowAngleDegrees(frame: PoseFrame): Float? {
        val left = computeAngle(frame, PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_WRIST)
        val right = computeAngle(frame, PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST)
        return when {
            left != null && right != null -> (left + right) / 2f
            left != null -> left
            right != null -> right
            else -> null
        }
    }

    fun inferExerciseMode(frame: PoseFrame): ExerciseMode {
        val leftShoulder = frame.landmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = frame.landmark(PoseLandmark.RIGHT_SHOULDER)
        val shoulderWidth = if (leftShoulder != null && rightShoulder != null) {
            kotlin.math.abs(rightShoulder.x - leftShoulder.x)
        } else {
            0f
        }
        if (shoulderWidth < 0.08f) return ExerciseMode.UNKNOWN

        var pullScore = 0
        var chinScore = 0

        val leftWrist = frame.landmark(PoseLandmark.LEFT_WRIST)
        val rightWrist = frame.landmark(PoseLandmark.RIGHT_WRIST)
        if (leftWrist != null && rightWrist != null) {
            val wristRatio = kotlin.math.abs(rightWrist.x - leftWrist.x) / shoulderWidth
            when {
                wristRatio >= 1.14f -> pullScore += 2
                wristRatio <= 0.95f -> chinScore += 2
            }
        }

        val leftElbow = frame.landmark(PoseLandmark.LEFT_ELBOW)
        val rightElbow = frame.landmark(PoseLandmark.RIGHT_ELBOW)
        if (leftElbow != null && rightElbow != null) {
            val elbowRatio = kotlin.math.abs(rightElbow.x - leftElbow.x) / shoulderWidth
            when {
                elbowRatio >= 1.04f -> pullScore += 1
                elbowRatio <= 0.90f -> chinScore += 1
            }
        }

        if (leftShoulder != null && rightShoulder != null && leftElbow != null && rightElbow != null) {
            val elbowOutLeft = leftShoulder.x - leftElbow.x
            val elbowOutRight = rightElbow.x - rightShoulder.x
            val elbowOutAvg = (elbowOutLeft + elbowOutRight) / 2f
            when {
                elbowOutAvg > 0.01f -> pullScore += 1
                elbowOutAvg < -0.01f -> chinScore += 1
            }
        }

        return when {
            pullScore >= chinScore + 2 -> ExerciseMode.PULL_UP
            chinScore >= pullScore + 2 -> ExerciseMode.CHIN_UP
            else -> ExerciseMode.UNKNOWN
        }
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
}
