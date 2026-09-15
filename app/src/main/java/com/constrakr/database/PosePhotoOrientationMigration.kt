package com.constrakr.database

import android.content.Context
import android.graphics.BitmapFactory
import com.constrakr.domain.SyncStatus
import com.constrakr.face.EnrollmentPhotoEncoder
import com.constrakr.face.rotate

/** One-time CCW rotation for pose JPEGs saved sideways from landscape camera frames. */
object PosePhotoOrientationMigration {
    private const val PREF = "constrakr_migrations"
    private const val KEY = "pose_orientation_v53"

    suspend fun runIfNeeded(context: Context, db: ConsTrakrDatabase) {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY, false)) return

        val dao = db.faceEnrollmentPhotoDao()
        for (row in dao.getAll()) {
            val bitmap = BitmapFactory.decodeByteArray(row.jpegData, 0, row.jpegData.size) ?: continue
            val fixed = bitmap.rotate(-90f)
            val jpeg = EnrollmentPhotoEncoder.encodeJpeg(fixed)
            dao.upsert(
                row.copy(
                    jpegData = jpeg,
                    syncStatus = SyncStatus.PENDING.name.lowercase()
                )
            )
        }
        prefs.edit().putBoolean(KEY, true).apply()
    }
}
