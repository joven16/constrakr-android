package com.constrakr.device

import android.content.Context
import com.constrakr.domain.DeviceStore
import com.constrakr.network.ApiClient
import com.constrakr.network.ConsTrakrApi
import com.constrakr.network.DeviceDto
import com.constrakr.network.DevicePlaySoundAckRequest
import com.constrakr.sync.SyncCoordinator
import com.constrakr.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class DeviceCommandService(
    private val context: Context,
    private val api: ConsTrakrApi,
    private val syncCoordinator: SyncCoordinator,
    private val deviceStore: DeviceStore,
    private val alarmPlayer: DeviceFindAlarmPlayer
) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val handleMutex = Mutex()

    suspend fun pollAndExecute(): Result<Unit> = withContext(Dispatchers.IO) {
        if (!syncCoordinator.isSignedIn) return@withContext Result.success(Unit)
        val token = syncCoordinator.authToken ?: return@withContext Result.success(Unit)
        val auth = ApiClient.authHeader(token) ?: return@withContext Result.success(Unit)
        runCatching {
            val dto = api.getDevice(auth, deviceStore.localId).device ?: return@runCatching
            deviceStore.applyFromServer(dto)
            handleDeviceDto(dto)
        }.onFailure { AppLog.w("Remote command poll failed: ${it.message}") }
    }

    suspend fun handleDeviceDto(dto: DeviceDto) {
        val requestId = dto.playSoundRequestId?.trim().orEmpty()
        if (requestId.isBlank()) return
        handleMutex.withLock {
            val lastHandled = prefs.getString(KEY_LAST_PLAY_SOUND_ID, null)
            if (requestId == lastHandled) return
            playSoundAndAck(requestId)
        }
    }

    private suspend fun playSoundAndAck(requestId: String) {
        if (!syncCoordinator.isSignedIn) return
        val token = syncCoordinator.authToken ?: return
        val auth = ApiClient.authHeader(token) ?: return
        AppLog.d("Playing remote locate alarm for request=$requestId")
        ackStage(auth, requestId, STAGE_RINGING)
        alarmPlayer.playFor()
        runCatching {
            ackStage(auth, requestId, STAGE_COMPLETED)
        }.onSuccess {
            prefs.edit().putString(KEY_LAST_PLAY_SOUND_ID, requestId).apply()
            AppLog.d("Play sound acknowledged for request=$requestId")
        }.onFailure {
            AppLog.w("Play sound ack failed: ${it.message}")
        }
    }

    private suspend fun ackStage(auth: String, requestId: String, stage: String) {
        runCatching {
            api.ackDevicePlaySound(
                auth,
                deviceStore.localId,
                DevicePlaySoundAckRequest(
                    deviceId = deviceStore.localId,
                    requestId = requestId,
                    stage = stage
                )
            )
        }.onFailure {
            AppLog.w("Play sound $stage ack failed: ${it.message}")
        }
    }

    companion object {
        private const val PREFS = "constrakr.device.commands"
        private const val KEY_LAST_PLAY_SOUND_ID = "last_play_sound_id"
        private const val STAGE_RINGING = "ringing"
        private const val STAGE_COMPLETED = "completed"
    }
}
