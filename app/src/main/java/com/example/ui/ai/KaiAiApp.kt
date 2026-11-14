package com.example.ui.ai

import android.content.Context
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets

/**
 * Built-in, privacy-first, zero-telemetry anonymous Kai AI application provider.
 * Loads the web application from assets/kai.html and serves it directly in WebView
 * via shouldInterceptRequest for kai.ai domain queries.
 * Contains top-ranked open intelligence personas with undisclosed internal model names.
 */
object KaiAiApp {

  @Volatile
  private var cachedBytes: ByteArray? = null

  fun openHtmlStream(context: Context): InputStream {
    return ByteArrayInputStream(getHtmlBytes(context))
  }

  fun getHtmlText(context: Context): String {
    return String(getHtmlBytes(context), StandardCharsets.UTF_8)
  }

  fun getHtmlBytes(context: Context): ByteArray {
    cachedBytes?.let { return it }
    synchronized(this) {
      cachedBytes?.let { return it }
      val bytes = try {
        context.assets.open("kai.html").use { it.readBytes() }
      } catch (_: Throwable) {
        FALLBACK_HTML.toByteArray(StandardCharsets.UTF_8)
      }
      cachedBytes = bytes
      return bytes
    }
  }

  const val FALLBACK_HTML: String =
    "<!DOCTYPE html><html><head><title>Kai AI</title></head><body style=\"background:#070B14;color:#F1F5F9;font-family:sans-serif;padding:24px;\"><h1>Kai AI</h1><p>Anonymous AI Workspace</p></body></html>"
}
