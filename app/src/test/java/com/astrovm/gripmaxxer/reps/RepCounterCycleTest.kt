package com.astrovm.gripmaxxer.reps

import com.astrovm.gripmaxxer.pose.PoseFeatureExtractor
import com.astrovm.gripmaxxer.testutil.Body
import com.astrovm.gripmaxxer.testutil.Poses
import com.astrovm.gripmaxxer.testutil.lm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RepCounterCycleTest {

    /** Chin above the bar with bent elbows, wrists still on the bar. */
    private val top = Body(
        nose = lm(0.5f, 0.2f),
        leftShoulder = lm(0.4f, 0.35f),
        rightShoulder = lm(0.6f, 0.35f),
        leftElbow = lm(0.3f, 0.33f),
        rightElbow = lm(0.7f, 0.33f),
        leftWrist = lm(0.4f, 0.3f),
        rightWrist = lm(0.6f, 0.3f),
    )

    private fun RepCounter.feedRange(body: Body, from: Long, to: Long): RepCounterResult {
        var result = RepCounterResult(0, false)
        var t = from
        while (t < to) {
            result = process(body.frame(t), hanging = true, nowMs = t)
            t += 50L
        }
        return result
    }

    private fun countCycles(counter: RepCounter, bottom: Body, up: Body): Int {
        var t = 1_000L
        repeat(2) {
            counter.feedRange(bottom, t, t + 1_500L)
            t += 1_500L
            counter.feedRange(up, t, t + 1_500L)
            t += 1_500L
        }
        return counter.feedRange(bottom, t, t + 1_500L).reps
    }

    @Test
    fun `counts consecutive pull-ups with the face visible`() {
        val counter = RepCounter(PoseFeatureExtractor())
        assertEquals(2, countCycles(counter, Poses.deadHang, top))
    }

    @Test
    fun `falls back to shoulder travel when the face is hidden`() {
        val counter = RepCounter(PoseFeatureExtractor(), RepCounterConfig())
        assertEquals(2, countCycles(counter, Poses.deadHang.copy(nose = null), top.copy(nose = null)))
    }

    @Test
    fun `elbow only frames never count an up`() {
        val counter = RepCounter(PoseFeatureExtractor())
        val elbowsOnly = Body(
            leftShoulder = lm(0.4f, 0.5f),
            rightShoulder = lm(0.6f, 0.5f),
            leftElbow = lm(0.4f, 0.4f),
            rightElbow = lm(0.6f, 0.4f),
        )
        val bentElbowsOnly = elbowsOnly.copy(leftElbow = lm(0.3f, 0.4f), rightElbow = lm(0.7f, 0.4f))
        assertEquals(0, countCycles(counter, elbowsOnly, bentElbowsOnly))
    }

    @Test
    fun `not hanging and reset keep the count at zero`() {
        val counter = RepCounter(PoseFeatureExtractor())
        val idle = counter.process(Poses.deadHang.frame(), hanging = false)
        assertFalse(idle.repEvent)
        assertEquals(0, idle.reps)
        counter.feedRange(Poses.deadHang, 1_000L, 2_000L)
        counter.reset()
        assertEquals(0, counter.feedRange(Poses.deadHang, 3_000L, 3_200L).reps)
    }

    @Test
    fun `frames without an elbow angle before any history are ignored`() {
        val counter = RepCounter(PoseFeatureExtractor())
        val noElbows = Poses.deadHang.copy(leftElbow = null, rightElbow = null)
        assertEquals(0, counter.process(noElbows.frame(), hanging = true, nowMs = 1_000L).reps)
    }
}
