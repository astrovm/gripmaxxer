package com.astrovm.gripmaxxer.tracking

/**
 * Tells whether someone is hanging from a bar: hands clearly above the shoulders.
 *
 * Hands often leave the frame at the top of a pull-up, so missing wrists don't end the
 * hang straight away. Only hands dropping below the shoulders, or losing the grip
 * signal for a while, does.
 */
class BarDetector {

    var onBar: Boolean = false
        private set

    /** When the current hang started. Null while off the bar. */
    var hangStartedAtMs: Long? = null
        private set

    /** Last frame that clearly showed hands on the bar. */
    var lastGripAtMs: Long = 0L
        private set

    private var candidateSinceMs: Long? = null

    fun update(pose: Pose?, nowMs: Long): Boolean {
        val lift = pose?.let(::handLift)
        if (onBar) {
            if (lift != null && lift.highest >= DROP_LIFT) lastGripAtMs = nowMs
            val dropped = lift != null && lift.highest < DROP_LIFT
            candidateSinceMs = if (dropped) candidateSinceMs ?: nowMs else null
            val droppedLongEnough = dropped && nowMs - candidateSinceMs!! >= DROP_MS
            if (droppedLongEnough || nowMs - lastGripAtMs >= LOST_MS) {
                onBar = false
                hangStartedAtMs = null
                candidateSinceMs = null
            }
        } else {
            val gripping = lift != null && lift.lowest >= GRIP_LIFT
            candidateSinceMs = if (gripping) candidateSinceMs ?: nowMs else null
            if (gripping && nowMs - candidateSinceMs!! >= GRIP_MS) {
                onBar = true
                hangStartedAtMs = candidateSinceMs
                lastGripAtMs = nowMs
                candidateSinceMs = null
            }
        }
        return onBar
    }

    /** How far each visible hand is above the shoulders, in torso lengths. */
    private fun handLift(pose: Pose): HandLift? {
        val shoulder = pose.shoulder ?: return null
        val scale = pose.bodyScale ?: return null
        val lifts = listOfNotNull(pose[Joint.LEFT_WRIST], pose[Joint.RIGHT_WRIST])
            .map { (shoulder.y - it.y) / scale }
        if (lifts.isEmpty()) return null
        return HandLift(lowest = lifts.min(), highest = lifts.max())
    }

    private data class HandLift(val lowest: Float, val highest: Float)

    private companion object {
        // Both hands at least half a torso above the shoulders: that's a hang, not a stretch.
        const val GRIP_LIFT = 0.5f
        // Both hands below shoulder height: off the bar.
        const val DROP_LIFT = -0.1f
        const val GRIP_MS = 300L
        const val DROP_MS = 400L
        const val LOST_MS = 2500L
    }
}
