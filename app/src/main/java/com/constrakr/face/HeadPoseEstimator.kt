package com.constrakr.face

import com.constrakr.config.ConsTrakrConstants
import com.constrakr.domain.FacePose
import com.google.mlkit.vision.face.Face
import kotlin.math.abs

/**
 * Port of iOS [HeadPoseEstimator.swift] — mirrored front-camera convention.
 * Subject's left → positive yaw · subject's right → negative yaw.
 */
object HeadPoseEstimator {
    private const val CENTER_YAW = 0.10f
    private const val CENTER_PITCH = 0.10f
    private const val TURN_YAW = 0.14f
    private const val PITCH_UP = 0.16f
    private const val PITCH_DOWN = 0.12f
    private const val PITCH_DOMINANCE = 0.02f

    fun estimate(face: Face): Pair<Float, Float> {
        // ML Kit: Y positive = face toward image right; X positive = looking up.
        val yaw = face.headEulerAngleY / 90f
        val pitch = face.headEulerAngleX / 90f
        return yaw to pitch
    }

    fun matches(pose: FacePose, yaw: Float, pitch: Float): Boolean = when (pose) {
        FacePose.CENTER -> abs(yaw) < CENTER_YAW && abs(pitch) < CENTER_PITCH
        // Mirrored selfie: subject's left → positive yaw (matches iOS).
        FacePose.LEFT -> yaw >= TURN_YAW && abs(yaw) >= abs(pitch)
        FacePose.RIGHT -> yaw <= -TURN_YAW && abs(yaw) >= abs(pitch)
        FacePose.UP -> pitch >= PITCH_UP && abs(pitch) >= abs(yaw) + PITCH_DOMINANCE
        FacePose.DOWN -> pitch <= -PITCH_DOWN && abs(pitch) >= abs(yaw) + PITCH_DOMINANCE
    }

    fun eyeAspectRatio(leftOpen: Float, rightOpen: Float): Float =
        (leftOpen + rightOpen) / 2f

    fun isBlinkClosed(ear: Float): Boolean =
        ear <= ConsTrakrConstants.LIVENESS_CLOSED_EAR

    fun isBlinkOpen(ear: Float): Boolean =
        ear >= ConsTrakrConstants.LIVENESS_OPEN_EAR
}
