package com.astrovm.gripmaxxer.service

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import android.graphics.Bitmap
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.PowerManager
import android.os.Looper
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import com.astrovm.gripmaxxer.GripApp
import com.astrovm.gripmaxxer.camera.CameraFrame
import com.astrovm.gripmaxxer.container
import com.astrovm.gripmaxxer.tracking.Exercise
import com.astrovm.gripmaxxer.tracking.Motion
import com.astrovm.gripmaxxer.tracking.PocketMoves
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
import org.robolectric.shadows.SensorEventBuilder
import org.robolectric.shadows.ShadowPowerManager
import org.robolectric.shadows.ShadowSensor
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

    /** The floating timer's label and value, like "Rest" and "0:03". */
    private fun timerText(): List<String>? {
        val box = windows.views.singleOrNull() as LinearLayout? ?: return null
        val face = box.getChildAt(0) as LinearLayout
        return (0 until face.childCount).map { (face.getChildAt(it) as TextView).text.toString() }
    }

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
        val notification = notifications.allNotifications.last()
        assertEquals(NotificationCompat.CATEGORY_WORKOUT, notification.category)
        // The workout clock ticks in the notification on its own.
        assertTrue(notification.extras.getBoolean(NotificationCompat.EXTRA_SHOW_CHRONOMETER))
        assertEquals(runBlocking { app.container.workouts.active.first() }!!.startedAtMs, notification.`when`)

        service.feed(Poses.standing, 1000)
        settle { notificationText() == "Dead hang: saved 0:02" }
        service.destroy()
    }

    @Test
    fun notificationIsNotTuckedAwayAsSilent() {
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
        startWorkout(Exercise.SQUAT)
        val service = service()
        val manager = app.getSystemService(NotificationManager::class.java)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, manager.getNotificationChannel("workout").importance)
        assertNull(manager.getNotificationChannel("tracking"))
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
        settle { timerText() == listOf("Push-up", "1") }

        runBlocking { app.container.settings.setOverlay(false) }
        settle { windows.views.isEmpty() }
        service.destroy()
    }

    @Test
    fun floatingTimerActions(): Unit = runBlocking {
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
        ShadowSettings.setCanDrawOverlays(true)
        app.container.settings.setOverlay(true)
        startWorkout(Exercise.SQUAT)
        val service = service()
        settle { windows.views.isNotEmpty() }
        fun tapAction(description: String) {
            val box = windows.views.single() as LinearLayout
            box.getChildAt(0).let { face ->
                val now = android.os.SystemClock.uptimeMillis()
                listOf(android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP).forEach { action ->
                    android.view.MotionEvent.obtain(now, now, action, 10f, 10f, 0).also {
                        face.dispatchTouchEvent(it)
                        it.recycle()
                    }
                }
            }
            val actions = box.getChildAt(1) as LinearLayout
            (0 until actions.childCount).map { actions.getChildAt(it) }.single { it.contentDescription == description }.performClick()
        }

        tapAction("Open Gripmaxxer")
        assertEquals(
            com.astrovm.gripmaxxer.MainActivity::class.java.name,
            shadowOf(app).nextStartedActivity.component!!.className,
        )

        // Hidden for the rest of the workout.
        tapAction("Hide")
        assertTrue(windows.views.isEmpty())
        waitMs(2_000)
        assertTrue(windows.views.isEmpty())

        val id = app.container.workouts.active.first()!!.id
        app.container.workouts.addSet(id, Exercise.SQUAT, 5, 0)
        service.destroy()
        startWorkout(Exercise.SQUAT)
        val again = service()
        settle { windows.views.isNotEmpty() }
        tapAction("Finish workout")
        settle { runBlocking { app.container.workouts.active.first() } == null }
        again.destroy()
    }

    @Test
    fun holdTimerShowsTime() {
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
        ShadowSettings.setCanDrawOverlays(true)
        startWorkout(Exercise.ACTIVE_HANG)
        val service = service()
        service.feed(Poses.deadHang, 2500)
        settle { timerText() == listOf("Active hang", "0:02") }

        // Off the bar: once the hang is saved, the timer counts the rest instead.
        service.feed(Poses.standing, 1000)
        settle { timerText()?.first() == "Rest" }
        runBlocking { app.container.controller.switchExercise(Exercise.DEAD_HANG) }
        settle { timerText()?.first() == "Rest" }
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

    private val sensors = app.getSystemService(SensorManager::class.java)

    private fun addSensor(type: Int): Sensor = ShadowSensor.newInstance(type).also {
        Shadow.extract<ShadowSensor>(it).setMaximumRange(5f)
        shadowOf(sensors).addSensor(it)
    }

    private fun cover(sensor: Sensor, distance: Float) {
        shadowOf(sensors).sendSensorEventToListeners(
            SensorEventBuilder.newBuilder().setSensor(sensor).setValues(floatArrayOf(distance)).build(),
            sensor,
        )
    }

    private fun waitMs(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(ms))

    @Test
    fun countsFromThePocketWhileTheProximitySensorIsCovered() {
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
        val proximity = addSensor(Sensor.TYPE_PROXIMITY)
        val accelerometer = addSensor(Sensor.TYPE_ACCELEROMETER)
        startWorkout(Exercise.SQUAT)
        val service = service()
        val controller = app.container.controller

        // A hand passing over the sensor doesn't count.
        cover(proximity, 0f)
        waitMs(500)
        cover(proximity, 5f)
        waitMs(2_000)
        assertFalse(controller.live.value.inPocket)

        cover(proximity, 0f)
        waitMs(1_500)
        assertTrue(controller.live.value.inPocket)
        assertTrue(shadowOf(sensors).hasListener(shadowOf(sensors).listeners.last(), accelerometer))
        assertTrue(ShadowPowerManager.getLatestWakeLock().isHeld)
        assertNull(app.container.preview.value)
        shadowOf(sensors).listeners.forEach { it.onAccuracyChanged(accelerometer, SensorManager.SENSOR_STATUS_ACCURACY_HIGH) }

        shadowOf(sensors).sendSensorEventToListeners(
            SensorEventBuilder.newBuilder().setSensor(accelerometer).setValues(floatArrayOf(0f, 9.8f, 0f)).build(),
            accelerometer,
        )
        val moves = PocketMoves(System.currentTimeMillis()) { motion, ms -> service.get().onMotion(motion, ms) }
        moves.still(2_000)
        repeat(2) {
            moves.lean(85f, 800)
            moves.lean(5f, 800)
        }
        idle()
        settle { notificationText() == "Squat: 2 reps" }

        cover(proximity, 5f)
        waitMs(500)
        assertFalse(controller.live.value.inPocket)
        assertFalse(ShadowPowerManager.getLatestWakeLock().isHeld)
        // The camera takes over, and the set from the pocket is saved.
        settle { notificationText() == "Squat: step into frame" }
        settle { runBlocking { app.container.workouts.active.first() }!!.sets.singleOrNull()?.reps == 2 }

        // Back in the pocket when the workout ends.
        cover(proximity, 0f)
        waitMs(1_500)
        service.destroy()
        assertFalse(ShadowPowerManager.getLatestWakeLock().isHeld)
        assertFalse(shadowOf(sensors).listeners.isNotEmpty())
    }

    @Test
    fun withoutMotionSensorsThePocketStaysQuiet() {
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
        startWorkout(Exercise.DIP)
        val service = service()
        app.container.controller.setInPocket(true)
        idle()
        service.get().onMotion(Motion(0f, 9.8f, 0f), 0)
        app.container.controller.setInPocket(false)
        idle()
        service.destroy()
    }

    @Test
    fun opensFromTheBackgroundWithTheRightPermission() {
        assertEquals(android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS, TrackingService.backgroundStartMode(36))
        @Suppress("DEPRECATION")
        assertEquals(android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED, TrackingService.backgroundStartMode(34))
        assertNull(TrackingService.backgroundStartMode(33))
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
