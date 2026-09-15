package com.constrakr.admin

import com.constrakr.config.ConsTrakrConstants
import com.constrakr.domain.DeviceStore
import com.constrakr.network.ApiClient
import com.constrakr.network.ConsTrakrApi
import com.constrakr.network.DeviceAdminCodeVerifyRequest
import com.constrakr.sync.SyncCoordinator
import com.constrakr.util.AppLog
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import retrofit2.HttpException

class AdminCodeService(
    private val api: ConsTrakrApi,
    private val deviceStore: DeviceStore,
    private val accessSession: AppAccessSession,
    private val syncCoordinator: SyncCoordinator
) {
    sealed class AdminGateResult {
        data object Allowed : AdminGateResult()
        data object NeedsPrompt : AdminGateResult()
        data class Blocked(val reason: String) : AdminGateResult()
    }

    fun ensureChangeAllowed(): AdminGateResult {
        preflight()?.let { return it }
        if (accessSession.isAdminUnlocked) return AdminGateResult.Allowed
        return AdminGateResult.NeedsPrompt
    }

    /** Job sites / settings saves — always prompt even if admin was unlocked earlier. */
    fun requirePromptForChange(): AdminGateResult {
        preflight()?.let { return it }
        return AdminGateResult.NeedsPrompt
    }

    private fun preflight(): AdminGateResult.Blocked? {
        if (deviceStore.isBlocked) {
            return AdminGateResult.Blocked(deviceStore.blockedReason ?: "This device is blocked.")
        }
        if (!deviceStore.hasAssignedUsers) {
            return AdminGateResult.Blocked(
                "No user assigned to this device. Assign one under Devices on the web, then Sync."
            )
        }
        if (!deviceStore.adminCodeRequired) {
            return AdminGateResult.Blocked(
                "Assigned user has no admin code set. Set it under Profile → Edit Profile on the web."
            )
        }
        return null
    }

    suspend fun verify(passcode: String): Result<String> {
        if (passcode.length != ConsTrakrConstants.ADMIN_CODE_DIGITS || passcode.any { !it.isDigit() }) {
            return Result.failure(IllegalArgumentException("Enter a 6-digit admin code."))
        }
        val token = syncCoordinator.authToken
            ?: return Result.failure(
                IllegalStateException("Sign in under More → Sync Account before using admin code.")
            )
        val auth = ApiClient.authHeader(token)!!

        return try {
            syncCoordinator.registerDeviceWithServer()
            AppLog.d("Verifying admin code for device ${deviceStore.localId}")
            val resp = api.verifyAdminCode(
                auth,
                DeviceAdminCodeVerifyRequest(
                    localId = java.util.UUID.fromString(deviceStore.localId),
                    passcode = passcode
                )
            )
            if (!resp.valid) {
                return Result.failure(IllegalArgumentException(mapServerError(resp.error)))
            }
            val name = resp.assignedUserName ?: "Admin"
            deviceStore.assignedUserName = name
            accessSession.unlock(name)
            AppLog.d("Admin unlock OK for $name")
            Result.success(name)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(IllegalArgumentException(parseFailure(error), error))
        }
    }

    private fun mapServerError(code: String?): String = when (code) {
        "invalid_passcode" -> "Incorrect admin code."
        "no_assigned_user" -> "No users assigned to this device. Assign on the web, then Sync."
        "admin_code_not_set" -> "Assigned user has no admin code. Set under Profile → Edit Profile."
        "device_not_registered" -> "Device not registered. Sign in and tap Sync now first."
        null -> "Verification failed."
        else -> code.replace('_', ' ')
    }

    private fun parseFailure(error: Throwable): String {
        AppLog.e("Admin code verify failed", error)
        if (error is IllegalArgumentException) return error.message ?: "Verification failed."
        if (error is HttpException) {
            val body = error.response()?.errorBody()?.string()
            AppLog.e("HTTP ${error.code()} body=$body")
            body?.let {
                runCatching {
                    val json = JSONObject(it)
                    when {
                        json.has("error") -> return mapServerError(json.getString("error"))
                        json.has("valid") && !json.getBoolean("valid") ->
                            return mapServerError(json.optString("error", null))
                    }
                }
            }
            return when (error.code()) {
                401 -> "Not authorized. Sign in under More → Sync Account first."
                404 -> "Device not registered. Sync first, then retry admin code."
                else -> "Server error (${error.code()}). Check Logcat tag ConsTrakr."
            }
        }
        return error.message ?: "Could not verify admin code. Check internet connection."
    }
}
