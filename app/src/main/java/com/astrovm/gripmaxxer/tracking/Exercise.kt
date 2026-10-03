package com.astrovm.gripmaxxer.tracking

/**
 * Exercises the camera can track. Names are stored in the database, so never rename them.
 */
enum class Exercise(val label: String, val isHold: Boolean, val onBar: Boolean) {
    DEAD_HANG("Dead hang", isHold = true, onBar = true),
    ACTIVE_HANG("Active hang", isHold = true, onBar = true),
    PULL_UP("Pull-up", isHold = false, onBar = true),
    CHIN_UP("Chin-up", isHold = false, onBar = true),
    HANGING_LEG_RAISE("Hanging leg raise", isHold = false, onBar = true),
    PUSH_UP("Push-up", isHold = false, onBar = false),
    SQUAT("Squat", isHold = false, onBar = false),
    DIP("Dip", isHold = false, onBar = false);

    companion object {
        fun fromName(name: String?): Exercise? = entries.firstOrNull { it.name == name }
    }
}
