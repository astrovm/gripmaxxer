package com.astrovm.gripmaxxer.reps

import com.astrovm.gripmaxxer.pose.PoseFrame

class RepEngine(
    private val detectors: Map<ExerciseMode, ModeRepDetector>,
    initialMode: ExerciseMode = ExerciseMode.PULL_UP,
) {

    private var selectedMode: ExerciseMode = initialMode

    fun setMode(mode: ExerciseMode, resetCurrent: Boolean) {
        if (selectedMode == mode) {
            if (resetCurrent) {
                detectors[selectedMode]?.reset()
            }
            return
        }
        selectedMode = mode
        if (resetCurrent) {
            detectors[selectedMode]?.reset()
        }
    }

    fun resetCurrent() {
        detectors[selectedMode]?.reset()
    }

    fun process(
        frame: PoseFrame,
        active: Boolean,
        nowMs: Long,
    ): RepCounterResult {
        val detector = detectors[selectedMode] ?: return RepCounterResult(reps = 0, repEvent = false)
        return detector.process(frame = frame, active = active, nowMs = nowMs)
    }
}
