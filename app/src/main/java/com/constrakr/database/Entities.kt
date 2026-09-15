package com.constrakr.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "employees")
data class EmployeeEntity(
    @PrimaryKey val id: String,
    val serverId: String?,
    val employeeCode: String,
    val firstName: String,
    val lastName: String,
    val department: String,
    val position: String,
    val assignedSiteId: String?,
    val assignedSiteName: String,
    val assignedSiteLocation: String,
    val faceEmbeddingsEncrypted: ByteArray,
    val syncStatus: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)

@Entity(tableName = "attendance")
data class AttendanceEntity(
    @PrimaryKey val id: String,
    val serverId: String?,
    val employeeId: String,
    val employeeServerId: String?,
    val checkType: String,
    val timestampMillis: Long,
    val syncStatus: String,
    val confidenceScore: Double,
    val notes: String?,
    val punchSiteId: String?,
    val punchSiteName: String?,
    val punchSiteLocation: String?
)

@Entity(tableName = "face_embeddings")
data class FaceEmbeddingEntity(
    @PrimaryKey val localId: String = UUID.randomUUID().toString(),
    val serverId: String?,
    val employeeLocalId: String,
    val employeeServerId: String?,
    val pose: String,
    val encryptedValues: ByteArray,
    val syncStatus: String
)

@Entity(tableName = "face_enrollment_photos")
data class FaceEnrollmentPhotoEntity(
    @PrimaryKey val localId: String = UUID.randomUUID().toString(),
    val serverId: String?,
    val employeeLocalId: String,
    val employeeServerId: String?,
    val pose: String,
    val jpegData: ByteArray,
    val syncStatus: String
)
