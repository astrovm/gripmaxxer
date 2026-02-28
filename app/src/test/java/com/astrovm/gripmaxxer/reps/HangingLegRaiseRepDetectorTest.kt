package com.astrovm.gripmaxxer.reps

import com.astrovm.gripmaxxer.pose.NormalizedLandmark
import com.astrovm.gripmaxxer.pose.PoseFrame
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

    @Test
    fun `counts fast consecutive reps`() {
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
            kneeY = 0.64f,
            ankleY = 0.72f,
        )

        detector.process(frame = downFrame, active = true, nowMs = 0L)
        detector.process(frame = downFrame, active = true, nowMs = 130L)
        detector.process(frame = upFrame, active = true, nowMs = 500L)
        val firstRep = detector.process(frame = upFrame, active = true, nowMs = 640L)
        assertTrue(firstRep.repEvent)
        assertEquals(1, firstRep.reps)

        detector.process(frame = downFrame, active = true, nowMs = 780L)
        detector.process(frame = downFrame, active = true, nowMs = 920L)
        detector.process(frame = upFrame, active = true, nowMs = 1040L)
        val secondRep = detector.process(frame = upFrame, active = true, nowMs = 1160L)
        assertTrue(secondRep.repEvent)
        assertEquals(2, secondRep.reps)
    }

    @Test
    fun `does not double count from brief ankle drop while knees stay raised`() {
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
            kneeY = 0.64f,
            ankleY = 0.72f,
        )
        // Ankles momentarily look lower, but knees are still clearly up.
        val ankleJitterFrame = buildFrame(
            shoulderY = 0.35f,
            hipY = 0.70f,
            kneeY = 0.64f,
            ankleY = 0.75f,
        )

        detector.process(frame = downFrame, active = true, nowMs = 0L)
        detector.process(frame = downFrame, active = true, nowMs = 130L)
        detector.process(frame = upFrame, active = true, nowMs = 600L)
        val firstRep = detector.process(frame = upFrame, active = true, nowMs = 760L)
        assertTrue(firstRep.repEvent)
        assertEquals(1, firstRep.reps)

        detector.process(frame = ankleJitterFrame, active = true, nowMs = 980L)
        detector.process(frame = upFrame, active = true, nowMs = 1120L)
        val noSecondRep = detector.process(frame = upFrame, active = true, nowMs = 1260L)
        assertFalse(noSecondRep.repEvent)
        assertEquals(1, noSecondRep.reps)
    }

    @Test
    fun `keeps counting when activity briefly flickers inactive`() {
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
            kneeY = 0.64f,
            ankleY = 0.72f,
        )

        detector.process(frame = downFrame, active = true, nowMs = 0L)
        detector.process(frame = downFrame, active = true, nowMs = 140L)
        detector.process(frame = upFrame, active = true, nowMs = 520L)
        // Hang detector can flicker false for a frame during fast motion.
        val maybeRep = detector.process(frame = upFrame, active = false, nowMs = 640L)
        val final = detector.process(frame = upFrame, active = false, nowMs = 760L)
        assertTrue(maybeRep.reps == 1 || final.reps == 1)
        assertEquals(1, final.reps)
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
