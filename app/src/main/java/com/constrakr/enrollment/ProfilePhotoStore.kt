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
        syncedMarkerFor(context, employeeLocalId).delete()
    }

    fun load(context: Context, employeeLocalId: UUID): ByteArray? {
        val file = fileFor(context, employeeLocalId)
        return if (file.exists()) file.readBytes() else null
    }

    fun markSynced(context: Context, employeeLocalId: UUID) {
        val file = fileFor(context, employeeLocalId)
        if (!file.exists()) return
        syncedMarkerFor(context, employeeLocalId).writeText("1")
    }

    fun delete(context: Context, employeeLocalId: String) {
        runCatching {
            val id = UUID.fromString(employeeLocalId)
            fileFor(context, id).delete()
            syncedMarkerFor(context, id).delete()
        }
    }

    fun pendingEmployeeIds(context: Context): List<String> {
        val dir = File(context.filesDir, FOLDER)
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles()
            ?.filter { it.isFile && it.extension == "jpg" }
            ?.mapNotNull { file ->
                runCatching {
                    val id = UUID.fromString(file.nameWithoutExtension)
                    if (syncedMarkerFor(context, id).exists()) null else file.nameWithoutExtension
                }.getOrNull()
            }
            ?: emptyList()
    }

    private fun fileFor(context: Context, employeeLocalId: UUID): File =
        File(context.filesDir, "$FOLDER/${employeeLocalId}.jpg")

    private fun syncedMarkerFor(context: Context, employeeLocalId: UUID): File =
        File(context.filesDir, "$FOLDER/${employeeLocalId}.synced")
}
