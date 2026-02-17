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
import android.media.AudioManager
import android.media.ToneGenerator
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
import com.astrolabs.gripmaxxer.reps.ArcherSquatActivityDetector
import com.astrolabs.gripmaxxer.reps.ArcherSquatRepDetector
import com.astrolabs.gripmaxxer.reps.DipActivityDetector
import com.astrolabs.gripmaxxer.reps.DipRepDetector
import com.astrolabs.gripmaxxer.reps.ExerciseMode
import com.astrolabs.gripmaxxer.reps.ActiveHangActivityDetector
import com.astrolabs.gripmaxxer.reps.DeadHangActivityDetector
import com.astrolabs.gripmaxxer.reps.HoldRepDetector
import com.astrolabs.gripmaxxer.reps.HandstandHoldActivityDetector
import com.astrolabs.gripmaxxer.reps.HangingLegRaiseActivityDetector
import com.astrolabs.gripmaxxer.reps.HangingLegRaiseRepDetector
import com.astrolabs.gripmaxxer.reps.HipThrustActivityDetector
import com.astrolabs.gripmaxxer.reps.HipThrustRepDetector
import com.astrolabs.gripmaxxer.reps.LungeActivityDetector
import com.astrolabs.gripmaxxer.reps.LungeRepDetector
import com.astrolabs.gripmaxxer.reps.BulgarianSplitSquatActivityDetector
import com.astrolabs.gripmaxxer.reps.BulgarianSplitSquatRepDetector
import com.astrolabs.gripmaxxer.reps.MuscleUpActivityDetector
import com.astrolabs.gripmaxxer.reps.MuscleUpRepDetector
import com.astrolabs.gripmaxxer.reps.MiddleSplitHoldActivityDetector
import com.astrolabs.gripmaxxer.reps.PistolSquatActivityDetector
import com.astrolabs.gripmaxxer.reps.PistolSquatRepDetector
import com.astrolabs.gripmaxxer.reps.PikePushUpActivityDetector
import com.astrolabs.gripmaxxer.reps.PikePushUpRepDetector
import com.astrolabs.gripmaxxer.reps.PlankHoldActivityDetector
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
    private val deadHangActivityDetector = DeadHangActivityDetector(featureExtractor)
    private val activeHangActivityDetector = ActiveHangActivityDetector(featureExtractor)
    private val handstandHoldActivityDetector = HandstandHoldActivityDetector(featureExtractor)
    private val plankHoldActivityDetector = PlankHoldActivityDetector(featureExtractor)
    private val middleSplitHoldActivityDetector = MiddleSplitHoldActivityDetector(featureExtractor)
    private val hangingLegRaiseActivityDetector = HangingLegRaiseActivityDetector()
    private val muscleUpActivityDetector = MuscleUpActivityDetector(featureExtractor)
    private val pushUpActivityDetector = PushUpActivityDetector(featureExtractor)
    private val pikePushUpActivityDetector = PikePushUpActivityDetector(featureExtractor)
    private val squatActivityDetector = SquatActivityDetector(featureExtractor)
    private val archerSquatActivityDetector = ArcherSquatActivityDetector(featureExtractor)
    private val pistolSquatActivityDetector = PistolSquatActivityDetector(featureExtractor)
    private val lungeActivityDetector = LungeActivityDetector(featureExtractor)
    private val bulgarianSplitSquatActivityDetector = BulgarianSplitSquatActivityDetector(featureExtractor)
    private val hipThrustActivityDetector = HipThrustActivityDetector(featureExtractor)
    private val benchPressActivityDetector = BenchPressActivityDetector(featureExtractor)
    private val dipActivityDetector = DipActivityDetector(featureExtractor)
    private val pullUpRepDetector = PullUpRepDetector(featureExtractor)
    private val muscleUpRepDetector = MuscleUpRepDetector(featureExtractor)
    private val pushUpRepDetector = PushUpRepDetector(featureExtractor)
    private val pikePushUpRepDetector = PikePushUpRepDetector(featureExtractor)
    private val squatRepDetector = SquatRepDetector(featureExtractor)
    private val archerSquatRepDetector = ArcherSquatRepDetector(featureExtractor)
    private val pistolSquatRepDetector = PistolSquatRepDetector(featureExtractor)
    private val lungeRepDetector = LungeRepDetector(featureExtractor)
    private val bulgarianSplitSquatRepDetector = BulgarianSplitSquatRepDetector(featureExtractor)
    private val hipThrustRepDetector = HipThrustRepDetector(featureExtractor)
    private val benchPressRepDetector = BenchPressRepDetector(featureExtractor)
    private val dipRepDetector = DipRepDetector(featureExtractor)
    private val deadHangRepDetector = HoldRepDetector()
    private val activeHangRepDetector = HoldRepDetector()
    private val handstandHoldRepDetector = HoldRepDetector()
    private val plankHoldRepDetector = HoldRepDetector()
    private val middleSplitHoldRepDetector = HoldRepDetector()
    private val hangingLegRaiseRepDetector = HangingLegRaiseRepDetector()
    private val repEngine = RepEngine(
        detectors = mapOf(
            ExerciseMode.PULL_UP to pullUpRepDetector,
            ExerciseMode.CHIN_UP to pullUpRepDetector,
            ExerciseMode.MUSCLE_UP to muscleUpRepDetector,
            ExerciseMode.ONE_ARM_PULL_UP to pullUpRepDetector,
            ExerciseMode.ONE_ARM_CHIN_UP to pullUpRepDetector,
            ExerciseMode.HANGING_LEG_RAISE to hangingLegRaiseRepDetector,
            ExerciseMode.DEAD_HANG to deadHangRepDetector,
            ExerciseMode.ACTIVE_HANG to activeHangRepDetector,
            ExerciseMode.ONE_ARM_DEAD_HANG to deadHangRepDetector,
            ExerciseMode.ONE_ARM_ACTIVE_HANG to activeHangRepDetector,
            ExerciseMode.HANDSTAND_HOLD to handstandHoldRepDetector,
            ExerciseMode.PLANK_HOLD to plankHoldRepDetector,
            ExerciseMode.MIDDLE_SPLIT_HOLD to middleSplitHoldRepDetector,
            ExerciseMode.PUSH_UP to pushUpRepDetector,
            ExerciseMode.PIKE_PUSH_UP to pikePushUpRepDetector,
            ExerciseMode.ONE_ARM_PUSH_UP to pushUpRepDetector,
            ExerciseMode.SQUAT to squatRepDetector,
            ExerciseMode.ARCHER_SQUAT to archerSquatRepDetector,
            ExerciseMode.PISTOL_SQUAT to pistolSquatRepDetector,
            ExerciseMode.LUNGE to lungeRepDetector,
            ExerciseMode.BULGARIAN_SPLIT_SQUAT to bulgarianSplitSquatRepDetector,
            ExerciseMode.HIP_THRUST to hipThrustRepDetector,
            ExerciseMode.BENCH_PRESS to benchPressRepDetector,
            ExerciseMode.DIP to dipRepDetector,
        ),
        initialMode = ExerciseMode.PULL_UP,
    )
    private val activityDetectors = mapOf(
        ExerciseMode.PULL_UP to pullUpActivityDetector,
        ExerciseMode.CHIN_UP to pullUpActivityDetector,
        ExerciseMode.MUSCLE_UP to muscleUpActivityDetector,
        ExerciseMode.ONE_ARM_PULL_UP to pullUpActivityDetector,
        ExerciseMode.ONE_ARM_CHIN_UP to pullUpActivityDetector,
        ExerciseMode.HANGING_LEG_RAISE to hangingLegRaiseActivityDetector,
        ExerciseMode.DEAD_HANG to deadHangActivityDetector,
        ExerciseMode.ACTIVE_HANG to activeHangActivityDetector,
        ExerciseMode.ONE_ARM_DEAD_HANG to deadHangActivityDetector,
        ExerciseMode.ONE_ARM_ACTIVE_HANG to activeHangActivityDetector,
        ExerciseMode.HANDSTAND_HOLD to handstandHoldActivityDetector,
        ExerciseMode.PLANK_HOLD to plankHoldActivityDetector,
        ExerciseMode.MIDDLE_SPLIT_HOLD to middleSplitHoldActivityDetector,
        ExerciseMode.PUSH_UP to pushUpActivityDetector,
        ExerciseMode.PIKE_PUSH_UP to pikePushUpActivityDetector,
        ExerciseMode.ONE_ARM_PUSH_UP to pushUpActivityDetector,
        ExerciseMode.SQUAT to squatActivityDetector,
        ExerciseMode.ARCHER_SQUAT to archerSquatActivityDetector,
        ExerciseMode.PISTOL_SQUAT to pistolSquatActivityDetector,
        ExerciseMode.LUNGE to lungeActivityDetector,
        ExerciseMode.BULGARIAN_SPLIT_SQUAT to bulgarianSplitSquatActivityDetector,
        ExerciseMode.HIP_THRUST to hipThrustActivityDetector,
        ExerciseMode.BENCH_PRESS to benchPressActivityDetector,
        ExerciseMode.DIP to dipActivityDetector,
    )

    @Volatile
    private var running = false
    private var currentMode = ExerciseMode.PULL_UP
    private var currentSettings = AppSettings()
    private var mediaControlEnabled = true
    private var repSoundEnabled = true
    private var currentHangState = false
    private var currentReps = 0
    private var currentSessionElapsedMs = 0L
    private var hangStartRealtimeMs = 0L
    private var latestFps = 0
    private val rawFramesSinceTick = AtomicInteger(0)
    private val lastFrameRealtimeMs = AtomicLong(0L)
    private var lastPosePresent = false
    private var repToneGenerator: ToneGenerator? = null

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(applicationContext)
        mediaControlManager = MediaControlManager(applicationContext)
        overlayTimerManager = OverlayTimerManager(applicationContext)
        repToneGenerator = runCatching {
            ToneGenerator(AudioManager.STREAM_MUSIC, REP_TONE_VOLUME)
        }.getOrNull()
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
        repToneGenerator?.release()
        repToneGenerator = null
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startMonitoring() {
        running = true
        currentHangState = false
        currentReps = 0
        currentMode = currentSettings.selectedExerciseMode
        mediaControlEnabled = currentSettings.mediaControlEnabled
        repSoundEnabled = currentSettings.repSoundEnabled
        hangStartRealtimeMs = 0L
        currentSessionElapsedMs = 0L
        latestFps = 0
        rawFramesSinceTick.set(0)
        lastFrameRealtimeMs.set(0L)
        lastPosePresent = false
        activityDetectors.values.forEach { it.reset() }
        applyModeCalibratedThresholds(settings = currentSettings, mode = currentMode)
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
                poseDetectorWrapper?.setAccurateMode(settings.poseModeAccurate)
                frameAnalyzer?.updateMinFrameIntervalMs(analyzerIntervalForSettings(settings))
                frameMutex.withLock {
                    val previousMode = currentMode
                    currentMode = settings.selectedExerciseMode
                    mediaControlEnabled = settings.mediaControlEnabled
                    repSoundEnabled = settings.repSoundEnabled
                    applyModeCalibratedThresholds(settings = settings, mode = currentMode)
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
                minFrameIntervalMs = analyzerIntervalForSettings(currentSettings),
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
            if (repResult.repEvent && repSoundEnabled) {
                repToneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, REP_TONE_DURATION_MS)
            }

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

    private fun applyModeCalibratedThresholds(
        settings: AppSettings,
        mode: ExerciseMode,
    ) {
        val baseMargin = settings.wristShoulderMargin.coerceAtLeast(MIN_WRIST_SHOULDER_MARGIN)
        val oneArmPullMode = mode == ExerciseMode.ONE_ARM_PULL_UP || mode == ExerciseMode.ONE_ARM_CHIN_UP
        val oneArmDeadHangMode = mode == ExerciseMode.ONE_ARM_DEAD_HANG
        val oneArmActiveHangMode = mode == ExerciseMode.ONE_ARM_ACTIVE_HANG

        val pullMargin = if (oneArmPullMode) {
            (baseMargin - ONE_ARM_HANG_MARGIN_RELAX).coerceAtLeast(MIN_ONE_ARM_WRIST_SHOULDER_MARGIN)
        } else {
            baseMargin
        }
        pullUpActivityDetector.updateConfig(
            HangDetectionConfig(
                wristShoulderMargin = pullMargin,
                wristShoulderReleaseMargin = (pullMargin - WRIST_RELEASE_GAP).coerceAtLeast(MIN_WRIST_RELEASE_MARGIN),
                missingPoseTimeoutMs = settings.missingPoseTimeoutMs,
                partialPoseHoldMs = if (oneArmPullMode) 3600L else 3000L,
                shoulderOnlyHoldMs = if (oneArmPullMode) 3900L else 3500L,
                occlusionHoldMs = if (oneArmPullMode) 3900L else 3500L,
                stableSwitchMs = if (oneArmPullMode) 600L else 500L,
                minToggleIntervalMs = if (oneArmPullMode) 1700L else 1500L,
            )
        )

        val deadHangMargin = if (oneArmDeadHangMode) {
            (baseMargin - ONE_ARM_HANG_MARGIN_RELAX).coerceAtLeast(MIN_ONE_ARM_WRIST_SHOULDER_MARGIN)
        } else {
            baseMargin
        }
        deadHangActivityDetector.updateConfig(
            HangDetectionConfig(
                wristShoulderMargin = deadHangMargin,
                wristShoulderReleaseMargin = (deadHangMargin - WRIST_RELEASE_GAP).coerceAtLeast(MIN_WRIST_RELEASE_MARGIN),
                missingPoseTimeoutMs = settings.missingPoseTimeoutMs,
                partialPoseHoldMs = if (oneArmDeadHangMode) 3600L else 3000L,
                shoulderOnlyHoldMs = if (oneArmDeadHangMode) 3900L else 3500L,
                occlusionHoldMs = if (oneArmDeadHangMode) 3900L else 3500L,
                stableSwitchMs = if (oneArmDeadHangMode) 600L else 500L,
                minToggleIntervalMs = if (oneArmDeadHangMode) 1700L else 1500L,
            )
        )

        val activeHangMargin = if (oneArmActiveHangMode) {
            (baseMargin - ONE_ARM_HANG_MARGIN_RELAX).coerceAtLeast(MIN_ONE_ARM_WRIST_SHOULDER_MARGIN)
        } else {
            baseMargin
        }
        activeHangActivityDetector.updateConfig(
            HangDetectionConfig(
                wristShoulderMargin = activeHangMargin,
                wristShoulderReleaseMargin = (activeHangMargin - WRIST_RELEASE_GAP).coerceAtLeast(MIN_WRIST_RELEASE_MARGIN),
                missingPoseTimeoutMs = settings.missingPoseTimeoutMs,
                partialPoseHoldMs = if (oneArmActiveHangMode) 3600L else 3000L,
                shoulderOnlyHoldMs = if (oneArmActiveHangMode) 3900L else 3500L,
                occlusionHoldMs = if (oneArmActiveHangMode) 3900L else 3500L,
                stableSwitchMs = if (oneArmActiveHangMode) 600L else 500L,
                minToggleIntervalMs = if (oneArmActiveHangMode) 1700L else 1500L,
            )
        )

        hangingLegRaiseActivityDetector.updateConfig(
            HangDetectionConfig(
                wristShoulderMargin = (baseMargin - HLR_MARGIN_RELAX).coerceAtLeast(MIN_HLR_WRIST_SHOULDER_MARGIN),
                wristShoulderReleaseMargin = (baseMargin - HLR_MARGIN_RELAX - WRIST_RELEASE_GAP)
                    .coerceAtLeast(MIN_WRIST_RELEASE_MARGIN),
                missingPoseTimeoutMs = settings.missingPoseTimeoutMs,
                partialPoseHoldMs = 3200L,
                shoulderOnlyHoldMs = 3600L,
                occlusionHoldMs = 3600L,
            )
        )

        muscleUpActivityDetector.updateConfig(
            HangDetectionConfig(
                wristShoulderMargin = (baseMargin - MUSCLE_UP_MARGIN_RELAX).coerceAtLeast(MIN_MUSCLE_UP_WRIST_SHOULDER_MARGIN),
                wristShoulderReleaseMargin = (baseMargin - MUSCLE_UP_MARGIN_RELAX - WRIST_RELEASE_GAP)
                    .coerceAtLeast(MIN_WRIST_RELEASE_MARGIN),
                missingPoseTimeoutMs = settings.missingPoseTimeoutMs,
                partialPoseHoldMs = 3200L,
                shoulderOnlyHoldMs = 3600L,
                occlusionHoldMs = 3600L,
                stableSwitchMs = 520L,
                minToggleIntervalMs = 1600L,
            )
        )

        pullUpRepDetector.updateConfig(buildPullRepConfig(settings = settings, mode = mode))
    }

    private fun buildPullRepConfig(
        settings: AppSettings,
        mode: ExerciseMode,
    ): RepCounterConfig {
        val base = RepCounterConfig(
            elbowUpAngle = settings.elbowUpAngle,
            elbowDownAngle = settings.elbowDownAngle,
            marginUp = settings.marginUp,
            marginDown = settings.marginDown,
            stableMs = settings.stableMs,
            minRepIntervalMs = settings.minRepIntervalMs,
        )

        return when (mode) {
            ExerciseMode.PULL_UP -> base.copy(
                stableMs = maxOf(base.stableMs, 220L),
                minRepIntervalMs = maxOf(base.minRepIntervalMs, 560L),
                requireBothWristsForGripUp = true,
            )

            ExerciseMode.CHIN_UP -> base.copy(
                elbowUpAngle = (base.elbowUpAngle + 3f).coerceIn(90f, 145f),
                elbowDownAngle = (base.elbowDownAngle - 2f).coerceIn(130f, 175f),
                marginUp = (base.marginUp - 0.004f).coerceIn(0.015f, 0.10f),
                stableMs = maxOf(base.stableMs, 210L),
                minRepIntervalMs = maxOf(base.minRepIntervalMs, 540L),
                requireBothWristsForGripUp = true,
            )

            ExerciseMode.ONE_ARM_PULL_UP -> base.copy(
                elbowUpAngle = (base.elbowUpAngle + 10f).coerceIn(95f, 150f),
                elbowDownAngle = (base.elbowDownAngle - 8f).coerceIn(125f, 175f),
                marginUp = (base.marginUp - 0.012f).coerceIn(0.012f, 0.10f),
                marginDown = (base.marginDown - 0.006f).coerceIn(0.008f, 0.08f),
                stableMs = maxOf(base.stableMs, 260L),
                minRepIntervalMs = maxOf(base.minRepIntervalMs, 760L),
                requireBothWristsForGripUp = false,
            )

            ExerciseMode.ONE_ARM_CHIN_UP -> base.copy(
                elbowUpAngle = (base.elbowUpAngle + 12f).coerceIn(95f, 150f),
                elbowDownAngle = (base.elbowDownAngle - 8f).coerceIn(125f, 175f),
                marginUp = (base.marginUp - 0.013f).coerceIn(0.012f, 0.10f),
                marginDown = (base.marginDown - 0.007f).coerceIn(0.008f, 0.08f),
                stableMs = maxOf(base.stableMs, 270L),
                minRepIntervalMs = maxOf(base.minRepIntervalMs, 780L),
                requireBothWristsForGripUp = false,
            )

            else -> base
        }
    }

    private suspend fun recordSessionIfMeaningful(completedAtMs: Long) {
        val hasAnySignal = currentReps > 0 || currentSessionElapsedMs >= MIN_SESSION_MS
        if (!hasAnySignal) return
        AutoSetEventStore.emit(
            mode = currentMode,
            reps = currentReps,
            activeMs = currentSessionElapsedMs,
            timestampMs = completedAtMs,
        )
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

    private fun analyzerIntervalForSettings(settings: AppSettings): Long {
        return if (settings.poseModeAccurate) {
            ACCURATE_ANALYZER_FRAME_INTERVAL_MS
        } else {
            FAST_ANALYZER_FRAME_INTERVAL_MS
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
        private const val FAST_ANALYZER_FRAME_INTERVAL_MS = 33L
        private const val ACCURATE_ANALYZER_FRAME_INTERVAL_MS = 45L
        private const val MIN_WRIST_SHOULDER_MARGIN = 0.08f
        private const val MIN_ONE_ARM_WRIST_SHOULDER_MARGIN = 0.06f
        private const val MIN_HLR_WRIST_SHOULDER_MARGIN = 0.065f
        private const val MIN_MUSCLE_UP_WRIST_SHOULDER_MARGIN = 0.06f
        private const val ONE_ARM_HANG_MARGIN_RELAX = 0.012f
        private const val HLR_MARGIN_RELAX = 0.008f
        private const val MUSCLE_UP_MARGIN_RELAX = 0.015f
        private const val WRIST_RELEASE_GAP = 0.015f
        private const val MIN_WRIST_RELEASE_MARGIN = 0.035f
        private const val MIN_SESSION_MS = 400L
        private const val REP_TONE_DURATION_MS = 100
        private const val REP_TONE_VOLUME = 80

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
