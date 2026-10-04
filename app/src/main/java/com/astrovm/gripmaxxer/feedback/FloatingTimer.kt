package com.astrovm.gripmaxxer.feedback

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.edit
import com.astrovm.gripmaxxer.R
import kotlin.math.hypot

/**
 * A small timer that floats over other apps, so you can see your set while watching
 * something: a short label on top, the count or time below.
 *
 * Drag it anywhere and it settles against the nearest side, where it stays next time.
 * Tap it for a few actions: open the app, finish the workout, or hide it.
 * Call everything on the main thread.
 */
class FloatingTimer(
    private val context: Context,
    private val onOpen: () -> Unit,
    private val onFinish: () -> Unit,
    private val onHide: () -> Unit,
) {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
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
        setColor(0xFF000000.toInt())
        setStroke(dp(2), IDLE_STROKE)
    }
    private val actions: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        visibility = View.GONE
        setPadding(0, dp(6), 0, 0)
        addView(action(R.drawable.ic_open_app, "Open Gripmaxxer") { onOpen() })
        addView(action(R.drawable.ic_finish_workout, "Finish workout") { onFinish() })
        addView(action(R.drawable.ic_hide_timer, "Hide") {
            hide()
            onHide()
        })
    }
    private val face: LinearLayout = createFace()
    private val view: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        minimumWidth = dp(88)
        setPadding(dp(16), dp(6), dp(16), dp(8))
        background = border
        addView(face)
        addView(actions)
        // Opening or closing the actions changes the width. On the right side, keep the right edge put.
        addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            val grew = (right - left) - (oldRight - oldLeft)
            if (grew != 0 && oldRight > oldLeft && onRightSide(oldRight - oldLeft)) {
                params.x -= grew
                relayout()
            }
        }
    }
    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = prefs.getInt(KEY_X, dp(12))
        y = prefs.getInt(KEY_Y, dp(64))
    }
    private val collapse = Runnable { actions.visibility = View.GONE }

    var isShowing: Boolean = false
        private set

    /** The actions are open. */
    val isExpanded: Boolean get() = actions.visibility == View.VISIBLE

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
        view.removeCallbacks(collapse)
        actions.visibility = View.GONE
        runCatching { windowManager.removeView(view) }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createFace(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        addView(label)
        addView(value)
        val slop = ViewConfiguration.get(context).scaledTouchSlop
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var dragging = false
        setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    dragging = false
                    view.alpha = PRESSED_ALPHA
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchX
                    val dy = event.rawY - touchY
                    if (!dragging && hypot(dx, dy) > slop) dragging = true
                    if (dragging) {
                        params.x = startX + dx.toInt()
                        params.y = startY + dy.toInt()
                        relayout()
                    }
                }

                MotionEvent.ACTION_UP -> {
                    view.alpha = 1f
                    if (dragging) settle() else toggleActions()
                }

                MotionEvent.ACTION_CANCEL -> view.alpha = 1f
            }
            true
        }
    }

    private fun action(@DrawableRes icon: Int, description: String, onClick: () -> Unit): ImageView = ImageView(context).apply {
        setImageResource(icon)
        contentDescription = description
        val size = dp(40)
        layoutParams = LinearLayout.LayoutParams(size, size).apply { marginStart = dp(2); marginEnd = dp(2) }
        setPadding(dp(8), dp(8), dp(8), dp(8))
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0x33FFFFFF)
        }
        setOnClickListener {
            actions.visibility = View.GONE
            onClick()
        }
    }

    private fun toggleActions() {
        view.removeCallbacks(collapse)
        if (isExpanded) {
            actions.visibility = View.GONE
        } else {
            actions.visibility = View.VISIBLE
            view.postDelayed(collapse, COLLAPSE_MS)
        }
    }

    /** Slides to the nearest side, kept on screen, and remembers the spot. */
    private fun settle() {
        val screen = context.resources.displayMetrics
        val margin = dp(12)
        val targetX = if (onRightSide(view.width)) screen.widthPixels - view.width - margin else margin
        params.y = params.y.coerceIn(0, (screen.heightPixels - view.height).coerceAtLeast(0))
        prefs.edit { putInt(KEY_X, targetX).putInt(KEY_Y, params.y) }
        ValueAnimator.ofInt(params.x, targetX).apply {
            duration = SETTLE_MS
            addUpdateListener {
                params.x = it.animatedValue as Int
                relayout()
            }
            start()
        }
    }

    private fun onRightSide(width: Int) = params.x + width / 2 > context.resources.displayMetrics.widthPixels / 2

    private fun relayout() {
        if (isShowing) runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()

    companion object {
        private const val IDLE_STROKE = 0x66FFFFFF
        private const val PRESSED_ALPHA = 0.7f
        private const val COLLAPSE_MS = 5_000L
        private const val SETTLE_MS = 150L
        private const val PREFS = "floating_timer"
        private const val KEY_X = "x"
        private const val KEY_Y = "y"

        fun canShow(context: Context): Boolean = Settings.canDrawOverlays(context)
    }
}
