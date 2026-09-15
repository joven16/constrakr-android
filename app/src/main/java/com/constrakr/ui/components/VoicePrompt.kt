package com.constrakr.ui.components

import android.content.Context
import android.speech.tts.TextToSpeech
import com.constrakr.util.AppLog
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class VoicePrompt(context: Context) {
    private var tts: TextToSpeech? = null
    private val ready = AtomicBoolean(false)
    private var pendingText: String? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val engine = tts
                if (engine != null) {
                    engine.language = Locale.US
                    engine.setSpeechRate(0.95f)
                }
                ready.set(true)
                pendingText?.let { text ->
                    pendingText = null
                    doSpeak(text)
                }
            } else {
                AppLog.w("TTS init failed: $status")
            }
        }
    }

    /** Scanner — only session start and final punch outcomes. */
    fun speakScanner(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        speakInternal(trimmed, force = true)
    }

    /** Registration steps — speak pose/phase cues; skip saving/upload chatter only. */
    fun speakEnrollment(text: String, force: Boolean = false) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || isEnrollmentSilent(trimmed)) return
        speakInternal(trimmed, force)
    }

    private fun speakInternal(text: String, force: Boolean) {
        if (!force && text == lastSpoken) return
        lastSpoken = text
        if (ready.get()) {
            doSpeak(text)
        } else {
            pendingText = text
        }
    }

    fun resetSpeech() {
        lastSpoken = null
        pendingText = null
    }

    private var lastSpoken: String? = null

    private fun doSpeak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "constrakr.scan")
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready.set(false)
        pendingText = null
    }

    companion object {
        private fun isEnrollmentSilent(text: String): Boolean {
            val t = text.lowercase()
            return t.contains("uploading") ||
                t.contains("saving employee") ||
                t.contains("adjust your face")
        }
    }
}
