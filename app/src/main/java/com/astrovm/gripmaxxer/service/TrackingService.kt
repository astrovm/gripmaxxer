package com.astrovm.gripmaxxer.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.astrovm.gripmaxxer.MainActivity
import com.astrovm.gripmaxxer.R
import com.astrovm.gripmaxxer.camera.CameraFrame
import com.astrovm.gripmaxxer.camera.PoseCamera
import com.astrovm.gripmaxxer.container
import com.astrovm.gripmaxxer.feedback.Cues
import com.astrovm.gripmaxxer.feedback.FloatingTimer
import com.astrovm.gripmaxxer.media.MediaRemote
import com.astrovm.gripmaxxer.tracking.LiveState
import com.astrovm.gripmaxxer.tracking.TrackingEffects
import com.astrovm.gripmaxxer.ui.formatDuration
import com.astrovm.gripmaxxer.ui.formatReps
import com.astrovm.gripmaxxer.ui.theme.accentColor
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps the front camera running while a workout is open, even with the app in the
 * background, so you can watch a video while you hang. The counting itself happens in
 * [com.astrovm.gripmaxxer.tracking.WorkoutController].
 */
class TrackingService : LifecycleService() {

    private var camera: PoseCamera? = null
    private var cues: Cues? = null
    private lateinit var timer: FloatingTimer
    private var lastNotice: String? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification(getString(R.string.tracking_starting)),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA,
        )
        val app = container
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            app.controller.stopTracking(getString(R.string.error_camera_permission))
            stopSelf()
            return
        }

        val media = MediaRemote(this)
        val cues = Cues(this).also { cues = it }
        timer = FloatingTimer(this)
        app.controller.effects = object : TrackingEffects {
            override fun beep() = cues.beep()
            override fun say(text: String) = cues.say(text)
            override fun playMedia() = media.play()
            override fun pauseMedia() = media.pause()
        }

        camera = PoseCamera(this, wantsImage = { app.previewVisible.value }, onFrame = ::onCameraFrame).also {
            it.start(this) { app.controller.stopTracking(getString(R.string.error_camera_unavailable)) }
        }

        val inBackground = ProcessLifecycleOwner.get().lifecycle.currentStateFlow
            .map { !it.isAtLeast(Lifecycle.State.STARTED) }
            .distinctUntilChanged()
        lifecycleScope.launch {
            combine(app.controller.live, app.settings.settings, inBackground) { live, settings, background ->
                Triple(live, settings, background)
            }.collect { (live, settings, background) ->
                if (settings.overlay && background && live.tracking) timer.show() else timer.hide()
                if (timer.isShowing) {
                    timer.update(liveValue(live), accentColor(settings.accent).toArgb().takeIf { live.inSet })
                }
                updateNotification(live)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_FINISH) {
            val app = container
            app.scope.launch { app.controller.finish() }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        val app = container
        camera?.stop()
        if (::timer.isInitialized) timer.hide()
        cues?.release()
        app.controller.effects = null
        app.preview.value = null
        // Stopped by the system rather than by the app: keep what was counted so far.
        app.controller.stopTracking()
        super.onDestroy()
    }

    internal fun onCameraFrame(frame: CameraFrame) {
        val app = container
        app.controller.onFrame(frame.pose, frame.timestampMs)
        if (frame.image != null) app.preview.value = frame
    }

    private fun liveValue(live: LiveState): String {
        val exercise = live.exercise ?: return ""
        return if (exercise.isHold) formatDuration(live.setDurationMs) else live.reps.toString()
    }

    private fun updateNotification(live: LiveState) {
        val exercise = live.exercise ?: return
        val status = when {
            !live.personVisible -> getString(R.string.status_step_in)
            !live.inSet -> getString(R.string.status_ready)
            exercise.isHold -> formatDuration(live.setDurationMs)
            else -> formatReps(live.reps)
        }
        val text = "${exercise.label}: $status"
        if (text == lastNotice) return
        lastNotice = text
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text))
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val finish = PendingIntent.getService(
            this,
            1,
            Intent(this, TrackingService::class.java).setAction(ACTION_FINISH),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .addAction(0, getString(R.string.finish_workout), finish)
            .build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { setShowBadge(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "tracking"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_FINISH = "com.astrovm.gripmaxxer.FINISH"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, TrackingService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TrackingService::class.java))
        }
    }
}
