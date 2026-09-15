package com.constrakr.face

import android.graphics.Bitmap
import android.graphics.RectF
import com.constrakr.config.ConsTrakrConstants

/**
 * Port of iOS [FaceImagePreprocessor.swift] — square crop + contrast gate for AdaFace.
 */
object FacePreprocessor {
    fun makeSquareFaceBitmap(
        frame: Bitmap,
        boundingBox: RectF,
        outputSize: Int = ConsTrakrConstants.ADA_FACE_INPUT_SIZE
    ): Bitmap {
        val w = frame.width
        val h = frame.height
        val shortSide = minOf(w, h).toFloat()
        var rect = pixelRect(boundingBox, w, h)
        val side = maxOf(rect.width(), rect.height())
        val cx = rect.centerX()
        val cy = rect.centerY()
        rect = RectF(
            cx - side / 2,
            cy - side / 2,
            cx + side / 2,
            cy + side / 2
        ).apply {
            left = left.coerceAtLeast(0f)
            top = top.coerceAtLeast(0f)
            right = right.coerceAtMost(w.toFloat())
            bottom = bottom.coerceAtMost(h.toFloat())
        }
        require(minOf(rect.width(), rect.height()) >= shortSide * ConsTrakrConstants.MIN_FACE_RELATIVE_SIZE * 0.85f) {
            "Move closer — face is too small or poorly aligned."
        }
        val left = rect.left.toInt()
        val top = rect.top.toInt()
        val cropped = Bitmap.createBitmap(
            frame,
            left,
            top,
            rect.width().toInt(),
            rect.height().toInt()
        )
        val contrast = lumaStdDev(cropped)
        require(contrast >= ConsTrakrConstants.MIN_CROP_CONTRAST) {
            "Face image is too blurry or low contrast."
        }
        return Bitmap.createScaledBitmap(cropped, outputSize, outputSize, true)
    }

    private fun pixelRect(normalized: RectF, width: Int, height: Int): RectF {
        return RectF(
            normalized.left * width,
            normalized.top * height,
            normalized.right * width,
            normalized.bottom * height
        )
    }

    private fun lumaStdDev(bitmap: Bitmap): Float {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val luma = FloatArray(pixels.size) { i ->
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            (0.299f * r + 0.587f * g + 0.114f * b) / 255f
        }
        val mean = luma.average().toFloat()
        var varSum = 0f
        for (v in luma) varSum += (v - mean) * (v - mean)
        return kotlin.math.sqrt(varSum / luma.size)
    }
}
