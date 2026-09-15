package com.constrakr.enrollment

import android.content.Context
import java.io.File
import java.util.UUID

/** Roster profile photo — separate from face-scan enrollment poses. */
object ProfilePhotoStore {
    private const val FOLDER = "profile_photos"

    fun save(context: Context, employeeLocalId: UUID, jpeg: ByteArray) {
        val file = fileFor(context, employeeLocalId)
        file.parentFile?.mkdirs()
        file.writeBytes(jpeg)
    }

    fun load(context: Context, employeeLocalId: UUID): ByteArray? {
        val file = fileFor(context, employeeLocalId)
        return if (file.exists()) file.readBytes() else null
    }

    fun delete(context: Context, employeeLocalId: String) {
        runCatching { fileFor(context, UUID.fromString(employeeLocalId)).delete() }
    }

    fun pendingEmployeeIds(context: Context): List<String> {
        val dir = File(context.filesDir, FOLDER)
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles()
            ?.filter { it.isFile && it.extension == "jpg" }
            ?.map { it.nameWithoutExtension }
            ?: emptyList()
    }

    private fun fileFor(context: Context, employeeLocalId: UUID): File =
        File(context.filesDir, "$FOLDER/${employeeLocalId}.jpg")
}
