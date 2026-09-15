package com.constrakr.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EmployeeDao {
    @Query("SELECT * FROM employees ORDER BY lastName, firstName")
    fun observeAll(): Flow<List<EmployeeEntity>>

    @Query("SELECT * FROM employees")
    suspend fun getAll(): List<EmployeeEntity>

    @Query("SELECT * FROM employees WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): EmployeeEntity?

    @Query("SELECT * FROM employees WHERE serverId = :serverId LIMIT 1")
    suspend fun getByServerId(serverId: String): EmployeeEntity?

    @Query("SELECT * FROM employees WHERE syncStatus = 'pending'")
    suspend fun getPending(): List<EmployeeEntity>

    @Query("SELECT * FROM employees WHERE syncStatus = 'pending' AND serverId IS NULL")
    suspend fun getPendingNew(): List<EmployeeEntity>

    @Query("SELECT * FROM employees WHERE syncStatus = 'pending' AND serverId IS NOT NULL")
    suspend fun getPendingUpdates(): List<EmployeeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: EmployeeEntity)

    @Query("UPDATE employees SET serverId = :serverId, syncStatus = 'synced' WHERE id = :id")
    suspend fun markSynced(id: String, serverId: String)

    @Query("UPDATE employees SET syncStatus = 'synced' WHERE id = :id")
    suspend fun markProfileSynced(id: String)

    @Query("DELETE FROM employees WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface AttendanceDao {
    @Query("SELECT * FROM attendance ORDER BY timestampMillis DESC")
    fun observeAll(): Flow<List<AttendanceEntity>>

    @Query(
        """SELECT * FROM attendance 
           WHERE timestampMillis >= :dayStart AND timestampMillis < :dayEnd 
           ORDER BY timestampMillis ASC"""
    )
    suspend fun forDay(dayStart: Long, dayEnd: Long): List<AttendanceEntity>

    @Query("SELECT * FROM attendance WHERE serverId = :serverId LIMIT 1")
    suspend fun getByServerId(serverId: String): AttendanceEntity?

    @Query("SELECT COUNT(*) FROM attendance WHERE syncStatus = 'pending'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT * FROM attendance WHERE syncStatus = 'pending'")
    suspend fun getPending(): List<AttendanceEntity>

    @Query(
        """SELECT COUNT(*) FROM attendance 
           WHERE employeeId = :employeeId AND checkType = :checkType 
           AND timestampMillis >= :dayStart AND timestampMillis < :dayEnd"""
    )
    suspend fun countForDay(
        employeeId: String,
        checkType: String,
        dayStart: Long,
        dayEnd: Long
    ): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: AttendanceEntity)

    @Query("UPDATE attendance SET serverId = :serverId, syncStatus = 'synced' WHERE id = :id")
    suspend fun markSynced(id: String, serverId: String)
}

@Dao
interface FaceEmbeddingDao {
    @Query("SELECT * FROM face_embeddings WHERE employeeLocalId = :employeeId")
    suspend fun forEmployee(employeeId: String): List<FaceEmbeddingEntity>

    @Query("DELETE FROM face_embeddings WHERE employeeLocalId = :employeeId")
    suspend fun deleteForEmployee(employeeId: String)

    @Query("SELECT * FROM face_embeddings WHERE syncStatus = 'pending'")
    suspend fun pending(): List<FaceEmbeddingEntity>

    @Query("SELECT * FROM face_embeddings WHERE serverId = :serverId LIMIT 1")
    suspend fun getByServerId(serverId: String): FaceEmbeddingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FaceEmbeddingEntity)

    @Query("UPDATE face_embeddings SET serverId = :serverId, syncStatus = 'synced' WHERE localId = :localId")
    suspend fun markSynced(localId: String, serverId: String)
}

@Dao
interface FaceEnrollmentPhotoDao {
    @Query("SELECT * FROM face_enrollment_photos WHERE employeeLocalId = :employeeId")
    suspend fun forEmployee(employeeId: String): List<FaceEnrollmentPhotoEntity>

    @Query("DELETE FROM face_enrollment_photos WHERE employeeLocalId = :employeeId")
    suspend fun deleteForEmployee(employeeId: String)

    @Query("SELECT * FROM face_enrollment_photos WHERE syncStatus = 'pending'")
    suspend fun pending(): List<FaceEnrollmentPhotoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FaceEnrollmentPhotoEntity)

    @Query("UPDATE face_enrollment_photos SET serverId = :serverId, syncStatus = 'synced' WHERE localId = :localId")
    suspend fun markSynced(localId: String, serverId: String)
}
