package com.astrovm.gripmaxxer.tracking

import com.astrovm.gripmaxxer.tracking.Poses.pose
import com.astrovm.gripmaxxer.tracking.Poses.without
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PoseTest {

    @Test
    fun anglesMeasureJointBend() {
        assertEquals(180f, Poses.armsStraight.elbowAngle!!, 0.5f)
        assertEquals(90f, Poses.armsBent.elbowAngle!!, 0.5f)
        assertEquals(180f, Poses.standing.kneeAngle!!, 0.5f)
    }

    @Test
    fun oneSideIsEnoughForAnAngle() {
        val leftOnly = Poses.armsBent.without(Joint.RIGHT_WRIST)
        assertEquals(90f, leftOnly.elbowAngle!!, 0.5f)
        val rightOnly = Poses.armsBent.without(Joint.LEFT_ELBOW)
        assertEquals(90f, rightOnly.elbowAngle!!, 0.5f)
        assertNull(Poses.armsBent.without(Joint.LEFT_WRIST, Joint.RIGHT_WRIST).elbowAngle)
    }

    @Test
    fun angleNeedsDistinctPoints() {
        val collapsed = pose(
            Joint.LEFT_SHOULDER to (10 to 10),
            Joint.LEFT_ELBOW to (10 to 10),
            Joint.LEFT_WRIST to (20 to 20),
        )
        assertNull(collapsed.angle(Joint.LEFT_SHOULDER, Joint.LEFT_ELBOW, Joint.LEFT_WRIST))
    }

    @Test
    fun midpointsFallBackToOneSide() {
        assertEquals(Point(240f, 300f), Poses.deadHang.shoulder)
        assertEquals(Point(190f, 140f), Poses.deadHang.without(Joint.RIGHT_WRIST).wrist)
        assertEquals(Point(290f, 140f), Poses.deadHang.without(Joint.LEFT_WRIST).wrist)
        assertNull(Poses.deadHang.without(Joint.LEFT_WRIST, Joint.RIGHT_WRIST).wrist)
        assertEquals(Point(240f, 220f), Poses.deadHang.elbow)
        assertEquals(Point(240f, 520f), Poses.deadHang.knee)
        assertEquals(Point(240f, 610f), Poses.deadHang.ankle)
    }

    @Test
    fun bodyScaleUsesTorsoThenShoulders() {
        assertEquals(120f, Poses.deadHang.bodyScale!!, 0.01f)
        val noHips = Poses.deadHang.without(Joint.LEFT_HIP, Joint.RIGHT_HIP)
        assertEquals(80f * 1.4f, noHips.bodyScale!!, 0.01f)
        assertNull(noHips.without(Joint.LEFT_SHOULDER).bodyScale)
        assertNull(noHips.without(Joint.RIGHT_SHOULDER).bodyScale)
    }
}
