package com.astrovm.gripmaxxer.feedback

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView

/**
 * A small timer that floats over other apps, so you can see your set while watching
 * something. Drag it anywhere. Call everything on the main thread.
 */
class FloatingTimer(private val context: Context) {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var view: TextView? = null
    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = 32
        y = 160
    }

    val isShowing: Boolean get() = view != null

    fun show() {
        if (view != null || !canShow(context)) return
        val text = createView()
        runCatching { windowManager.addView(text, params) }.onSuccess { view = text }
    }

    fun update(text: String, highlight: Int?) {
        val current = view ?: return
        current.text = text
        (current.background as GradientDrawable).setStroke(dp(2), highlight ?: IDLE_STROKE)
    }

    fun hide() {
        val current = view ?: return
        view = null
        runCatching { windowManager.removeView(current) }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createView() = TextView(context).apply {
        setTextColor(0xFFFFFFFF.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        gravity = Gravity.CENTER
        minWidth = dp(88)
        setPadding(dp(16), dp(8), dp(16), dp(8))
        background = GradientDrawable().apply {
            cornerRadius = dp(20).toFloat()
            setColor(0xE6000000.toInt())
            setStroke(dp(2), IDLE_STROKE)
        }
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                }

                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (event.rawX - touchX).toInt()
                    params.y = startY + (event.rawY - touchY).toInt()
                    windowManager.updateViewLayout(this, params)
                }
            }
            true
        }
    }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()

    companion object {
        private const val IDLE_STROKE = 0x66FFFFFF

        fun canShow(context: Context): Boolean = Settings.canDrawOverlays(context)
    }
}
