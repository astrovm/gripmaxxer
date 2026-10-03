package com.astrovm.gripmaxxer.feedback

import android.content.Context
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.ImageView
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Duration
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowWindowManagerImpl
import org.robolectric.shadows.ShadowSettings

@RunWith(RobolectricTestRunner::class)
class FloatingTimerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val windows: ShadowWindowManagerImpl = Shadow.extract(context.getSystemService(WindowManager::class.java))

    private val log = mutableListOf<String>()

    private fun timer() = FloatingTimer(context, onOpen = { log += "open" }, onFinish = { log += "finish" }, onHide = { log += "hide" })

    private fun shownView() = windows.views.single() as LinearLayout
    private fun face() = shownView().getChildAt(0) as LinearLayout
    private fun actions() = shownView().getChildAt(1) as LinearLayout
    private fun params() = shownView().layoutParams as WindowManager.LayoutParams

    /** The label and the value, like "Rest" and "1:12". */
    private fun shownText() = (0 until face().childCount).map { (face().getChildAt(it) as TextView).text.toString() }

    private fun action(description: String) =
        (0 until actions().childCount).map { actions().getChildAt(it) as ImageView }.single { it.contentDescription == description }

    private fun touch(action: Int, x: Float, y: Float) {
        val now = SystemClock.uptimeMillis()
        MotionEvent.obtain(now, now, action, x, y, 0).also {
            it.setLocation(x, y)
            face().dispatchTouchEvent(it)
            it.recycle()
        }
    }

    private fun tap() {
        touch(MotionEvent.ACTION_DOWN, 100f, 100f)
        touch(MotionEvent.ACTION_UP, 100f, 100f)
    }

    private fun drag(dx: Float, dy: Float) {
        touch(MotionEvent.ACTION_DOWN, 100f, 100f)
        touch(MotionEvent.ACTION_MOVE, 100f + dx / 2, 100f + dy / 2)
        touch(MotionEvent.ACTION_MOVE, 100f + dx, 100f + dy)
        touch(MotionEvent.ACTION_UP, 100f + dx, 100f + dy)
        // Let it slide to the side.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
    }

    /** Lays the timer out at [width] pixels, like the window manager would. */
    private fun layOut(width: Int) = shownView().layout(0, 0, width, 200)

    private val margin get() = (12 * context.resources.displayMetrics.density).toInt()
    private val screenWidth get() = context.resources.displayMetrics.widthPixels

    @Test
    fun staysHiddenWithoutPermission() {
        ShadowSettings.setCanDrawOverlays(false)
        val timer = timer()
        timer.show()
        timer.update("Dead hang", "0:10", null)
        assertFalse(timer.isShowing)
        assertTrue(windows.views.isEmpty())
        timer.hide()
    }

    @Test
    fun showsUpdatesAndHides() {
        ShadowSettings.setCanDrawOverlays(true)
        val timer = timer()
        timer.show()
        timer.show()
        assertTrue(timer.isShowing)

        timer.update("Dead hang", "0:42", 0xFFFF6FAE.toInt())
        assertEquals(listOf("Dead hang", "0:42"), shownText())
        timer.update("Rest", "0:07", null)
        assertEquals(listOf("Rest", "0:07"), shownText())

        timer.hide()
        timer.hide()
        assertFalse(timer.isShowing)
        assertTrue(windows.views.isEmpty())

        // Comes back after being hidden.
        timer.show()
        assertTrue(timer.isShowing)
        timer.hide()
    }

    @Test
    fun tapShowsActionsThatCloseOnTheirOwn() {
        ShadowSettings.setCanDrawOverlays(true)
        val timer = timer()
        timer.show()
        assertFalse(timer.isExpanded)
        tap()
        assertTrue(timer.isExpanded)
        assertEquals(1f, shownView().alpha)
        tap()
        assertFalse(timer.isExpanded)

        tap()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
        assertFalse(timer.isExpanded)
        timer.hide()
    }

    @Test
    fun actionsOpenFinishAndHide() {
        ShadowSettings.setCanDrawOverlays(true)
        val timer = timer()
        timer.show()
        tap()
        action("Open Gripmaxxer").performClick()
        assertFalse(timer.isExpanded)
        tap()
        action("Finish workout").performClick()
        tap()
        action("Hide").performClick()
        assertEquals(listOf("open", "finish", "hide"), log)
        assertFalse(timer.isShowing)
        assertTrue(windows.views.isEmpty())
    }

    @Test
    fun hidingClosesTheActions() {
        ShadowSettings.setCanDrawOverlays(true)
        val timer = timer()
        timer.show()
        tap()
        timer.hide()
        timer.show()
        assertFalse(timer.isExpanded)
        assertEquals(View.GONE, actions().visibility)
        timer.hide()
    }

    @Test
    fun dragSettlesOnTheNearestSideAndIsRemembered() {
        ShadowSettings.setCanDrawOverlays(true)
        val timer = timer()
        timer.show()
        layOut(40)
        drag(screenWidth.toFloat(), 300f)
        val rightX = params().x
        assertEquals(screenWidth - 40 - margin, rightX)
        val y = params().y
        timer.hide()

        val next = timer()
        next.show()
        assertEquals(rightX, params().x)
        assertEquals(y, params().y)

        layOut(40)
        drag(-screenWidth.toFloat(), -10_000f)
        assertEquals(margin, params().x)
        assertEquals(0, params().y)
        next.hide()
    }

    @Test
    fun touchLetGoElsewhereRestoresIt() {
        ShadowSettings.setCanDrawOverlays(true)
        val timer = timer()
        timer.show()
        touch(MotionEvent.ACTION_DOWN, 100f, 100f)
        assertEquals(0.7f, shownView().alpha)
        touch(MotionEvent.ACTION_CANCEL, 100f, 100f)
        assertEquals(1f, shownView().alpha)
        assertFalse(timer.isExpanded)
        timer.hide()
    }

    @Test
    fun onTheRightSideTheActionsGrowToTheLeft() {
        ShadowSettings.setCanDrawOverlays(true)
        val timer = timer()
        timer.show()
        layOut(40)
        drag(screenWidth.toFloat(), 0f)
        val x = params().x
        layOut(shownView().width + 30)
        assertEquals(x - 30, params().x)

        timer.hide()
    }
}
