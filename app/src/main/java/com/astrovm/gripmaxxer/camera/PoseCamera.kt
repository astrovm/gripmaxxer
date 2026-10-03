package com.astrovm.gripmaxxer.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.astrovm.gripmaxxer.tracking.Joint
import com.astrovm.gripmaxxer.tracking.Point
import com.astrovm.gripmaxxer.tracking.Pose
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.Executors

/** One analyzed camera frame. [image] is only filled in when a preview was asked for. */
class CameraFrame(
    val pose: Pose?,
    val timestampMs: Long,
    val width: Int,
    val height: Int,
    val image: Bitmap?,
)

/**
 * Runs the front camera through ML Kit pose detection.
 * [onFrame] is called on a single background thread, one frame at a time.
 */
class PoseCamera(
    private val context: Context,
    private val wantsImage: () -> Boolean,
    private val onFrame: (CameraFrame) -> Unit,
) {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val detector: PoseDetector = PoseDetection.getClient(
        PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.STREAM_MODE).build(),
    )
    private var provider: ProcessCameraProvider? = null

    @Volatile
    private var stopped = false

    // Detection can finish after stop(). Run its callback inline then so the frame still gets closed.
    private val callbackExecutor = Executor { task ->
        try {
            executor.execute(task)
        } catch (_: RejectedExecutionException) {
            task.run()
        }
    }

    fun start(owner: LifecycleOwner, onError: (Throwable) -> Unit) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            runCatching {
                val cameraProvider = future.get()
                provider = cameraProvider
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setResolutionStrategy(
                                ResolutionStrategy(
                                    Size(640, 480),
                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                                ),
                            )
                            .build(),
                    )
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor, ::analyze)
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(owner, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
            }.onFailure(onError)
        }, ContextCompat.getMainExecutor(context))
    }

    fun stop() {
        stopped = true
        provider?.unbindAll()
        provider = null
        detector.close()
        executor.shutdown()
    }

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    internal fun analyze(proxy: ImageProxy) {
        val media = proxy.image
        if (media == null) {
            proxy.close()
            return
        }
        val rotation = proxy.imageInfo.rotationDegrees
        val upright = rotation == 90 || rotation == 270
        val width = if (upright) proxy.height else proxy.width
        val height = if (upright) proxy.width else proxy.height
        val timestamp = System.currentTimeMillis()
        detector.process(InputImage.fromMediaImage(media, rotation))
            .addOnCompleteListener(callbackExecutor) { task ->
                val pose = if (task.isSuccessful) toPose(task.result) else null
                val image = if (!stopped && wantsImage()) proxy.toUprightBitmap(rotation) else null
                proxy.close()
                if (!stopped) onFrame(CameraFrame(pose, timestamp, width, height, image))
            }
    }

    private fun ImageProxy.toUprightBitmap(rotation: Int): Bitmap {
        val raw = toBitmap()
        if (rotation == 0) return raw
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
    }
}

private val JOINTS = mapOf(
    PoseLandmark.NOSE to Joint.NOSE,
    PoseLandmark.LEFT_SHOULDER to Joint.LEFT_SHOULDER,
    PoseLandmark.RIGHT_SHOULDER to Joint.RIGHT_SHOULDER,
    PoseLandmark.LEFT_ELBOW to Joint.LEFT_ELBOW,
    PoseLandmark.RIGHT_ELBOW to Joint.RIGHT_ELBOW,
    PoseLandmark.LEFT_WRIST to Joint.LEFT_WRIST,
    PoseLandmark.RIGHT_WRIST to Joint.RIGHT_WRIST,
    PoseLandmark.LEFT_HIP to Joint.LEFT_HIP,
    PoseLandmark.RIGHT_HIP to Joint.RIGHT_HIP,
    PoseLandmark.LEFT_KNEE to Joint.LEFT_KNEE,
    PoseLandmark.RIGHT_KNEE to Joint.RIGHT_KNEE,
    PoseLandmark.LEFT_ANKLE to Joint.LEFT_ANKLE,
    PoseLandmark.RIGHT_ANKLE to Joint.RIGHT_ANKLE,
)

/** ML Kit guesses joints it can't see. Only keep the ones it's fairly sure are in frame. */
private const val MIN_IN_FRAME = 0.5f

internal fun toPose(pose: com.google.mlkit.vision.pose.Pose): Pose? {
    val joints = buildMap {
        for ((type, joint) in JOINTS) {
            val landmark = pose.getPoseLandmark(type) ?: continue
            if (landmark.inFrameLikelihood < MIN_IN_FRAME) continue
            put(joint, Point(landmark.position.x, landmark.position.y))
        }
    }
    return if (joints.isEmpty()) null else Pose(joints)
}
