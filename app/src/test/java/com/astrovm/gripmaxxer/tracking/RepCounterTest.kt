package com.astrovm.gripmaxxer.tracking

import com.astrovm.gripmaxxer.tracking.Position.PEAK
import com.astrovm.gripmaxxer.tracking.Position.REST
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RepCounterTest {

    /** Feeds [position] every 50 ms for [ms]. Returns how many reps were counted. */
    private fun RepCounter.hold(position: Position?, ms: Long, clock: LongArray): Int {
        var counted = 0
        val end = clock[0] + ms
        while (clock[0] < end) {
            if (update(position, clock[0])) counted++
            clock[0] += 50
        }
        return counted
    }

    @Test
    fun pullsCountAtThePeak() {
        val counter = RepCounter(countAtPeak = true)
        val clock = longArrayOf(0)
        counter.hold(REST, 300, clock)
        assertEquals(1, counter.hold(PEAK, 300, clock))
        assertEquals(1, counter.reps)
        counter.hold(null, 300, clock)
        counter.hold(REST, 300, clock)
        assertEquals(1, counter.hold(PEAK, 300, clock))
        assertEquals(2, counter.reps)
    }

    @Test
    fun peakWithoutRestFirstDoesNotCount() {
        val counter = RepCounter(countAtPeak = true)
        val clock = longArrayOf(0)
        assertEquals(0, counter.hold(PEAK, 500, clock))
        counter.hold(REST, 300, clock)
        assertEquals(1, counter.hold(PEAK, 300, clock))
    }

    @Test
    fun pushesCountBackAtRest() {
        val counter = RepCounter(countAtPeak = false)
        val clock = longArrayOf(0)
        counter.hold(REST, 300, clock)
        assertEquals(0, counter.hold(PEAK, 300, clock))
        assertEquals(1, counter.hold(REST, 300, clock))
        assertEquals(1, counter.reps)
    }

    @Test
    fun halfRepDoesNotCount() {
        val counter = RepCounter(countAtPeak = false)
        val clock = longArrayOf(0)
        counter.hold(REST, 300, clock)
        counter.hold(null, 300, clock)
        assertEquals(0, counter.hold(REST, 300, clock))
        assertEquals(0, counter.reps)
    }

    @Test
    fun oneFrameGlitchIsIgnored() {
        val counter = RepCounter(countAtPeak = true)
        val clock = longArrayOf(0)
        counter.hold(REST, 300, clock)
        assertEquals(0, counter.hold(PEAK, 50, clock))
        counter.hold(REST, 300, clock)
        assertEquals(0, counter.reps)
    }

    @Test
    fun remembersWhenTheRepStarted() {
        val counter = RepCounter(countAtPeak = false)
        assertNull(counter.repStartedAtMs)
        counter.update(REST, 0)
        counter.update(REST, 100)
        counter.update(REST, 200)
        counter.update(null, 250)
        counter.update(PEAK, 300)
        counter.update(PEAK, 400)
        assertEquals(200L, counter.repStartedAtMs)
    }

    @Test
    fun countsRightAwayWithNoSettleTime() {
        val counter = RepCounter(countAtPeak = true, settleMs = 0)
        assertFalse(counter.update(REST, 0))
        assertTrue(counter.update(PEAK, 1))
    }
}
