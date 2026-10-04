package com.astrovm.gripmaxxer.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
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
import com.astrovm.gripmaxxer.tracking.Motion
import com.astrovm.gripmaxxer.tracking.TrackingEffects
import com.astrovm.gripmaxxer.ui.formatDuration
import com.astrovm.gripmaxxer.ui.formatResult
import com.astrovm.gripmaxxer.ui.theme.accentColor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps the front camera running while a workout is open, even with the app in the
 * background, so you can watch a video while you hang. The counting itself happens in
 * [com.astrovm.gripmaxxer.tracking.WorkoutController].
 *
 * When the proximity sensor stays covered, or the screen is off, the camera goes off
 * and the motion sensors count instead. Turning the screen on outside a pocket restores
 * the camera.
 */
class TrackingService : LifecycleService() {

    private var camera: PoseCamera? = null
    private lateinit var sensors: SensorManager
    private var wakeLock: PowerManager.WakeLock? = null
    private var pocketJob: Job? = null
    private var proximityCovered: Boolean? = null
    private var screenOff = false
    private var screenReceiverRegistered = false
    private var cues: Cues? = null
    private lateinit var timer: FloatingTimer
    private val timerHidden = MutableStateFlow(false)
    private var lastNotice: Pair<String, Boolean>? = null

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
        timer = FloatingTimer(
            this,
            onOpen = ::openApp,
            onFinish = { app.scope.launch { app.controller.finish() } },
            // Hidden until this workout ends, or until "Show timer" in the notification.
            onHide = { timerHidden.value = true },
        )
        app.controller.effects = object : TrackingEffects {
            override fun beep() = cues.beep()
            override fun say(text: String) = cues.say(text)
            override fun playMedia() = media.play()
            override fun pauseMedia() = media.pause()
        }

        sensors = getSystemService(SensorManager::class.java)
        sensors.getDefaultSensor(Sensor.TYPE_PROXIMITY)?.let {
            sensors.registerListener(proximityListener, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        screenReceiverRegistered = true
        screenOff = !getSystemService(PowerManager::class.java).isInteractive
        if (screenOff) app.controller.setInPocket(true)
        lifecycleScope.launch {
            app.controller.live.map { it.inPocket }.distinctUntilChanged().collect { inPocket ->
                if (inPocket) usePocket() else useCamera()
            }
        }

        val inBackground = ProcessLifecycleOwner.get().lifecycle.currentStateFlow
            .map { !it.isAtLeast(Lifecycle.State.STARTED) }
            .distinctUntilChanged()
        val away = combine(inBackground, timerHidden) { background, hidden -> background to hidden }
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
            combine(app.controller.live, app.settings.settings, away, clock, lastSetAt) { live, settings, (background, hidden), now, restFrom ->
                if (settings.overlay && background && live.tracking && !hidden) timer.show() else timer.hide()
                val (label, value) = timerText(live, restFrom.takeUnless { live.inSet }?.let { now - it })
                timer.update(label, value, accentColor(settings.accent).toArgb().takeIf { live.inSet })
                updateNotification(live, showTimerAction = settings.overlay && hidden)
            }.collect {}
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_FINISH) {
            val app = container
            app.scope.launch { app.controller.finish() }
        }
        if (intent?.action == ACTION_SHOW_TIMER) timerHidden.value = false
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        val app = container
        camera?.stop()
        if (screenReceiverRegistered) unregisterReceiver(screenReceiver)
        if (::sensors.isInitialized) {
            sensors.unregisterListener(proximityListener)
            sensors.unregisterListener(motionListener)
        }
        releaseWakeLock()
        if (::timer.isInitialized) timer.hide()
        cues?.release()
        app.controller.effects = null
        app.preview.value = null
        // Stopped by the system rather than by the app: keep what was counted so far.
        app.controller.stopTracking()
        super.onDestroy()
    }

    /**
     * Brings the app back from a tap on the floating timer. Android only lets a background
     * app bring its task forward through a pending intent that says so.
     */
    private fun openApp() {
        val options = ActivityOptions.makeBasic()
        backgroundStartMode(Build.VERSION.SDK_INT)?.let {
            @SuppressLint("NewApi") // Only returned where it exists.
            options.setPendingIntentBackgroundActivityStartMode(it)
        }
        runCatching { openIntent().send(this, 0, null, null, null, null, options.toBundle()) }
    }

    private fun openIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun useCamera() {
        sensors.unregisterListener(motionListener)
        releaseWakeLock()
        val app = container
        if (camera != null || !app.controller.live.value.tracking) return
        camera = PoseCamera(this, wantsImage = { app.previewVisible.value }, onFrame = ::onCameraFrame).also {
            it.start(this) { app.controller.stopTracking(getString(R.string.error_camera_unavailable)) }
        }
    }

    private fun usePocket() {
        camera?.stop()
        camera = null
        container.preview.value = null
        sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensors.registerListener(motionListener, it, SensorManager.SENSOR_DELAY_GAME)
        }
        // The accelerometer stops reporting once the phone sleeps with the screen off.
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "gripmaxxer:pocket")
            .apply { acquire(MAX_POCKET_MS) }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private val proximityListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val covered = event.values[0] < event.sensor.maximumRange
            // Only a change in coverage starts the delay. Repeated readings must not
            // keep postponing it while the phone stays covered (or uncovered).
            if (proximityCovered == covered) return
            proximityCovered = covered
            updatePocketMode()
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            screenOff = intent.action == Intent.ACTION_SCREEN_OFF
            updatePocketMode()
        }
    }

    private fun updatePocketMode() {
        pocketJob?.cancel()
        if (screenOff) {
            // Screen-off workouts use motion even if proximity never reports "near".
            container.controller.setInPocket(true)
        } else {
            val covered = proximityCovered == true
            // A hand passing over the sensor isn't a pocket.
            pocketJob = lifecycleScope.launch {
                delay(if (covered) POCKET_IN_MS else POCKET_OUT_MS)
                container.controller.setInPocket(covered)
            }
        }
    }

    private val motionListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            // Sensor time runs on the boot clock. Line it up with the wall clock sets are saved in.
            val ageMs = (SystemClock.elapsedRealtimeNanos() - event.timestamp) / 1_000_000
            val (x, y, z) = event.values
            onMotion(Motion(x, y, z), System.currentTimeMillis() - ageMs)
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
    }

    internal fun onMotion(motion: Motion, timestampMs: Long) = container.controller.onMotion(motion, timestampMs)

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

    private fun updateNotification(live: LiveState, showTimerAction: Boolean) {
        val exercise = live.exercise ?: return
        val lastSet = live.lastSet
        val status = when {
            !live.personVisible -> getString(R.string.status_step_in)
            live.inSet -> formatResult(exercise, live.reps, live.setDurationMs)
            lastSet != null -> getString(R.string.status_saved, formatResult(lastSet.exercise, lastSet.reps, lastSet.durationMs))
            else -> getString(R.string.status_ready)
        }
        val text = "${exercise.label}: $status"
        if (text to showTimerAction == lastNotice) return
        lastNotice = text to showTimerAction
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text, live, showTimerAction))
    }

    private fun notification(text: String, live: LiveState, showTimerAction: Boolean = false): Notification {
        val open = openIntent()
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
        if (showTimerAction) {
            val show = PendingIntent.getService(
                this,
                2,
                Intent(this, TrackingService::class.java).setAction(ACTION_SHOW_TIMER),
                PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(0, getString(R.string.show_timer), show)
        }
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
        private const val ACTION_SHOW_TIMER = "com.astrovm.gripmaxxer.SHOW_TIMER"
        private const val POCKET_IN_MS = 1_500L
        private const val POCKET_OUT_MS = 500L
        /** A safety net in case the wake lock is somehow never let go. */
        private const val MAX_POCKET_MS = 4 * 60 * 60 * 1000L

        /** How to ask Android to let the app come forward from the background, on [sdk]. */
        @Suppress("DEPRECATION")
        internal fun backgroundStartMode(sdk: Int): Int? = when {
            sdk >= Build.VERSION_CODES.BAKLAVA -> ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
            sdk >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            else -> null
        }

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, TrackingService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TrackingService::class.java))
        }
    }
}
