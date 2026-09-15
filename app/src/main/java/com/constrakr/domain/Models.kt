package com.constrakr.domain

import java.util.UUID

enum class FacePose(val raw: String) {
    CENTER("center"),
    LEFT("left"),
    RIGHT("right"),
    UP("up"),
    DOWN("down");

    val displayName: String
        get() = when (this) {
            CENTER -> "Look Straight"
            LEFT -> "Look Left"
            RIGHT -> "Look Right"
            UP -> "Look Up"
            DOWN -> "Look Down"
        }

    companion object {
        val enrollmentOrder = listOf(CENTER, LEFT, RIGHT, UP, DOWN)
    }
}

enum class CheckType(val raw: String) {
    CHECK_IN("check_in"),
    CHECK_OUT("check_out");

    val displayName: String
        get() = when (this) {
            CHECK_IN -> "Time In"
            CHECK_OUT -> "Time Out"
        }
}

enum class SyncStatus { PENDING, SYNCING, SYNCED, FAILED }

data class FaceEmbedding(
    val pose: FacePose,
    val values: FloatArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FaceEmbedding) return false
        return pose == other.pose && values.contentEquals(other.values)
    }

    override fun hashCode(): Int = 31 * pose.hashCode() + values.contentHashCode()
}

data class Employee(
    val id: UUID,
    val serverId: String?,
    val employeeCode: String,
    val firstName: String,
    val lastName: String,
    val department: String,
    val position: String,
    val assignedSiteId: UUID?,
    val assignedSiteName: String = "",
    val assignedSiteLocation: String = "",
    val faceEmbeddings: List<FaceEmbedding>,
    val syncStatus: SyncStatus
) {
    val fullName: String get() = "$firstName $lastName".trim()
    val isEnrolled: Boolean get() = faceEmbeddings.isNotEmpty()
}

data class AttendanceRecord(
    val id: UUID,
    val serverId: String?,
    val employeeId: UUID,
    val checkType: CheckType,
    val timestampMillis: Long,
    val confidenceScore: Float,
    val syncStatus: SyncStatus,
    val notes: String? = null
)
