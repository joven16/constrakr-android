package com.constrakr.domain

import android.content.Context
import com.constrakr.network.DeviceDto
import com.constrakr.util.AppLog
import java.util.UUID

class DeviceStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val localId: String
        get() {
            val existing = prefs.getString(KEY_LOCAL_ID, null)
            if (existing != null) return existing
            val id = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_LOCAL_ID, id).apply()
            AppLog.d("New device local_id=$id")
            return id
        }

    var deviceName: String
        get() = prefs.getString(KEY_NAME, android.os.Build.MODEL) ?: "Android device"
        set(value) { prefs.edit().putString(KEY_NAME, value).apply() }

    var adminCodeRequired: Boolean
        get() = prefs.getBoolean(KEY_ADMIN_REQUIRED, false)
        set(value) { prefs.edit().putBoolean(KEY_ADMIN_REQUIRED, value).apply() }

    var hasAssignedUsers: Boolean
        get() = prefs.getBoolean(KEY_HAS_ASSIGNED, false)
        set(value) { prefs.edit().putBoolean(KEY_HAS_ASSIGNED, value).apply() }

    var assignedUserName: String?
        get() = prefs.getString(KEY_ASSIGNED_NAME, null)
        set(value) {
            if (value == null) prefs.edit().remove(KEY_ASSIGNED_NAME).apply()
            else prefs.edit().putString(KEY_ASSIGNED_NAME, value).apply()
        }

    var isBlocked: Boolean
        get() = prefs.getBoolean(KEY_BLOCKED, false)
        set(value) { prefs.edit().putBoolean(KEY_BLOCKED, value).apply() }

    var blockedReason: String?
        get() = prefs.getString(KEY_BLOCKED_REASON, null)
        set(value) {
            if (value == null) prefs.edit().remove(KEY_BLOCKED_REASON).apply()
            else prefs.edit().putString(KEY_BLOCKED_REASON, value).apply()
        }

    fun applyFromServer(dto: DeviceDto) {
        hasAssignedUsers = !dto.assignedUsers.isNullOrEmpty() ||
            !dto.assignedUserName.isNullOrBlank()
        adminCodeRequired = dto.adminCodeRequired == true
        assignedUserName = dto.assignedUserName
            ?: dto.assignedUsers?.firstOrNull()?.name
            ?: dto.assignedUsers?.firstOrNull()?.username
        isBlocked = dto.isBlocked == true
        blockedReason = dto.blockedReason?.takeIf { it.isNotBlank() }
        dto.name?.takeIf { it.isNotBlank() }?.let { deviceName = it }
        AppLog.d(
            "Device updated: assigned=$hasAssignedUsers adminCodeRequired=$adminCodeRequired blocked=$isBlocked"
        )
    }

    companion object {
        private const val PREFS = "constrakr.device"
        private const val KEY_LOCAL_ID = "device_local_id"
        private const val KEY_NAME = "device_name"
        private const val KEY_ADMIN_REQUIRED = "deviceAdminCodeRequired"
        private const val KEY_HAS_ASSIGNED = "deviceHasAssignedUsers"
        private const val KEY_ASSIGNED_NAME = "deviceAssignedUserName"
        private const val KEY_BLOCKED = "deviceBlocked"
        private const val KEY_BLOCKED_REASON = "deviceBlockedReason"
    }
}
