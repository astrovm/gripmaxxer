package com.astrovm.gripmaxxer.camera

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.RealObject
import java.io.OutputStream

/** Robolectric has no YuvImage codec; decode NV21 here and use its real bitmap JPEG encoder. */
@Implements(YuvImage::class)
class TestYuvImageShadow {
    @RealObject
    private lateinit var image: YuvImage

    @Implementation
    protected fun compressToJpeg(rect: Rect, quality: Int, stream: OutputStream): Boolean {
        check(image.yuvFormat == ImageFormat.NV21)
        val width = image.width
        val height = image.height
        val bytes = image.yuvData
        val colors = IntArray(rect.width() * rect.height())
        for (y in rect.top until rect.bottom) {
            for (x in rect.left until rect.right) {
                val luminance = (bytes[y * width + x].toInt() and 255) - 16
                val chroma = width * height + (y / 2) * width + (x / 2) * 2
                val v = (bytes[chroma].toInt() and 255) - 128
                val u = (bytes[chroma + 1].toInt() and 255) - 128
                val r = ((298 * luminance + 409 * v + 128) shr 8).coerceIn(0, 255)
                val g = ((298 * luminance - 100 * u - 208 * v + 128) shr 8).coerceIn(0, 255)
                val b = ((298 * luminance + 516 * u + 128) shr 8).coerceIn(0, 255)
                colors[(y - rect.top) * rect.width() + x - rect.left] =
                    (255 shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        val bitmap = Bitmap.createBitmap(colors, rect.width(), rect.height(), Bitmap.Config.ARGB_8888)
        return try {
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        } finally {
            bitmap.recycle()
        }
    }
}
