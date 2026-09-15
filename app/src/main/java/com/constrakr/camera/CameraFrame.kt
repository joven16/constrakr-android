package com.constrakr.camera

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage

data class CameraFrame(
    val bitmap: Bitmap,
    val inputImage: InputImage
)

/**
 * Bitmap copy for ML Kit — must not use [InputImage.fromMediaImage] here because
 * [ImageProxy] is closed immediately after the analyzer callback returns.
 */
fun ImageProxy.toCameraFrame(): CameraFrame? = runCatching {
    val bitmap = toBitmap() ?: return null
    CameraFrame(bitmap, InputImage.fromBitmap(bitmap, 0))
}.getOrNull()
