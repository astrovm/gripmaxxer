package com.example.hangplaycam.pose

import com.google.mlkit.vision.pose.PoseLandmark

data class NormalizedLandmark(
    val x: Float,
    val y: Float,
)

data class PoseFrame(
    val landmarks: Map<Int, NormalizedLandmark>,
    val timestampMs: Long,
    val posePresent: Boolean,
) {
    fun landmark(type: Int): NormalizedLandmark? = landmarks[type]

    fun averageY(vararg landmarkTypes: Int): Float? {
        val values = landmarkTypes.toList().mapNotNull { landmarks[it]?.y }
        if (values.isEmpty()) return null
        return values.average().toFloat()
    }

    fun noseOrMouthY(): Float? {
        landmarks[PoseLandmark.NOSE]?.let { return it.y }
        val mouthLeft = landmarks[PoseLandmark.LEFT_MOUTH]?.y
        val mouthRight = landmarks[PoseLandmark.RIGHT_MOUTH]?.y
        val values = listOfNotNull(mouthLeft, mouthRight)
        if (values.isEmpty()) return null
        return values.average().toFloat()
    }
}
