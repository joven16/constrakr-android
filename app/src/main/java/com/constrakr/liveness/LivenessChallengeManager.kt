package com.constrakr.liveness

import com.constrakr.attendance.LivenessChallengeType
import com.constrakr.config.ConsTrakrConstants
import com.constrakr.face.DetectedFace
import com.constrakr.face.HeadPoseEstimator
import kotlin.math.abs
import kotlin.random.Random

/**
 * Adaptive active liveness — port of iOS [LivenessChecker.swift] + Android randomized challenges.
 */
class LivenessChallengeManager {
    var challenge: LivenessChallengeType? = null
        private set
    private var blinkClosedFrames = 0
    private var blinkOpenFrames = 0
    private var sawTargetPose = false
    private var returnToCenterFrames = 0
    private var closerBaselineArea: Float? = null
    private var closerGoodFrames = 0
    var isComplete = false
        private set

    fun startRandomChallenge(): LivenessChallengeType {
        challenge = LivenessChallengeType.entries.random(Random.Default)
        resetProgress()
        return challenge!!
    }

    fun reset() {
        challenge = null
        isComplete = false
        resetProgress()
    }

    fun update(face: DetectedFace?, faceAreaRatio: Float): String? {
        val c = challenge ?: return null
        if (face == null) return "Look at the camera"
        val ear = HeadPoseEstimator.eyeAspectRatio(
            face.leftEyeOpenProbability,
            face.rightEyeOpenProbability
        )
        return when (c) {
            LivenessChallengeType.BLINK -> {
                when {
                    HeadPoseEstimator.isBlinkClosed(ear) -> {
                        blinkClosedFrames++
                        "Blink slowly"
                    }
                    blinkClosedFrames >= 2 && HeadPoseEstimator.isBlinkOpen(ear) -> {
                        blinkOpenFrames++
                        if (blinkOpenFrames >= 2) complete("Blink verified")
                        else "Open your eyes"
                    }
                    else -> "Blink both eyes"
                }
            }
            LivenessChallengeType.TURN_LEFT -> updateTurn(
                reached = face.yaw >= ConsTrakrConstants.LIVENESS_YAW_TARGET,
                centered = abs(face.yaw) < CENTER_YAW && abs(face.pitch) < CENTER_PITCH,
                turnInstruction = "Turn toward your left shoulder"
            )
            LivenessChallengeType.TURN_RIGHT -> updateTurn(
                reached = face.yaw <= -ConsTrakrConstants.LIVENESS_YAW_TARGET,
                centered = abs(face.yaw) < CENTER_YAW && abs(face.pitch) < CENTER_PITCH,
                turnInstruction = "Turn toward your right shoulder"
            )
            LivenessChallengeType.MOVE_CLOSER -> {
                if (closerBaselineArea == null) closerBaselineArea = faceAreaRatio
                val base = closerBaselineArea ?: faceAreaRatio
                if (faceAreaRatio >= base * ConsTrakrConstants.LIVENESS_CLOSER_SCALE) {
                    closerGoodFrames++
                    if (closerGoodFrames >= 4) complete("Move closer verified")
                    else "A little closer"
                } else "Move closer to the camera"
            }
        }
    }

    /** iOS parity: turn to target, then return to center before passing. */
    private fun updateTurn(
        reached: Boolean,
        centered: Boolean,
        turnInstruction: String
    ): String {
        if (reached) {
            sawTargetPose = true
            returnToCenterFrames = 0
            return "Now look straight at the camera"
        }
        if (!sawTargetPose) {
            return turnInstruction
        }
        return if (centered) {
            returnToCenterFrames++
            if (returnToCenterFrames >= RETURN_TO_CENTER_FRAMES) {
                complete("Turn verified")
            } else {
                "Now look straight at the camera"
            }
        } else {
            returnToCenterFrames = 0
            "Now look straight at the camera"
        }
    }

    private fun complete(msg: String): String {
        isComplete = true
        return msg
    }

    private fun resetProgress() {
        blinkClosedFrames = 0
        blinkOpenFrames = 0
        sawTargetPose = false
        returnToCenterFrames = 0
        closerBaselineArea = null
        closerGoodFrames = 0
        isComplete = false
    }

    companion object {
        private const val CENTER_YAW = 0.12f
        private const val CENTER_PITCH = 0.12f
        private const val RETURN_TO_CENTER_FRAMES = 3
    }
}
