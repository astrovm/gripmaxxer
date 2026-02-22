package com.astrolabs.gripmaxxer.reps

import com.astrolabs.gripmaxxer.pose.NormalizedLandmark
import com.astrolabs.gripmaxxer.pose.PoseFrame
import com.google.mlkit.vision.pose.PoseLandmark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HangingLegRaiseRepDetectorTest {

    @Test
    fun `counts rep for standard hanging knee raise cycle`() {
        val detector = HangingLegRaiseRepDetector()

        val downFrame = buildFrame(
            shoulderY = 0.35f,
            hipY = 0.70f,
            kneeY = 0.88f,
            ankleY = 0.95f,
        )
        val upFrame = buildFrame(
            shoulderY = 0.35f,
            hipY = 0.70f,
            kneeY = 0.65f,
            ankleY = 0.73f,
        )

        detector.process(frame = downFrame, active = true, nowMs = 0L)
        val intoDown = detector.process(frame = downFrame, active = true, nowMs = 200L)
        assertFalse(intoDown.repEvent)
        assertEquals(0, intoDown.reps)

        detector.process(frame = upFrame, active = true, nowMs = 800L)
        val rep = detector.process(frame = upFrame, active = true, nowMs = 1000L)
        assertTrue(rep.repEvent)
        assertEquals(1, rep.reps)
    }

    private fun buildFrame(
        shoulderY: Float,
        hipY: Float,
        kneeY: Float,
        ankleY: Float,
    ): PoseFrame {
        val landmarks = mapOf(
            PoseLandmark.LEFT_SHOULDER to NormalizedLandmark(x = 0.45f, y = shoulderY),
            PoseLandmark.RIGHT_SHOULDER to NormalizedLandmark(x = 0.55f, y = shoulderY),
            PoseLandmark.LEFT_HIP to NormalizedLandmark(x = 0.46f, y = hipY),
            PoseLandmark.RIGHT_HIP to NormalizedLandmark(x = 0.54f, y = hipY),
            PoseLandmark.LEFT_KNEE to NormalizedLandmark(x = 0.46f, y = kneeY),
            PoseLandmark.RIGHT_KNEE to NormalizedLandmark(x = 0.54f, y = kneeY),
            PoseLandmark.LEFT_ANKLE to NormalizedLandmark(x = 0.46f, y = ankleY),
            PoseLandmark.RIGHT_ANKLE to NormalizedLandmark(x = 0.54f, y = ankleY),
        )
        return PoseFrame(
            landmarks = landmarks,
            timestampMs = 0L,
            posePresent = true,
        )
    }
}
