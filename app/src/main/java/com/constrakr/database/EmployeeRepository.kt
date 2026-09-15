package com.constrakr.database

import android.util.Base64
import com.constrakr.domain.Employee
import com.constrakr.domain.FaceEmbedding
import com.constrakr.domain.FacePose
import com.constrakr.domain.SyncStatus
import com.constrakr.network.EmployeeDto
import com.constrakr.network.FaceEmbeddingDto
import com.constrakr.security.SecureFaceTemplateStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class EmployeeRepository(
    private val db: ConsTrakrDatabase,
    private val secureStore: SecureFaceTemplateStore
) {
    private val dao get() = db.employeeDao()

    fun embeddingDao() = db.faceEmbeddingDao()
    fun enrollmentPhotoDao() = db.faceEnrollmentPhotoDao()

    suspend fun getEntity(id: String): EmployeeEntity? = dao.getById(id)

    fun observeEmployees(): Flow<List<Employee>> =
        dao.observeAll().map { list -> list.map { it.toDomain(secureStore) } }

    fun observeEmployeesForSite(siteId: UUID?): Flow<List<Employee>> =
        observeEmployees().map { list ->
            if (siteId == null) emptyList()
            else list.filter { it.assignedSiteId == siteId || it.assignedSiteId == null }
        }

    suspend fun getById(id: UUID): Employee? = dao.getById(id.toString())?.toDomain(secureStore)

    suspend fun getCenterEnrollmentPhoto(employeeId: UUID): ByteArray? =
        enrollmentPhotoDao().forEmployee(employeeId.toString())
            .firstOrNull { it.pose == FacePose.CENTER.raw }
            ?.jpegData

    suspend fun getAllEnrolled(): List<Employee> =
        dao.getAll().map { it.toDomain(secureStore) }.filter { it.isEnrolled }

    suspend fun getPending(): List<EmployeeEntity> = dao.getPending()

    suspend fun getPendingNew(): List<EmployeeEntity> = dao.getPendingNew()

    suspend fun getPendingUpdates(): List<EmployeeEntity> = dao.getPendingUpdates()

    suspend fun markProfileSynced(localId: String) {
        dao.markProfileSynced(localId)
    }

    suspend fun markSynced(localId: String, serverId: String) {
        dao.markSynced(localId, serverId)
        db.faceEmbeddingDao().forEmployee(localId).forEach { row ->
            if (row.employeeServerId == null) {
                db.faceEmbeddingDao().upsert(row.copy(employeeServerId = serverId))
            }
        }
        db.faceEnrollmentPhotoDao().forEmployee(localId).forEach { row ->
            if (row.employeeServerId == null) {
                db.faceEnrollmentPhotoDao().upsert(row.copy(employeeServerId = serverId))
            }
        }
    }

    suspend fun upsertFromRemote(dto: EmployeeDto): EmployeeEntity {
        val existing = dto.serverId?.let { dao.getByServerId(it) }
            ?: dao.getById(dto.localId.toString())
        val now = System.currentTimeMillis()

        if (existing != null && existing.syncStatus == SyncStatus.PENDING.name.lowercase()) {
            val linked = if (existing.serverId.isNullOrBlank() && !dto.serverId.isNullOrBlank()) {
                existing.copy(serverId = dto.serverId)
            } else {
                existing
            }
            if (linked != existing) dao.upsert(linked)
            return linked
        }

        if (existing != null) {
            val remoteUpdated = parseRemoteMillis(dto.updatedAt)
            if (remoteUpdated != null && remoteUpdated <= existing.updatedAtMillis) {
                return existing
            }
        }

        val entity = EmployeeEntity(
            id = existing?.id ?: dto.localId.toString(),
            serverId = dto.serverId ?: existing?.serverId,
            employeeCode = dto.employeeCode,
            firstName = dto.firstName,
            lastName = dto.lastName,
            department = dto.department,
            position = dto.position,
            assignedSiteId = dto.assignedSiteId?.toString() ?: existing?.assignedSiteId,
            assignedSiteName = dto.assignedSiteName ?: existing?.assignedSiteName ?: "",
            assignedSiteLocation = dto.assignedSiteLocation ?: existing?.assignedSiteLocation ?: "",
            faceEmbeddingsEncrypted = existing?.faceEmbeddingsEncrypted ?: ByteArray(0),
            syncStatus = SyncStatus.SYNCED.name.lowercase(),
            createdAtMillis = existing?.createdAtMillis ?: now,
            updatedAtMillis = parseRemoteMillis(dto.updatedAt) ?: now
        )
        dao.upsert(entity)
        return entity
    }

    suspend fun upsertEmbeddingFromRemote(dto: FaceEmbeddingDto, employeeLocalId: String) {
        val encrypted = Base64.decode(dto.encryptedValuesBase64, Base64.DEFAULT)
        val existing = dto.serverId?.let { db.faceEmbeddingDao().getByServerId(it) }
        val entity = FaceEmbeddingEntity(
            localId = existing?.localId ?: dto.localId.toString(),
            serverId = dto.serverId,
            employeeLocalId = employeeLocalId,
            employeeServerId = dto.employeeServerId,
            pose = dto.pose,
            encryptedValues = encrypted,
            syncStatus = SyncStatus.SYNCED.name.lowercase()
        )
        db.faceEmbeddingDao().upsert(entity)
        rebuildEmployeeEmbeddings(employeeLocalId)
    }

    private suspend fun rebuildEmployeeEmbeddings(employeeLocalId: String) {
        val employee = dao.getById(employeeLocalId) ?: return
        val embeddings = db.faceEmbeddingDao().forEmployee(employeeLocalId).mapNotNull { row ->
            runCatching {
                val pose = FacePose.entries.first { it.raw == row.pose }
                FaceEmbedding(pose, secureStore.decryptValues(row.encryptedValues))
            }.getOrNull()
        }
        if (embeddings.isEmpty()) return
        dao.upsert(
            employee.copy(
                faceEmbeddingsEncrypted = secureStore.encryptEmbeddings(embeddings),
                updatedAtMillis = System.currentTimeMillis()
            )
        )
    }

    suspend fun updateProfile(
        id: UUID,
        firstName: String,
        lastName: String,
        department: String,
        position: String,
        assignedSiteId: UUID?,
        assignedSiteName: String,
        assignedSiteLocation: String
    ): Employee {
        val existing = dao.getById(id.toString()) ?: error("Employee not found")
        val updated = existing.copy(
            firstName = firstName.trim(),
            lastName = lastName.trim(),
            department = department,
            position = position,
            assignedSiteId = assignedSiteId?.toString(),
            assignedSiteName = assignedSiteName,
            assignedSiteLocation = assignedSiteLocation,
            syncStatus = com.constrakr.domain.SyncStatus.PENDING.name.lowercase(),
            updatedAtMillis = System.currentTimeMillis()
        )
        dao.upsert(updated)
        return updated.toDomain(secureStore)
    }

    suspend fun delete(id: UUID) {
        val idStr = id.toString()
        db.faceEmbeddingDao().deleteForEmployee(idStr)
        db.faceEnrollmentPhotoDao().deleteForEmployee(idStr)
        dao.delete(idStr)
    }

    /** Drops synced employees the server no longer returns (e.g. after web truncate or soft-delete). */
    suspend fun reconcileRemoteRoster(remote: List<EmployeeDto>) {
        val remoteServerIds = remote.mapNotNull { dto ->
            dto.serverId?.trim()?.takeIf { it.isNotEmpty() }
        }.toSet()
        val remoteLocalIds = remote.map { it.localId.toString() }.toSet()
        for (local in dao.getAll()) {
            val serverId = local.serverId?.trim()?.takeIf { it.isNotEmpty() }
            val remove = when {
                serverId != null && serverId !in remoteServerIds -> true
                local.syncStatus == SyncStatus.SYNCED.name.lowercase() &&
                    local.id !in remoteLocalIds &&
                    (serverId == null || serverId !in remoteServerIds) -> true
                else -> false
            }
            if (remove) delete(UUID.fromString(local.id))
        }
    }

    suspend fun register(
        firstName: String,
        lastName: String,
        department: String,
        position: String,
        embeddings: List<FaceEmbedding>,
        enrollmentPhotos: Map<FacePose, ByteArray> = emptyMap(),
        assignedSiteId: UUID? = null,
        assignedSiteName: String = "",
        assignedSiteLocation: String = "",
        employeeCode: String = generateCode()
    ): Employee {
        val id = UUID.randomUUID()
        val now = System.currentTimeMillis()
        val encrypted = secureStore.encryptEmbeddings(embeddings)
        val entity = EmployeeEntity(
            id = id.toString(),
            serverId = null,
            employeeCode = employeeCode,
            firstName = firstName.trim(),
            lastName = lastName.trim(),
            department = department,
            position = position,
            assignedSiteId = assignedSiteId?.toString(),
            assignedSiteName = assignedSiteName,
            assignedSiteLocation = assignedSiteLocation,
            faceEmbeddingsEncrypted = encrypted,
            syncStatus = SyncStatus.PENDING.name.lowercase(),
            createdAtMillis = now,
            updatedAtMillis = now
        )
        dao.upsert(entity)
        for (emb in embeddings) {
            db.faceEmbeddingDao().upsert(
                FaceEmbeddingEntity(
                    employeeLocalId = id.toString(),
                    employeeServerId = null,
                    pose = emb.pose.raw,
                    encryptedValues = secureStore.encryptValues(emb.values),
                    syncStatus = SyncStatus.PENDING.name.lowercase(),
                    serverId = null
                )
            )
        }
        for ((pose, jpeg) in enrollmentPhotos) {
            db.faceEnrollmentPhotoDao().upsert(
                FaceEnrollmentPhotoEntity(
                    employeeLocalId = id.toString(),
                    employeeServerId = null,
                    pose = pose.raw,
                    jpegData = jpeg,
                    syncStatus = SyncStatus.PENDING.name.lowercase(),
                    serverId = null
                )
            )
        }
        return entity.toDomain(secureStore)
    }

    private fun generateCode(): String {
        val now = java.time.LocalDate.now()
        val prefix = String.format("%04d%02d", now.year, now.monthValue)
        return "$prefix${System.currentTimeMillis() % 1000}"
    }
}

private fun parseRemoteMillis(iso: String?): Long? {
    if (iso.isNullOrBlank()) return null
    return runCatching { java.time.Instant.parse(iso).toEpochMilli() }.getOrNull()
}

private fun EmployeeEntity.toDomain(store: SecureFaceTemplateStore): Employee {
    val embeddings = runCatching { store.decryptEmbeddings(faceEmbeddingsEncrypted) }.getOrDefault(emptyList())
    return Employee(
        id = UUID.fromString(id),
        serverId = serverId,
        employeeCode = employeeCode,
        firstName = firstName,
        lastName = lastName,
        department = department,
        position = position,
        assignedSiteId = assignedSiteId?.let(UUID::fromString),
        assignedSiteName = assignedSiteName,
        assignedSiteLocation = assignedSiteLocation,
        faceEmbeddings = embeddings,
        syncStatus = runCatching { SyncStatus.valueOf(syncStatus.uppercase()) }
            .getOrDefault(SyncStatus.PENDING)
    )
}
