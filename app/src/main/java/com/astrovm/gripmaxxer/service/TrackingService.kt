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
import com.astrovm.gripmaxxer.ui.formatResult
import com.astrovm.gripmaxxer.ui.theme.accentColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
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
            notification(getString(R.string.tracking_starting), LiveState()),
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
        // Ticks so the rest time on the floating timer keeps counting between frames.
        val clock = flow {
            while (true) {
                emit(System.currentTimeMillis())
                delay(1_000)
            }
        }
        // Rest counts from the latest set, typed in or counted, like on the live screen.
        val lastSetAt = app.workouts.active.map { it?.sets?.lastOrNull()?.completedAtMs }.distinctUntilChanged()
        lifecycleScope.launch {
            combine(app.controller.live, app.settings.settings, inBackground, clock, lastSetAt) { live, settings, background, now, restFrom ->
                if (settings.overlay && background && live.tracking) timer.show() else timer.hide()
                val (label, value) = timerText(live, restFrom.takeUnless { live.inSet }?.let { now - it })
                timer.update(label, value, accentColor(settings.accent).toArgb().takeIf { live.inSet })
                updateNotification(live)
            }.collect {}
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

    /** Label and value for the floating timer: how long you've rested, or the set in progress. */
    private fun timerText(live: LiveState, restMs: Long?): Pair<String, String> =
        if (restMs != null) getString(R.string.rest) to formatDuration(restMs) else (live.exercise?.label ?: "") to liveValue(live)

    private fun updateNotification(live: LiveState) {
        val exercise = live.exercise ?: return
        val lastSet = live.lastSet
        val status = when {
            !live.personVisible -> getString(R.string.status_step_in)
            live.inSet -> formatResult(exercise, live.reps, live.setDurationMs)
            lastSet != null -> getString(R.string.status_saved, formatResult(lastSet.exercise, lastSet.reps, lastSet.durationMs))
            else -> getString(R.string.status_ready)
        }
        val text = "${exercise.label}: $status"
        if (text == lastNotice) return
        lastNotice = text
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text, live))
    }

    private fun notification(text: String, live: LiveState): Notification {
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
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            // A live update: pinned at the top, with a chip in the status bar on Android 16 and up.
            .setRequestPromotedOngoing(true)
            .addAction(0, getString(R.string.finish_workout), finish)
        // The workout clock ticks on its own, so the notification doesn't need an update every second.
        live.workoutStartedAtMs?.let { builder.setWhen(it).setShowWhen(true).setUsesChronometer(true) }
        if (live.inSet) builder.setShortCriticalText(liveValue(live))
        return builder.build()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        // Default importance keeps it out of the collapsed "Silent" section. It still makes no sound.
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
        manager.deleteNotificationChannel(OLD_CHANNEL_ID)
    }

    companion object {
        private const val CHANNEL_ID = "workout"
        /** Was silent, so the notification got tucked away. */
        private const val OLD_CHANNEL_ID = "tracking"
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
