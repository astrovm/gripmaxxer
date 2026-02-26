package com.astrovm.gripmaxxer.reps

import com.astrovm.gripmaxxer.pose.NormalizedLandmark
import com.astrovm.gripmaxxer.pose.PoseFeatureExtractor
import com.astrovm.gripmaxxer.pose.PoseFrame
import com.google.mlkit.vision.pose.PoseLandmark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RepCounterTest {

    @Test
    fun `pull-up mode does not count up when one wrist is missing`() {
        val counter = RepCounter(
            featureExtractor = PoseFeatureExtractor(),
            config = RepCounterConfig(
                stableMs = 40L,
                minRepIntervalMs = 0L,
                requireBothWristsForGripUp = true,
            ),
        )

        val downFrame = buildFrame(
            noseY = 0.45f,
            leftShoulderY = 0.50f,
            rightShoulderY = 0.50f,
            leftElbow = NormalizedLandmark(0.45f, 0.40f),
            rightElbow = NormalizedLandmark(0.55f, 0.40f),
            leftWrist = NormalizedLandmark(0.45f, 0.30f),
            rightWrist = NormalizedLandmark(0.55f, 0.30f),
        )
        val oneWristUpFrame = buildFrame(
            noseY = 0.20f,
            leftShoulderY = 0.50f,
            rightShoulderY = 0.50f,
            leftElbow = NormalizedLandmark(0.45f, 0.40f),
            rightElbow = null,
            leftWrist = NormalizedLandmark(0.53f, 0.35f),
            rightWrist = null,
        )

        counter.process(frame = downFrame, hanging = true, nowMs = 0L)
        counter.process(frame = oneWristUpFrame, hanging = true, nowMs = 100L)
        val result = counter.process(frame = oneWristUpFrame, hanging = true, nowMs = 200L)

        assertFalse(result.repEvent)
        assertEquals(0, result.reps)
    }

    @Test
    fun `pull-up mode does not count release motion with wrists below face`() {
        val counter = RepCounter(
            featureExtractor = PoseFeatureExtractor(),
            config = RepCounterConfig(
                stableMs = 40L,
                minRepIntervalMs = 0L,
                requireBothWristsForGripUp = true,
            ),
        )

        val downFrame = buildFrame(
            noseY = 0.35f,
            leftShoulderY = 0.50f,
            rightShoulderY = 0.50f,
            leftElbow = NormalizedLandmark(0.45f, 0.60f),
            rightElbow = NormalizedLandmark(0.55f, 0.60f),
            leftWrist = NormalizedLandmark(0.45f, 0.24f),
            rightWrist = NormalizedLandmark(0.55f, 0.24f),
        )
        val releaseLikeFrame = buildFrame(
            noseY = 0.35f,
            leftShoulderY = 0.50f,
            rightShoulderY = 0.50f,
            leftElbow = NormalizedLandmark(0.45f, 0.60f),
            rightElbow = NormalizedLandmark(0.55f, 0.60f),
            leftWrist = NormalizedLandmark(0.53f, 0.42f),
            rightWrist = NormalizedLandmark(0.47f, 0.42f),
        )

        counter.process(frame = downFrame, hanging = true, nowMs = 0L)
        counter.process(frame = downFrame, hanging = true, nowMs = 60L)
        counter.process(frame = releaseLikeFrame, hanging = true, nowMs = 140L)
        val result = counter.process(frame = releaseLikeFrame, hanging = true, nowMs = 220L)

        assertFalse(result.repEvent)
        assertEquals(0, result.reps)
    }

    private fun buildFrame(
        noseY: Float,
        leftShoulderY: Float,
        rightShoulderY: Float,
        leftElbow: NormalizedLandmark?,
        rightElbow: NormalizedLandmark?,
        leftWrist: NormalizedLandmark?,
        rightWrist: NormalizedLandmark?,
    ): PoseFrame {
        val landmarks = mutableMapOf<Int, NormalizedLandmark>()
        landmarks[PoseLandmark.NOSE] = NormalizedLandmark(0.50f, noseY)
        landmarks[PoseLandmark.LEFT_SHOULDER] = NormalizedLandmark(0.45f, leftShoulderY)
        landmarks[PoseLandmark.RIGHT_SHOULDER] = NormalizedLandmark(0.55f, rightShoulderY)
        leftElbow?.let { landmarks[PoseLandmark.LEFT_ELBOW] = it }
        rightElbow?.let { landmarks[PoseLandmark.RIGHT_ELBOW] = it }
        leftWrist?.let { landmarks[PoseLandmark.LEFT_WRIST] = it }
        rightWrist?.let { landmarks[PoseLandmark.RIGHT_WRIST] = it }

        return PoseFrame(
            landmarks = landmarks,
            timestampMs = 0L,
            posePresent = true,
        )
    }
}
