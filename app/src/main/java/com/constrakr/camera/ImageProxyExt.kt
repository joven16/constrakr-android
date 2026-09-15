package com.constrakr.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProcessingUtil
import androidx.camera.core.ImageProxy

/** YUV → bitmap using CameraX native converter, then apply [ImageProxy.imageInfo] rotation. */
fun ImageProxy.toBitmap(): Bitmap? = runCatching {
    val raw = ImageProcessingUtil.convertYUVToBitmap(this)
    val rotation = imageInfo.rotationDegrees
    if (rotation == 0) {
        raw
    } else {
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
    }
}.getOrNull()
