package com.constrakr.attendance

import android.content.Context
import android.os.SystemClock
import com.constrakr.config.ConsTrakrConstants
import org.json.JSONObject
import java.time.Instant
import java.time.OffsetDateTime
import kotlin.math.abs

/**
 * Port of iOS [ClockIntegrityGuard.swift].
 * Detects manual clock changes via wall-time vs uptime drift — not elapsed time since last app use.
 */
class ClockIntegrityGuard(
    context: Context,
    private val serverTimeProvider: suspend () -> Long? = { null }
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private var cachedServerTimeMillis: Long? = null
    private var cachedServerTimeFetchedAtMillis: Long? = null

    /** Refreshes the offline jump baseline when the scanner opens. */
    fun bootstrapIfNeeded() {
        recordCheckpoint()
    }

    /**
     * Call once before recording attendance — not on every camera frame.
     * Prefers server time when online; falls back to offline jump detection.
     */
    suspend fun verifyBeforePunch(): Result<Unit> {
        val serverMillis = runCatching { serverTimeProvider() }.getOrNull()
        if (serverMillis != null) {
            cachedServerTimeMillis = serverMillis
            cachedServerTimeFetchedAtMillis = System.currentTimeMillis()
            val driftSec = abs(serverMillis - System.currentTimeMillis()) / 1000.0
            if (driftSec > ConsTrakrConstants.MAX_CLOCK_DRIFT_SECONDS) {
                val minutes = (driftSec / 60).toInt().coerceAtLeast(1)
                return Result.failure(
                    IllegalStateException(
                        "Device date/time looks wrong (about $minutes min off). " +
                            "Turn on automatic date & time in Settings, then try again."
                    )
                )
            }
            recordCheckpoint()
            return Result.success(Unit)
        }
        return verifyNoClockJump()
    }

    /** Best timestamp for a new punch — server-adjusted when online. */
    fun preferredPunchTimestampMillis(): Long {
        val server = cachedServerTimeMillis
        val fetchedAt = cachedServerTimeFetchedAtMillis
        if (server != null && fetchedAt != null) {
            val elapsed = System.currentTimeMillis() - fetchedAt
            return server + elapsed
        }
        return System.currentTimeMillis()
    }

    fun recordSuccessfulPunch() {
        recordCheckpoint()
        cachedServerTimeMillis = null
        cachedServerTimeFetchedAtMillis = null
    }

    fun verifyNoClockJump(): Result<Unit> = runCatching {
        val last = loadCheckpoint()
        if (last == null) {
            recordCheckpoint()
            return@runCatching
        }

        val nowWallSec = System.currentTimeMillis() / 1000.0
        val nowUptimeSec = SystemClock.elapsedRealtime() / 1000.0

        if (nowUptimeSec < last.uptimeSec) {
            recordCheckpoint()
            return@runCatching
        }

        val wallDeltaSec = nowWallSec - last.wallTimeSec
        if (wallDeltaSec > ConsTrakrConstants.CLOCK_CHECKPOINT_MAX_AGE_SECONDS) {
            recordCheckpoint()
            return@runCatching
        }

        val uptimeDeltaSec = nowUptimeSec - last.uptimeSec
        val driftSec = abs(wallDeltaSec - uptimeDeltaSec)
        if (driftSec > ConsTrakrConstants.MAX_CLOCK_JUMP_TOLERANCE_SECONDS) {
            throw IllegalStateException(
                "Device date/time was changed. Restore automatic date & time in Settings, then try again."
            )
        }
    }

    private fun recordCheckpoint() {
        val json = JSONObject()
            .put("wallTimeSec", System.currentTimeMillis() / 1000.0)
            .put("uptimeSec", SystemClock.elapsedRealtime() / 1000.0)
        prefs.edit().putString(KEY_CHECKPOINT, json.toString()).apply()
    }

    private fun loadCheckpoint(): Checkpoint? {
        val raw = prefs.getString(KEY_CHECKPOINT, null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            Checkpoint(
                wallTimeSec = json.getDouble("wallTimeSec"),
                uptimeSec = json.getDouble("uptimeSec")
            )
        }.getOrNull()
    }

    private data class Checkpoint(val wallTimeSec: Double, val uptimeSec: Double)

    companion object {
        private const val PREFS = "constrakr.clock"
        private const val KEY_CHECKPOINT = "checkpointJson"

        fun parseServerTimeMillis(raw: String?): Long? {
            if (raw.isNullOrBlank()) return null
            return runCatching { Instant.parse(raw).toEpochMilli() }
                .getOrElse {
                    runCatching { OffsetDateTime.parse(raw).toInstant().toEpochMilli() }.getOrNull()
                }
        }
    }
}
