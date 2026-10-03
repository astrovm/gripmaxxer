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
import android.widget.LinearLayout
import android.widget.TextView

/**
 * A small timer that floats over other apps, so you can see your set while watching
 * something: a short label on top, the count or time below. Drag it anywhere.
 * Call everything on the main thread.
 */
class FloatingTimer(private val context: Context) {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val label = TextView(context).apply {
        setTextColor(0xB3FFFFFF.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        gravity = Gravity.CENTER
        maxLines = 1
    }
    private val value = TextView(context).apply {
        setTextColor(0xFFFFFFFF.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        gravity = Gravity.CENTER
    }
    private val border = GradientDrawable().apply {
        cornerRadius = dp(20).toFloat()
        setColor(0xE6000000.toInt())
        setStroke(dp(2), IDLE_STROKE)
    }
    private val view = createView()
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

    var isShowing: Boolean = false
        private set

    fun show() {
        if (isShowing || !canShow(context)) return
        isShowing = runCatching { windowManager.addView(view, params) }.isSuccess
    }

    fun update(label: String, value: String, highlight: Int?) {
        if (!isShowing) return
        this.label.text = label
        this.value.text = value
        border.setStroke(dp(2), highlight ?: IDLE_STROKE)
    }

    fun hide() {
        if (!isShowing) return
        isShowing = false
        runCatching { windowManager.removeView(view) }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createView() = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        minimumWidth = dp(88)
        setPadding(dp(16), dp(6), dp(16), dp(8))
        background = border
        addView(label)
        addView(value)
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
