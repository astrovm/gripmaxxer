package com.astrovm.gripmaxxer.tracking

/**
 * Reads where the body is within a rep for one exercise.
 * Each reader has a gap between its REST and PEAK thresholds, so noise near one
 * threshold can't flip the position back and forth.
 */
fun interface PositionReader {
    fun read(pose: Pose): Position?

    companion object {
        fun forExercise(exercise: Exercise): PositionReader? = when (exercise) {
            Exercise.DEAD_HANG, Exercise.ACTIVE_HANG -> null
            Exercise.PULL_UP, Exercise.CHIN_UP -> PullReader()
            Exercise.HANGING_LEG_RAISE -> LegRaiseReader()
            Exercise.PUSH_UP -> PressReader(restAbove = 150f, peakBelow = 100f)
            Exercise.DIP -> PressReader(restAbove = 150f, peakBelow = 105f)
            Exercise.SQUAT -> SquatReader()
        }
    }
}

/** Smooths a noisy reading. A missing reading resets it. */
class Smoother(private val weight: Float = 0.5f) {
    private var value: Float? = null

    fun update(sample: Float?): Float? {
        val previous = value
        value = when {
            sample == null -> null
            previous == null -> sample
            else -> previous + weight * (sample - previous)
        }
        return value
    }
}

/** Pull-ups and chin-ups: straight arms at rest, chin over the hands at the peak. */
class PullReader : PositionReader {
    private val elbow = Smoother()
    private val lift = Smoother()

    override fun read(pose: Pose): Position? {
        val nose = pose[Joint.NOSE]
        val wrist = pose.wrist
        val shoulder = pose.shoulder
        // Hands dropping below the shoulders means letting go, not a rep.
        if (wrist != null && shoulder != null && wrist.y > shoulder.y) {
            elbow.update(null)
            lift.update(null)
            return null
        }
        val headOverHands = nose != null && wrist != null && nose.y < wrist.y
        val angle = elbow.update(pose.elbowAngle)
        if (angle != null) {
            lift.update(null)
            return when {
                headOverHands || angle < 95f -> Position.PEAK
                angle > 145f -> Position.REST
                else -> null
            }
        }
        // Elbows hidden: fall back to how far the hands are above the shoulders.
        val scale = pose.bodyScale
        val handLift = lift.update(
            if (shoulder != null && wrist != null && scale != null) (shoulder.y - wrist.y) / scale else null,
        )
        return when {
            headOverHands -> Position.PEAK
            handLift == null -> null
            handLift < 0.35f -> Position.PEAK
            handLift > 0.9f -> Position.REST
            else -> null
        }
    }
}

/** Hanging leg raises: legs hanging at rest, knees up to hip height at the peak. */
class LegRaiseReader : PositionReader {
    private val knee = Smoother()
    private val ankle = Smoother()

    override fun read(pose: Pose): Position? {
        val hip = pose.hip ?: return reset()
        val scale = pose.bodyScale ?: return reset()
        val kneeDrop = knee.update(pose.knee?.let { (it.y - hip.y) / scale })
        if (kneeDrop != null) {
            ankle.update(null)
            return when {
                kneeDrop < 0.15f -> Position.PEAK
                kneeDrop > 0.5f -> Position.REST
                else -> null
            }
        }
        val ankleDrop = ankle.update(pose.ankle?.let { (it.y - hip.y) / scale }) ?: return null
        return when {
            ankleDrop < 0.35f -> Position.PEAK
            ankleDrop > 1.0f -> Position.REST
            else -> null
        }
    }

    private fun reset(): Position? {
        knee.update(null)
        ankle.update(null)
        return null
    }
}

/** Push-ups and dips: arms locked out at rest, bent at the peak. */
class PressReader(private val restAbove: Float, private val peakBelow: Float) : PositionReader {
    private val elbow = Smoother()

    override fun read(pose: Pose): Position? {
        val angle = elbow.update(pose.elbowAngle) ?: return null
        return when {
            angle < peakBelow -> Position.PEAK
            angle > restAbove -> Position.REST
            else -> null
        }
    }
}

/**
 * Squats: standing at rest, hips down at the peak.
 * Side on, the knee angle shows depth. Facing the camera the knee barely looks bent,
 * so it also compares thigh height to shin length.
 */
class SquatReader : PositionReader {
    private val knee = Smoother()
    private val thigh = Smoother()

    override fun read(pose: Pose): Position? {
        val angle = knee.update(pose.kneeAngle)
        val hip = pose.hip
        val kneePoint = pose.knee
        val anklePoint = pose.ankle
        val shin = if (kneePoint != null && anklePoint != null) anklePoint.y - kneePoint.y else 0f
        val thighRatio = thigh.update(
            if (hip != null && kneePoint != null && shin > 0f) (kneePoint.y - hip.y) / shin else null,
        )
        if (angle == null && thighRatio == null) return null
        val deep = (angle != null && angle < 110f) || (thighRatio != null && thighRatio < 0.45f)
        val tall = (angle == null || angle > 160f) && (thighRatio == null || thighRatio > 0.75f)
        return when {
            deep -> Position.PEAK
            tall -> Position.REST
            else -> null
        }
    }
}
