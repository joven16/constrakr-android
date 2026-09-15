package com.constrakr.attendance

import com.constrakr.config.ConsTrakrConstants
import com.constrakr.config.MatchThresholdSettings
import com.constrakr.domain.CheckType
import com.constrakr.domain.Employee
import com.constrakr.domain.FaceEmbedding
import com.constrakr.face.DetectedFace
import com.constrakr.liveness.Classification
import com.constrakr.liveness.PassiveLivenessResult
import com.constrakr.recognition.FaceMatchResult
import com.constrakr.recognition.FaceMatchingService
import com.constrakr.scanner.ScannerPhrases
import java.util.UUID

/**
 * Central attendance decision engine — port of iOS [AttendanceScannerViewModel] acceptance rules.
 * Compose UI must not contain final accept/reject logic.
 */
class AttendanceVerificationEngine(
    private val matchingService: FaceMatchingService = FaceMatchingService()
) {
    private var consensusEmployeeId: UUID? = null
    private var consensusCount = 0
    private var warmupFramesRemaining = ConsTrakrConstants.SCANNER_WARMUP_FRAMES

    fun resetSession() {
        consensusEmployeeId = null
        consensusCount = 0
        warmupFramesRemaining = ConsTrakrConstants.SCANNER_WARMUP_FRAMES
    }

    fun evaluateFrame(input: VerificationFrameInput): VerificationResult {
        if (warmupFramesRemaining > 0) {
            warmupFramesRemaining--
            return VerificationResult.NeedsAction("Warming up camera…")
        }

        val quality = assessFaceQuality(input.detectedFace)
        if (quality != null) return VerificationResult.NeedsAction(quality)

        if (input.passiveLiveness.classification == Classification.SPOOF) {
            return VerificationResult.Rejected("Presentation attack detected")
        }

        if (input.passiveLiveness.classification == Classification.UNCERTAIN &&
            input.requestAdaptiveChallenge
        ) {
            return VerificationResult.NeedsAction(
                message = "Additional verification required",
                challenge = input.nextRandomChallenge
            )
        }

        if (input.activeChallengeIncomplete) {
            return VerificationResult.NeedsAction("Complete the on-screen challenge")
        }

        val probe = input.probeEmbedding ?: return VerificationResult.NeedsAction("Hold still…")

        val (match, _) = matchingService.match(probe, input.enrolledEmployees)
        if (match == null) {
            return VerificationResult.Rejected("Not recognized")
        }

        if (input.sessionIdentityId != null && input.sessionIdentityId != match.employeeId) {
            return VerificationResult.Rejected("Identity changed during verification")
        }

        if (consensusEmployeeId == match.employeeId) {
            consensusCount++
        } else {
            consensusEmployeeId = match.employeeId
            consensusCount = 1
        }

        if (consensusCount < ConsTrakrConstants.SCANNER_CONSENSUS_FRAMES) {
            return VerificationResult.NeedsAction("Verifying identity…")
        }

        if (input.alreadyRecordedToday) {
            return VerificationResult.Rejected(
                alreadyRecordedReason(match.employeeName, input.checkType)
            )
        }

        if (input.siteRejected) {
            return VerificationResult.Rejected(input.siteRejectionMessage ?: "Wrong job site")
        }

        if (input.clockIntegrityFailed) {
            return VerificationResult.Rejected(input.clockIntegrityMessage ?: "Clock check failed")
        }

        return VerificationResult.Accepted(
            employeeId = match.employeeId,
            employeeCode = match.employeeCode,
            employeeName = match.employeeName,
            confidence = match.similarity,
            matchedPose = match.matchedPose,
            checkType = input.checkType
        )
    }

    private fun alreadyRecordedReason(name: String, checkType: CheckType): String =
        ScannerPhrases.punchAlready(name, checkType)

    private fun assessFaceQuality(face: DetectedFace?): String? {
        if (face == null) return "Look at the camera"
        if (face.confidence < ConsTrakrConstants.MIN_FACE_CONFIDENCE) return "Move closer"
        // TODO: both eyes visible, single face, lighting — extend with ML Kit landmarks
        return null
    }
}

data class VerificationFrameInput(
    val detectedFace: DetectedFace?,
    val probeEmbedding: FaceEmbedding?,
    val passiveLiveness: PassiveLivenessResult,
    val enrolledEmployees: List<Employee>,
    val checkType: CheckType,
    val alreadyRecordedToday: Boolean,
    val siteRejected: Boolean = false,
    val siteRejectionMessage: String? = null,
    val clockIntegrityFailed: Boolean = false,
    val clockIntegrityMessage: String? = null,
    val activeChallengeIncomplete: Boolean = false,
    val requestAdaptiveChallenge: Boolean = false,
    val nextRandomChallenge: LivenessChallengeType? = null,
    val sessionIdentityId: UUID? = null
)

sealed class VerificationResult {
    data class Accepted(
        val employeeId: UUID,
        val employeeCode: String,
        val employeeName: String,
        val confidence: Float,
        val matchedPose: com.constrakr.domain.FacePose,
        val checkType: CheckType
    ) : VerificationResult()

    data class Rejected(val reason: String) : VerificationResult()
    data class NeedsAction(
        val message: String,
        val challenge: LivenessChallengeType? = null
    ) : VerificationResult()
}

enum class LivenessChallengeType {
    BLINK, TURN_LEFT, TURN_RIGHT, MOVE_CLOSER
}
