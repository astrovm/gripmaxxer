package com.astrovm.gripmaxxer.hang

import com.astrovm.gripmaxxer.testutil.Body
import com.astrovm.gripmaxxer.testutil.Poses
import com.astrovm.gripmaxxer.testutil.lm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HangDetectorTest {

    private fun HangDetector.feed(body: Body, nowMs: Long) = process(body.frame(nowMs), nowMs)

    /** Brings the detector into the hanging state at [start] + 600 and returns that time. */
    private fun HangDetector.startHanging(start: Long = 10_000L): Long {
        assertFalse(feed(Poses.deadHang, start).isHanging)
        val result = feed(Poses.deadHang, start + 600L)
        assertTrue(result.isHanging)
        assertTrue(result.transitioned)
        return start + 600L
    }

    @Test
    fun `hang requires a stable candidate before switching`() {
        val detector = HangDetector()
        val first = detector.feed(Poses.deadHang, 10_000L)
        assertFalse(first.isHanging)
        assertFalse(first.transitioned)

        // Still inside the stable window.
        assertFalse(detector.feed(Poses.deadHang, 10_200L).isHanging)

        val switched = detector.feed(Poses.deadHang, 10_600L)
        assertEquals(HangDetectionResult(isHanging = true, transitioned = true), switched)

        // Unchanged raw state clears the candidate and reports no transition.
        assertEquals(HangDetectionResult(isHanging = true, transitioned = false), detector.feed(Poses.deadHang, 10_700L))
    }

    @Test
    fun `toggle interval blocks rapid flip flops`() {
        val detector = HangDetector()
        val t = detector.startHanging()

        // Release candidate is stable but the min toggle interval has not passed yet.
        detector.feed(Poses.standing, t + 100L)
        assertTrue(detector.feed(Poses.standing, t + 700L).isHanging)

        // After the toggle interval the release is accepted.
        val released = detector.feed(Poses.standing, t + 1_600L)
        assertFalse(released.isHanging)
        assertTrue(released.transitioned)
    }

    @Test
    fun `candidate flip resets the stability timer`() {
        val detector = HangDetector()
        detector.feed(Poses.deadHang, 10_000L)
        // Raw state goes back to not hanging, which equals the current state and clears the candidate.
        assertFalse(detector.feed(Poses.standing, 10_300L).isHanging)
        detector.feed(Poses.deadHang, 10_400L)
        assertFalse(detector.feed(Poses.deadHang, 10_800L).isHanging)
        assertTrue(detector.feed(Poses.deadHang, 10_900L).isHanging)
    }

    @Test
    fun `wrists between margins keep the previous state`() {
        val detector = HangDetector()
        val t = detector.startHanging()
        // shoulder 0.5, wrists 0.48: delta 0.02 is inside the dead band.
        val deadBand = Poses.deadHang.copy(
            leftElbow = lm(0.4f, 0.49f),
            rightElbow = lm(0.6f, 0.49f),
            leftWrist = lm(0.4f, 0.48f),
            rightWrist = lm(0.6f, 0.48f),
        )
        // Arms are too short to be reliable with elbows, so use elbow-less arms for the dead band.
        val deadBandNoElbows = deadBand.copy(leftElbow = null, rightElbow = null)
        assertTrue(detector.feed(deadBandNoElbows, t + 2_000L).isHanging)
        assertTrue(detector.feed(deadBandNoElbows, t + 4_000L).isHanging)
    }

    @Test
    fun `weak exit evidence is treated as occlusion then partial pose then missing`() {
        val detector = HangDetector()
        val t = detector.startHanging()

        // One wrist dropped below the shoulders and the other is missing: weak exit evidence.
        val weakExit = Poses.deadHang.copy(rightWrist = null, leftWrist = lm(0.4f, 0.7f), leftElbow = lm(0.4f, 0.6f))
        assertTrue(detector.feed(weakExit, t + 100L).isHanging)
        assertTrue(detector.feed(weakExit, t + 3_500L).isHanging)

        // Occlusion hold expired; upper body core is still visible so the partial hold keeps hanging.
        assertTrue(detector.feed(weakExit, t + 3_700L).isHanging)
        assertTrue(detector.feed(weakExit, t + 6_600L).isHanging)

        // Partial hold expired; the missing timer starts and then expires.
        assertTrue(detector.feed(weakExit, t + 6_800L).isHanging)
        detector.feed(weakExit, t + 7_200L)
        val released = detector.feed(weakExit, t + 7_800L)
        assertFalse(released.isHanging)
        assertTrue(released.transitioned)
    }

    @Test
    fun `shoulder only frames hold the hang for a while`() {
        val detector = HangDetector()
        val t = detector.startHanging()
        val shouldersOnly = Body(leftShoulder = lm(0.4f, 0.5f), rightShoulder = lm(0.6f, 0.5f))

        assertTrue(detector.feed(shouldersOnly, t + 100L).isHanging) // occlusion hold
        assertTrue(detector.feed(shouldersOnly, t + 3_700L).isHanging) // shoulder-only hold starts
        assertTrue(detector.feed(shouldersOnly, t + 7_100L).isHanging)
        assertTrue(detector.feed(shouldersOnly, t + 7_300L).isHanging) // missing timer starts
        detector.feed(shouldersOnly, t + 7_700L)
        assertFalse(detector.feed(shouldersOnly, t + 8_300L).isHanging)
    }

    @Test
    fun `empty frames drop out of the hang after the missing timeout`() {
        val detector = HangDetector()
        val t = detector.startHanging()
        val empty = Body()
        assertTrue(detector.feed(empty, t + 100L).isHanging)
        assertTrue(detector.feed(empty, t + 3_700L).isHanging)
        detector.feed(empty, t + 4_100L)
        assertFalse(detector.feed(empty, t + 4_700L).isHanging)
    }

    @Test
    fun `missing pose while idle stays idle`() {
        val detector = HangDetector()
        assertFalse(detector.feed(Body(), 10_000L).isHanging)
        assertFalse(detector.feed(Body(), 11_000L).isHanging)
        assertFalse(detector.feed(Body(leftShoulder = lm(0.4f, 0.5f), rightShoulder = lm(0.6f, 0.5f)), 12_000L).isHanging)
    }

    @Test
    fun `unreliable poses are not used to compute a hang`() {
        val unreliable = listOf(
            // Missing right shoulder.
            Poses.deadHang.copy(rightShoulder = null),
            // Shoulders too narrow.
            Poses.deadHang.copy(leftShoulder = lm(0.48f, 0.5f), rightShoulder = lm(0.52f, 0.5f)),
            // No wrists at all.
            Poses.deadHang.copy(leftWrist = null, rightWrist = null),
            // Arms too short with elbows.
            Poses.deadHang.copy(
                leftElbow = lm(0.4f, 0.49f),
                rightElbow = lm(0.6f, 0.49f),
                leftWrist = lm(0.4f, 0.48f),
                rightWrist = lm(0.6f, 0.48f),
            ),
            // Wrists far too wide apart.
            Poses.deadHang.copy(leftWrist = lm(0.0f, 0.3f), rightWrist = lm(1.0f, 0.3f), leftElbow = null, rightElbow = null),
            // Wrists too close together.
            Poses.deadHang.copy(leftWrist = lm(0.5f, 0.3f), rightWrist = lm(0.51f, 0.3f), leftElbow = null, rightElbow = null),
            // Elbow-less arms where the wrist sits on the shoulder.
            Poses.deadHang.copy(leftWrist = lm(0.41f, 0.49f), rightWrist = lm(0.59f, 0.49f), leftElbow = null, rightElbow = null),
        )
        unreliable.forEach { body ->
            val detector = HangDetector()
            assertFalse(detector.feed(body, 10_000L).isHanging)
            assertFalse("pose should not be reliable: $body", detector.feed(body, 10_600L).isHanging)
        }
    }

    @Test
    fun `single reliable arm is enough to compute a hang`() {
        val detector = HangDetector()
        val oneArm = Poses.deadHang.copy(rightElbow = null, rightWrist = null)
        detector.feed(oneArm, 10_000L)
        assertTrue(detector.feed(oneArm, 10_600L).isHanging)
    }

    @Test
    fun `upper body core checks reject narrow or armless frames`() {
        val narrowWithArms = Body(
            leftShoulder = lm(0.48f, 0.5f),
            rightShoulder = lm(0.53f, 0.5f),
            leftElbow = lm(0.48f, 0.4f),
        )
        val detector = HangDetector()
        val t = detector.startHanging()
        // Occlusion hold first.
        assertTrue(detector.feed(narrowWithArms, t + 100L).isHanging)
        // After occlusion, narrow shoulders are neither core nor a shoulder pair: missing timer.
        assertTrue(detector.feed(narrowWithArms, t + 3_700L).isHanging)
        detector.feed(narrowWithArms, t + 4_100L)
        assertFalse(detector.feed(narrowWithArms, t + 4_700L).isHanging)

        val oneShoulderWithArm = Body(leftShoulder = lm(0.4f, 0.5f), leftWrist = lm(0.4f, 0.3f), rightElbow = lm(0.6f, 0.4f))
        val second = HangDetector()
        val t2 = second.startHanging()
        assertTrue(second.feed(oneShoulderWithArm, t2 + 100L).isHanging)
        assertTrue(second.feed(oneShoulderWithArm, t2 + 3_700L).isHanging) // partial hold
        assertTrue(second.feed(oneShoulderWithArm, t2 + 6_600L).isHanging)
    }

    @Test
    fun `reset and config updates change behaviour`() {
        val detector = HangDetector()
        detector.startHanging()
        detector.reset()
        assertFalse(detector.feed(Poses.deadHang, 20_000L).isHanging)

        detector.updateConfig(HangDetectionConfig(wristShoulderMargin = 0.5f, stableSwitchMs = 0L, minToggleIntervalMs = 0L))
        detector.reset()
        // With a huge margin the dead hang is inside the dead band and never switches.
        assertFalse(detector.feed(Poses.deadHang, 30_000L).isHanging)
        assertFalse(detector.feed(Poses.deadHang, 31_000L).isHanging)
    }
}
