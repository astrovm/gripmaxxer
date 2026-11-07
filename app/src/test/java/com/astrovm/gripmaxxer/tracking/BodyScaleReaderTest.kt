package com.astrovm.gripmaxxer.tracking

import com.astrovm.gripmaxxer.tracking.Joint.*
import com.astrovm.gripmaxxer.tracking.Poses.without
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BodyScaleReaderTest {
    @Test
    fun zeroBodyScaleDoesNotPoisonTheLegRaiseReader() {
        val zero = Poses.deadHang.copy(joints = Poses.deadHang.joints + mapOf(
            LEFT_HIP to Point(210f, 300f), RIGHT_HIP to Point(270f, 300f),
            LEFT_KNEE to Point(210f, 300f), RIGHT_KNEE to Point(270f, 300f),
        ))
        val reader = LegRaiseReader()
        assertNull(reader.read(zero))
        assertEquals(Position.REST, reader.read(Poses.deadHang))
    }

    @Test
    fun nonFiniteBodyScaleIsNotUsed() {
        val invalid = Poses.deadHang.copy(joints = Poses.deadHang.joints + mapOf(
            LEFT_HIP to Point(Float.POSITIVE_INFINITY, 420f),
            RIGHT_HIP to Point(Float.POSITIVE_INFINITY, 420f),
        ))
        assertNull(invalid.bodyScale)
    }

    @Test
    fun coincidentShouldersCannotEstimateBodyScale() {
        val zero = Poses.deadHang.without(LEFT_HIP, RIGHT_HIP).copy(joints =
            Poses.deadHang.without(LEFT_HIP, RIGHT_HIP).joints + mapOf(
                LEFT_SHOULDER to Point(240f, 300f), RIGHT_SHOULDER to Point(240f, 300f),
            ),
        )
        assertNull(zero.bodyScale)
    }

}
