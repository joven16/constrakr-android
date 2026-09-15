package com.constrakr.face

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix

object JpegImageUtils {
    fun decodeDisplayBitmap(jpeg: ByteArray): Bitmap? =
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)

    fun encodePortraitJpeg(bitmap: Bitmap, quality: Int = 85): ByteArray =
        EnrollmentPhotoEncoder.encodeJpeg(bitmap, quality)

    /** Full camera frame for pose display (not the 112×112 AdaFace crop). */
    fun encodePoseDisplayFrame(frame: Bitmap, quality: Int = 85): ByteArray {
        val display = if (frame.width > frame.height) frame.rotate(-90f) else frame
        return EnrollmentPhotoEncoder.encodeJpeg(display, quality)
    }
}

fun Bitmap.rotate(degrees: Float): Bitmap {
    if (degrees == 0f) return this
    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}
