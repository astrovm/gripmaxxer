package com.astrolabs.gripmaxxer.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

object ImageProxyBitmapConverter {

    fun toDebugBitmap(
        image: ImageProxy,
        rotationDegrees: Int,
        mirrorHorizontally: Boolean,
        maxWidth: Int = 540,
    ): Bitmap? {
        val nv21 = yuv420888ToNv21(image)
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val stream = ByteArrayOutputStream()
        val compressed = yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 70, stream)
        if (!compressed) return null

        val jpegBytes = stream.toByteArray()
        val decoded = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size) ?: return null

        val transformed = transform(decoded, rotationDegrees, mirrorHorizontally)
        if (transformed.width <= maxWidth) return transformed

        val scale = maxWidth.toFloat() / transformed.width.toFloat()
        val scaled = Bitmap.createScaledBitmap(
            transformed,
            maxWidth,
            (transformed.height * scale).toInt().coerceAtLeast(1),
            true,
        )
        if (scaled != transformed) transformed.recycle()
        return scaled
    }

    private fun transform(source: Bitmap, rotationDegrees: Int, mirrorHorizontally: Boolean): Bitmap {
        if (rotationDegrees == 0 && !mirrorHorizontally) return source

        val matrix = Matrix()
        if (rotationDegrees != 0) {
            matrix.postRotate(rotationDegrees.toFloat())
        }
        if (mirrorHorizontally) {
            matrix.postScale(-1f, 1f)
        }

        val transformed = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        if (transformed != source) {
            source.recycle()
        }
        return transformed
    }

    private fun yuv420888ToNv21(image: ImageProxy): ByteArray {
        val width = image.width
        val height = image.height
        val ySize = width * height
        val uvSize = width * height / 4
        val output = ByteArray(ySize + uvSize * 2)

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        var outIndex = 0

        // Copy Y plane
        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        for (row in 0 until height) {
            val rowStart = row * yRowStride
            for (col in 0 until width) {
                output[outIndex++] = yBuffer.get(rowStart + col * yPixelStride)
            }
        }

        // Copy UV planes as NV21 (V then U)
        val chromaHeight = height / 2
        val chromaWidth = width / 2
        val uRowStride = uPlane.rowStride
        val vRowStride = vPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vPixelStride = vPlane.pixelStride

        for (row in 0 until chromaHeight) {
            val uRowStart = row * uRowStride
            val vRowStart = row * vRowStride
            for (col in 0 until chromaWidth) {
                output[outIndex++] = vBuffer.get(vRowStart + col * vPixelStride)
                output[outIndex++] = uBuffer.get(uRowStart + col * uPixelStride)
            }
        }

        return output
    }
}
