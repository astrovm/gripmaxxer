package com.astrolabs.hangmaxxer.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import java.util.Locale

class OverlayTimerManager(context: Context) {

    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private var textView: TextView? = null
    private var isAdded = false
    private var isMonitoring = false
    private var isRunning = false
    private var sessionReps = 0
    private var frozenElapsedMs = 0L
    private var startElapsedMs = 0L

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
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER
        }
        windowManager.addView(view, params)
        isAdded = true
    }

    private fun hideAndReset() {
        textView?.text = formatOverlayText(elapsedMs = 0L, reps = 0)
        if (isAdded) {
            runCatching { windowManager.removeView(textView) }
            isAdded = false
        }
    }

    private fun formatOverlayText(elapsedMs: Long, reps: Int): String {
        val elapsedSeconds = (elapsedMs / 1000f).coerceAtLeast(0f)
        return String.format(Locale.US, "%.1fs\nReps %d", elapsedSeconds, reps)
    }

    companion object {
        private const val UPDATE_INTERVAL_MS = 100L

        fun isOverlayPermissionGranted(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else {
                true
            }
        }
    }
}
