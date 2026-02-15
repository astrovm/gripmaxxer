package com.astrolabs.hangmaxxer.camera

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class FrontCameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraExecutor: ExecutorService? = null
    private var analysisUseCase: ImageAnalysis? = null

    suspend fun start(analyzer: PoseFrameAnalyzer) {
        val provider = cameraProvider ?: ProcessCameraProvider.getInstance(context).await().also {
            cameraProvider = it
        }

        val executor = Executors.newSingleThreadExecutor().also { cameraExecutor = it }
        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(executor, analyzer)
            }

        analysisUseCase = imageAnalysis

        withContext(Dispatchers.Main.immediate) {
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_FRONT_CAMERA,
                imageAnalysis,
            )
        }
    }

    fun stop() {
        analysisUseCase?.clearAnalyzer()
        analysisUseCase = null
        val provider = cameraProvider
        if (provider != null) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                provider.unbindAll()
            } else {
                Handler(Looper.getMainLooper()).post {
                    provider.unbindAll()
                }
            }
        }
        cameraExecutor?.shutdownNow()
        cameraExecutor = null
    }
}

private suspend fun <T> com.google.common.util.concurrent.ListenableFuture<T>.await(): T {
    return suspendCancellableCoroutine { cont ->
        addListener(
            {
                try {
                    cont.resume(get())
                } catch (throwable: Throwable) {
                    cont.resumeWithException(throwable)
                }
            },
            Runnable::run,
        )

        cont.invokeOnCancellation { cancel(true) }
    }
}
