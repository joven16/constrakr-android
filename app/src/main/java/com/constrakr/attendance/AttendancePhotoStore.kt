package com.constrakr.attendance

import android.content.Context
import java.io.File
import java.util.UUID

/** Face crop at punch time — uploaded as punch_photo_base64 for IMS DTR audit. */
object AttendancePhotoStore {
    private const val FOLDER = "attendance_photos"

    fun save(context: Context, attendanceLocalId: UUID, jpeg: ByteArray) {
        val file = fileFor(context, attendanceLocalId)
        file.parentFile?.mkdirs()
        file.writeBytes(jpeg)
    }

    fun load(context: Context, attendanceLocalId: String): ByteArray? {
        val file = fileFor(context, UUID.fromString(attendanceLocalId))
        return if (file.exists()) file.readBytes() else null
    }

    fun delete(context: Context, attendanceLocalId: String) {
        fileFor(context, UUID.fromString(attendanceLocalId)).delete()
    }

    private fun fileFor(context: Context, attendanceLocalId: UUID): File =
        File(context.filesDir, "$FOLDER/${attendanceLocalId}.jpg")
}
