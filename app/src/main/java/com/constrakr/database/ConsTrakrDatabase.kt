package com.constrakr.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.constrakr.device.tracking.DeviceHeartbeatDao
import com.constrakr.device.tracking.DeviceHeartbeatEntity

@Database(
    entities = [
        EmployeeEntity::class,
        AttendanceEntity::class,
        FaceEmbeddingEntity::class,
        FaceEnrollmentPhotoEntity::class,
        DeviceHeartbeatEntity::class,
    ],
    version = 3,
    exportSchema = false
)
abstract class ConsTrakrDatabase : RoomDatabase() {
    abstract fun employeeDao(): EmployeeDao
    abstract fun attendanceDao(): AttendanceDao
    abstract fun faceEmbeddingDao(): FaceEmbeddingDao
    abstract fun faceEnrollmentPhotoDao(): FaceEnrollmentPhotoDao
    abstract fun deviceHeartbeatDao(): DeviceHeartbeatDao

    companion object {
        fun build(context: Context): ConsTrakrDatabase =
            Room.databaseBuilder(context, ConsTrakrDatabase::class.java, "constrakr.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
