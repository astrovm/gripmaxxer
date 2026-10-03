package com.astrovm.gripmaxxer.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseTrackerTest {

    private var now = 0L
    private val finished = mutableListOf<TrackedSet>()
    private var repEvents = 0

    /** Feeds [pose] every 50 ms for [ms] and returns the last state. */
    private fun ExerciseTracker.feed(pose: Pose?, ms: Long): TrackerState {
        var state = TrackerState()
        val end = now + ms
        while (now < end) {
            state = update(pose, now)
            state.finishedSet?.let(finished::add)
            if (state.repCounted) repEvents++
            now += 50
        }
        return state
    }

    private fun ExerciseTracker.pullUp() {
        feed(Poses.pullUpMiddle, 300)
        feed(Poses.pullUpTop, 400)
        feed(Poses.pullUpMiddle, 300)
        feed(Poses.deadHang, 400)
    }

    @Test
    fun deadHangIsOneTimedSet() {
        val tracker = ExerciseTracker(Exercise.DEAD_HANG)
        tracker.feed(Poses.standing, 1000)
        val hanging = tracker.feed(Poses.deadHang, 5000)
        assertTrue(hanging.inSet)
        assertEquals(0, hanging.reps)
        tracker.feed(Poses.standing, 1000)

        val set = finished.single()
        assertEquals(Exercise.DEAD_HANG, set.exercise)
        assertEquals(4950L, set.durationMs)
        assertEquals(0, set.reps)
    }

    @Test
    fun shortHangIsNotSaved() {
        val tracker = ExerciseTracker(Exercise.ACTIVE_HANG)
        tracker.feed(Poses.deadHang, 1500)
        tracker.feed(Poses.standing, 1000)
        assertTrue(finished.isEmpty())
    }

    @Test
    fun pullUpsCountWhileHanging() {
        val tracker = ExerciseTracker(Exercise.PULL_UP)
        tracker.feed(Poses.deadHang, 1000)
        repeat(3) { tracker.pullUp() }
        tracker.feed(Poses.standing, 1000)

        assertEquals(3, repEvents)
        assertEquals(3, finished.single().reps)
    }

    @Test
    fun gettingDownAfterPullUpsIsNotARep() {
        val tracker = ExerciseTracker(Exercise.PULL_UP)
        tracker.feed(Poses.deadHang, 1000)
        repeat(3) { tracker.pullUp() }
        // Let go: hands come down past the face with bent elbows, then off the bar.
        tracker.feed(Poses.lettingGo, 500)
        tracker.feed(Poses.standing, 1000)

        assertEquals(3, repEvents)
        assertEquals(3, finished.single().reps)
    }

    @Test
    fun hangWithoutPullUpsIsNotARepSet() {
        val tracker = ExerciseTracker(Exercise.CHIN_UP)
        tracker.feed(Poses.deadHang, 5000)
        tracker.feed(Poses.standing, 1000)
        assertTrue(finished.isEmpty())
    }

    @Test
    fun repsOnlyCountOnTheBar() {
        val tracker = ExerciseTracker(Exercise.PULL_UP)
        tracker.feed(Poses.pullUpTop, 1000)
        assertEquals(0, repEvents)
    }

    @Test
    fun legRaisesCount() {
        val tracker = ExerciseTracker(Exercise.HANGING_LEG_RAISE)
        tracker.feed(Poses.deadHang, 1000)
        repeat(2) {
            tracker.feed(Poses.kneesUp, 400)
            tracker.feed(Poses.deadHang, 400)
        }
        val state = tracker.feed(Poses.deadHang, 100)
        assertEquals(2, state.reps)
        assertTrue(state.setDurationMs > 0)
    }

    @Test
    fun pushUpSetEndsAfterRest() {
        val tracker = ExerciseTracker(Exercise.PUSH_UP)
        tracker.feed(Poses.armsStraight, 1000)
        assertFalse(tracker.feed(Poses.armsStraight, 100).inSet)
        repeat(4) {
            tracker.feed(Poses.armsBent, 400)
            tracker.feed(Poses.armsStraight, 400)
        }
        assertTrue(tracker.feed(Poses.armsStraight, 4000).inSet)
        tracker.feed(Poses.armsStraight, 1500)

        val set = finished.single()
        assertEquals(4, set.reps)
        // From the last moment at rest before the first rep to the last rep, a smoothed frame late.
        assertEquals(3000L, set.durationMs)
    }

    @Test
    fun floorSetEndsWhenThePersonLeaves() {
        val tracker = ExerciseTracker(Exercise.SQUAT)
        tracker.feed(Poses.standing, 500)
        tracker.feed(Poses.squatBottom, 400)
        tracker.feed(Poses.standing, 400)
        assertTrue(tracker.feed(null, 2900).inSet)
        assertFalse(tracker.feed(null, 200).inSet)
        assertEquals(1, finished.single().reps)
    }

    @Test
    fun finishSavesTheSetInProgress() {
        val hang = ExerciseTracker(Exercise.DEAD_HANG)
        hang.feed(Poses.deadHang, 3000)
        val set = hang.finish(now)!!
        assertEquals(2950L, set.durationMs)
        assertNull(hang.finish(now))

        val dips = ExerciseTracker(Exercise.DIP)
        dips.feed(Poses.armsStraight, 300)
        dips.feed(Poses.armsBent, 300)
        dips.feed(Poses.armsStraight, 300)
        assertEquals(1, dips.finish(now)!!.reps)
    }

    @Test
    fun finishWithNothingDoneSavesNothing() {
        val squats = ExerciseTracker(Exercise.SQUAT)
        squats.feed(Poses.standing, 1000)
        assertNull(squats.finish(now))
    }
}
