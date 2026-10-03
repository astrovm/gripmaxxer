package com.astrovm.gripmaxxer.tracking

import com.astrovm.gripmaxxer.tracking.Joint.LEFT_ANKLE
import com.astrovm.gripmaxxer.tracking.Joint.LEFT_ELBOW
import com.astrovm.gripmaxxer.tracking.Joint.LEFT_HIP
import com.astrovm.gripmaxxer.tracking.Joint.LEFT_KNEE
import com.astrovm.gripmaxxer.tracking.Joint.LEFT_SHOULDER
import com.astrovm.gripmaxxer.tracking.Joint.LEFT_WRIST
import com.astrovm.gripmaxxer.tracking.Joint.NOSE
import com.astrovm.gripmaxxer.tracking.Joint.RIGHT_ANKLE
import com.astrovm.gripmaxxer.tracking.Joint.RIGHT_ELBOW
import com.astrovm.gripmaxxer.tracking.Joint.RIGHT_HIP
import com.astrovm.gripmaxxer.tracking.Joint.RIGHT_KNEE
import com.astrovm.gripmaxxer.tracking.Joint.RIGHT_SHOULDER
import com.astrovm.gripmaxxer.tracking.Joint.RIGHT_WRIST

/**
 * Body shapes as the front camera sees them, in a 480x640 frame.
 * Torso length is 120 px in all of them.
 */
object Poses {

    fun pose(vararg joints: Pair<Joint, Pair<Number, Number>>): Pose =
        Pose(joints.associate { (joint, xy) -> joint to Point(xy.first.toFloat(), xy.second.toFloat()) })

    fun Pose.without(vararg joints: Joint): Pose = Pose(this.joints - joints.toSet())

    /** Hanging with straight arms. */
    val deadHang = pose(
        NOSE to (240 to 270),
        LEFT_SHOULDER to (200 to 300), RIGHT_SHOULDER to (280 to 300),
        LEFT_ELBOW to (195 to 220), RIGHT_ELBOW to (285 to 220),
        LEFT_WRIST to (190 to 140), RIGHT_WRIST to (290 to 140),
        LEFT_HIP to (210 to 420), RIGHT_HIP to (270 to 420),
        LEFT_KNEE to (210 to 520), RIGHT_KNEE to (270 to 520),
        LEFT_ANKLE to (210 to 610), RIGHT_ANKLE to (270 to 610),
    )

    /** Top of a pull-up: chin over the hands, elbows bent. */
    val pullUpTop = pose(
        NOSE to (240 to 130),
        LEFT_SHOULDER to (200 to 180), RIGHT_SHOULDER to (280 to 180),
        LEFT_ELBOW to (140 to 190), RIGHT_ELBOW to (340 to 190),
        LEFT_WRIST to (190 to 140), RIGHT_WRIST to (290 to 140),
        LEFT_HIP to (210 to 300), RIGHT_HIP to (270 to 300),
        LEFT_KNEE to (210 to 400), RIGHT_KNEE to (270 to 400),
    )

    /** Halfway up: elbows at about 120 degrees. */
    val pullUpMiddle = pose(
        NOSE to (240 to 200),
        LEFT_SHOULDER to (200 to 240), RIGHT_SHOULDER to (280 to 240),
        LEFT_ELBOW to (160 to 190), RIGHT_ELBOW to (320 to 190),
        LEFT_WRIST to (190 to 140), RIGHT_WRIST to (290 to 140),
        LEFT_HIP to (210 to 360), RIGHT_HIP to (270 to 360),
    )

    /** Hanging with knees pulled up to hip height. */
    val kneesUp = deadHang.copy(
        joints = deadHang.joints + mapOf(
            LEFT_KNEE to Point(210f, 425f), RIGHT_KNEE to Point(270f, 425f),
            LEFT_ANKLE to Point(210f, 440f), RIGHT_ANKLE to Point(270f, 440f),
        ),
    )

    /** Standing with arms down. */
    val standing = pose(
        NOSE to (240 to 150),
        LEFT_SHOULDER to (200 to 200), RIGHT_SHOULDER to (280 to 200),
        LEFT_ELBOW to (195 to 260), RIGHT_ELBOW to (285 to 260),
        LEFT_WRIST to (190 to 320), RIGHT_WRIST to (290 to 320),
        LEFT_HIP to (210 to 320), RIGHT_HIP to (270 to 320),
        LEFT_KNEE to (210 to 440), RIGHT_KNEE to (270 to 440),
        LEFT_ANKLE to (210 to 560), RIGHT_ANKLE to (270 to 560),
    )

    /** Bottom of a squat, facing the camera: hips almost down at knee height. */
    val squatBottom = standing.copy(
        joints = standing.joints + mapOf(
            LEFT_SHOULDER to Point(200f, 330f), RIGHT_SHOULDER to Point(280f, 330f),
            LEFT_HIP to Point(210f, 410f), RIGHT_HIP to Point(270f, 410f),
        ),
    )

    /** Arms locked out, seen from the side. */
    val armsStraight = pose(
        LEFT_SHOULDER to (200 to 300), RIGHT_SHOULDER to (280 to 300),
        LEFT_ELBOW to (200 to 380), RIGHT_ELBOW to (280 to 380),
        LEFT_WRIST to (200 to 460), RIGHT_WRIST to (280 to 460),
        LEFT_HIP to (200 to 420), RIGHT_HIP to (280 to 420),
    )

    /** Arms bent to 90 degrees. */
    val armsBent = pose(
        LEFT_SHOULDER to (200 to 300), RIGHT_SHOULDER to (280 to 300),
        LEFT_ELBOW to (200 to 380), RIGHT_ELBOW to (280 to 380),
        LEFT_WRIST to (120 to 380), RIGHT_WRIST to (360 to 380),
        LEFT_HIP to (200 to 420), RIGHT_HIP to (280 to 420),
    )

    /** Arms at about 125 degrees, between the push-up thresholds. */
    val armsHalf = pose(
        LEFT_SHOULDER to (200 to 300), RIGHT_SHOULDER to (280 to 300),
        LEFT_ELBOW to (200 to 380), RIGHT_ELBOW to (280 to 380),
        LEFT_WRIST to (135 to 426), RIGHT_WRIST to (345 to 426),
        LEFT_HIP to (200 to 420), RIGHT_HIP to (280 to 420),
    )
}
