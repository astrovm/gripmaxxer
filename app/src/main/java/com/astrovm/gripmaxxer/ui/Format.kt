package com.astrovm.gripmaxxer.ui

import com.astrovm.gripmaxxer.tracking.Exercise
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** "0:07", "12:30", "1:02:03". */
fun formatDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val hours = total / 3600
    val minutes = total % 3600 / 60
    val seconds = total % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

/** "5 reps", "1 rep". */
fun formatReps(reps: Int): String = if (reps == 1) "1 rep" else "$reps reps"

/** What a set came to: "8 reps" for rep exercises, "0:42" for holds. */
fun formatResult(exercise: Exercise, reps: Int, durationMs: Long): String =
    if (exercise.isHold) formatDuration(durationMs) else formatReps(reps)

/** "Sat, Oct 3 · 8:45 AM" style, in the phone's language and time zone. */
fun formatDateTime(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val time = Instant.ofEpochMilli(epochMs).atZone(zone)
    val day = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()).format(time)
    val clock = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault()).format(time)
    return "$day, $clock"
}
