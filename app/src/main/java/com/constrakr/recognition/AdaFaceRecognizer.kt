package com.constrakr.recognition

import android.content.Context
import android.graphics.Bitmap
import com.constrakr.config.ConsTrakrConstants
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlin.math.sqrt

/**
 * AdaFace IR-18 (512-d) via ONNX — matches iOS [CoreMLFaceRecognizer.swift].
 * Input: 112×112 BGR, normalized (x/255 − 0.5) / 0.5 · Output: 512-d embedding (L2-normalized).
 */
class AdaFaceRecognizer(context: Context) {
    val isReady: Boolean get() = session != null
    val loadError: String? get() = _loadError

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null
    private var _loadError: String? = null

    init {
        try {
            context.assets.open(MODEL_ASSET).use { stream ->
                val bytes = stream.readBytes()
                session = env.createSession(bytes, OrtSession.SessionOptions())
            }
        } catch (e: Exception) {
            _loadError = e.message
            session = null
        }
    }

    fun embed(face: Bitmap): FloatArray {
        val model = session ?: throw IllegalStateException(
            _loadError ?: "AdaFace model not loaded. Add adaface_ir18.onnx to assets/models/"
        )
        require(face.width == ConsTrakrConstants.ADA_FACE_INPUT_SIZE &&
            face.height == ConsTrakrConstants.ADA_FACE_INPUT_SIZE) {
            "Face must be ${ConsTrakrConstants.ADA_FACE_INPUT_SIZE}×${ConsTrakrConstants.ADA_FACE_INPUT_SIZE}"
        }

        val size = ConsTrakrConstants.ADA_FACE_INPUT_SIZE
        val input = Array(1) { Array(3) { Array(size) { FloatArray(size) } } }
        val pixels = IntArray(size * size)
        face.getPixels(pixels, 0, size, 0, 0, size, size)
        // NCHW BGR — same normalization as iOS Core ML ImageType (scale=1/127.5, bias=-1)
        for (y in 0 until size) {
            for (x in 0 until size) {
                val pixel = pixels[y * size + x]
                val b = (pixel and 0xFF) / 255f
                val g = ((pixel shr 8) and 0xFF) / 255f
                val r = ((pixel shr 16) and 0xFF) / 255f
                input[0][0][y][x] = (b - 0.5f) / 0.5f
                input[0][1][y][x] = (g - 0.5f) / 0.5f
                input[0][2][y][x] = (r - 0.5f) / 0.5f
            }
        }

        val tensor = OnnxTensor.createTensor(env, input)
        return model.run(mapOf("input" to tensor)).use { result ->
            @Suppress("UNCHECKED_CAST")
            val raw = (result[0].value as Array<FloatArray>)[0]
            l2Normalize(raw)
        }
    }

    companion object {
        private const val MODEL_ASSET = "models/adaface_ir18.onnx"

        fun l2Normalize(values: FloatArray): FloatArray {
            var sum = 0f
            for (v in values) sum += v * v
            val norm = sqrt(sum)
            if (norm <= 1e-6f) return values
            return FloatArray(values.size) { values[it] / norm }
        }

        fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
            require(a.size == b.size)
            var dot = 0f
            for (i in a.indices) dot += a[i] * b[i]
            return dot
        }
    }
}
