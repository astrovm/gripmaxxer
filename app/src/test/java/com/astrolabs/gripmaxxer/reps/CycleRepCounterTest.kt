package com.astrolabs.gripmaxxer.reps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CycleRepCounterTest {

    @Test
    fun `counts one rep on down to up cycle`() {
        val counter = CycleRepCounter(
            stableMs = 50L,
            minRepIntervalMs = 100L,
        )

        // Move into DOWN.
        counter.process(isDown = true, isUp = false, nowMs = 0L)
        counter.process(isDown = true, isUp = false, nowMs = 60L)

        // Move back to UP and count one rep.
        counter.process(isDown = false, isUp = true, nowMs = 120L)
        val result = counter.process(isDown = false, isUp = true, nowMs = 180L)

        assertTrue(result.repEvent)
        assertEquals(1, result.reps)
    }

    @Test
    fun `does not overcount without full cycle and cooldown`() {
        val counter = CycleRepCounter(
            stableMs = 50L,
            minRepIntervalMs = 200L,
        )

        // First full rep.
        counter.process(isDown = true, isUp = false, nowMs = 0L)
        counter.process(isDown = true, isUp = false, nowMs = 60L)
        counter.process(isDown = false, isUp = true, nowMs = 220L)
        val firstRep = counter.process(isDown = false, isUp = true, nowMs = 280L)
        assertTrue(firstRep.repEvent)
        assertEquals(1, firstRep.reps)

        // Up signals without returning DOWN should not count.
        val noCycleResult = counter.process(isDown = false, isUp = true, nowMs = 330L)
        assertFalse(noCycleResult.repEvent)
        assertEquals(1, noCycleResult.reps)

        // Complete a second cycle after cooldown.
        counter.process(isDown = true, isUp = false, nowMs = 500L)
        counter.process(isDown = true, isUp = false, nowMs = 560L)
        counter.process(isDown = false, isUp = true, nowMs = 620L)
        val secondRep = counter.process(isDown = false, isUp = true, nowMs = 680L)
        assertTrue(secondRep.repEvent)
        assertEquals(2, secondRep.reps)
    }
}
