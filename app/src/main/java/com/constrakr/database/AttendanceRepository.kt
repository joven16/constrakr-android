package com.constrakr.database

import com.constrakr.attendance.AttendancePhotoStore
import com.constrakr.domain.AttendanceRecord
import com.constrakr.domain.CheckType
import com.constrakr.domain.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar
import java.util.UUID

class AttendanceRepository(private val db: ConsTrakrDatabase) {
    private val dao get() = db.attendanceDao()

    fun observeAll(): Flow<List<AttendanceRecord>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observePendingCount(): Flow<Int> = dao.observePendingCount()

    suspend fun hasRecordedToday(employeeId: UUID, checkType: CheckType): Boolean {
        val (start, end) = dayBounds()
        return dao.countForDay(employeeId.toString(), checkType.raw, start, end) > 0
    }

    suspend fun record(
        employeeId: UUID,
        employeeServerId: String?,
        checkType: CheckType,
        confidence: Float,
        notes: String? = null,
        timestampMillis: Long = System.currentTimeMillis()
    ): AttendanceRecord {
        val entity = AttendanceEntity(
            id = UUID.randomUUID().toString(),
            serverId = null,
            employeeId = employeeId.toString(),
            employeeServerId = employeeServerId,
            checkType = checkType.raw,
            timestampMillis = timestampMillis,
            syncStatus = SyncStatus.PENDING.name.lowercase(),
            confidenceScore = confidence.toDouble(),
            notes = notes,
            punchSiteId = null,
            punchSiteName = null,
            punchSiteLocation = null
        )
        dao.insert(entity)
        return entity.toDomain()
    }

    suspend fun getPending(): List<AttendanceEntity> = dao.getPending()

    suspend fun markSynced(localId: String, serverId: String) = dao.markSynced(localId, serverId)

    suspend fun forDay(dayStart: Long, dayEnd: Long): List<AttendanceRecord> =
        dao.forDay(dayStart, dayEnd).map { it.toDomain() }

    suspend fun forDayEntities(dayStart: Long, dayEnd: Long): List<AttendanceEntity> =
        dao.forDay(dayStart, dayEnd)

    suspend fun deleteLocalRecord(context: android.content.Context, localId: String) {
        AttendancePhotoStore.delete(context, localId)
        dao.deleteById(localId)
    }

    suspend fun findForVoidReconcile(
        serverId: String?,
        localId: UUID,
        employeeServerId: String?,
        timestampMillis: Long,
        checkType: String
    ): AttendanceEntity? {
        serverId?.trim()?.takeIf { it.isNotEmpty() }?.let { dao.getByServerId(it) }?.let { return it }
        dao.getById(localId.toString())?.let { return it }
        val empServer = employeeServerId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val windowMs = 3_000L
        return dao.findByEmployeeServerAndTime(
            employeeServerId = empServer,
            checkType = checkType,
            windowStart = timestampMillis - windowMs,
            windowEnd = timestampMillis + windowMs
        )
    }

    suspend fun upsertFromRemote(
        serverId: String?,
        localId: UUID,
        employeeId: UUID,
        employeeServerId: String?,
        checkType: CheckType,
        timestampMillis: Long,
        confidence: Float,
        notes: String?
    ): Boolean {
        val normalizedServerId = serverId?.trim()?.takeIf { it.isNotEmpty() }
        val existingByServer = normalizedServerId?.let { dao.getByServerId(it) }
        if (existingByServer != null) {
            if (existingByServer.timestampMillis != timestampMillis ||
                existingByServer.notes != notes
            ) {
                dao.insert(
                    existingByServer.copy(
                        timestampMillis = timestampMillis,
                        notes = notes,
                        confidenceScore = confidence.toDouble()
                    )
                )
                return true
            }
            return false
        }

        val existingByLocal = dao.getById(localId.toString())
        if (existingByLocal != null) {
            if (existingByLocal.serverId.isNullOrBlank() && normalizedServerId != null) {
                dao.insert(
                    existingByLocal.copy(
                        serverId = normalizedServerId,
                        syncStatus = SyncStatus.SYNCED.name.lowercase()
                    )
                )
                return true
            }
            return false
        }

        val entity = AttendanceEntity(
            id = localId.toString(),
            serverId = normalizedServerId,
            employeeId = employeeId.toString(),
            employeeServerId = employeeServerId,
            checkType = checkType.raw,
            timestampMillis = timestampMillis,
            syncStatus = SyncStatus.SYNCED.name.lowercase(),
            confidenceScore = confidence.toDouble(),
            notes = notes,
            punchSiteId = null,
            punchSiteName = null,
            punchSiteLocation = null
        )
        dao.insert(entity)
        return true
    }

    private fun dayBounds(): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 1)
        return start to cal.timeInMillis
    }
}

private fun AttendanceEntity.toDomain() = AttendanceRecord(
    id = UUID.fromString(id),
    serverId = serverId,
    employeeId = UUID.fromString(employeeId),
    checkType = CheckType.entries.first { it.raw == checkType },
    timestampMillis = timestampMillis,
    confidenceScore = confidenceScore.toFloat(),
    syncStatus = runCatching { SyncStatus.valueOf(syncStatus.uppercase()) }.getOrDefault(SyncStatus.PENDING),
    notes = notes
)
