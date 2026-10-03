package com.astrovm.gripmaxxer.tracking

import kotlin.math.acos
import kotlin.math.hypot

/** Body joints the trackers use. Left and right are the person's own sides. */
enum class Joint {
    NOSE,
    LEFT_SHOULDER, RIGHT_SHOULDER,
    LEFT_ELBOW, RIGHT_ELBOW,
    LEFT_WRIST, RIGHT_WRIST,
    LEFT_HIP, RIGHT_HIP,
    LEFT_KNEE, RIGHT_KNEE,
    LEFT_ANKLE, RIGHT_ANKLE,
}

/** A point in upright image pixels. Y grows downwards. */
data class Point(val x: Float, val y: Float) {
    fun distanceTo(other: Point): Float = hypot(x - other.x, y - other.y)
}

/** Visible joints for one camera frame. Joints the detector is unsure about are left out. */
data class Pose(val joints: Map<Joint, Point>) {

    operator fun get(joint: Joint): Point? = joints[joint]

    val shoulder: Point? get() = midpoint(Joint.LEFT_SHOULDER, Joint.RIGHT_SHOULDER)
    val elbow: Point? get() = midpoint(Joint.LEFT_ELBOW, Joint.RIGHT_ELBOW)
    val wrist: Point? get() = midpoint(Joint.LEFT_WRIST, Joint.RIGHT_WRIST)
    val hip: Point? get() = midpoint(Joint.LEFT_HIP, Joint.RIGHT_HIP)
    val knee: Point? get() = midpoint(Joint.LEFT_KNEE, Joint.RIGHT_KNEE)
    val ankle: Point? get() = midpoint(Joint.LEFT_ANKLE, Joint.RIGHT_ANKLE)

    /**
     * Rough body size in pixels, so thresholds work at any distance from the camera.
     * Torso length when hips are visible, otherwise estimated from shoulder width.
     */
    val bodyScale: Float?
        get() {
            val shoulder = shoulder
            val hip = hip
            if (shoulder != null && hip != null) return shoulder.distanceTo(hip)
            val left = joints[Joint.LEFT_SHOULDER] ?: return null
            val right = joints[Joint.RIGHT_SHOULDER] ?: return null
            return left.distanceTo(right) * SHOULDER_TO_TORSO
        }

    /** Average elbow bend in degrees. 180 is a straight arm. */
    val elbowAngle: Float?
        get() = averageOf(
            angle(Joint.LEFT_SHOULDER, Joint.LEFT_ELBOW, Joint.LEFT_WRIST),
            angle(Joint.RIGHT_SHOULDER, Joint.RIGHT_ELBOW, Joint.RIGHT_WRIST),
        )

    /** Average knee bend in degrees. 180 is a straight leg. */
    val kneeAngle: Float?
        get() = averageOf(
            angle(Joint.LEFT_HIP, Joint.LEFT_KNEE, Joint.LEFT_ANKLE),
            angle(Joint.RIGHT_HIP, Joint.RIGHT_KNEE, Joint.RIGHT_ANKLE),
        )

    /** Angle at [vertex] between [a] and [c], in degrees. */
    fun angle(a: Joint, vertex: Joint, c: Joint): Float? {
        val pa = joints[a] ?: return null
        val pv = joints[vertex] ?: return null
        val pc = joints[c] ?: return null
        val v1x = pa.x - pv.x
        val v1y = pa.y - pv.y
        val v2x = pc.x - pv.x
        val v2y = pc.y - pv.y
        val lengths = hypot(v1x, v1y) * hypot(v2x, v2y)
        if (lengths == 0f) return null
        val cosine = ((v1x * v2x + v1y * v2y) / lengths).coerceIn(-1f, 1f)
        return Math.toDegrees(acos(cosine).toDouble()).toFloat()
    }

    private fun midpoint(left: Joint, right: Joint): Point? {
        val l = joints[left]
        val r = joints[right]
        return when {
            l != null && r != null -> Point((l.x + r.x) / 2f, (l.y + r.y) / 2f)
            else -> l ?: r
        }
    }

    private fun averageOf(a: Float?, b: Float?): Float? = when {
        a != null && b != null -> (a + b) / 2f
        else -> a ?: b
    }

    private companion object {
        // Shoulder joints sit about 0.7 torso lengths apart on an adult.
        const val SHOULDER_TO_TORSO = 1.4f
    }
}
