package com.astrovm.gripmaxxer.reps

import com.astrovm.gripmaxxer.hang.HangDetectionConfig
import com.astrovm.gripmaxxer.pose.PoseFeatureExtractor
import com.astrovm.gripmaxxer.testutil.Body
import com.astrovm.gripmaxxer.testutil.Poses
import com.astrovm.gripmaxxer.testutil.lm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModeDetectorsTest {

    private val extractor = PoseFeatureExtractor()

    // region poses

    private val pushUpTop = Body(
        leftShoulder = lm(0.4f, 0.5f),
        rightShoulder = lm(0.6f, 0.5f),
        leftElbow = lm(0.4f, 0.6f),
        rightElbow = lm(0.6f, 0.6f),
        leftWrist = lm(0.4f, 0.7f),
        rightWrist = lm(0.6f, 0.7f),
        leftHip = lm(0.42f, 0.55f),
        rightHip = lm(0.58f, 0.55f),
    )

    private val pushUpBottom = Body(
        leftShoulder = lm(0.4f, 0.56f),
        rightShoulder = lm(0.6f, 0.56f),
        leftElbow = lm(0.34f, 0.62f),
        rightElbow = lm(0.66f, 0.62f),
        leftWrist = lm(0.4f, 0.68f),
        rightWrist = lm(0.6f, 0.68f),
        leftHip = lm(0.42f, 0.58f),
        rightHip = lm(0.58f, 0.58f),
    )

    private val squatStanding = Body(
        leftShoulder = lm(0.45f, 0.3f),
        rightShoulder = lm(0.55f, 0.3f),
        leftHip = lm(0.45f, 0.5f),
        rightHip = lm(0.55f, 0.5f),
        leftKnee = lm(0.45f, 0.7f),
        rightKnee = lm(0.55f, 0.7f),
        leftAnkle = lm(0.45f, 0.9f),
        rightAnkle = lm(0.55f, 0.9f),
    )

    /** Knees and hips both at 90 degrees. */
    private val squatBottom = Body(
        leftShoulder = lm(0.3f, 0.55f),
        rightShoulder = lm(0.7f, 0.55f),
        leftHip = lm(0.3f, 0.75f),
        rightHip = lm(0.7f, 0.75f),
        leftKnee = lm(0.45f, 0.75f),
        rightKnee = lm(0.55f, 0.75f),
        leftAnkle = lm(0.45f, 0.9f),
        rightAnkle = lm(0.55f, 0.9f),
    )

    private val dipTop = Body(
        leftShoulder = lm(0.4f, 0.3f),
        rightShoulder = lm(0.6f, 0.3f),
        leftElbow = lm(0.4f, 0.45f),
        rightElbow = lm(0.6f, 0.45f),
        leftWrist = lm(0.4f, 0.6f),
        rightWrist = lm(0.6f, 0.6f),
    )

    private val dipBottom = Body(
        leftShoulder = lm(0.4f, 0.4f),
        rightShoulder = lm(0.6f, 0.4f),
        leftElbow = lm(0.3f, 0.5f),
        rightElbow = lm(0.7f, 0.5f),
        leftWrist = lm(0.4f, 0.6f),
        rightWrist = lm(0.6f, 0.6f),
    )

    // endregion

    private fun ModeActivityDetector.feed(body: Body, nowMs: Long) = process(body.frame(nowMs), nowMs)

    private fun ModeRepDetector.feed(body: Body, nowMs: Long, active: Boolean = true) =
        process(body.frame(nowMs), active, nowMs)

    /** Feeds [body] every 50ms from [from] (inclusive) to [to] (exclusive), returning the last result. */
    private fun ModeRepDetector.feedRange(body: Body, from: Long, to: Long, active: Boolean = true): RepCounterResult {
        var result = RepCounterResult(0, false)
        var t = from
        while (t < to) {
            result = feed(body, t, active)
            t += 50L
        }
        return result
    }

    @Test
    fun `feature extractor computes joint angles`() {
        assertEquals(180f, extractor.elbowAngleDegrees(Poses.deadHang.frame())!!, 0.5f)
        assertEquals(90f, extractor.elbowAngleDegrees(Poses.bentHang.frame())!!, 0.5f)
        assertEquals(90f, extractor.kneeAngleDegrees(squatBottom.frame())!!, 0.5f)
        assertEquals(90f, extractor.hipAngleDegrees(squatBottom.frame())!!, 0.5f)
        assertEquals(180f, extractor.hipAngleDegrees(squatStanding.frame())!!, 0.5f)
        // Only one side available.
        assertEquals(90f, extractor.elbowAngleDegrees(Poses.bentHang.copy(rightWrist = null).frame())!!, 0.5f)
        assertEquals(90f, extractor.elbowAngleDegrees(Poses.bentHang.copy(leftWrist = null).frame())!!, 0.5f)
        // Missing or degenerate joints.
        assertEquals(null, extractor.elbowAngleDegrees(Body().frame()))
        val degenerate = Body(leftShoulder = lm(0.4f, 0.4f), leftElbow = lm(0.4f, 0.4f), leftWrist = lm(0.4f, 0.6f))
        assertEquals(null, extractor.elbowAngleDegrees(degenerate.frame()))
        assertEquals(ExerciseMode.PULL_UP, extractor.inferExerciseMode(Body().frame()))
    }

    @Test
    fun `pose frame averages and face fallbacks`() {
        val frame = Body(leftMouth = lm(0.1f, 0.2f), rightMouth = lm(0.3f, 0.4f)).frame()
        assertEquals(0.3f, frame.noseOrMouthY()!!, 0.0001f)
        assertEquals(0.4f, Body(rightMouth = lm(0.3f, 0.4f)).frame().noseOrMouthY()!!, 0.0001f)
        assertEquals(0.45f, Poses.deadHang.frame().noseOrMouthY()!!, 0.0001f)
        assertEquals(null, Body().frame().noseOrMouthY())
        assertEquals(null, Body().frame().averageY(1, 2))
    }

    @Test
    fun `dead hang activity confirms on straight arms and tolerates brief bends`() {
        val detector = DeadHangActivityDetector(extractor)
        detector.updateConfig(HangDetectionConfig())
        assertFalse(detector.feed(Poses.deadHang, 10_000L))
        assertTrue(detector.feed(Poses.deadHang, 10_600L))
        assertTrue(detector.feed(Poses.deadHang, 10_700L))

        // Bent arms: grace period then drop.
        assertTrue(detector.feed(Poses.bentHang, 11_000L))
        assertTrue(detector.feed(Poses.bentHang, 12_000L))
        assertFalse(detector.feed(Poses.bentHang, 12_400L))

        // Straight again resets the timer.
        assertTrue(detector.feed(Poses.deadHang, 12_500L))

        // Elbows disappear: missing-angle grace period.
        val noElbows = Poses.deadHang.copy(leftElbow = null, rightElbow = null)
        assertTrue(detector.feed(noElbows, 12_600L))
        assertTrue(detector.feed(noElbows, 13_900L))
        assertFalse(detector.feed(noElbows, 14_100L))

        // Releasing the bar clears the mode.
        detector.feed(Poses.standing, 14_200L)
        assertFalse(detector.feed(Poses.standing, 14_800L))
        detector.reset()
        assertFalse(detector.feed(Poses.bentHang, 20_000L))
    }

    @Test
    fun `dead hang is not confirmed with bent arms`() {
        val detector = DeadHangActivityDetector(extractor)
        detector.feed(Poses.bentHang, 10_000L)
        assertFalse(detector.feed(Poses.bentHang, 10_600L))
        assertFalse(detector.feed(Poses.bentHang, 10_700L))
    }

    @Test
    fun `active hang activity confirms on bent arms and tolerates brief straightening`() {
        val detector = ActiveHangActivityDetector(extractor)
        detector.updateConfig(HangDetectionConfig())
        detector.feed(Poses.bentHang, 10_000L)
        assertTrue(detector.feed(Poses.bentHang, 10_600L))
        assertTrue(detector.feed(Poses.bentHang, 10_700L))

        assertTrue(detector.feed(Poses.deadHang, 11_000L))
        assertTrue(detector.feed(Poses.deadHang, 12_000L))
        assertFalse(detector.feed(Poses.deadHang, 12_400L))
        assertTrue(detector.feed(Poses.bentHang, 12_500L))

        val noElbows = Poses.bentHang.copy(leftElbow = null, rightElbow = null)
        assertTrue(detector.feed(noElbows, 12_600L))
        assertFalse(detector.feed(noElbows, 14_100L))

        detector.feed(Poses.standing, 14_200L)
        assertFalse(detector.feed(Poses.standing, 14_800L))
        detector.reset()
        assertFalse(detector.feed(Poses.deadHang, 20_000L))
    }

    @Test
    fun `active hang is not confirmed with straight arms`() {
        val detector = ActiveHangActivityDetector(extractor)
        detector.feed(Poses.deadHang, 10_000L)
        assertFalse(detector.feed(Poses.deadHang, 10_600L))
    }

    @Test
    fun `hang based activity detectors follow the hang detector`() {
        listOf(PullUpActivityDetector(), HangingLegRaiseActivityDetector()).forEach { detector ->
            when (detector) {
                is PullUpActivityDetector -> detector.updateConfig(HangDetectionConfig())
                is HangingLegRaiseActivityDetector -> detector.updateConfig(HangDetectionConfig())
            }
            assertFalse(detector.feed(Poses.deadHang, 10_000L))
            assertTrue(detector.feed(Poses.deadHang, 10_600L))
            detector.reset()
            assertFalse(detector.feed(Poses.deadHang, 10_700L))
            // Interface default timestamp is accepted too.
            assertFalse(detector.process(Poses.standing.frame()))
        }
    }

    @Test
    fun `push-up activity starts on motion and decays when idle`() {
        val detector = PushUpActivityDetector(extractor)
        assertFalse(detector.feed(pushUpTop, 1_000L))
        assertTrue(detector.feed(pushUpBottom, 1_100L))
        assertTrue(detector.feed(pushUpTop, 1_200L))
        assertFalse(detector.feed(pushUpTop, 2_700L))

        assertTrue(detector.feed(pushUpBottom, 3_000L))
        // Missing data decays the state.
        assertTrue(detector.feed(Body(), 3_100L))
        assertTrue(detector.feed(pushUpTop.copy(leftHip = null, rightHip = null), 3_200L))
        assertTrue(detector.feed(pushUpTop.copy(leftElbow = null, rightElbow = null), 3_300L))
        assertFalse(detector.feed(Body(), 4_500L))

        detector.reset()
        assertFalse(detector.feed(pushUpTop, 5_000L))
    }

    @Test
    fun `push-up activity ignores non push-up postures`() {
        val detector = PushUpActivityDetector(extractor)
        val narrow = pushUpBottom.copy(leftShoulder = lm(0.49f, 0.56f), rightShoulder = lm(0.51f, 0.56f))
        val standing = pushUpBottom.copy(leftHip = lm(0.42f, 0.9f), rightHip = lm(0.58f, 0.9f))
        val kneeling = pushUpBottom.copy(
            leftKnee = lm(0.3f, 0.58f),
            rightKnee = lm(0.7f, 0.58f),
            leftAnkle = lm(0.3f, 0.4f),
            rightAnkle = lm(0.7f, 0.4f),
        )
        val piked = pushUpBottom.copy(
            leftKnee = lm(0.42f, 0.4f),
            rightKnee = lm(0.58f, 0.4f),
        )
        listOf(narrow, standing, kneeling, piked).forEach { body ->
            assertFalse(detector.feed(body, 1_000L))
        }
        val shouldersMissing = PushUpActivityDetector(extractor)
        // Only one shoulder: averageY works but the posture check needs both.
        assertFalse(shouldersMissing.feed(pushUpBottom.copy(rightShoulder = null), 1_000L))
    }

    @Test
    fun `push-up reps count full cycles`() {
        val detector = PushUpRepDetector(extractor)
        detector.feedRange(pushUpTop, 1_000L, 1_200L)
        detector.feedRange(pushUpBottom, 1_200L, 1_600L)
        val rep = detector.feedRange(pushUpTop, 1_600L, 2_000L)
        assertEquals(1, rep.reps)

        // Inactive or bad frames keep the count.
        assertEquals(1, detector.feed(pushUpBottom, 2_100L, active = false).reps)
        assertEquals(1, detector.feed(Body(), 2_150L).reps)
        assertEquals(1, detector.feed(pushUpTop.copy(leftHip = null, rightHip = null), 2_200L).reps)
        assertEquals(1, detector.feed(pushUpTop.copy(rightShoulder = null), 2_250L).reps)
        assertEquals(
            1,
            detector.feed(pushUpTop.copy(leftShoulder = lm(0.49f, 0.5f), rightShoulder = lm(0.51f, 0.5f)), 2_300L).reps,
        )
        assertEquals(1, detector.feed(pushUpTop.copy(leftElbow = null, rightElbow = null), 2_350L).reps)
        val kneeling = pushUpTop.copy(
            leftKnee = lm(0.3f, 0.55f),
            rightKnee = lm(0.7f, 0.55f),
            leftAnkle = lm(0.3f, 0.4f),
            rightAnkle = lm(0.7f, 0.4f),
        )
        assertEquals(1, detector.feed(kneeling, 2_400L).reps)
        val piked = pushUpTop.copy(leftKnee = lm(0.42f, 0.4f), rightKnee = lm(0.58f, 0.4f))
        assertEquals(1, detector.feed(piked, 2_450L).reps)

        detector.reset()
        assertEquals(0, detector.feed(pushUpTop, 3_000L).reps)
    }

    @Test
    fun `squat activity and reps`() {
        val activity = SquatActivityDetector(extractor)
        assertFalse(activity.feed(squatStanding, 1_000L))
        assertTrue(activity.feed(squatBottom, 1_100L))
        assertTrue(activity.feed(Body(), 1_200L))
        assertTrue(activity.feed(squatStanding.copy(leftHip = null, rightHip = null, leftKnee = null, rightKnee = null), 1_300L))
        assertTrue(activity.feed(squatStanding, 1_400L))
        assertFalse(activity.feed(squatStanding, 3_000L))
        assertFalse(activity.feed(Body(), 3_100L))
        activity.reset()
        assertFalse(activity.feed(squatStanding, 4_000L))
        assertTrue(activity.feed(squatBottom, 4_050L))
        assertFalse(activity.feed(Body(), 6_000L))
        activity.reset()
        assertFalse(activity.feed(squatStanding, 6_100L))
        // Hip drop without knee motion.
        assertTrue(activity.feed(squatStanding.copy(leftHip = lm(0.45f, 0.55f), rightHip = lm(0.55f, 0.55f)), 6_200L))

        val reps = SquatRepDetector(extractor)
        assertEquals(0, reps.feed(squatBottom, 1_000L, active = false).reps)
        reps.feedRange(squatBottom, 1_000L, 1_400L)
        assertEquals(1, reps.feedRange(squatStanding, 1_400L, 1_800L).reps)
        assertEquals(1, reps.feed(Body(), 1_900L).reps)
        // Knees only (no hip angle available) still counts.
        val kneesOnlyDown = squatBottom.copy(leftShoulder = null, rightShoulder = null)
        val kneesOnlyUp = squatStanding.copy(leftShoulder = null, rightShoulder = null)
        reps.feedRange(kneesOnlyDown, 2_000L, 2_400L)
        assertEquals(2, reps.feedRange(kneesOnlyUp, 2_400L, 2_800L).reps)
        reps.reset()
        assertEquals(0, reps.feed(squatStanding, 3_000L).reps)
    }

    @Test
    fun `dip activity and reps`() {
        val activity = DipActivityDetector(extractor)
        assertFalse(activity.feed(dipTop, 1_000L))
        assertTrue(activity.feed(dipBottom, 1_100L))
        assertTrue(activity.feed(Body(), 1_200L))
        assertTrue(activity.feed(dipTop, 1_300L))
        assertFalse(activity.feed(dipTop, 2_600L))
        assertTrue(activity.feed(dipBottom, 2_700L))
        assertFalse(activity.feed(Body(), 4_200L))
        activity.reset()

        val badPostures = listOf(
            dipBottom.copy(leftShoulder = null),
            dipBottom.copy(rightShoulder = null),
            dipBottom.copy(leftElbow = null),
            dipBottom.copy(rightElbow = null),
            dipBottom.copy(leftWrist = null),
            dipBottom.copy(rightWrist = null),
            dipBottom.copy(leftShoulder = lm(0.49f, 0.4f), rightShoulder = lm(0.51f, 0.4f)),
            dipBottom.copy(leftWrist = lm(0.4f, 0.2f)),
        )
        badPostures.forEach { assertFalse(activity.feed(it, 5_000L)) }

        val reps = DipRepDetector(extractor)
        assertEquals(0, reps.feed(dipBottom, 1_000L, active = false).reps)
        badPostures.forEach { assertEquals(0, reps.feed(it, 1_000L).reps) }
        reps.feedRange(dipBottom, 1_000L, 1_400L)
        assertEquals(1, reps.feedRange(dipTop, 1_400L, 1_800L).reps)
        reps.reset()
        assertEquals(0, reps.feed(dipTop, 2_000L).reps)
    }

    @Test
    fun `dip rep detector needs an elbow angle`() {
        val reps = DipRepDetector(extractor)
        // Support posture needs the elbows, so an angle can only be missing when the elbow sits on the shoulder.
        val degenerate = dipTop.copy(leftWrist = lm(0.4f, 0.45f), rightWrist = lm(0.6f, 0.45f))
        assertEquals(0, reps.feed(degenerate, 1_000L).reps)
        val activity = DipActivityDetector(extractor)
        assertFalse(activity.feed(degenerate, 1_000L))
    }

    @Test
    fun `hold detector never counts reps`() {
        val detector = HoldRepDetector()
        detector.reset()
        assertEquals(RepCounterResult(0, false), detector.feed(Poses.deadHang, 1_000L))
        assertEquals(RepCounterResult(0, false), detector.process(Poses.deadHang.frame(), active = true))
    }

    @Test
    fun `pull-up rep detector delegates to rep counter`() {
        val detector = PullUpRepDetector(extractor)
        detector.updateConfig(RepCounterConfig(stableMs = 40L, minRepIntervalMs = 0L))
        assertEquals(0, detector.feed(Poses.deadHang, 1_000L, active = false).reps)
        detector.reset()
        assertEquals(0, detector.feed(Poses.deadHang, 1_000L).reps)
    }

    @Test
    fun `rep engine routes to the selected detector`() {
        val pull = PullUpRepDetector(extractor)
        val hold = HoldRepDetector()
        val engine = RepEngine(mapOf(ExerciseMode.PULL_UP to pull, ExerciseMode.DEAD_HANG to hold))
        engine.setMode(ExerciseMode.PULL_UP, resetCurrent = true)
        engine.setMode(ExerciseMode.PULL_UP, resetCurrent = false)
        engine.setMode(ExerciseMode.DEAD_HANG, resetCurrent = false)
        assertEquals(RepCounterResult(0, false), engine.process(Poses.deadHang.frame(), true, 1_000L))
        engine.setMode(ExerciseMode.SQUAT, resetCurrent = true)
        engine.resetCurrent()
        assertEquals(RepCounterResult(0, false), engine.process(Poses.deadHang.frame(), true, 1_000L))
    }

    @Test
    fun `cycle counter exposes and resets its count`() {
        val counter = CycleRepCounter(stableMs = 0L, minRepIntervalMs = 0L)
        counter.process(isDown = true, isUp = false, nowMs = 10L)
        counter.process(isDown = false, isUp = false, nowMs = 20L)
        assertEquals(1, counter.process(isDown = false, isUp = true, nowMs = 30L).reps)
        assertEquals(1, counter.currentReps())
        counter.reset()
        assertEquals(0, counter.currentReps())
    }

    @Test
    fun `hanging leg raise detector handles missing landmarks and reset`() {
        val detector = HangingLegRaiseRepDetector()
        val legs = Body(
            leftHip = lm(0.45f, 0.7f),
            rightHip = lm(0.55f, 0.7f),
            leftKnee = lm(0.45f, 0.88f),
            rightKnee = lm(0.55f, 0.88f),
        )
        assertEquals(0, detector.feed(legs, 1_000L, active = false).reps)
        assertEquals(0, detector.feed(Body(), 1_000L).reps)
        assertEquals(0, detector.feed(legs.copy(leftKnee = null, rightKnee = null), 1_050L).reps)
        assertEquals(0, detector.feed(legs, 1_100L).reps)
        // Inactive long after the last active frame.
        assertEquals(0, detector.feed(legs, 5_000L, active = false).reps)
        detector.reset()
        assertEquals(0, detector.feed(legs, 6_000L).reps)
    }
}
