package com.constrakr.database

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

    suspend fun upsertFromRemote(
        serverId: String?,
        localId: UUID,
        employeeId: UUID,
        employeeServerId: String?,
        checkType: CheckType,
        timestampMillis: Long,
        confidence: Float,
        notes: String?
    ) {
        val existing = serverId?.let { dao.getByServerId(it) }
        val entity = AttendanceEntity(
            id = existing?.id ?: localId.toString(),
            serverId = serverId,
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
