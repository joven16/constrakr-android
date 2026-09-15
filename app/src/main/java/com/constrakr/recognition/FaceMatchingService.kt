package com.constrakr.recognition

import com.constrakr.config.ConsTrakrConstants
import com.constrakr.config.MatchThresholdSettings
import com.constrakr.domain.Employee
import com.constrakr.domain.FaceEmbedding
import com.constrakr.domain.FacePose

/**
 * Port of iOS [FaceMatchingService.swift] — multi-pose verification + margin check.
 */
class FaceMatchingService(
    var threshold: Float = MatchThresholdSettings.current,
    var minimumMargin: Float = MatchThresholdSettings.margin
) {
    fun match(
        probe: FaceEmbedding,
        employees: List<Employee>
    ): Pair<FaceMatchResult?, FaceMatchDiagnostics> {
        threshold = MatchThresholdSettings.current
        minimumMargin = MatchThresholdSettings.margin

        val probeValues = AdaFaceRecognizer.l2Normalize(probe.values)
        val enrolled = employees.filter { it.isEnrolled }

        val effectiveThreshold = if (enrolled.size <= 1) {
            maxOf(threshold, MatchThresholdSettings.soloFloor)
        } else {
            threshold
        }

        data class Candidate(val result: FaceMatchResult, val meanPoseScore: Float)

        val candidates = mutableListOf<Candidate>()

        for (employee in enrolled) {
            val poseScores = mutableListOf<Pair<FacePose, Float>>()
            for (emb in employee.faceEmbeddings) {
                if (emb.values.size != probeValues.size) continue
                val score = AdaFaceRecognizer.cosineSimilarity(probeValues, emb.values)
                poseScores.add(emb.pose to score)
            }
            if (poseScores.isEmpty()) continue

            val best = poseScores.maxByOrNull { it.second }!!
            val topScores = poseScores.map { it.second }.sortedDescending()
            val topCount = minOf(2, topScores.size)
            val topMean = topScores.take(topCount).average().toFloat()
            val meanFloor = effectiveThreshold - ConsTrakrConstants.MULTI_POSE_MEAN_SLACK

            if (best.second >= effectiveThreshold && topMean >= meanFloor) {
                candidates.add(
                    Candidate(
                        FaceMatchResult(
                            employeeId = employee.id,
                            employeeCode = employee.employeeCode,
                            employeeName = employee.fullName,
                            similarity = best.second,
                            matchedPose = best.first
                        ),
                        topMean
                    )
                )
            }
        }

        if (candidates.isEmpty()) {
            return null to FaceMatchDiagnostics(accepted = false, resultLabel = "No match")
        }

        val sorted = candidates.sortedByDescending { it.result.similarity }
        val best = sorted.first()
        if (sorted.size >= 2) {
            val margin = best.result.similarity - sorted[1].result.similarity
            if (margin < minimumMargin) {
                return null to FaceMatchDiagnostics(
                    accepted = false,
                    resultLabel = "Ambiguous match (margin $margin)"
                )
            }
        }

        return best.result to FaceMatchDiagnostics(accepted = true, resultLabel = "Accepted")
    }
}

data class FaceMatchResult(
    val employeeId: java.util.UUID,
    val employeeCode: String,
    val employeeName: String,
    val similarity: Float,
    val matchedPose: FacePose
)

data class FaceMatchDiagnostics(
    val accepted: Boolean,
    val resultLabel: String
)
