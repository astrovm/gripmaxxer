package com.astrovm.gripmaxxer.service

import android.Manifest
import android.app.Application
import android.app.Service
import android.content.Intent
import android.os.Looper
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import com.astrovm.gripmaxxer.camera.FrontCameraManager
import com.astrovm.gripmaxxer.camera.PoseFrameAnalyzer
import com.astrovm.gripmaxxer.datastore.SettingsRepository
import com.astrovm.gripmaxxer.pose.PoseDetectorWrapper
import com.astrovm.gripmaxxer.pose.PoseFrame
import com.astrovm.gripmaxxer.reps.*
import com.astrovm.gripmaxxer.testutil.SettingsStore
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController

@RunWith(RobolectricTestRunner::class)
class HangCamServiceTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val camera = mockk<FrontCameraManager>(relaxed = true)
    private val detector = mockk<PoseDetectorWrapper>(relaxed = true)
    private lateinit var controller: ServiceController<HangCamService>
    private lateinit var service: HangCamService
    private var active = false
    private var reps = 0
    private val originalCameraFactory = HangCamService.cameraManagerFactory
    private val originalPoseFactory = HangCamService.poseDetectorFactory
    private val analyzer = slot<PoseFrameAnalyzer>()

    @Before fun setup() {
        SettingsStore.clear(app)
        MonitoringStateStore.reset()
        HangCamService.cameraManagerFactory = { _, _ -> camera }
        HangCamService.poseDetectorFactory = { detector }
        coEvery { camera.start(capture(analyzer)) } just Runs
        mockkConstructor(PullUpActivityDetector::class, DeadHangActivityDetector::class,
            ActiveHangActivityDetector::class, HangingLegRaiseActivityDetector::class,
            PushUpActivityDetector::class, SquatActivityDetector::class, DipActivityDetector::class,
            RepEngine::class)
        every { anyConstructed<PullUpActivityDetector>().process(any(), any()) } answers { active }
        every { anyConstructed<DeadHangActivityDetector>().process(any(), any()) } answers { active }
        every { anyConstructed<ActiveHangActivityDetector>().process(any(), any()) } answers { active }
        every { anyConstructed<HangingLegRaiseActivityDetector>().process(any(), any()) } answers { active }
        every { anyConstructed<PushUpActivityDetector>().process(any(), any()) } answers { active }
        every { anyConstructed<SquatActivityDetector>().process(any(), any()) } answers { active }
        every { anyConstructed<DipActivityDetector>().process(any(), any()) } answers { active }
        every { anyConstructed<RepEngine>().process(any(), any(), any()) } answers { RepCounterResult(reps, reps > 0) }
        controller = Robolectric.buildService(HangCamService::class.java).create()
        service = controller.get()
    }
    @After fun cleanup() {
        controller.destroy()
        HangCamService.cameraManagerFactory = originalCameraFactory
        HangCamService.poseDetectorFactory = originalPoseFactory
        unmockkAll()
    }
    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 5_000_000_000L
        do {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(10)
        } while (System.nanoTime() < deadline)
        fail("Service state did not settle: ${MonitoringStateStore.snapshot.value}")
    }
    private fun start() {
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA, Manifest.permission.POST_NOTIFICATIONS)
        assertEquals(Service.START_NOT_STICKY, service.onStartCommand(Intent(HangCamService.ACTION_START), 0, 1))
        await { analyzer.isCaptured }
    }
    private fun frame(present: Boolean = true) = runBlocking {
        service.processPoseFrame(PoseFrame(emptyMap(), System.currentTimeMillis(), present))
    }

    @Test fun startsOncePublishesFramesAndStopsCamera() {
        assertEquals(Service.START_NOT_STICKY, service.onStartCommand(null, 0, 1))
        start()
        service.onStartCommand(Intent(HangCamService.ACTION_START), 0, 2)
        coVerify(exactly = 1) { camera.start(any()) }
        active = true; reps = 3; frame()
        val state = MonitoringStateStore.snapshot.value
        assertTrue(state.serviceRunning)
        assertTrue(state.hanging)
        assertEquals(3, state.reps)
        assertTrue(state.posePresent)
        active = false; frame(false)
        assertFalse(MonitoringStateStore.snapshot.value.hanging)
        service.onStartCommand(Intent(HangCamService.ACTION_STOP), 0, 3)
        await { !MonitoringStateStore.snapshot.value.serviceRunning }
        verify { camera.stop() }
        frame()
        assertFalse(MonitoringStateStore.snapshot.value.serviceRunning)
    }

    @Test fun settingsSwitchEveryExerciseAndAccuratePoseMode() = runBlocking {
        start()
        val settings = SettingsRepository(app)
        settings.setMediaControlEnabled(false)
        settings.setRepSoundEnabled(false)
        settings.setVoiceCueEnabled(true)
        settings.setPoseModeAccurate(true)
        for (mode in ExerciseMode.entries) {
            settings.setSelectedExerciseMode(mode)
            await { MonitoringStateStore.snapshot.value.mode == mode }
            active = true; reps = 2; frame()
            assertEquals(mode, MonitoringStateStore.snapshot.value.mode)
            assertEquals(2, MonitoringStateStore.snapshot.value.reps)
            SystemClock.sleep(450)
            active = false; frame()
            assertFalse(MonitoringStateStore.snapshot.value.hanging)
        }
        coVerify { detector.setAccurateMode(true) }
        settings.setVoiceCueEnabled(false)
        settings.setPoseModeAccurate(false)
        await { runCatching { coVerify { detector.setAccurateMode(false) }; true }.getOrDefault(false) }
    }

    @Test fun missingCameraPermissionStopsWithoutStartingHardware() {
        shadowOf(app).denyPermissions(Manifest.permission.CAMERA)
        service.onStartCommand(Intent(HangCamService.ACTION_START), 0, 1)
        await { !MonitoringStateStore.snapshot.value.serviceRunning }
        coVerify(exactly = 0) { camera.start(any()) }
    }

    @Test fun cameraStartupFailureReleasesMonitoringResources() {
        coEvery { camera.start(capture(analyzer)) } throws IllegalStateException("camera unavailable")
        start()
        await { !MonitoringStateStore.snapshot.value.serviceRunning }
        verify { camera.stop() }
    }

    @Test fun companionCommandsUseExplicitServiceActions() {
        HangCamService.start(app)
        assertEquals(HangCamService.ACTION_START, shadowOf(app).nextStartedService.action)
        HangCamService.stop(app)
        assertEquals(HangCamService.ACTION_STOP, shadowOf(app).nextStartedService.action)
    }

    private fun field(name: String): java.lang.reflect.Field = HangCamService::class.java.getDeclaredField(name).apply { isAccessible = true }
    private fun invoke(name: String, value: Long): Any? = HangCamService::class.java.getDeclaredMethod(name, Long::class.javaPrimitiveType).apply { isAccessible = true }.invoke(service, value)

    @Test fun speechInitializationReportsFailuresAndFallsBackToEnglish() {
        val tts = field("voiceCueTts").get(service) as android.speech.tts.TextToSpeech
        val listener = shadowOf(tts).onInitListener
        listener.onInit(android.speech.tts.TextToSpeech.ERROR)
        assertFalse(field("voiceCueTtsReady").getBoolean(service))
        listener.onInit(android.speech.tts.TextToSpeech.SUCCESS)
        assertTrue(field("voiceCueTtsReady").getBoolean(service))
        assertEquals(java.util.Locale.US, shadowOf(tts).currentLanguage)
        org.robolectric.shadows.ShadowTextToSpeech.addLanguageAvailability(java.util.Locale.getDefault())
        listener.onInit(android.speech.tts.TextToSpeech.SUCCESS)
        assertEquals(java.util.Locale.getDefault(), shadowOf(tts).currentLanguage)
        field("voiceCueTts").set(service, null)
        listener.onInit(android.speech.tts.TextToSpeech.SUCCESS)
        field("voiceCueTts").set(service, tts)
    }

    @Test fun timedVoiceCuesUseIntervalsDeduplicateAndPluralize() {
        val tts = field("voiceCueTts").get(service) as android.speech.tts.TextToSpeech
        val shadow = shadowOf(tts)
        field("currentSettings").set(service, com.astrovm.gripmaxxer.datastore.AppSettings(voiceCueEnabled = true))
        field("currentMode").set(service, ExerciseMode.DEAD_HANG)
        field("currentHangState").setBoolean(service, true)
        invoke("maybeSpeakVoiceCue", 10000)
        assertNull(shadow.lastSpokenText)
        shadow.onInitListener.onInit(android.speech.tts.TextToSpeech.SUCCESS)
        for (seconds in listOf(0L, 9L, 11L)) invoke("maybeSpeakVoiceCue", seconds * 1000)
        assertNull(shadow.lastSpokenText)
        val cases = mapOf(10L to "10 seconds", 60L to "1 minute", 120L to "2 minutes", 70L to "1 minute 10 seconds", 130L to "2 minutes 10 seconds")
        for ((seconds, text) in cases) {
            invoke("maybeSpeakVoiceCue", seconds * 1000)
            assertEquals(text, shadow.lastSpokenText)
            val count = shadow.spokenTextList.size
            invoke("maybeSpeakVoiceCue", seconds * 1000)
            assertEquals(count, shadow.spokenTextList.size)
        }
        assertEquals("1 minute 1 second", invoke("buildVoiceCueText", 61))
        assertEquals("2 minutes 1 second", invoke("buildVoiceCueText", 121))
    }

    @Test fun cameraCallbacksPublishDebugFramesAndFrameFreshness() {
        start()
        DebugPreviewStore.enabled.value = true
        val captured = analyzer.captured
        fun callback(name: String): Any = PoseFrameAnalyzer::class.java.getDeclaredField(name).apply { isAccessible = true }.get(captured)
        @Suppress("UNCHECKED_CAST")
        val tick = callback("onFrameTick") as () -> Unit
        tick()
        val bitmap = android.graphics.Bitmap.createBitmap(10, 10, android.graphics.Bitmap.Config.ARGB_8888)
        val pose = PoseFrame(emptyMap(), 1234, true)
        @Suppress("UNCHECKED_CAST")
        val debug = callback("onDebugFrame") as (android.graphics.Bitmap, PoseFrame) -> Unit
        debug(bitmap, pose)
        assertEquals(1234L, DebugPreviewStore.frame.value!!.timestampMs)
        @Suppress("UNCHECKED_CAST")
        val detected = callback("onPoseFrame") as (PoseFrame) -> Unit
        detected(pose)
        await { MonitoringStateStore.snapshot.value.posePresent }
        assertTrue(MonitoringStateStore.snapshot.value.lastFrameAgeMs < Long.MAX_VALUE)
        DebugPreviewStore.enabled.value = false
    }

    @Test fun switchingModesDuringAnActiveSetStopsTheCounter() = runBlocking {
        start()
        active = true
        frame()
        assertTrue(MonitoringStateStore.snapshot.value.hanging)
        SettingsRepository(app).setSelectedExerciseMode(ExerciseMode.SQUAT)
        await { MonitoringStateStore.snapshot.value.mode == ExerciseMode.SQUAT }
        assertFalse(MonitoringStateStore.snapshot.value.hanging)
        assertEquals(0L, MonitoringStateStore.snapshot.value.elapsedHangMs)
        originalCameraFactory(app, service).stop()
    }

    @Test @org.robolectric.annotation.Config(sdk = [28])
    fun supportedOlderAndroidStartsForegroundMonitoring() {
        start()
        assertTrue(MonitoringStateStore.snapshot.value.serviceRunning)
    }
}
