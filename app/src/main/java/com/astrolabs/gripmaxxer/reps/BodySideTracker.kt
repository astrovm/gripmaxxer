package com.astrolabs.gripmaxxer.reps

import com.astrolabs.gripmaxxer.pose.BodySide
import kotlin.math.abs

/**
 * Keeps unilateral exercise tracking on a stable body side and avoids rapid side flapping.
 */
class BodySideTracker(
    private val switchDelta: Float,
    private val switchStableMs: Long,
) {

    private var selectedSide: BodySide? = null
    private var candidateSide: BodySide? = null
    private var candidateSinceMs: Long = 0L

    fun reset() {
        selectedSide = null
        candidateSide = null
        candidateSinceMs = 0L
    }

    fun selectLower(
        leftValue: Float?,
        rightValue: Float?,
        nowMs: Long,
    ): BodySide? {
        return select(leftValue = leftValue, rightValue = rightValue, nowMs = nowMs, preferLower = true)
    }

    private fun select(
        leftValue: Float?,
        rightValue: Float?,
        nowMs: Long,
        preferLower: Boolean,
    ): BodySide? {
        val proposed = when {
            leftValue == null && rightValue == null -> null
            leftValue != null && rightValue == null -> BodySide.LEFT
            leftValue == null && rightValue != null -> BodySide.RIGHT
            else -> {
                val left = leftValue ?: return selectedSide
                val right = rightValue ?: return selectedSide
                if (abs(left - right) < switchDelta) {
                    selectedSide ?: if (preferLower) {
                        if (left <= right) BodySide.LEFT else BodySide.RIGHT
                    } else {
                        if (left >= right) BodySide.LEFT else BodySide.RIGHT
                    }
                } else if (preferLower) {
                    if (left < right) BodySide.LEFT else BodySide.RIGHT
                } else {
                    if (left > right) BodySide.LEFT else BodySide.RIGHT
                }
            }
        } ?: return selectedSide

        val current = selectedSide
        if (current == null) {
            selectedSide = proposed
            candidateSide = null
            return selectedSide
        }
        if (proposed == current) {
            candidateSide = null
            return selectedSide
        }

        if (candidateSide != proposed) {
            candidateSide = proposed
            candidateSinceMs = nowMs
            return selectedSide
        }

        val stableMs = nowMs - candidateSinceMs
        if (stableMs >= switchStableMs) {
            selectedSide = proposed
            candidateSide = null
        }
        return selectedSide
    }
}
