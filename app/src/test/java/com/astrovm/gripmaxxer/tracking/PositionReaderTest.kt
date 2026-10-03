package com.astrovm.gripmaxxer.tracking

import com.astrovm.gripmaxxer.tracking.Joint.LEFT_ELBOW
import com.astrovm.gripmaxxer.tracking.Joint.LEFT_HIP
import com.astrovm.gripmaxxer.tracking.Joint.LEFT_KNEE
import com.astrovm.gripmaxxer.tracking.Joint.LEFT_SHOULDER
import com.astrovm.gripmaxxer.tracking.Joint.LEFT_WRIST
import com.astrovm.gripmaxxer.tracking.Joint.NOSE
import com.astrovm.gripmaxxer.tracking.Joint.RIGHT_ELBOW
import com.astrovm.gripmaxxer.tracking.Joint.RIGHT_HIP
import com.astrovm.gripmaxxer.tracking.Joint.RIGHT_KNEE
import com.astrovm.gripmaxxer.tracking.Joint.RIGHT_SHOULDER
import com.astrovm.gripmaxxer.tracking.Joint.RIGHT_WRIST
import com.astrovm.gripmaxxer.tracking.Position.PEAK
import com.astrovm.gripmaxxer.tracking.Position.REST
import com.astrovm.gripmaxxer.tracking.Poses.without
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PositionReaderTest {

    @Test
    fun eachExerciseGetsTheRightReader() {
        assertNull(PositionReader.forExercise(Exercise.DEAD_HANG))
        assertNull(PositionReader.forExercise(Exercise.ACTIVE_HANG))
        assertTrue(PositionReader.forExercise(Exercise.PULL_UP) is PullReader)
        assertTrue(PositionReader.forExercise(Exercise.CHIN_UP) is PullReader)
        assertTrue(PositionReader.forExercise(Exercise.HANGING_LEG_RAISE) is LegRaiseReader)
        assertTrue(PositionReader.forExercise(Exercise.PUSH_UP) is PressReader)
        assertTrue(PositionReader.forExercise(Exercise.DIP) is PressReader)
        assertTrue(PositionReader.forExercise(Exercise.SQUAT) is SquatReader)
    }

    @Test
    fun smootherBlendsAndResets() {
        val smoother = Smoother(weight = 0.5f)
        assertEquals(10f, smoother.update(10f))
        assertEquals(15f, smoother.update(20f))
        assertNull(smoother.update(null))
        assertEquals(4f, smoother.update(4f))
    }

    @Test
    fun pullReadsElbows() {
        assertEquals(REST, PullReader().read(Poses.deadHang))
        assertEquals(PEAK, PullReader().read(Poses.pullUpTop))
        assertNull(PullReader().read(Poses.pullUpMiddle))
    }

    @Test
    fun pullCountsChinOverHandsEvenWithStraightishArms() {
        val chinOver = Poses.pullUpMiddle.joints + (NOSE to Point(240f, 120f))
        assertEquals(PEAK, PullReader().read(Pose(chinOver)))
    }

    @Test
    fun lettingGoIsNotARep() {
        // Standing under the bar: head above the hands, but the hands are down.
        assertNull(PullReader().read(Poses.standing))
    }

    @Test
    fun pullFallsBackToHandHeightWithoutElbows() {
        val reader = PullReader()
        assertEquals(REST, reader.read(Poses.deadHang.without(LEFT_ELBOW, RIGHT_ELBOW)))
        val top = Poses.pullUpTop.without(LEFT_ELBOW, RIGHT_ELBOW, NOSE)
        assertEquals(PEAK, PullReader().read(top))
        val middle = Poses.pullUpMiddle.without(LEFT_ELBOW, RIGHT_ELBOW)
        assertNull(PullReader().read(middle))
        val chinOver = Poses.pullUpTop.without(LEFT_ELBOW, RIGHT_ELBOW)
        assertEquals(PEAK, PullReader().read(chinOver))
    }

    @Test
    fun pullCantTellWithoutArms() {
        val bare = Poses.deadHang.without(LEFT_ELBOW, RIGHT_ELBOW, LEFT_WRIST, RIGHT_WRIST)
        assertNull(PullReader().read(bare))
        val noScale = Poses.deadHang.without(LEFT_ELBOW, RIGHT_ELBOW, LEFT_HIP, RIGHT_HIP, LEFT_SHOULDER)
        assertNull(PullReader().read(noScale))
        val noShoulders = Poses.deadHang.without(LEFT_ELBOW, RIGHT_ELBOW, LEFT_SHOULDER, RIGHT_SHOULDER)
        assertNull(PullReader().read(noShoulders))
    }

    @Test
    fun legRaiseReadsKnees() {
        assertEquals(REST, LegRaiseReader().read(Poses.deadHang))
        assertEquals(PEAK, LegRaiseReader().read(Poses.kneesUp))
        val halfway = Poses.deadHang.joints + mapOf(LEFT_KNEE to Point(210f, 470f), RIGHT_KNEE to Point(270f, 470f))
        assertNull(LegRaiseReader().read(Pose(halfway)))
    }

    @Test
    fun legRaiseFallsBackToAnkles() {
        assertEquals(REST, LegRaiseReader().read(Poses.deadHang.without(LEFT_KNEE, RIGHT_KNEE)))
        assertEquals(PEAK, LegRaiseReader().read(Poses.kneesUp.without(LEFT_KNEE, RIGHT_KNEE)))
        val ankles = Poses.deadHang.joints - setOf(LEFT_KNEE, RIGHT_KNEE) +
            mapOf(Joint.LEFT_ANKLE to Point(210f, 520f), Joint.RIGHT_ANKLE to Point(270f, 520f))
        assertNull(LegRaiseReader().read(Pose(ankles)))
        val noLegs = Poses.deadHang.without(LEFT_KNEE, RIGHT_KNEE, Joint.LEFT_ANKLE, Joint.RIGHT_ANKLE)
        assertNull(LegRaiseReader().read(noLegs))
    }

    @Test
    fun legRaiseNeedsHipsAndScale() {
        assertNull(LegRaiseReader().read(Poses.deadHang.without(LEFT_HIP, RIGHT_HIP)))
        val noScale = Poses.deadHang.without(LEFT_SHOULDER, RIGHT_SHOULDER)
        assertNull(LegRaiseReader().read(noScale))
    }

    @Test
    fun pressReadsElbows() {
        val reader = PositionReader.forExercise(Exercise.PUSH_UP)!!
        assertEquals(REST, reader.read(Poses.armsStraight))
        assertEquals(PEAK, PositionReader.forExercise(Exercise.PUSH_UP)!!.read(Poses.armsBent))
        assertNull(PositionReader.forExercise(Exercise.DIP)!!.read(Poses.armsHalf))
        assertNull(reader.read(Poses.armsStraight.without(LEFT_WRIST, RIGHT_WRIST)))
    }

    @Test
    fun squatReadsDepth() {
        assertEquals(REST, SquatReader().read(Poses.standing))
        assertEquals(PEAK, SquatReader().read(Poses.squatBottom))
    }

    @Test
    fun squatFromTheSideUsesKneeAngle() {
        val bent = Poses.standing.joints + mapOf(LEFT_HIP to Point(150f, 420f), RIGHT_HIP to Point(150f, 420f))
        assertEquals(PEAK, SquatReader().read(Pose(bent)))
        // Feet level with the knees: no shin to compare against, so only the angle decides.
        val kickedOut = Poses.pose(
            LEFT_HIP to (210 to 320), LEFT_KNEE to (210 to 440), Joint.LEFT_ANKLE to (330 to 440),
        )
        assertEquals(PEAK, SquatReader().read(kickedOut))
    }

    @Test
    fun squatWithOnlyThighRatio() {
        // Joints from different sides: no full leg for an angle, but enough for heights.
        val mixed = Poses.pose(
            LEFT_HIP to (210 to 320), RIGHT_KNEE to (270 to 440), Joint.LEFT_ANKLE to (210 to 560),
        )
        assertEquals(REST, SquatReader().read(mixed))
    }

    @Test
    fun squatHalfwayIsNeither() {
        val half = Poses.standing.joints + mapOf(LEFT_HIP to Point(210f, 370f), RIGHT_HIP to Point(270f, 370f))
        assertNull(SquatReader().read(Pose(half)))
        val kneesIn = Poses.standing.joints + mapOf(LEFT_KNEE to Point(240f, 440f), RIGHT_KNEE to Point(240f, 440f))
        assertNull(SquatReader().read(Pose(kneesIn)))
    }

    @Test
    fun squatNeedsLegs() {
        assertNull(SquatReader().read(Poses.standing.without(LEFT_KNEE, RIGHT_KNEE)))
    }
}
