package com.astrovm.gripmaxxer.service

import android.graphics.Bitmap
import com.astrovm.gripmaxxer.reps.ExerciseMode
import com.astrovm.gripmaxxer.testutil.lm
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StoresTest {

    @Test
    fun `monitoring store updates and resets`() {
        MonitoringStateStore.reset()
        MonitoringStateStore.update { it.copy(serviceRunning = true, reps = 4, mode = ExerciseMode.DIP) }
        val snapshot = MonitoringStateStore.snapshot.value
        assertTrue(snapshot.serviceRunning)
        assertEquals(4, snapshot.reps)
        assertEquals(ExerciseMode.DIP, snapshot.mode)
        MonitoringStateStore.reset()
        assertEquals(MonitoringSnapshot(), MonitoringStateStore.snapshot.value)
    }

    @Test
    fun `auto set events are broadcast with increasing ids`() = runBlocking {
        val first = async { AutoSetEventStore.events.first() }
        yield()
        AutoSetEventStore.emit(mode = ExerciseMode.PULL_UP, reps = 7, activeMs = 9_000L, timestampMs = 42L)
        val event = first.await()
        assertEquals(ExerciseMode.PULL_UP, event.mode)
        assertEquals(7, event.reps)
        assertEquals(9_000L, event.activeMs)
        assertEquals(42L, event.timestampMs)

        val second = async { AutoSetEventStore.events.first() }
        yield()
        AutoSetEventStore.emit(mode = ExerciseMode.DIP, reps = 1, activeMs = 1L, timestampMs = 43L)
        assertTrue(second.await().eventId > event.eventId)
    }

    @Test
    fun `debug preview store publishes and clears frames`() {
        val frame = DebugPreviewFrame(
            bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888),
            landmarks = mapOf(0 to lm(0.1f, 0.2f)),
            timestampMs = 5L,
        )
        DebugPreviewStore.publish(frame)
        assertEquals(frame, DebugPreviewStore.frame.value)
        DebugPreviewStore.clear()
        assertNull(DebugPreviewStore.frame.value)
    }
}
