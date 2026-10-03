package com.astrovm.gripmaxxer.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PocketTrackerTest {

    private class Run(exercise: Exercise) {
        val tracker = PocketTracker(exercise)
        val sets = mutableListOf<TrackedSet>()
        var beeps = 0
        var state = TrackerState()
        val moves = PocketMoves { motion, now ->
            state = tracker.update(motion, now)
            state.finishedSet?.let { sets += it }
            if (state.repCounted) beeps++
        }

        init {
            moves.still(2_000)
        }
    }

    @Test
    fun jumpUpToTheBarAndHang() {
        val run = Run(Exercise.DEAD_HANG)
        run.moves.jumpToBar()
        run.moves.still(10_000)
        assertTrue(run.state.inSet)
        run.moves.drop()
        assertFalse(run.state.inSet)
        val set = run.sets.single()
        // From the jump to letting go.
        assertEquals(10_400.0, set.durationMs.toDouble(), 400.0)
        assertEquals(0, set.reps)
    }

    @Test
    fun jumpThatLandsIsNotAHang() {
        val run = Run(Exercise.ACTIVE_HANG)
        run.moves.jump()
        run.moves.still(3_000)
        assertFalse(run.state.inSet)
        assertTrue(run.sets.isEmpty())
    }

    @Test
    fun walkingAwayEndsAHangWithoutADrop() {
        val run = Run(Exercise.DEAD_HANG)
        run.moves.jumpToBar()
        run.moves.still(5_000)
        run.moves.walk(3)
        assertFalse(run.state.inSet)
        // Ends at the first step.
        assertEquals(5_400.0, run.sets.single().durationMs.toDouble(), 400.0)
    }

    @Test
    fun standingUpFromASquatIsNotAHop() {
        val run = Run(Exercise.DEAD_HANG)
        run.moves.lean(90f, 1_000)
        run.moves.still(1_000)
        run.moves.lean(5f, 600)
        run.moves.move(0.3f, 600)
        run.moves.still(3_000)
        assertFalse(run.state.inSet)
    }

    @Test
    fun pullUpsFromAReachableBarCountTheFirstOne() {
        val run = Run(Exercise.PULL_UP)
        repeat(3) { run.moves.pullUp() }
        assertTrue(run.state.inSet)
        assertEquals(3, run.state.reps)
        assertEquals(3, run.beeps)
        run.moves.drop()
        assertEquals(3, run.sets.single().reps)
    }

    @Test
    fun pullUpsWithoutPausing() {
        val run = Run(Exercise.PULL_UP)
        repeat(5) {
            run.moves.move(0.4f, 1_000)
            run.moves.move(-0.4f, 1_000)
        }
        run.moves.still(500)
        assertEquals(5, run.state.reps)
    }

    @Test
    fun walkingAroundIsNotAHang() {
        val run = Run(Exercise.PULL_UP)
        run.moves.walk(20)
        run.moves.still(2_000)
        assertFalse(run.state.inSet)
        assertTrue(run.sets.isEmpty())
    }

    @Test
    fun jumpingUpToTheBarIsNotARep() {
        val run = Run(Exercise.CHIN_UP)
        run.moves.jumpToBar()
        run.moves.still(1_000)
        assertEquals(0, run.state.reps)
        repeat(2) { run.moves.pullUp() }
        run.moves.drop()
        assertEquals(2, run.sets.single().reps)
    }

    @Test
    fun legRaises() {
        val run = Run(Exercise.HANGING_LEG_RAISE)
        run.moves.jumpToBar()
        run.moves.still(1_000)
        repeat(4) {
            run.moves.lean(85f, 700)
            run.moves.lean(5f, 700)
        }
        assertEquals(4, run.state.reps)
        run.moves.drop()
        assertEquals(4, run.sets.single().reps)
    }

    @Test
    fun squatsEndAfterRest() {
        val run = Run(Exercise.SQUAT)
        repeat(3) {
            run.moves.lean(85f, 800)
            run.moves.lean(5f, 800)
        }
        assertTrue(run.state.inSet)
        assertEquals(3, run.state.reps)
        run.moves.still(6_000)
        val set = run.sets.single()
        assertEquals(3, set.reps)
        assertEquals(4_300.0, set.durationMs.toDouble(), 300.0)
    }

    @Test
    fun sittingDownAndUpIsNotASet() {
        val run = Run(Exercise.SQUAT)
        run.moves.lean(90f, 800)
        run.moves.still(20_000)
        run.moves.lean(5f, 800)
        run.moves.still(6_000)
        assertTrue(run.sets.isEmpty())
        // The next real set starts counting from zero.
        repeat(2) {
            run.moves.lean(85f, 800)
            run.moves.lean(5f, 800)
        }
        assertEquals(2, run.state.reps)
    }

    @Test
    fun pushUpsWithThighsLevel() {
        val run = Run(Exercise.PUSH_UP)
        run.moves.lean(85f, 1_500)
        run.moves.still(1_000)
        repeat(3) {
            run.moves.move(-0.15f, 800)
            run.moves.still(400)
            run.moves.move(0.15f, 800)
            run.moves.still(300)
        }
        assertEquals(3, run.state.reps)
        run.moves.still(6_000)
        assertEquals(3, run.sets.single().reps)
    }

    @Test
    fun movingUpAndDownStandingIsNotAPushUp() {
        val run = Run(Exercise.PUSH_UP)
        repeat(3) {
            run.moves.move(-0.15f, 800)
            run.moves.move(0.15f, 800)
        }
        assertEquals(0, run.state.reps)
    }

    @Test
    fun dips() {
        val run = Run(Exercise.DIP)
        repeat(3) {
            run.moves.move(-0.3f, 1_000)
            run.moves.move(0.3f, 1_000)
            run.moves.still(300)
        }
        assertEquals(3, run.state.reps)
        run.moves.still(6_000)
        assertEquals(3, run.sets.single().reps)
    }

    @Test
    fun finishSavesTheSetInProgress() {
        val hang = Run(Exercise.DEAD_HANG)
        assertNull(hang.tracker.finish(hang.moves.now))
        hang.moves.jumpToBar()
        hang.moves.still(3_000)
        assertEquals(3_400.0, hang.tracker.finish(hang.moves.now)!!.durationMs.toDouble(), 400.0)

        val squat = Run(Exercise.SQUAT)
        repeat(2) {
            squat.moves.lean(85f, 800)
            squat.moves.lean(5f, 800)
        }
        assertEquals(2, squat.tracker.finish(squat.moves.now)!!.reps)
        assertNull(squat.tracker.finish(squat.moves.now))
    }
}
