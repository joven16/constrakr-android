package com.constrakr.sync

import android.content.Context
import android.util.Base64
import com.constrakr.attendance.AttendancePhotoStore
import com.constrakr.enrollment.ProfilePhotoStore
import com.constrakr.database.AttendanceRepository
import com.constrakr.database.EmployeeEntity
import com.constrakr.database.EmployeeRepository
import com.constrakr.domain.CheckType
import com.constrakr.domain.DeviceStore
import com.constrakr.domain.JobSite
import com.constrakr.domain.JobSiteStore
import com.constrakr.network.ApiClient
import com.constrakr.network.AttendancePostRequest
import com.constrakr.network.ConsTrakrApi
import com.constrakr.network.EmployeeUpsertRequest
import com.constrakr.network.FaceEmbeddingPostRequest
import com.constrakr.network.DeviceRegisterRequest
import com.constrakr.network.JobSitePostRequest
import com.constrakr.admin.PendingEmployeeDeletionStore
import com.constrakr.domain.Employee
import com.constrakr.util.AppLog
import com.constrakr.BuildConfig
import retrofit2.HttpException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

class SyncCoordinator(
    private val context: Context,
    private val api: ConsTrakrApi,
    private val employees: EmployeeRepository,
    private val attendance: AttendanceRepository,
    private val jobSiteStore: JobSiteStore,
    private val deviceStore: DeviceStore
) {
    private val prefs = context.getSharedPreferences("constrakr.sync", Context.MODE_PRIVATE)

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status

    private val _authState = MutableStateFlow(readAuthState())
    val authState: StateFlow<SyncAuthState> = _authState.asStateFlow()

    val deviceLocalId: String get() = deviceStore.localId

    val isSignedIn: Boolean get() = !authToken.isNullOrBlank()

    var authToken: String?
        get() = prefs.getString(KEY_AUTH_TOKEN, null)
        private set(value) {
            prefs.edit().apply {
                if (value.isNullOrBlank()) remove(KEY_AUTH_TOKEN) else putString(KEY_AUTH_TOKEN, value)
            }.apply()
            _authState.value = readAuthState()
        }

    var syncUsername: String?
        get() = prefs.getString(KEY_SYNC_USERNAME, null)
        private set(value) {
            prefs.edit().apply {
                if (value.isNullOrBlank()) remove(KEY_SYNC_USERNAME) else putString(KEY_SYNC_USERNAME, value)
            }.apply()
            _authState.value = readAuthState()
        }

    fun signOut() {
        authToken = null
        syncUsername = null
        _status.value = null
    }

    private fun readAuthState(): SyncAuthState = SyncAuthState(
        isSignedIn = !prefs.getString(KEY_AUTH_TOKEN, null).isNullOrBlank(),
        username = prefs.getString(KEY_SYNC_USERNAME, null)
    )

    suspend fun pingHealth(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val r = api.health()
            "Server OK · ${r.serverTime ?: r.status ?: "online"}"
        }
    }

    suspend fun login(username: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val r = api.login(com.constrakr.network.LoginRequest(username, password))
            authToken = r.resolvedToken ?: error("No token in response")
            syncUsername = username.trim()
            registerDeviceWithServer()
                .getOrThrow()
            "Signed in as $syncUsername"
        }
    }

    /** Registers this installation so it appears under People → Devices on the web. */
    suspend fun registerDeviceWithServer(): Result<Unit> = withContext(Dispatchers.IO) {
        val token = authToken ?: return@withContext Result.failure(
            IllegalStateException("Sign in first to register this device.")
        )
        val auth = ApiClient.authHeader(token) ?: return@withContext Result.failure(
            IllegalStateException("Invalid auth token.")
        )
        runCatching {
            AppLog.d("Registering device local_id=$deviceLocalId name=${deviceStore.deviceName}")
            val dto = api.registerDevice(
                auth,
                deviceLocalId,
                DeviceRegisterRequest(
                    localId = UUID.fromString(deviceLocalId),
                    name = deviceStore.deviceName,
                    appVersion = BuildConfig.VERSION_NAME
                )
            )
            deviceStore.applyFromServer(dto)
            AppLog.d("Device registered on server")
        }.recoverCatching { first ->
            AppLog.w("Device register POST failed, trying GET", first)
            val existing = api.getDevice(auth, deviceLocalId).device
                ?: throw first
            deviceStore.applyFromServer(existing)
        }
    }

    suspend fun refreshDeviceState(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val token = authToken ?: return@runCatching Unit
            val auth = ApiClient.authHeader(token) ?: return@runCatching Unit
            registerDeviceWithServer()
            api.getDevice(auth, deviceLocalId).device?.let { deviceStore.applyFromServer(it) }
            Unit
        }
    }

    suspend fun syncPending(focusDate: LocalDate? = null): Result<Int> = withContext(Dispatchers.IO) {
        val token = authToken ?: return@withContext Result.failure(
            IllegalStateException("Sign in under More → Sync Account")
        )
        val auth = ApiClient.authHeader(token)!!
        var total = 0

        runCatching {
            _status.value = "Syncing…"
            registerDeviceWithServer()
            processPendingEmployeeDeletions()

            _status.value = "Syncing job sites…"
            total += syncJobSites(auth)

            _status.value = "Uploading employee changes…"
            total += uploadPendingEmployeeUpdates(auth)
            total += uploadPendingNewEmployees(auth)

            _status.value = "Downloading roster…"
            val remote = api.getEmployees(auth, deviceLocalId).items
            val localByServerId = mutableMapOf<String, String>()
            for (dto in remote) {
                val entity = employees.upsertFromRemote(dto)
                dto.serverId?.let { localByServerId[it] = entity.id }
            }
            employees.reconcileRemoteRoster(remote)

            _status.value = "Downloading face data…"
            for (dto in api.getFaceEmbeddings(auth, deviceLocalId).items) {
                val localId = dto.employeeServerId?.let { localByServerId[it] }
                    ?: dto.employeeLocalId.toString()
                employees.upsertEmbeddingFromRemote(dto, localId)
            }

            _status.value = "Uploading face data…"
            total += uploadPendingFaceEmbeddings(auth)
            total += uploadPendingEnrollmentPhotos(auth)
            total += uploadPendingProfilePhotos(auth)

            _status.value = "Uploading attendance…"
            for (row in attendance.getPending()) {
                val punchJpeg = AttendancePhotoStore.load(context, row.id)
                val body = AttendancePostRequest(
                    localId = UUID.fromString(row.id),
                    employeeLocalId = UUID.fromString(row.employeeId),
                    employeeServerId = row.employeeServerId,
                    checkType = row.checkType,
                    timestamp = Instant.ofEpochMilli(row.timestampMillis).toString(),
                    confidenceScore = row.confidenceScore,
                    notes = row.notes,
                    punchPhotoBase64 = punchJpeg?.let {
                        Base64.encodeToString(it, Base64.NO_WRAP)
                    }
                )
                val resp = api.postAttendance(auth, deviceLocalId, body)
                resp.serverId?.let {
                    attendance.markSynced(row.id, it)
                    AttendancePhotoStore.delete(context, row.id)
                }
                total++
            }

            _status.value = "Downloading attendance…"
            total += pullAttendance(auth, focusDate ?: LocalDate.now())

            _status.value = if (total > 0) "Synced $total item(s)" else "Up to date"
        }.fold(
            onSuccess = { Result.success(total) },
            onFailure = {
                AppLog.e("Sync failed", it)
                _status.value = it.message ?: "Sync failed"
                Result.failure(it)
            }
        )
    }

    /** Push a newly registered employee and all pending face poses to the server (iOS parity). */
    suspend fun syncRegistration(localEmployeeId: UUID): Result<Int> = withContext(Dispatchers.IO) {
        val token = authToken ?: return@withContext Result.failure(
            IllegalStateException("Sign in under More → Sync Account, then register again or tap Sync now.")
        )
        val auth = ApiClient.authHeader(token)!!
        val id = localEmployeeId.toString()
        runCatching {
            _status.value = "Uploading registration…"
            registerDeviceWithServer()
            var total = 0
            val entity = employees.getEntity(id) ?: error("Employee not found on device")
            if (entity.syncStatus == com.constrakr.domain.SyncStatus.PENDING.name.lowercase()) {
                val resp = api.postEmployee(
                    auth,
                    deviceLocalId,
                    EmployeeUpsertRequest(
                        localId = UUID.fromString(entity.id),
                        employeeCode = entity.employeeCode,
                        firstName = entity.firstName,
                        lastName = entity.lastName,
                        department = entity.department,
                        position = entity.position,
                        assignedSiteId = entity.assignedSiteId?.let(UUID::fromString),
                        assignedSiteName = entity.assignedSiteName,
                        assignedSiteLocation = entity.assignedSiteLocation
                    )
                )
                val serverId = resp.serverId ?: error("Employee upload missing server_id")
                employees.markSynced(entity.id, serverId)
                total++
            }
            total += uploadPendingFaceEmbeddings(auth, id)
            total += uploadPendingEnrollmentPhotos(auth, id)
            total += uploadPendingProfilePhotos(auth, id)
            _status.value = if (total > 0) "Uploaded $total registration item(s)" else "Registration uploaded"
            total
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = {
                AppLog.e("Registration sync failed", it)
                _status.value = it.message ?: "Registration sync failed"
                Result.failure(it)
            }
        )
    }

    private suspend fun uploadPendingEmployeeUpdates(auth: String): Int {
        var count = 0
        for (row in employees.getPendingUpdates()) {
            val serverId = row.serverId?.trim()?.takeIf { it.isNotEmpty() } ?: continue
            api.putEmployee(auth, deviceLocalId, serverId, row.toUpsertRequest())
            employees.markProfileSynced(row.id)
            count++
            AppLog.d("Pushed employee profile update: ${row.employeeCode}")
        }
        return count
    }

    private suspend fun uploadPendingNewEmployees(auth: String): Int {
        var count = 0
        for (row in employees.getPendingNew()) {
            val resp = api.postEmployee(auth, deviceLocalId, row.toUpsertRequest())
            val serverId = resp.serverId ?: error("Employee upload missing server_id")
            employees.markSynced(row.id, serverId)
            count++
            count += uploadPendingFaceEmbeddings(auth, row.id)
            count += uploadPendingEnrollmentPhotos(auth, row.id)
            count += uploadPendingProfilePhotos(auth, row.id)
        }
        return count
    }

    private suspend fun uploadPendingProfilePhotos(auth: String, employeeLocalId: String? = null): Int {
        val pendingIds = ProfilePhotoStore.pendingEmployeeIds(context).let { ids ->
            if (employeeLocalId == null) ids else ids.filter { it == employeeLocalId }
        }
        var count = 0
        for (localId in pendingIds) {
            val jpeg = ProfilePhotoStore.load(context, UUID.fromString(localId)) ?: continue
            val employee = employees.getEntity(localId) ?: continue
            val serverId = employee.serverId
                ?: error("Profile photo: upload employee first")
            api.postEmployeeProfilePhoto(
                auth,
                deviceLocalId,
                com.constrakr.network.EmployeeProfilePhotoPostRequest(
                    employeeLocalId = UUID.fromString(localId),
                    employeeServerId = serverId,
                    jpegBase64 = Base64.encodeToString(jpeg, Base64.NO_WRAP)
                )
            )
            ProfilePhotoStore.delete(context, localId)
            count++
            AppLog.d("Uploaded profile photo for employee $localId")
        }
        return count
    }

    private suspend fun uploadPendingFaceEmbeddings(auth: String, employeeLocalId: String? = null): Int {
        val embDao = employees.embeddingDao()
        val pending = embDao.pending().let { rows ->
            if (employeeLocalId == null) rows
            else rows.filter { it.employeeLocalId == employeeLocalId }
        }
        var count = 0
        for (row in pending) {
            val employee = employees.getEntity(row.employeeLocalId)
                ?: error("Face pose ${row.pose}: employee missing on device")
            val serverId = employee.serverId
                ?: error("Face pose ${row.pose}: upload employee first")
            val body = FaceEmbeddingPostRequest(
                localId = UUID.fromString(row.localId),
                employeeLocalId = UUID.fromString(row.employeeLocalId),
                employeeServerId = serverId,
                pose = row.pose,
                encryptedValuesBase64 = Base64.encodeToString(row.encryptedValues, Base64.NO_WRAP)
            )
            val resp = api.postFaceEmbedding(auth, deviceLocalId, body)
            val returnedId = resp["server_id"] ?: resp["id"]
                ?: error("Face pose ${row.pose}: server returned no id")
            embDao.markSynced(row.localId, returnedId)
            count++
            AppLog.d("Uploaded face pose ${row.pose} for employee ${row.employeeLocalId}")
        }
        return count
    }

    private suspend fun uploadPendingEnrollmentPhotos(auth: String, employeeLocalId: String? = null): Int {
        val photoDao = employees.enrollmentPhotoDao()
        val pending = photoDao.pending().let { rows ->
            if (employeeLocalId == null) rows
            else rows.filter { it.employeeLocalId == employeeLocalId }
        }
        var count = 0
        for (row in pending) {
            val employee = employees.getEntity(row.employeeLocalId)
                ?: error("Enrollment photo ${row.pose}: employee missing on device")
            val serverId = employee.serverId
                ?: error("Enrollment photo ${row.pose}: upload employee first")
            val body = com.constrakr.network.FaceEnrollmentPhotoPostRequest(
                localId = UUID.fromString(row.localId),
                employeeLocalId = UUID.fromString(row.employeeLocalId),
                employeeServerId = serverId,
                pose = row.pose,
                jpegBase64 = Base64.encodeToString(row.jpegData, Base64.NO_WRAP)
            )
            val resp = api.postFaceEnrollmentPhoto(auth, deviceLocalId, body)
            val returnedId = resp["server_id"] ?: resp["id"]
                ?: error("Enrollment photo ${row.pose}: server returned no id")
            photoDao.markSynced(row.localId, returnedId)
            count++
            AppLog.d("Uploaded enrollment photo ${row.pose} for employee ${row.employeeLocalId}")
        }
        return count
    }

    suspend fun deleteEmployee(employee: Employee) = withContext(Dispatchers.IO) {
        employee.serverId?.let { PendingEmployeeDeletionStore.enqueue(context, it) }
        employees.delete(employee.id)
        processPendingEmployeeDeletions()
    }

    suspend fun processPendingEmployeeDeletions() {
        val token = authToken ?: return
        val auth = ApiClient.authHeader(token) ?: return
        for (serverId in PendingEmployeeDeletionStore.pending(context)) {
            runCatching {
                api.deleteEmployee(auth, deviceLocalId, serverId)
                PendingEmployeeDeletionStore.remove(context, serverId)
            }.onFailure { error ->
                if (error is HttpException && error.code() == 404) {
                    PendingEmployeeDeletionStore.remove(context, serverId)
                }
            }
        }
    }

    private suspend fun syncJobSites(auth: String): Int {
        var count = 0
        for (id in jobSiteStore.pendingDeleteIds) {
            runCatching {
                api.deleteJobSite(auth, deviceLocalId, id.toString())
                jobSiteStore.clearPendingDelete(id)
                count++
            }
        }
        for (id in jobSiteStore.pendingUploadIds) {
            val site = jobSiteStore.site(id) ?: continue
            runCatching {
                api.postJobSite(
                    auth, deviceLocalId,
                    JobSitePostRequest(
                        id = site.id,
                        name = site.name,
                        locationLabel = site.locationLabel.ifEmpty { null },
                        latitude = site.latitude,
                        longitude = site.longitude,
                        radiusMeters = site.radiusMeters
                    )
                )
                jobSiteStore.clearPendingUpload(id)
                count++
            }
        }
        val remote = api.getJobSites(auth, deviceLocalId).items
            .filter { it.deletedAt == null }
            .map { dto ->
                JobSite(
                    id = dto.id,
                    name = dto.name,
                    locationLabel = dto.locationLabel ?: "",
                    latitude = dto.latitude,
                    longitude = dto.longitude,
                    radiusMeters = JobSite.clampRadius(dto.radiusMeters),
                    updatedAtMillis = System.currentTimeMillis()
                )
            }
        jobSiteStore.applyRemoteCatalog(remote)
        return count
    }

    private suspend fun pullAttendance(auth: String, date: LocalDate): Int {
        val fmt = DateTimeFormatter.ISO_LOCAL_DATE
        val start = date.format(fmt)
        val remote = api.getAttendance(auth, deviceLocalId, startDate = start, endDate = start).items
        var count = 0
        for (dto in remote) {
            val ts = runCatching { Instant.parse(dto.timestamp).toEpochMilli() }.getOrNull() ?: continue
            attendance.upsertFromRemote(
                serverId = dto.serverId,
                localId = dto.localId,
                employeeId = dto.employeeLocalId,
                employeeServerId = dto.employeeServerId,
                checkType = CheckType.entries.firstOrNull { it.raw == dto.checkType } ?: continue,
                timestampMillis = ts,
                confidence = (dto.confidenceScore ?: 0.0).toFloat(),
                notes = dto.notes
            )
            count++
        }
        return count
    }

    data class SyncAuthState(
        val isSignedIn: Boolean,
        val username: String?
    )

    companion object {
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_SYNC_USERNAME = "sync_username"
    }
}

private fun EmployeeEntity.toUpsertRequest(): EmployeeUpsertRequest = EmployeeUpsertRequest(
    localId = UUID.fromString(id),
    employeeCode = employeeCode,
    firstName = firstName,
    lastName = lastName,
    department = department,
    position = position,
    assignedSiteId = assignedSiteId?.let(UUID::fromString),
    assignedSiteName = assignedSiteName,
    assignedSiteLocation = assignedSiteLocation
)
