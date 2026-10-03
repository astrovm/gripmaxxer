package com.astrovm.gripmaxxer.feedback

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowWindowManagerImpl
import org.robolectric.shadows.ShadowSettings

@RunWith(RobolectricTestRunner::class)
class FloatingTimerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val windows: ShadowWindowManagerImpl = Shadow.extract(context.getSystemService(WindowManager::class.java))

    private fun shownView() = windows.views.single() as TextView

    @Test
    fun staysHiddenWithoutPermission() {
        ShadowSettings.setCanDrawOverlays(false)
        val timer = FloatingTimer(context)
        timer.show()
        timer.update("0:10", null)
        assertFalse(timer.isShowing)
        assertTrue(windows.views.isEmpty())
        timer.hide()
    }

    @Test
    fun showsUpdatesAndHides() {
        ShadowSettings.setCanDrawOverlays(true)
        val timer = FloatingTimer(context)
        timer.show()
        timer.show()
        assertTrue(timer.isShowing)

        timer.update("0:42", 0xFFFF6FAE.toInt())
        assertEquals("0:42", shownView().text.toString())
        timer.update("7", null)
        assertEquals("7", shownView().text.toString())

        timer.hide()
        assertFalse(timer.isShowing)
        assertTrue(windows.views.isEmpty())
    }

    @Test
    fun canBeDragged() {
        ShadowSettings.setCanDrawOverlays(true)
        val timer = FloatingTimer(context)
        timer.show()
        val view = shownView()
        val start = (view.layoutParams as WindowManager.LayoutParams).let { it.x to it.y }

        fun touch(action: Int, x: Float, y: Float) {
            val now = SystemClock.uptimeMillis()
            MotionEvent.obtain(now, now, action, x, y, 0).also {
                it.setLocation(x, y)
                view.dispatchTouchEvent(it)
                it.recycle()
            }
        }
        touch(MotionEvent.ACTION_DOWN, 100f, 100f)
        touch(MotionEvent.ACTION_MOVE, 150f, 130f)
        touch(MotionEvent.ACTION_UP, 150f, 130f)

        val params = view.layoutParams as WindowManager.LayoutParams
        assertEquals(start.first + 50, params.x)
        assertEquals(start.second + 30, params.y)
    }
}
