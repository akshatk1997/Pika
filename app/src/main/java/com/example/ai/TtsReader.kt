package com.example.ai

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class TtsReader(
  private val context: Context,
  private val onStateChanged: (isPlaying: Boolean, isPaused: Boolean, error: String?) -> Unit
) {

  private var tts: TextToSpeech? = null
  private var isInitialized = false
  private var currentText: String? = null
  private var speechRate = 1.0f

  init {
    try {
      tts = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
          isInitialized = true
          tts?.language = Locale.getDefault()
          tts?.setSpeechRate(speechRate)
          tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
              onStateChanged(true, false, null)
            }

            override fun onDone(utteranceId: String?) {
              onStateChanged(false, false, null)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
              onStateChanged(false, false, "TTS playback error")
            }
          })
        } else {
          isInitialized = false
        }
      }
    } catch (_: Throwable) {
      isInitialized = false
    }
  }

  fun speak(text: String) {
    val clean = text.replace(Regex("""[#*•_`]"""), " ").trim()
    if (clean.isBlank()) return
    currentText = clean

    if (!isInitialized || tts == null) {
      onStateChanged(false, false, "Speech engine not ready")
      return
    }

    try {
      tts?.stop()
      tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "kai_tts_${System.currentTimeMillis()}")
      onStateChanged(true, false, null)
    } catch (e: Throwable) {
      onStateChanged(false, false, e.message)
    }
  }

  fun stop() {
    try {
      tts?.stop()
    } catch (_: Throwable) {
    }
    onStateChanged(false, false, null)
  }

  fun setSpeechRate(rate: Float) {
    speechRate = rate.coerceIn(0.5f, 2.5f)
    try {
      tts?.setSpeechRate(speechRate)
    } catch (_: Throwable) {
    }
  }

  fun shutdown() {
    try {
      tts?.stop()
      tts?.shutdown()
    } catch (_: Throwable) {
    }
    tts = null
    isInitialized = false
  }
}
