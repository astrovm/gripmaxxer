package com.astrovm.gripmaxxer.tracking

import com.astrovm.gripmaxxer.tracking.Poses.without
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BarDetectorTest {

    private val bar = BarDetector()
    private var now = 0L

    /** Feeds [pose] every 50 ms for [ms] and returns the last result. */
    private fun feed(pose: Pose?, ms: Long): Boolean {
        var result = bar.onBar
        val end = now + ms
        while (now < end) {
            result = bar.update(pose, now)
            now += 50
        }
        return result
    }

    @Test
    fun hangingStartsAfterAShortHold() {
        assertFalse(feed(Poses.deadHang, 250))
        assertTrue(feed(Poses.deadHang, 100))
        assertEquals(0L, bar.hangStartedAtMs)
    }

    @Test
    fun briefArmRaiseIsNotAHang() {
        feed(Poses.deadHang, 200)
        assertFalse(feed(Poses.standing, 500))
        assertNull(bar.hangStartedAtMs)
    }

    @Test
    fun lettingGoEndsTheHang() {
        feed(Poses.deadHang, 1000)
        assertTrue(feed(Poses.standing, 350))
        assertFalse(feed(Poses.standing, 100))
        assertNull(bar.hangStartedAtMs)
        assertEquals(950L, bar.lastGripAtMs)
    }

    @Test
    fun oneDroppedFrameKeepsTheHang() {
        feed(Poses.deadHang, 1000)
        feed(Poses.standing, 100)
        assertTrue(feed(Poses.deadHang, 1000))
    }

    @Test
    fun hiddenHandsKeepTheHangForAWhile() {
        feed(Poses.deadHang, 1000)
        val handsOut = Poses.pullUpTop.without(Joint.LEFT_WRIST, Joint.RIGHT_WRIST)
        assertTrue(feed(handsOut, 2000))
        assertFalse(feed(handsOut, 1000))
    }

    @Test
    fun losingThePersonEndsTheHang() {
        feed(Poses.deadHang, 1000)
        assertTrue(feed(null, 2000))
        assertFalse(feed(null, 1000))
    }

    @Test
    fun cantTellWithoutShouldersOrScale() {
        assertFalse(feed(Poses.deadHang.without(Joint.LEFT_SHOULDER, Joint.RIGHT_SHOULDER), 1000))
        val noScale = Poses.deadHang.without(Joint.LEFT_HIP, Joint.RIGHT_HIP, Joint.RIGHT_SHOULDER)
        assertFalse(feed(noScale, 1000))
    }

    @Test
    fun topOfPullUpStaysOnTheBar() {
        feed(Poses.deadHang, 1000)
        assertTrue(feed(Poses.pullUpTop, 3000))
    }

    @Test
    fun oneHandStillCountsAsHanging() {
        feed(Poses.deadHang, 1000)
        val oneHand = Poses.deadHang.joints + (Joint.RIGHT_WRIST to Point(290f, 400f))
        assertTrue(feed(Pose(oneHand), 3000))
    }
}
