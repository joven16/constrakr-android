package com.constrakr.face

import android.graphics.RectF
import com.constrakr.config.ConsTrakrConstants
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.tasks.await

/**
 * Android port of iOS [FaceDetectionService.swift] (Vision → ML Kit).
 */
class FaceDetectionService {
    private val detector: FaceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .enableTracking()
            .build()
    )

    suspend fun detectPrimaryFace(image: InputImage): DetectedFace? {
        val faces = detector.process(image).await()
        return faces
            .filter { it.trackingId != null || it.boundingBox.width() > 0 }
            .maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
            ?.takeIf { (it.trackingId ?: 1) >= 0 }
            ?.let { mapFace(it, image.width, image.height) }
            ?.takeIf { it.confidence >= ConsTrakrConstants.MIN_FACE_CONFIDENCE }
    }

    private fun mapFace(face: Face, imageWidth: Int, imageHeight: Int): DetectedFace {
        val box = RectF(
            face.boundingBox.left.toFloat() / imageWidth,
            face.boundingBox.top.toFloat() / imageHeight,
            face.boundingBox.right.toFloat() / imageWidth,
            face.boundingBox.bottom.toFloat() / imageHeight
        )
        val (yaw, pitch) = HeadPoseEstimator.estimate(face)
        val leftEar = face.leftEyeOpenProbability ?: 0.5f
        val rightEar = face.rightEyeOpenProbability ?: 0.5f
        return DetectedFace(
            boundingBox = box,
            confidence = 1f, // ML Kit does not expose Vision-style confidence; use geometry gates
            yaw = yaw,
            pitch = pitch,
            roll = face.headEulerAngleZ,
            leftEyeOpenProbability = leftEar,
            rightEyeOpenProbability = rightEar,
            trackingId = face.trackingId
        )
    }

    fun close() = detector.close()
}

data class DetectedFace(
    val boundingBox: RectF,
    val confidence: Float,
    val yaw: Float,
    val pitch: Float,
    val roll: Float,
    val leftEyeOpenProbability: Float,
    val rightEyeOpenProbability: Float,
    val trackingId: Int?
)
