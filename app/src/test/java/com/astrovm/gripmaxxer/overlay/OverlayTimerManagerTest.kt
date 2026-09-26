package com.astrovm.gripmaxxer.overlay

import android.content.Context
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowSettings
import org.robolectric.shadows.ShadowWindowManagerImpl
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class OverlayTimerManagerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private fun addedViews(): List<View> = Shadow.extract<ShadowWindowManagerImpl>(windowManager).views

    private fun idle(ms: Long = 0L) {
        if (ms > 0) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
        } else {
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    private fun touch(view: View, action: Int, x: Float, y: Float): Boolean {
        val now = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(now, now, action, x, y, 0)
        return view.dispatchTouchEvent(event).also { event.recycle() }
    }

    @After
    fun tearDown() {
        ShadowSettings.setCanDrawOverlays(false)
    }

    @Test
    fun `nothing is shown without overlay permission`() {
        ShadowSettings.setCanDrawOverlays(false)
        assertFalse(OverlayTimerManager.isOverlayPermissionGranted(context))
        val manager = OverlayTimerManager(context)
        manager.onMonitoringChanged(true)
        idle()
        assertTrue(addedViews().isEmpty())
        manager.onHangStateChanged(true)
        manager.onRepCountChanged(3)
        idle()
        assertTrue(addedViews().isEmpty())
    }

    @Test
    fun `timer shows elapsed time and reps while monitoring`() {
        ShadowSettings.setCanDrawOverlays(true)
        assertTrue(OverlayTimerManager.isOverlayPermissionGranted(context))
        val manager = OverlayTimerManager(context)

        // Ignored before monitoring starts.
        manager.onHangStateChanged(true)
        manager.onRepCountChanged(9)
        idle()
        assertEquals(0L, manager.currentElapsedMs())

        manager.onMonitoringChanged(true)
        idle()
        val view = addedViews().single() as TextView
        assertEquals("0.0s\nReps 0", view.text.toString())

        manager.onHangStateChanged(true)
        idle(1_500L)
        assertTrue(manager.currentElapsedMs() >= 1_500L)
        manager.onRepCountChanged(-2)
        idle(100L)
        assertTrue(view.text.toString().endsWith("Reps 0"))
        manager.onRepCountChanged(3)
        idle(100L)
        assertTrue(view.text.toString().endsWith("Reps 3"))

        manager.onHangStateChanged(false)
        idle(100L)
        val frozen = manager.currentElapsedMs()
        idle(1_000L)
        assertEquals(frozen, manager.currentElapsedMs())

        // Monitoring again keeps the same view.
        manager.onMonitoringChanged(true)
        idle(100L)
        assertEquals(1, addedViews().size)

        manager.onMonitoringChanged(false)
        idle()
        assertTrue(addedViews().isEmpty())
        assertEquals("0.0s\nReps 0", view.text.toString())
        assertEquals(0L, manager.currentElapsedMs())
        // Not added anymore, so rep updates are ignored.
        manager.onRepCountChanged(4)
        idle()
        assertEquals("0.0s\nReps 0", view.text.toString())
        // Touches without layout params are ignored.
        touch(view, MotionEvent.ACTION_DOWN, 1f, 1f)

        manager.release()
        idle()
    }

    @Test
    fun `dragging moves the overlay and remembers the position`() {
        ShadowSettings.setCanDrawOverlays(true)
        val manager = OverlayTimerManager(context)
        manager.onMonitoringChanged(true)
        idle()
        val view = addedViews().single()
        val params = view.layoutParams as WindowManager.LayoutParams
        assertEquals(24, params.x)
        assertEquals(180, params.y)

        assertTrue(touch(view, MotionEvent.ACTION_DOWN, 100f, 100f))
        // Tiny movement below the touch slop does not drag.
        assertTrue(touch(view, MotionEvent.ACTION_MOVE, 101f, 101f))
        assertTrue(touch(view, MotionEvent.ACTION_UP, 101f, 101f))
        val prefs = context.getSharedPreferences("gripmaxxer_overlay", Context.MODE_PRIVATE)
        assertFalse(prefs.contains("overlay_x"))

        assertTrue(touch(view, MotionEvent.ACTION_DOWN, 100f, 100f))
        assertTrue(touch(view, MotionEvent.ACTION_MOVE, 150f, 130f))
        assertTrue(touch(view, MotionEvent.ACTION_CANCEL, 150f, 130f))
        assertEquals(74, prefs.getInt("overlay_x", -1))
        assertEquals(210, prefs.getInt("overlay_y", -1))

        touch(view, MotionEvent.ACTION_HOVER_MOVE, 0f, 0f)

        manager.release()
        idle()
        assertTrue(addedViews().isEmpty())

        // A new manager restores the saved position.
        val restored = OverlayTimerManager(context)
        restored.onMonitoringChanged(true)
        idle()
        val restoredParams = addedViews().single().layoutParams as WindowManager.LayoutParams
        assertEquals(74, restoredParams.x)
        assertEquals(210, restoredParams.y)
        restored.release()
        idle()
    }
}
