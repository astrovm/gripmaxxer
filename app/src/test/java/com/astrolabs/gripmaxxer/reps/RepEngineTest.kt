package com.astrolabs.gripmaxxer.reps

import com.astrolabs.gripmaxxer.pose.PoseFrame
import org.junit.Assert.assertEquals
import org.junit.Test

class RepEngineTest {

    @Test
    fun `switching mode with reset clears new mode detector state`() {
        val pullUp = FakeRepDetector()
        val squat = FakeRepDetector()
        val engine = RepEngine(
            detectors = mapOf(
                ExerciseMode.PULL_UP to pullUp,
                ExerciseMode.SQUAT to squat,
            ),
            initialMode = ExerciseMode.PULL_UP,
        )

        val frame = PoseFrame(
            landmarks = emptyMap(),
            timestampMs = 0L,
            posePresent = false,
        )

        engine.process(frame = frame, active = true, nowMs = 0L)
        assertEquals(1, pullUp.count)

        engine.setMode(ExerciseMode.SQUAT, resetCurrent = true)
        engine.process(frame = frame, active = true, nowMs = 1L)
        assertEquals(1, squat.count)
        assertEquals(1, squat.resetCalls)
    }

    private class FakeRepDetector : ModeRepDetector {
        var count = 0
        var resetCalls = 0

        override fun reset() {
            count = 0
            resetCalls += 1
        }

        override fun process(frame: PoseFrame, active: Boolean, nowMs: Long): RepCounterResult {
            if (active) count += 1
            return RepCounterResult(reps = count, repEvent = false)
        }
    }
}
