package com.constrakr.device

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import com.constrakr.R
import com.constrakr.util.AppLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

/** Plays the iPhone-style alarm tone (looping) so admins can locate a kiosk device remotely. */
class DeviceFindAlarmPlayer(context: Context) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var player: MediaPlayer? = null
    private var savedAlarmVolume: Int? = null
    @Volatile
    private var stopRequested = false

    suspend fun playFor(durationMs: Long = DEFAULT_DURATION_MS) {
        stop()
        stopRequested = false
        val targetVolume = forceMaxAlarmVolume()
        runCatching {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                val asset = appContext.resources.openRawResourceFd(R.raw.find_device_buzzer)
                setDataSource(asset.fileDescriptor, asset.startOffset, asset.length)
                asset.close()
                isLooping = true
                prepare()
                start()
            }
            val startedAt = System.currentTimeMillis()
            while (
                coroutineContext.isActive &&
                !stopRequested &&
                System.currentTimeMillis() - startedAt < durationMs
            ) {
                enforceMaxAlarmVolume(targetVolume)
                delay(VOLUME_GUARD_INTERVAL_MS)
            }
        }.onFailure {
            AppLog.w("Play sound alarm failed: ${it.message}")
        }.also {
            releasePlayer()
        }
    }

    fun stop() {
        stopRequested = true
        releasePlayer()
    }

    private fun releasePlayer() {
        runCatching {
            player?.stop()
            player?.release()
        }
        player = null
        restoreAlarmVolume()
    }

    private fun forceMaxAlarmVolume(): Int {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        if (savedAlarmVolume == null) {
            savedAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
        }
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, max, 0)
        return max
    }

    private fun enforceMaxAlarmVolume(targetVolume: Int) {
        if (audioManager.getStreamVolume(AudioManager.STREAM_ALARM) < targetVolume) {
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, targetVolume, 0)
        }
    }

    private fun restoreAlarmVolume() {
        savedAlarmVolume?.let { previous ->
            runCatching {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, previous, 0)
            }
        }
        savedAlarmVolume = null
    }

    companion object {
        const val DEFAULT_DURATION_MS = 45_000L
        private const val VOLUME_GUARD_INTERVAL_MS = 250L
    }
}
