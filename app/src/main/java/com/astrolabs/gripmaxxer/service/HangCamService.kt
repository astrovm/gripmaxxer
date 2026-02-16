package com.astrolabs.gripmaxxer.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.astrolabs.gripmaxxer.MainActivity
import com.astrolabs.gripmaxxer.R
import com.astrolabs.gripmaxxer.camera.FrontCameraManager
import com.astrolabs.gripmaxxer.camera.PoseFrameAnalyzer
import com.astrolabs.gripmaxxer.datastore.AppSettings
import com.astrolabs.gripmaxxer.datastore.SettingsRepository
import com.astrolabs.gripmaxxer.datastore.WorkoutSession
import com.astrolabs.gripmaxxer.hang.HangDetectionConfig
import com.astrolabs.gripmaxxer.media.MediaControlManager
import com.astrolabs.gripmaxxer.overlay.OverlayTimerManager
import com.astrolabs.gripmaxxer.pose.PoseDetectorWrapper
import com.astrolabs.gripmaxxer.pose.PoseFeatureExtractor
import com.astrolabs.gripmaxxer.pose.PoseFrame
import com.astrolabs.gripmaxxer.reps.BenchPressActivityDetector
import com.astrolabs.gripmaxxer.reps.BenchPressRepDetector
import com.astrolabs.gripmaxxer.reps.DipActivityDetector
import com.astrolabs.gripmaxxer.reps.DipRepDetector
import com.astrolabs.gripmaxxer.reps.ExerciseMode
import com.astrolabs.gripmaxxer.reps.PullUpActivityDetector
import com.astrolabs.gripmaxxer.reps.PullUpRepDetector
import com.astrolabs.gripmaxxer.reps.PushUpActivityDetector
import com.astrolabs.gripmaxxer.reps.PushUpRepDetector
import com.astrolabs.gripmaxxer.reps.RepCounterConfig
import com.astrolabs.gripmaxxer.reps.RepEngine
import com.astrolabs.gripmaxxer.reps.SquatActivityDetector
import com.astrolabs.gripmaxxer.reps.SquatRepDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class HangCamService : LifecycleService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val frameMutex = Mutex()

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var mediaControlManager: MediaControlManager

    private var settingsJob: Job? = null
    private var tickerJob: Job? = null
    private var mediaStatusJob: Job? = null

    private var frontCameraManager: FrontCameraManager? = null
    private var poseDetectorWrapper: PoseDetectorWrapper? = null
    private var frameAnalyzer: PoseFrameAnalyzer? = null
    private var overlayTimerManager: OverlayTimerManager? = null

    private val featureExtractor = PoseFeatureExtractor()
    private val pullUpActivityDetector = PullUpActivityDetector()
    private val pushUpActivityDetector = PushUpActivityDetector(featureExtractor)
    private val squatActivityDetector = SquatActivityDetector(featureExtractor)
    private val benchPressActivityDetector = BenchPressActivityDetector(featureExtractor)
    private val dipActivityDetector = DipActivityDetector(featureExtractor)
    private val pullUpRepDetector = PullUpRepDetector(featureExtractor)
    private val pushUpRepDetector = PushUpRepDetector(featureExtractor)
    private val squatRepDetector = SquatRepDetector(featureExtractor)
    private val benchPressRepDetector = BenchPressRepDetector(featureExtractor)
    private val dipRepDetector = DipRepDetector(featureExtractor)
    private val repEngine = RepEngine(
        detectors = mapOf(
            ExerciseMode.PULL_UP to pullUpRepDetector,
            ExerciseMode.PUSH_UP to pushUpRepDetector,
            ExerciseMode.SQUAT to squatRepDetector,
            ExerciseMode.BENCH_PRESS to benchPressRepDetector,
            ExerciseMode.DIP to dipRepDetector,
        ),
        initialMode = ExerciseMode.PULL_UP,
    )
    private val activityDetectors = mapOf(
        ExerciseMode.PULL_UP to pullUpActivityDetector,
        ExerciseMode.PUSH_UP to pushUpActivityDetector,
        ExerciseMode.SQUAT to squatActivityDetector,
        ExerciseMode.BENCH_PRESS to benchPressActivityDetector,
        ExerciseMode.DIP to dipActivityDetector,
    )

    @Volatile
    private var running = false
    private var currentMode = ExerciseMode.PULL_UP
    private var currentSettings = AppSettings()
    private var mediaControlEnabled = true
    private var currentHangState = false
    private var currentReps = 0
    private var currentSessionElapsedMs = 0L
    private var hangStartRealtimeMs = 0L
    private var latestFps = 0
    private val rawFramesSinceTick = AtomicInteger(0)
    private val lastFrameRealtimeMs = AtomicLong(0L)
    private var lastPosePresent = false

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(applicationContext)
        mediaControlManager = MediaControlManager(applicationContext)
        overlayTimerManager = OverlayTimerManager(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Required so LifecycleService can dispatch STARTED state to its LifecycleOwner.
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_START -> {
                startForegroundNow("Preparing camera pipeline")
                if (!running) {
                    startMonitoring()
                }
            }

            ACTION_STOP -> {
                stopMonitoring()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return Service.START_NOT_STICKY
    }

    override fun onDestroy() {
        stopMonitoring()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startMonitoring() {
        running = true
        currentHangState = false
        currentReps = 0
        currentMode = currentSettings.selectedExerciseMode
        mediaControlEnabled = currentSettings.mediaControlEnabled
        hangStartRealtimeMs = 0L
        currentSessionElapsedMs = 0L
        latestFps = 0
        rawFramesSinceTick.set(0)
        lastFrameRealtimeMs.set(0L)
        lastPosePresent = false
        activityDetectors.values.forEach { it.reset() }
        repEngine.setMode(currentMode, resetCurrent = true)
        updateOverlayVisibility()

        MonitoringStateStore.update {
            it.copy(
                serviceRunning = true,
                hanging = false,
                reps = 0,
                elapsedHangMs = 0L,
                cameraFps = 0,
                posePresent = false,
                lastFrameAgeMs = Long.MAX_VALUE,
                mode = currentMode,
            )
        }

        settingsJob?.cancel()
        settingsJob = serviceScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                currentSettings = settings
                pullUpActivityDetector.updateConfig(
                    HangDetectionConfig(
                        wristShoulderMargin = settings.wristShoulderMargin.coerceAtLeast(MIN_WRIST_SHOULDER_MARGIN),
                        missingPoseTimeoutMs = settings.missingPoseTimeoutMs,
                    )
                )
                pullUpRepDetector.updateConfig(
                    RepCounterConfig(
                        elbowUpAngle = settings.elbowUpAngle,
                        elbowDownAngle = settings.elbowDownAngle,
                        marginUp = settings.marginUp,
                        marginDown = settings.marginDown,
                        stableMs = settings.stableMs,
                        minRepIntervalMs = settings.minRepIntervalMs,
                    )
                )
                poseDetectorWrapper?.setAccurateMode(settings.poseModeAccurate)
                frameMutex.withLock {
                    val previousMode = currentMode
                    currentMode = settings.selectedExerciseMode
                    mediaControlEnabled = settings.mediaControlEnabled
                    repEngine.setMode(currentMode, resetCurrent = previousMode != currentMode)
                    if (previousMode != currentMode) {
                        handleModeChangeLocked()
                    }
                }
                updateOverlayVisibility()
            }
        }

        mediaControlManager.start()
        mediaStatusJob?.cancel()
        mediaStatusJob = serviceScope.launch {
            mediaControlManager.status.collect { status ->
                MonitoringStateStore.update {
                    it.copy(
                        hasMediaController = status.hasController,
                        mediaControllerPackage = status.controllerPackage,
                    )
                }
            }
        }

        serviceScope.launch {
            if (!hasCameraPermission()) {
                running = false
                MonitoringStateStore.update { it.copy(serviceRunning = false) }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return@launch
            }

            currentSettings = settingsRepository.settingsFlow.first()
            updateOverlayVisibility()
            poseDetectorWrapper = PoseDetectorWrapper(currentSettings.poseModeAccurate)

            val analyzer = PoseFrameAnalyzer(
                detectorWrapper = poseDetectorWrapper ?: return@launch,
                featureExtractor = featureExtractor,
                minFrameIntervalMs = 66L,
                onFrameTick = {
                    rawFramesSinceTick.incrementAndGet()
                    lastFrameRealtimeMs.set(SystemClock.elapsedRealtime())
                },
                shouldEmitDebugFrame = { DebugPreviewStore.enabled.value },
                onDebugFrame = { bitmap, poseFrame ->
                    DebugPreviewStore.publish(
                        DebugPreviewFrame(
                            bitmap = bitmap,
                            landmarks = poseFrame.landmarks,
                            timestampMs = poseFrame.timestampMs,
                        )
                    )
                },
            ) { frame ->
                serviceScope.launch {
                    processPoseFrame(frame)
                }
            }
            frameAnalyzer = analyzer

            val cameraManager = FrontCameraManager(applicationContext, this@HangCamService)
            frontCameraManager = cameraManager
            try {
                cameraManager.start(analyzer)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start camera monitoring", e)
                stopMonitoring()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return@launch
            }

            startTicker()
        }
    }

    private fun stopMonitoring() {
        if (!running) return

        running = false
        currentHangState = false
        currentReps = 0
        currentMode = currentSettings.selectedExerciseMode
        hangStartRealtimeMs = 0L
        currentSessionElapsedMs = 0L
        latestFps = 0
        rawFramesSinceTick.set(0)
        lastFrameRealtimeMs.set(0L)
        lastPosePresent = false
        activityDetectors.values.forEach { it.reset() }
        repEngine.resetCurrent()

        settingsJob?.cancel()
        settingsJob = null

        tickerJob?.cancel()
        tickerJob = null

        mediaStatusJob?.cancel()
        mediaStatusJob = null

        frameAnalyzer?.stop()
        frameAnalyzer = null

        frontCameraManager?.stop()
        frontCameraManager = null

        serviceScope.launch {
            poseDetectorWrapper?.close()
            poseDetectorWrapper = null
        }

        mediaControlManager.stop()

        overlayTimerManager?.onMonitoringChanged(false)
        overlayTimerManager?.release()
        overlayTimerManager = OverlayTimerManager(applicationContext)

        MonitoringStateStore.reset()
        DebugPreviewStore.clear()
    }

    private suspend fun processPoseFrame(frame: PoseFrame) {
        frameMutex.withLock {
            if (!running) return
            val nowMs = System.currentTimeMillis()
            lastPosePresent = frame.posePresent
            val activityDetector = activityDetectors[currentMode] ?: pullUpActivityDetector
            val nextActive = activityDetector.process(frame = frame, nowMs = nowMs)

            if (nextActive != currentHangState) {
                if (nextActive) {
                    currentHangState = true
                    currentReps = 0
                    currentSessionElapsedMs = 0L
                    repEngine.resetCurrent()
                    hangStartRealtimeMs = SystemClock.elapsedRealtime()
                    overlayTimerManager?.onHangStateChanged(true)
                    if (mediaControlEnabled) {
                        mediaControlManager.play()
                    }
                } else {
                    currentHangState = false
                    if (hangStartRealtimeMs > 0L) {
                        currentSessionElapsedMs = SystemClock.elapsedRealtime() - hangStartRealtimeMs
                    }
                    recordSessionIfMeaningful(nowMs)
                    hangStartRealtimeMs = 0L
                    overlayTimerManager?.onHangStateChanged(false)
                    if (mediaControlEnabled) {
                        mediaControlManager.pause()
                    }
                }
            }

            val repResult = repEngine.process(
                frame = frame,
                active = currentHangState,
                nowMs = nowMs,
            )
            currentReps = repResult.reps

            overlayTimerManager?.onRepCountChanged(currentReps)

            publishStateAndNotification()
        }
    }

    private suspend fun handleModeChangeLocked() {
        if (currentHangState && hangStartRealtimeMs > 0L) {
            currentSessionElapsedMs = SystemClock.elapsedRealtime() - hangStartRealtimeMs
        }
        recordSessionIfMeaningful(System.currentTimeMillis())
        activityDetectors.values.forEach { it.reset() }
        repEngine.resetCurrent()
        currentHangState = false
        currentReps = 0
        currentSessionElapsedMs = 0L
        hangStartRealtimeMs = 0L
        overlayTimerManager?.onHangStateChanged(false)
        overlayTimerManager?.onRepCountChanged(0)
        if (mediaControlEnabled) {
            mediaControlManager.pause()
        }
        publishStateAndNotification()
    }

    private suspend fun recordSessionIfMeaningful(completedAtMs: Long) {
        val hasAnySignal = currentReps > 0 || currentSessionElapsedMs >= MIN_SESSION_MS
        if (!hasAnySignal) return
        settingsRepository.recordWorkoutSession(
            WorkoutSession(
                completedAtMs = completedAtMs,
                mode = currentMode,
                reps = currentReps,
                activeMs = currentSessionElapsedMs,
            )
        )
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            while (isActive && running) {
                latestFps = rawFramesSinceTick.getAndSet(0)
                publishStateAndNotification()
                delay(1000L)
            }
        }
    }

    private fun publishStateAndNotification() {
        val elapsedMs = if (currentHangState && hangStartRealtimeMs > 0L) {
            currentSessionElapsedMs = SystemClock.elapsedRealtime() - hangStartRealtimeMs
            currentSessionElapsedMs
        } else {
            currentSessionElapsedMs
        }
        val lastFrameMs = lastFrameRealtimeMs.get()
        val frameAgeMs = if (lastFrameMs > 0L) {
            SystemClock.elapsedRealtime() - lastFrameMs
        } else {
            Long.MAX_VALUE
        }

        MonitoringStateStore.update {
            it.copy(
                serviceRunning = running,
                hanging = currentHangState,
                reps = currentReps,
                elapsedHangMs = elapsedMs,
                cameraFps = latestFps,
                posePresent = lastPosePresent,
                lastFrameAgeMs = frameAgeMs,
                mode = currentMode,
            )
        }

        NotificationManagerCompat.from(this).notify(
            NOTIFICATION_ID,
            buildNotification(
                text = "${if (currentHangState) "ACTIVE" else "IDLE"} | Reps: $currentReps | ${formatSeconds(elapsedMs)}s | Cam:${latestFps}fps ${if (lastPosePresent) "pose" else "no-pose"} | ${currentMode.label}"
            )
        )
    }

    private fun updateOverlayVisibility() {
        val shouldShowOverlay = running &&
            OverlayTimerManager.isOverlayPermissionGranted(applicationContext)
        overlayTimerManager?.onMonitoringChanged(shouldShowOverlay)
    }

    private fun startForegroundNow(text: String) {
        val notification = buildNotification(text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(text: String): android.app.Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val stopIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, HangCamService::class.java).apply {
                action = ACTION_STOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .addAction(0, getString(R.string.notification_stop), stopIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun formatSeconds(ms: Long): String {
        return String.format(java.util.Locale.US, "%.1f", ms / 1000f)
    }

    companion object {
        private const val TAG = "HangCamService"
        const val ACTION_START = "com.astrolabs.gripmaxxer.action.START"
        const val ACTION_STOP = "com.astrolabs.gripmaxxer.action.STOP"
        private const val MIN_WRIST_SHOULDER_MARGIN = 0.08f
        private const val MIN_SESSION_MS = 400L

        private const val NOTIFICATION_ID = 1001
        private const val NOTIFICATION_CHANNEL_ID = "hang_monitoring"

        fun start(context: Context) {
            val intent = Intent(context, HangCamService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, HangCamService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
