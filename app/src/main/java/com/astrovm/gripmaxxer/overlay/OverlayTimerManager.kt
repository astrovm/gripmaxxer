package com.astrovm.gripmaxxer.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.TextView
import java.util.Locale
import kotlin.math.abs

class OverlayTimerManager(context: Context) {

    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val touchSlop = ViewConfiguration.get(appContext).scaledTouchSlop

    private var textView: TextView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var isAdded = false
    private var isMonitoring = false
    private var isRunning = false
    private var sessionReps = 0
    private var frozenElapsedMs = 0L
    private var startElapsedMs = 0L
    private var dragStartRawX = 0f
    private var dragStartRawY = 0f
    private var dragStartWindowX = 0
    private var dragStartWindowY = 0
    private var dragging = false

    private val updateRunnable = object : Runnable {
        override fun run() {
            if (isMonitoring && isAdded) {
                val elapsedMs = if (isRunning) {
                    SystemClock.elapsedRealtime() - startElapsedMs
                } else {
                    frozenElapsedMs
                }
                textView?.text = formatOverlayText(elapsedMs = elapsedMs, reps = sessionReps)
                handler.postDelayed(this, UPDATE_INTERVAL_MS)
            }
        }
    }

    fun onMonitoringChanged(monitoring: Boolean) {
        handler.post {
            isMonitoring = monitoring
            if (monitoring) {
                if (!isOverlayPermissionGranted(appContext)) return@post
                ensureView()
                showIfNeeded()
                handler.removeCallbacks(updateRunnable)
                handler.post(updateRunnable)
            } else {
                isRunning = false
                sessionReps = 0
                frozenElapsedMs = 0L
                startElapsedMs = 0L
                handler.removeCallbacks(updateRunnable)
                hideAndReset()
            }
        }
    }

    fun onHangStateChanged(hanging: Boolean) {
        handler.post {
            if (!isMonitoring) return@post
            if (hanging && !isRunning) {
                startElapsedMs = SystemClock.elapsedRealtime()
                frozenElapsedMs = 0L
                sessionReps = 0
                isRunning = true
            } else if (!hanging && isRunning) {
                frozenElapsedMs = SystemClock.elapsedRealtime() - startElapsedMs
                isRunning = false
            }
            handler.removeCallbacks(updateRunnable)
            handler.post(updateRunnable)
        }
    }

    fun onRepCountChanged(reps: Int) {
        handler.post {
            if (!isMonitoring || !isAdded) return@post
            sessionReps = reps.coerceAtLeast(0)
            handler.removeCallbacks(updateRunnable)
            handler.post(updateRunnable)
        }
    }

    fun currentElapsedMs(): Long {
        if (isRunning) {
            return SystemClock.elapsedRealtime() - startElapsedMs
        }
        return frozenElapsedMs
    }

    fun release() {
        handler.post {
            isRunning = false
            handler.removeCallbacksAndMessages(null)
            if (isAdded) {
                runCatching { windowManager.removeView(textView) }
                isAdded = false
            }
            layoutParams = null
            textView = null
        }
    }

    private fun ensureView() {
        if (textView != null) return
        textView = TextView(appContext).apply {
            text = formatOverlayText(elapsedMs = 0L, reps = 0)
            textSize = 40f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            gravity = Gravity.CENTER
            setShadowLayer(8f, 0f, 0f, Color.BLACK)
            setOnTouchListener { _, event -> onOverlayTouch(event) }
        }
    }

    private fun showIfNeeded() {
        val view = textView ?: return
        if (isAdded) return
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt(KEY_OVERLAY_X, DEFAULT_OVERLAY_X)
            y = prefs.getInt(KEY_OVERLAY_Y, DEFAULT_OVERLAY_Y)
        }
        layoutParams = params
        windowManager.addView(view, params)
        isAdded = true
    }

    private fun hideAndReset() {
        textView?.text = formatOverlayText(elapsedMs = 0L, reps = 0)
        if (isAdded) {
            runCatching { windowManager.removeView(textView) }
            isAdded = false
        }
        layoutParams = null
    }

    private fun onOverlayTouch(event: MotionEvent): Boolean {
        val view = textView ?: return false
        val params = layoutParams ?: return false

        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragStartRawX = event.rawX
                dragStartRawY = event.rawY
                dragStartWindowX = params.x
                dragStartWindowY = params.y
                dragging = false
                true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - dragStartRawX).toInt()
                val dy = (event.rawY - dragStartRawY).toInt()
                if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                    dragging = true
                }
                if (dragging) {
                    val maxX = (screenWidthPx() - view.width).coerceAtLeast(0)
                    val maxY = (screenHeightPx() - view.height).coerceAtLeast(0)
                    params.x = (dragStartWindowX + dx).coerceIn(0, maxX)
                    params.y = (dragStartWindowY + dy).coerceIn(0, maxY)
                    runCatching { windowManager.updateViewLayout(view, params) }
                }
                true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    prefs.edit()
                        .putInt(KEY_OVERLAY_X, params.x)
                        .putInt(KEY_OVERLAY_Y, params.y)
                        .apply()
                }
                dragging = false
                true
            }

            else -> false
        }
    }

    private fun screenWidthPx(): Int = appContext.resources.displayMetrics.widthPixels

    private fun screenHeightPx(): Int = appContext.resources.displayMetrics.heightPixels

    private fun formatOverlayText(elapsedMs: Long, reps: Int): String {
        val elapsedSeconds = (elapsedMs / 1000f).coerceAtLeast(0f)
        return String.format(Locale.US, "%.1fs\nReps %d", elapsedSeconds, reps)
    }

    companion object {
        private const val UPDATE_INTERVAL_MS = 100L
        private const val PREFS_NAME = "gripmaxxer_overlay"
        private const val KEY_OVERLAY_X = "overlay_x"
        private const val KEY_OVERLAY_Y = "overlay_y"
        private const val DEFAULT_OVERLAY_X = 24
        private const val DEFAULT_OVERLAY_Y = 180

        fun isOverlayPermissionGranted(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else {
                true
            }
        }
    }
}
