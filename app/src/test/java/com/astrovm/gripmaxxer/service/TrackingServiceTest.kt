package com.astrovm.gripmaxxer.service

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import android.graphics.Bitmap
import android.os.Looper
import android.view.WindowManager
import android.widget.TextView
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import com.astrovm.gripmaxxer.GripApp
import com.astrovm.gripmaxxer.camera.CameraFrame
import com.astrovm.gripmaxxer.container
import com.astrovm.gripmaxxer.tracking.Exercise
import com.astrovm.gripmaxxer.tracking.Pose
import com.astrovm.gripmaxxer.tracking.Poses
import com.google.common.util.concurrent.Futures
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetectorOptionsBase
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowSettings
import org.robolectric.shadows.ShadowWindowManagerImpl

@RunWith(RobolectricTestRunner::class)
@Config(application = GripApp::class)
class TrackingServiceTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val notifications = shadowOf(app.getSystemService(NotificationManager::class.java))
    private val windows: ShadowWindowManagerImpl = Shadow.extract(app.getSystemService(WindowManager::class.java))
    private var now = 0L

    @Before
    fun setUp() {
        mockkStatic(PoseDetection::class)
        every { PoseDetection.getClient(any<PoseDetectorOptionsBase>()) } returns mockk(relaxed = true)
        mockkObject(ProcessCameraProvider.Companion)
        every { ProcessCameraProvider.getInstance(any()) } returns Futures.immediateFuture(mockk(relaxed = true))
    }

    @After
    fun tearDown() = unmockkAll()

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    /** Lets background work land, then runs what it posted to the main thread. */
    private fun settle(check: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!check() && System.currentTimeMillis() < deadline) {
            idle()
            Thread.sleep(20)
        }
        idle()
        assertTrue("notification: ${notificationText()}, live: ${app.container.controller.live.value}", check())
    }

    private fun startWorkout(exercise: Exercise) = runBlocking { app.container.controller.start(exercise) }

    private fun service(): ServiceController<TrackingService> = Robolectric.buildService(TrackingService::class.java).create()

    private fun ServiceController<TrackingService>.feed(pose: Pose?, ms: Long, image: Bitmap? = null) {
        val end = now + ms
        while (now < end) {
            get().onCameraFrame(CameraFrame(pose, now, 480, 640, image))
            now += 50
        }
        idle()
    }

    private fun notificationText(): String? = notifications.allNotifications.lastOrNull()
        ?.extras?.getCharSequence(NotificationCompat.EXTRA_TEXT)?.toString()

    @Test
    fun withoutCameraPermissionItStopsAndSaysWhy() {
        startWorkout(Exercise.DEAD_HANG)
        val service = service()
        assertEquals("Camera permission is off", app.container.controller.live.value.error)
        assertTrue(shadowOf(service.get()).isStoppedBySelf)
        service.destroy()
    }

    @Test
    fun countsFramesAndShowsProgress() {
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
        startWorkout(Exercise.PULL_UP)
        val service = service()
        assertNotNull(app.container.controller.effects)

        service.feed(null, 100)
        settle { notificationText() == "Pull-up: step into frame" }
        service.feed(Poses.deadHang, 1000)
        settle { notificationText() == "Pull-up: 0 reps" }
        service.feed(Poses.standing, 1000)
        settle { notificationText() == "Pull-up: ready" }

        app.container.previewVisible.value = true
        val image = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        service.feed(Poses.standing, 50, image)
        assertSame(image, app.container.preview.value!!.image)

        service.destroy()
        assertNull(app.container.controller.effects)
        assertNull(app.container.preview.value)
        assertFalse(app.container.controller.live.value.tracking)
    }

    @Test
    fun holdTimeShowsInTheNotification() {
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
        startWorkout(Exercise.DEAD_HANG)
        val service = service()
        service.feed(Poses.deadHang, 3000)
        settle { notificationText() == "Dead hang: 0:02" }
        service.destroy()
    }

    @Test
    fun floatingTimerShowsWhileTheAppIsAway() {
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
        ShadowSettings.setCanDrawOverlays(true)
        startWorkout(Exercise.PUSH_UP)
        val service = service()
        settle { windows.views.isNotEmpty() }
        service.feed(Poses.armsStraight, 300)
        service.feed(Poses.armsBent, 400)
        service.feed(Poses.armsStraight, 400)
        settle { (windows.views.single() as TextView).text.toString() == "1" }

        runBlocking { app.container.settings.setOverlay(false) }
        settle { windows.views.isEmpty() }
        service.destroy()
    }

    @Test
    fun holdTimerShowsTime() {
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
        ShadowSettings.setCanDrawOverlays(true)
        startWorkout(Exercise.ACTIVE_HANG)
        val service = service()
        service.feed(Poses.deadHang, 1500)
        settle { (windows.views.single() as TextView).text.toString() == "0:01" }
        service.destroy()
        assertTrue(windows.views.isEmpty())
    }

    @Test
    fun effectsReachTheirTargets() {
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
        startWorkout(Exercise.DIP)
        val service = service()
        val effects = app.container.controller.effects!!
        effects.beep()
        effects.say("10 seconds")
        effects.playMedia()
        effects.pauseMedia()
        service.destroy()
    }

    @Test
    fun cameraFailureStopsTracking() {
        every { ProcessCameraProvider.getInstance(any()) } returns
            Futures.immediateFailedFuture(IllegalStateException("busy"))
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
        startWorkout(Exercise.SQUAT)
        val service = service()
        idle()
        assertEquals("Camera unavailable", app.container.controller.live.value.error)
        service.destroy()
    }

    @Test
    fun finishFromTheNotification(): Unit = runBlocking {
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
        startWorkout(Exercise.SQUAT)
        val id = app.container.workouts.active.first()!!.id
        app.container.workouts.addSet(id, Exercise.SQUAT, 5, 0)
        val finish = Intent(app, TrackingService::class.java).setAction("com.astrovm.gripmaxxer.FINISH")
        val service = Robolectric.buildService(TrackingService::class.java, finish).create().startCommand(0, 1)
        Robolectric.buildService(TrackingService::class.java).startCommand(0, 2)
        settle { runBlocking { app.container.workouts.active.first() } == null }
        service.destroy()
    }

    @Test
    fun startAndStopHelpers() {
        TrackingService.start(app)
        assertEquals(
            TrackingService::class.java.name,
            shadowOf(app).nextStartedService.component!!.className,
        )
        TrackingService.stop(app)
        assertEquals(
            TrackingService::class.java.name,
            shadowOf(app).nextStoppedService.component!!.className,
        )
    }
}
