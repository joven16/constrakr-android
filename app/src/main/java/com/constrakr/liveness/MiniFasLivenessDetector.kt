package com.constrakr.liveness

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import com.constrakr.config.ConsTrakrConstants
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlin.math.exp

/**
 * MiniFASNetV2 anti-spoof via ONNX — matches iOS [CoreMLAntiSpoof.swift].
 * Input: 80×80 BGR float NCHW (0–255) · Output: 3-class logits (class 1 = live).
 */
class MiniFasLivenessDetector(context: Context) {
    val isReady: Boolean get() = session != null
    val loadError: String? get() = _loadError

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null
    private var _loadError: String? = null
    private val tracker = AntiSpoofTracker()

    init {
        try {
            context.assets.open(MODEL_ASSET).use { stream ->
                val bytes = stream.readBytes()
                session = env.createSession(bytes, OrtSession.SessionOptions())
            }
        } catch (e: Exception) {
            _loadError = e.message
        }
    }

    fun resetTracker() = tracker.reset()

    fun classify(frame: Bitmap, faceBox: RectF): PassiveLivenessResult {
        val model = session ?: return PassiveLivenessResult(
            score = 0f,
            classification = Classification.UNCERTAIN,
            message = _loadError ?: "MiniFASNet not loaded"
        )
        val crop = cropFace(frame, faceBox)
        val input = bitmapToNchwBgr(crop)
        val tensor = OnnxTensor.createTensor(env, input)
        val logits = model.run(mapOf("input" to tensor)).use { result ->
            @Suppress("UNCHECKED_CAST")
            (result[0].value as Array<FloatArray>)[0]
        }
        val probs = softmax(logits)
        val liveScore = if (probs.size >= 2) probs[1] else probs.last()
        val classification = when {
            liveScore >= ConsTrakrConstants.MINIFAS_LIVE_THRESHOLD -> Classification.LIVE
            tracker.observe(liveScore) -> Classification.SPOOF
            else -> Classification.UNCERTAIN
        }
        return PassiveLivenessResult(liveScore, classification)
    }

    private fun cropFace(frame: Bitmap, box: RectF): Bitmap {
        val scale = ConsTrakrConstants.MINIFAS_CROP_SCALE
        val srcW = frame.width
        val srcH = frame.height
        val boxW = box.width()
        val boxH = box.height()
        val scaleFactor = minOf((srcH - 1) / boxH, (srcW - 1) / boxW, scale)
        val newW = boxW * scaleFactor
        val newH = boxH * scaleFactor
        val cx = box.centerX()
        val cy = box.centerY()
        val left = maxOf(0, (cx - newW / 2).toInt())
        val top = maxOf(0, (cy - newH / 2).toInt())
        val right = minOf(srcW - 1, (cx + newW / 2).toInt())
        val bottom = minOf(srcH - 1, (cy + newH / 2).toInt())
        val cropped = Bitmap.createBitmap(frame, left, top, right - left + 1, bottom - top + 1)
        return Bitmap.createScaledBitmap(
            cropped,
            ConsTrakrConstants.MINIFAS_INPUT_SIZE,
            ConsTrakrConstants.MINIFAS_INPUT_SIZE,
            true
        )
    }

    private fun bitmapToNchwBgr(bitmap: Bitmap): Array<Array<Array<FloatArray>>> {
        val size = ConsTrakrConstants.MINIFAS_INPUT_SIZE
        val input = Array(1) { Array(3) { Array(size) { FloatArray(size) } } }
        val pixels = IntArray(size * size)
        bitmap.getPixels(pixels, 0, size, 0, 0, size, size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                val pixel = pixels[y * size + x]
                input[0][0][y][x] = (pixel and 0xFF).toFloat()
                input[0][1][y][x] = ((pixel shr 8) and 0xFF).toFloat()
                input[0][2][y][x] = ((pixel shr 16) and 0xFF).toFloat()
            }
        }
        return input
    }

    private fun softmax(logits: FloatArray): FloatArray {
        val max = logits.max()
        val exp = FloatArray(logits.size) { exp((logits[it] - max).toDouble()).toFloat() }
        val sum = exp.sum()
        return FloatArray(logits.size) { exp[it] / sum }
    }

    companion object {
        private const val MODEL_ASSET = "models/minifasnetv2.onnx"
    }
}

enum class Classification { LIVE, SPOOF, UNCERTAIN }

data class PassiveLivenessResult(
    val score: Float,
    val classification: Classification,
    val message: String? = null
)

class AntiSpoofTracker(
    private val rejectAfter: Int = ConsTrakrConstants.MINIFAS_REJECT_AFTER_STREAK,
    private val liveThreshold: Float = ConsTrakrConstants.MINIFAS_LIVE_THRESHOLD
) {
    private var spoofStreak = 0

    fun reset() { spoofStreak = 0 }

    fun observe(liveScore: Float): Boolean {
        if (liveScore < liveThreshold) {
            spoofStreak++
        } else {
            spoofStreak = maxOf(0, spoofStreak - 2)
        }
        return spoofStreak >= rejectAfter
    }
}
