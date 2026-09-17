package com.constrakr.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.UUID

@JsonClass(generateAdapter = true)
data class HealthResponse(
    @Json(name = "status") val status: String?,
    @Json(name = "server_time") val serverTime: String?
)

@JsonClass(generateAdapter = true)
data class LoginRequest(
    @Json(name = "username") val username: String,
    @Json(name = "password") val password: String
)

@JsonClass(generateAdapter = true)
data class LoginResponse(
    @Json(name = "access_token") val accessToken: String?,
    @Json(name = "token") val token: String?,
    @Json(name = "expires_in") val expiresIn: Int? = null
) {
    val resolvedToken: String? get() = accessToken ?: token
}

@JsonClass(generateAdapter = true)
data class EmployeeDto(
    @Json(name = "server_id") val serverId: String?,
    @Json(name = "local_id") val localId: UUID,
    @Json(name = "employee_code") val employeeCode: String,
    @Json(name = "first_name") val firstName: String,
    @Json(name = "last_name") val lastName: String,
    @Json(name = "department") val department: String,
    @Json(name = "position") val position: String,
    @Json(name = "assigned_site_id") val assignedSiteId: UUID? = null,
    @Json(name = "assigned_site_name") val assignedSiteName: String? = null,
    @Json(name = "assigned_site_location") val assignedSiteLocation: String? = null,
    @Json(name = "updated_at") val updatedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class FaceEmbeddingDto(
    @Json(name = "server_id") val serverId: String?,
    @Json(name = "local_id") val localId: UUID,
    @Json(name = "employee_server_id") val employeeServerId: String?,
    @Json(name = "employee_local_id") val employeeLocalId: UUID,
    @Json(name = "pose") val pose: String,
    @Json(name = "encrypted_values_base64") val encryptedValuesBase64: String
)

@JsonClass(generateAdapter = true)
data class EmployeeUpsertRequest(
    @Json(name = "local_id") val localId: UUID,
    @Json(name = "employee_code") val employeeCode: String,
    @Json(name = "first_name") val firstName: String,
    @Json(name = "last_name") val lastName: String,
    @Json(name = "department") val department: String,
    @Json(name = "position") val position: String,
    @Json(name = "assigned_site_id") val assignedSiteId: UUID? = null,
    @Json(name = "assigned_site_name") val assignedSiteName: String? = null,
    @Json(name = "assigned_site_location") val assignedSiteLocation: String? = null
)

@JsonClass(generateAdapter = true)
data class EmployeeUpsertResponse(
    @Json(name = "server_id") val serverId: String?,
    @Json(name = "local_id") val localId: UUID
)

@JsonClass(generateAdapter = true)
data class AttendanceDto(
    @Json(name = "server_id") val serverId: String?,
    @Json(name = "local_id") val localId: UUID,
    @Json(name = "employee_server_id") val employeeServerId: String?,
    @Json(name = "employee_local_id") val employeeLocalId: UUID,
    @Json(name = "check_type") val checkType: String,
    @Json(name = "timestamp") val timestamp: String,
    @Json(name = "confidence_score") val confidenceScore: Double?,
    @Json(name = "notes") val notes: String?,
    @Json(name = "is_void") val isVoid: Boolean = false
)

@JsonClass(generateAdapter = true)
data class AttendancePostRequest(
    @Json(name = "local_id") val localId: UUID,
    @Json(name = "employee_local_id") val employeeLocalId: UUID,
    @Json(name = "employee_server_id") val employeeServerId: String?,
    @Json(name = "check_type") val checkType: String,
    @Json(name = "timestamp") val timestamp: String,
    @Json(name = "confidence_score") val confidenceScore: Double,
    @Json(name = "notes") val notes: String?,
    @Json(name = "punch_photo_base64") val punchPhotoBase64: String? = null
)

@JsonClass(generateAdapter = true)
data class AttendanceUpsertResponse(
    @Json(name = "server_id") val serverId: String?,
    @Json(name = "local_id") val localId: UUID
)

@JsonClass(generateAdapter = true)
data class JobSiteDto(
    @Json(name = "id") val id: UUID,
    @Json(name = "name") val name: String,
    @Json(name = "location_label") val locationLabel: String?,
    @Json(name = "latitude") val latitude: Double,
    @Json(name = "longitude") val longitude: Double,
    @Json(name = "radius_meters") val radiusMeters: Double,
    @Json(name = "updated_at") val updatedAt: String?,
    @Json(name = "deleted_at") val deletedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class JobSitePostRequest(
    @Json(name = "id") val id: UUID,
    @Json(name = "name") val name: String,
    @Json(name = "location_label") val locationLabel: String?,
    @Json(name = "latitude") val latitude: Double,
    @Json(name = "longitude") val longitude: Double,
    @Json(name = "radius_meters") val radiusMeters: Double
)

@JsonClass(generateAdapter = true)
data class DeviceAdminCodeVerifyRequest(
    @Json(name = "local_id") val localId: UUID,
    @Json(name = "passcode") val passcode: String
)

@JsonClass(generateAdapter = true)
data class DeviceAdminCodeVerifyResponse(
    @Json(name = "valid") val valid: Boolean,
    @Json(name = "error") val error: String?,
    @Json(name = "assigned_user_name") val assignedUserName: String?
)

@JsonClass(generateAdapter = true)
data class DeviceAssignedUserDto(
    @Json(name = "id") val id: Long?,
    @Json(name = "name") val name: String?,
    @Json(name = "username") val username: String?,
    @Json(name = "admin_code_set") val adminCodeSet: Boolean?
)

@JsonClass(generateAdapter = true)
data class DeviceDto(
    @Json(name = "id") val id: String? = null,
    @Json(name = "local_id") val localId: UUID,
    @Json(name = "name") val name: String?,
    @Json(name = "app_version") val appVersion: String?,
    @Json(name = "assigned_users") val assignedUsers: List<DeviceAssignedUserDto>?,
    @Json(name = "assigned_user_name") val assignedUserName: String?,
    @Json(name = "is_blocked") val isBlocked: Boolean?,
    @Json(name = "blocked_reason") val blockedReason: String?,
    @Json(name = "admin_code_required") val adminCodeRequired: Boolean?,
    @Json(name = "play_sound_request_id") val playSoundRequestId: String? = null,
    @Json(name = "play_sound_requested_at") val playSoundRequestedAt: String? = null,
    @Json(name = "play_sound_played_at") val playSoundPlayedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class DevicePlaySoundAckRequest(
    @Json(name = "device_id") val deviceId: String,
    @Json(name = "request_id") val requestId: String
)

@JsonClass(generateAdapter = true)
data class DeviceRegisterRequest(
    @Json(name = "local_id") val localId: UUID,
    @Json(name = "name") val name: String,
    @Json(name = "app_version") val appVersion: String
)

@JsonClass(generateAdapter = true)
data class DeviceHeartbeatRequest(
    @Json(name = "device_id") val deviceId: String,
    @Json(name = "site_id") val siteId: String?,
    @Json(name = "latitude") val latitude: Double?,
    @Json(name = "longitude") val longitude: Double?,
    @Json(name = "accuracy_meters") val accuracyMeters: Float?,
    @Json(name = "battery_percent") val batteryPercent: Int,
    @Json(name = "is_charging") val isCharging: Boolean,
    @Json(name = "network_type") val networkType: String?,
    @Json(name = "is_online") val isOnline: Boolean,
    @Json(name = "is_kiosk_mode_active") val isKioskModeActive: Boolean,
    @Json(name = "device_model") val deviceModel: String,
    @Json(name = "android_version") val androidVersion: String,
    @Json(name = "app_version") val appVersion: String,
    @Json(name = "timestamp") val timestamp: Long
)

@JsonClass(generateAdapter = true)
data class DeviceLookupResponse(
    @Json(name = "device") val device: DeviceDto?
)

@JsonClass(generateAdapter = true)
data class EmployeesListResponse(
    @Json(name = "employees") val employees: List<EmployeeDto>? = null
) {
    val items: List<EmployeeDto> get() = employees.orEmpty()
}

@JsonClass(generateAdapter = true)
data class JobSitesListResponse(
    @Json(name = "job_sites") val jobSites: List<JobSiteDto>? = null
) {
    val items: List<JobSiteDto> get() = jobSites.orEmpty()
}

@JsonClass(generateAdapter = true)
data class FaceEmbeddingsListResponse(
    @Json(name = "face_embeddings") val faceEmbeddings: List<FaceEmbeddingDto>? = null
) {
    val items: List<FaceEmbeddingDto> get() = faceEmbeddings.orEmpty()
}

@JsonClass(generateAdapter = true)
data class AttendanceListResponse(
    @Json(name = "attendance") val attendance: List<AttendanceDto>? = null
) {
    val items: List<AttendanceDto> get() = attendance.orEmpty()
}

@JsonClass(generateAdapter = true)
data class FaceEmbeddingPostRequest(
    @Json(name = "local_id") val localId: UUID,
    @Json(name = "employee_local_id") val employeeLocalId: UUID,
    @Json(name = "employee_server_id") val employeeServerId: String?,
    @Json(name = "pose") val pose: String,
    @Json(name = "encrypted_values_base64") val encryptedValuesBase64: String
)

@JsonClass(generateAdapter = true)
data class FaceEnrollmentPhotoPostRequest(
    @Json(name = "local_id") val localId: UUID,
    @Json(name = "employee_local_id") val employeeLocalId: UUID,
    @Json(name = "employee_server_id") val employeeServerId: String?,
    @Json(name = "pose") val pose: String,
    @Json(name = "jpeg_base64") val jpegBase64: String
)

@JsonClass(generateAdapter = true)
data class EmployeeProfilePhotoPostRequest(
    @Json(name = "employee_local_id") val employeeLocalId: UUID,
    @Json(name = "employee_server_id") val employeeServerId: String?,
    @Json(name = "jpeg_base64") val jpegBase64: String
)

@JsonClass(generateAdapter = true)
data class EmployeeProfilePhotoDto(
    @Json(name = "employee_server_id") val employeeServerId: String? = null,
    @Json(name = "employee_local_id") val employeeLocalId: UUID? = null,
    @Json(name = "has_jpeg_data") val hasJpegData: Boolean = false,
    @Json(name = "jpeg_base64") val jpegBase64: String? = null
)

@JsonClass(generateAdapter = true)
data class EmployeeProfilePhotosListResponse(
    @Json(name = "employee_profile_photos") val employeeProfilePhotos: List<EmployeeProfilePhotoDto>? = null
) {
    val items: List<EmployeeProfilePhotoDto> get() = employeeProfilePhotos.orEmpty()
}
