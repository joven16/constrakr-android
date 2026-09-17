package com.constrakr.device

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import com.constrakr.util.AppLog
import kotlinx.coroutines.delay

/** Plays a loud looping alarm so admins can locate a kiosk device remotely. */
class DeviceFindAlarmPlayer(private val context: Context) {
    private var player: MediaPlayer? = null

    suspend fun playFor(durationMs: Long = DEFAULT_DURATION_MS) {
        stop()
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: return
        runCatching {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(context, uri)
                isLooping = true
                prepare()
                start()
            }
            delay(durationMs)
        }.onFailure {
            AppLog.w("Play sound alarm failed: ${it.message}")
        }.also {
            stop()
        }
    }

    fun stop() {
        runCatching {
            player?.stop()
            player?.release()
        }
        player = null
    }

    companion object {
        const val DEFAULT_DURATION_MS = 45_000L
    }
}
