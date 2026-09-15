package com.constrakr.network

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface ConsTrakrApi {
    @GET("/constrakr-api/health")
    suspend fun health(): HealthResponse

    @POST("/constrakr-api/auth/admin/login")
    suspend fun login(@Body body: LoginRequest): LoginResponse

    @POST("/constrakr-api/devices/verify-admin-code")
    suspend fun verifyAdminCode(
        @Header("Authorization") auth: String,
        @Body body: DeviceAdminCodeVerifyRequest
    ): DeviceAdminCodeVerifyResponse

    @GET("/constrakr-api/devices")
    suspend fun getDevice(
        @Header("Authorization") auth: String,
        @Query("local_id") localId: String
    ): DeviceLookupResponse

    @POST("/constrakr-api/devices")
    suspend fun registerDevice(
        @Header("Authorization") auth: String,
        @Header("X-Device-Local-Id") deviceId: String,
        @Body body: DeviceRegisterRequest
    ): DeviceDto

    @GET("/constrakr-api/employees")
    suspend fun getEmployees(
        @Header("Authorization") auth: String,
        @Header("X-Device-Local-Id") deviceId: String
    ): EmployeesListResponse

    @POST("/constrakr-api/employees")
    suspend fun postEmployee(
        @Header("Authorization") auth: String,
        @Header("X-Device-Local-Id") deviceId: String,
        @Body body: EmployeeUpsertRequest
    ): EmployeeUpsertResponse

    @PUT("/constrakr-api/employees/{id}")
    suspend fun putEmployee(
        @Header("Authorization") auth: String,
        @Header("X-Device-Local-Id") deviceId: String,
        @Path("id") serverId: String,
        @Body body: EmployeeUpsertRequest
    ): EmployeeUpsertResponse

    @DELETE("/constrakr-api/employees/{id}")
    suspend fun deleteEmployee(
        @Header("Authorization") auth: String,
        @Header("X-Device-Local-Id") deviceId: String,
        @Path("id") serverId: String
    )

    @GET("/constrakr-api/job-sites")
    suspend fun getJobSites(
        @Header("Authorization") auth: String,
        @Header("X-Device-Local-Id") deviceId: String
    ): JobSitesListResponse

    @POST("/constrakr-api/job-sites")
    suspend fun postJobSite(
        @Header("Authorization") auth: String,
        @Header("X-Device-Local-Id") deviceId: String,
        @Body body: JobSitePostRequest
    ): JobSiteDto

    @DELETE("/constrakr-api/job-sites/{id}")
    suspend fun deleteJobSite(
        @Header("Authorization") auth: String,
        @Header("X-Device-Local-Id") deviceId: String,
        @Path("id") id: String
    )

    @GET("/constrakr-api/face-embeddings")
    suspend fun getFaceEmbeddings(
        @Header("Authorization") auth: String,
        @Header("X-Device-Local-Id") deviceId: String,
        @Query("employee_server_id") employeeServerId: String? = null
    ): FaceEmbeddingsListResponse

    @POST("/constrakr-api/face-embeddings")
    suspend fun postFaceEmbedding(
        @Header("Authorization") auth: String,
        @Header("X-Device-Local-Id") deviceId: String,
        @Body body: FaceEmbeddingPostRequest
    ): Map<String, String>

    @POST("/constrakr-api/face-enrollment-photos")
    suspend fun postFaceEnrollmentPhoto(
        @Header("Authorization") auth: String,
        @Header("X-Device-Local-Id") deviceId: String,
        @Body body: FaceEnrollmentPhotoPostRequest
    ): Map<String, String>

    @POST("/constrakr-api/employee-profile-photos")
    suspend fun postEmployeeProfilePhoto(
        @Header("Authorization") auth: String,
        @Header("X-Device-Local-Id") deviceId: String,
        @Body body: EmployeeProfilePhotoPostRequest
    ): Map<String, String>

    @GET("/constrakr-api/attendance")
    suspend fun getAttendance(
        @Header("Authorization") auth: String,
        @Header("X-Device-Local-Id") deviceId: String,
        @Query("start_date") startDate: String? = null,
        @Query("end_date") endDate: String? = null
    ): AttendanceListResponse

    @POST("/constrakr-api/attendance")
    suspend fun postAttendance(
        @Header("Authorization") auth: String,
        @Header("X-Device-Local-Id") deviceId: String,
        @Body body: AttendancePostRequest
    ): AttendanceUpsertResponse
}
