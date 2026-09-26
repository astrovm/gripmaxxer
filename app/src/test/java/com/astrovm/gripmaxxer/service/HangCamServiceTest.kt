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
}
