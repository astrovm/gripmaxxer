package com.example.hangplaycam.overlay

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
    private var isRunning = false
    private var isShowingRep = false
    private var startElapsedMs = 0L

    private val updateRunnable = object : Runnable {
        override fun run() {
            if (isRunning && !isShowingRep) {
                val elapsedSeconds = (SystemClock.elapsedRealtime() - startElapsedMs) / 1000f
                textView?.text = String.format(Locale.US, "%.1f", elapsedSeconds)
                handler.postDelayed(this, UPDATE_INTERVAL_MS)
            }
        }
    }

    fun onHangStateChanged(hanging: Boolean) {
        handler.post {
            if (hanging) {
                if (!isOverlayPermissionGranted(appContext)) return@post
                ensureView()
                showIfNeeded()
                startElapsedMs = SystemClock.elapsedRealtime()
                isRunning = true
                isShowingRep = false
                textView?.text = "0.0"
                handler.removeCallbacks(updateRunnable)
                handler.post(updateRunnable)
            } else {
                isRunning = false
                isShowingRep = false
                handler.removeCallbacks(updateRunnable)
                hideAndReset()
            }
        }
    }

    fun onRepEvent(reps: Int) {
        handler.post {
            if (!isRunning || !isAdded) return@post
            isShowingRep = true
            textView?.text = reps.toString()
            handler.postDelayed({
                isShowingRep = false
                if (isRunning) {
                    handler.removeCallbacks(updateRunnable)
                    handler.post(updateRunnable)
                }
            }, REP_DISPLAY_MS)
        }
    }

    fun currentElapsedMs(): Long {
        if (!isRunning) return 0L
        return SystemClock.elapsedRealtime() - startElapsedMs
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
            text = "0.0"
            textSize = 64f
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
        textView?.text = "0.0"
        if (isAdded) {
            runCatching { windowManager.removeView(textView) }
            isAdded = false
        }
    }

    companion object {
        private const val UPDATE_INTERVAL_MS = 100L
        private const val REP_DISPLAY_MS = 800L

        fun isOverlayPermissionGranted(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else {
                true
            }
        }
    }
}
