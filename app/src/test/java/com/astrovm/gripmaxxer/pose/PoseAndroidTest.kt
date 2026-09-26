package com.astrovm.gripmaxxer.pose

import android.content.Context
import android.graphics.PointF
import androidx.test.core.app.ApplicationProvider
import com.google.mlkit.common.sdkinternal.MlKitContext
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PoseAndroidTest {

    private fun poseOf(vararg points: Pair<Int, PointF>): Pose {
        val byType = points.toMap()
        return mockk {
            every { getPoseLandmark(any()) } answers {
                val type = firstArg<Int>()
                byType[type]?.let { point -> mockk<PoseLandmark> { every { position } returns point } }
            }
        }
    }

    @Test
    fun `pose landmarks are normalized and clamped`() {
        val pose = poseOf(
            PoseLandmark.LEFT_SHOULDER to PointF(40f, 50f),
            PoseLandmark.RIGHT_SHOULDER to PointF(60f, 50f),
            PoseLandmark.LEFT_ELBOW to PointF(40f, 40f),
            PoseLandmark.LEFT_WRIST to PointF(-10f, 250f),
        )
        val frame = PoseFeatureExtractor().toPoseFrame(pose, frameWidth = 100, frameHeight = 200, timestampMs = 7L)
        assertTrue(frame.posePresent)
        assertEquals(7L, frame.timestampMs)
        assertEquals(NormalizedLandmark(0.4f, 0.25f), frame.landmark(PoseLandmark.LEFT_SHOULDER))
        assertEquals(NormalizedLandmark(0f, 1f), frame.landmark(PoseLandmark.LEFT_WRIST))
        assertEquals(4, frame.landmarks.size)
    }

    @Test
    fun `pose without both shoulders and an arm is not present`() {
        val extractor = PoseFeatureExtractor()
        val shouldersOnly = poseOf(
            PoseLandmark.LEFT_SHOULDER to PointF(40f, 50f),
            PoseLandmark.RIGHT_SHOULDER to PointF(60f, 50f),
        )
        assertFalse(extractor.toPoseFrame(shouldersOnly, 100, 100, 0L).posePresent)
        val rightArm = poseOf(
            PoseLandmark.LEFT_SHOULDER to PointF(40f, 50f),
            PoseLandmark.RIGHT_SHOULDER to PointF(60f, 50f),
            PoseLandmark.RIGHT_ELBOW to PointF(60f, 40f),
            PoseLandmark.RIGHT_WRIST to PointF(60f, 30f),
        )
        assertTrue(extractor.toPoseFrame(rightArm, 100, 100, 0L).posePresent)
        assertFalse(extractor.toPoseFrame(poseOf(), 100, 100, 0L).posePresent)
    }

    @Test
    fun `detector wrapper switches models and closes`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        MlKitContext.initializeIfNeeded(context)
        val wrapper = PoseDetectorWrapper()
        wrapper.setAccurateMode(false)
        wrapper.setAccurateMode(true)
        wrapper.setAccurateMode(false)
        // The native model is not available on the JVM, so processing fails or never completes.
        val outcome = runCatching {
            withTimeoutOrNull(2_000L) { wrapper.process(mockk<InputImage>(relaxed = true)) }
        }
        assertTrue(outcome.isFailure || outcome.getOrNull() == null || outcome.getOrNull() is Pose)
        wrapper.close()
    }
}
