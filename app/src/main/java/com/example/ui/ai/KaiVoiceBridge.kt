package com.example.ui.ai

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.MutableContextWrapper
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.util.Locale

/**
 * Hardened native Android Speech Recognition JavaScript Interface for Kai AI.
 * Provides direct, zero-telemetry speech-to-text bridge to Android's SpeechRecognizer,
 * replacing unsupported/stubbed window.webkitSpeechRecognition inside Android WebView.
 * Restricted to https://kai.ai origin for strict security and user privacy.
 */
class KaiVoiceBridge(
  private val webViewRef: WeakReference<WebView>,
  private val onPermissionNeeded: (() -> Unit)? = null
) {

  companion object {
    const val JS_INTERFACE_NAME = "KaiVoice"
    const val REQUEST_CODE_RECORD_AUDIO = 2002
    private const val KAI_ORIGIN_PREFIX = "https://kai.ai"
  }

  private val mainHandler = Handler(Looper.getMainLooper())
  private var speechRecognizer: SpeechRecognizer? = null
  private var isListening = false

  private fun getContext(): Context? {
    val webView = webViewRef.get() ?: return null
    val ctx = webView.context
    if (ctx is MutableContextWrapper) {
      return ctx.baseContext ?: ctx
    }
    return ctx
  }

  private fun getActivity(): Activity? {
    var ctx = getContext()
    while (ctx is ContextWrapper) {
      if (ctx is Activity) return ctx
      ctx = ctx.baseContext
    }
    return null
  }

  private fun isAuthorizedCaller(): Boolean {
    val wv = webViewRef.get() ?: return false
    val url = wv.url.orEmpty()
    return url.isEmpty() || url.startsWith(KAI_ORIGIN_PREFIX)
  }

  @JavascriptInterface
  fun isAvailable(): Boolean {
    if (!isAuthorizedCaller()) return false
    val ctx = getContext() ?: return false
    return SpeechRecognizer.isRecognitionAvailable(ctx)
  }

  @JavascriptInterface
  fun hasPermission(): Boolean {
    if (!isAuthorizedCaller()) return false
    val ctx = getContext() ?: return false
    return ContextCompat.checkSelfPermission(
      ctx,
      android.Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED
  }

  @JavascriptInterface
  fun requestPermission() {
    if (!isAuthorizedCaller()) return
    mainHandler.post {
      val activity = getActivity()
      if (activity != null) {
        ActivityCompat.requestPermissions(
          activity,
          arrayOf(android.Manifest.permission.RECORD_AUDIO),
          REQUEST_CODE_RECORD_AUDIO
        )
      } else {
        onPermissionNeeded?.invoke()
      }
    }
  }

  @JavascriptInterface
  fun startListening() {
    if (!isAuthorizedCaller()) return
    mainHandler.post {
      val ctx = getContext() ?: return@post

      if (!hasPermission()) {
        requestPermission()
        notifyError("Microphone permission required. Please grant permission.")
        return@post
      }

      if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
        notifyError("Speech recognition is not available on this device.")
        return@post
      }

      try {
        destroyRecognizerInternal()
        val recognizer = SpeechRecognizer.createSpeechRecognizer(ctx)
        speechRecognizer = recognizer

        recognizer.setRecognitionListener(object : RecognitionListener {
          override fun onReadyForSpeech(params: Bundle?) {
            isListening = true
            notifyStart()
          }

          override fun onBeginningOfSpeech() {}

          override fun onRmsChanged(rmsdB: Float) {}

          override fun onBufferReceived(buffer: ByteArray?) {}

          override fun onEndOfSpeech() {}

          override fun onError(error: Int) {
            isListening = false
            val message = when (error) {
              SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
              SpeechRecognizer.ERROR_CLIENT -> "Client speech error"
              SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
              SpeechRecognizer.ERROR_NETWORK -> "Network error during speech recognition"
              SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
              SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
              SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
              SpeechRecognizer.ERROR_SERVER -> "Recognition server error"
              SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input received"
              else -> "Speech error ($error)"
            }
            notifyError(message)
            destroyRecognizerInternal()
            notifyEnd()
          }

          override fun onResults(results: Bundle?) {
            isListening = false
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val transcript = matches?.firstOrNull() ?: ""
            notifyResult(transcript, isFinal = true)
            destroyRecognizerInternal()
            notifyEnd()
          }

          override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val transcript = matches?.firstOrNull() ?: ""
            if (transcript.isNotEmpty()) {
              notifyResult(transcript, isFinal = false)
            }
          }

          override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
          putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
          putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
          putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
          putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
          putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
        }

        recognizer.startListening(intent)
      } catch (t: Throwable) {
        isListening = false
        notifyError("Failed to start voice recognition: ${t.message ?: "unknown"}")
        destroyRecognizerInternal()
        notifyEnd()
      }
    }
  }

  @JavascriptInterface
  fun stopListening() {
    if (!isAuthorizedCaller()) return
    mainHandler.post {
      try {
        if (isListening) {
          speechRecognizer?.stopListening()
        }
      } catch (_: Throwable) {
      }
    }
  }

  @JavascriptInterface
  fun cancel() {
    if (!isAuthorizedCaller()) return
    mainHandler.post {
      destroyRecognizerInternal()
      notifyEnd()
    }
  }

  fun cleanup() {
    mainHandler.post {
      destroyRecognizerInternal()
    }
  }

  private fun destroyRecognizerInternal() {
    isListening = false
    try {
      speechRecognizer?.cancel()
      speechRecognizer?.destroy()
    } catch (_: Throwable) {
    }
    speechRecognizer = null
  }

  private fun notifyStart() {
    evaluateJs("if(window.__kaiVoiceOnStart){window.__kaiVoiceOnStart();}")
  }

  private fun notifyResult(transcript: String, isFinal: Boolean) {
    val safeText = JSONObject.quote(transcript)
    evaluateJs("if(window.__kaiVoiceOnResult){window.__kaiVoiceOnResult($safeText, $isFinal);}")
  }

  private fun notifyError(error: String) {
    val safeError = JSONObject.quote(error)
    evaluateJs("if(window.__kaiVoiceOnError){window.__kaiVoiceOnError($safeError);}")
  }

  private fun notifyEnd() {
    evaluateJs("if(window.__kaiVoiceOnEnd){window.__kaiVoiceOnEnd();}")
  }

  private fun evaluateJs(js: String) {
    mainHandler.post {
      val wv = webViewRef.get() ?: return@post
      try {
        wv.evaluateJavascript(js, null)
      } catch (_: Throwable) {
      }
    }
  }
}
