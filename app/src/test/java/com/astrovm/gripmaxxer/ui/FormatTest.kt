package com.astrovm.gripmaxxer.ui

import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {

    @Test
    fun durations() {
        assertEquals("0:00", formatDuration(-5))
        assertEquals("0:07", formatDuration(7_900))
        assertEquals("12:30", formatDuration(750_000))
        assertEquals("1:02:03", formatDuration(3_723_000))
    }

    @Test
    fun reps() {
        assertEquals("1 rep", formatReps(1))
        assertEquals("0 reps", formatReps(0))
        assertEquals("12 reps", formatReps(12))
    }

    @Test
    fun dateTime() {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.US)
        try {
            val ms = LocalDateTime.of(2026, 10, 3, 8, 45).toInstant(ZoneOffset.UTC).toEpochMilli()
            assertEquals("Sat, Oct 3, 8:45 AM", formatDateTime(ms, ZoneOffset.UTC))
        } finally {
            Locale.setDefault(previous)
        }
    }
}
