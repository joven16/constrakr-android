package com.constrakr.face

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

object EnrollmentPhotoEncoder {
    fun encodeJpeg(bitmap: Bitmap, quality: Int = 85): ByteArray {
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            return out.toByteArray()
        }
    }
}
