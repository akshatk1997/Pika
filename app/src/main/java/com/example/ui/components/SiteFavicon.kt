package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.security.UrlValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Zero-dependency website favicon cache & fetcher (memory LRU + compact disk cache).
 * Captures live WebView icons via onReceivedIcon and falls back to privacy-first HTTPS favicon fetch.
 * Private tabs never persist icons to disk.
 */
object SiteFaviconCache {

  private const val MAX_MEM_ENTRIES = 128
  private const val MAX_ICON_PX = 96
  private const val MAX_DOWNLOAD_BYTES = 131_072 // 128 KB safety cap
  private const val CACHE_DIR_NAME = "favicons"

  private val memCache = LruCache<String, ImageBitmap>(MAX_MEM_ENTRIES)
  private val failedHosts = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
  private val inFlight = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

  private val _revision = MutableStateFlow(0L)
  val revision: StateFlow<Long> = _revision.asStateFlow()

  fun normalizeHost(urlOrHost: String): String {
    if (urlOrHost.isBlank()) return ""
    val host = if (urlOrHost.contains("://")) {
      UrlValidator.extractHost(urlOrHost)
    } else {
      urlOrHost.lowercase().trim()
    }
    return host.lowercase().trimEnd('.')
  }

  fun getFromMemory(urlOrHost: String): ImageBitmap? {
    val host = normalizeHost(urlOrHost)
    if (host.isEmpty()) return null
    return synchronized(memCache) { memCache.get(host) }
  }

  fun storeFromWebView(context: Context, url: String, bitmap: Bitmap, isPrivate: Boolean) {
    val host = normalizeHost(url)
    if (host.isEmpty() || bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return
    val scaled = scaleIconIfNeeded(bitmap)
    val imageBitmap = try {
      scaled.asImageBitmap()
    } catch (_: Throwable) {
      return
    }
    if (!isPrivate) {
      synchronized(memCache) { memCache.put(host, imageBitmap) }
      failedHosts.remove(host)
      _revision.value = _revision.value + 1L
      Thread {
        saveToDisk(context.applicationContext, host, scaled)
      }.start()
    }
  }

  suspend fun loadOrFetch(context: Context, url: String, allowNetwork: Boolean = true): ImageBitmap? {
    val host = normalizeHost(url)
    if (host.isEmpty() || url == "about:blank") return null

    synchronized(memCache) { memCache.get(host) }?.let { return it }

    return withContext(Dispatchers.IO) {
      // 1. Check disk cache first
      loadFromDisk(context.applicationContext, host)?.let { diskBmp ->
        val img = diskBmp.asImageBitmap()
        synchronized(memCache) { memCache.put(host, img) }
        return@withContext img
      }

      if (!allowNetwork || isRobolectricEnvironment() || host in failedHosts) {
        return@withContext null
      }
      if (!inFlight.add(host)) {
        return@withContext synchronized(memCache) { memCache.get(host) }
      }

      try {
        // 2. Privacy-first icon lookup via DuckDuckGo ip3, then direct first-party /favicon.ico
        val candidates = listOf(
          "https://icons.duckduckgo.com/ip3/$host.ico",
          "https://$host/favicon.ico"
        )
        for (candidateUrl in candidates) {
          val downloaded = downloadAndDecodeIcon(candidateUrl)
          if (downloaded != null) {
            val scaled = scaleIconIfNeeded(downloaded)
            val img = scaled.asImageBitmap()
            synchronized(memCache) { memCache.put(host, img) }
            saveToDisk(context.applicationContext, host, scaled)
            _revision.value = _revision.value + 1L
            return@withContext img
          }
        }
        failedHosts.add(host)
        null
      } catch (_: Throwable) {
        failedHosts.add(host)
        null
      } finally {
        inFlight.remove(host)
      }
    }
  }

  fun clearAll(context: Context) {
    synchronized(memCache) { memCache.evictAll() }
    failedHosts.clear()
    try {
      val dir = File(context.applicationContext.cacheDir, CACHE_DIR_NAME)
      if (dir.exists()) {
        dir.listFiles()?.forEach { runCatching { it.delete() } }
      }
    } catch (_: Throwable) {
    }
    _revision.value = _revision.value + 1L
  }

  private fun isRobolectricEnvironment(): Boolean {
    return try {
      Build.FINGERPRINT.equals("robolectric", ignoreCase = true)
    } catch (_: Throwable) {
      false
    }
  }

  private fun cacheFileForHost(context: Context, host: String): File {
    val dir = File(context.cacheDir, CACHE_DIR_NAME)
    if (!dir.exists()) dir.mkdirs()
    val safeKey = host.replace(Regex("[^a-z0-9._-]"), "_").take(64) + "_" + (host.hashCode().toUInt().toString(16))
    return File(dir, "$safeKey.png")
  }

  private fun loadFromDisk(context: Context, host: String): Bitmap? {
    return try {
      val file = cacheFileForHost(context, host)
      if (!file.exists() || file.length() <= 0L) return null
      BitmapFactory.decodeFile(file.absolutePath)
    } catch (_: Throwable) {
      null
    }
  }

  private fun saveToDisk(context: Context, host: String, bitmap: Bitmap) {
    try {
      val file = cacheFileForHost(context, host)
      FileOutputStream(file).use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
      }
    } catch (_: Throwable) {
    }
  }

  private fun downloadAndDecodeIcon(iconUrl: String): Bitmap? {
    var conn: HttpURLConnection? = null
    return try {
      conn = (URL(iconUrl).openConnection() as? HttpURLConnection) ?: return null
      conn.connectTimeout = 4000
      conn.readTimeout = 4000
      conn.instanceFollowRedirects = true
      conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/134.0.0.0 Mobile Safari/537.36")
      if (conn.responseCode !in 200..299) return null
      val bytes = conn.inputStream.use { input ->
        val out = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        var total = 0
        while (true) {
          val read = input.read(buf)
          if (read <= 0) break
          total += read
          if (total > MAX_DOWNLOAD_BYTES) return null
          out.write(buf, 0, read)
        }
        out.toByteArray()
      }
      if (bytes.isEmpty()) return null
      val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
      if (bmp.width < 8 || bmp.height < 8) null else bmp
    } catch (_: Throwable) {
      null
    } finally {
      try {
        conn?.disconnect()
      } catch (_: Throwable) {
      }
    }
  }

  private fun scaleIconIfNeeded(src: Bitmap): Bitmap {
    if (src.width <= MAX_ICON_PX && src.height <= MAX_ICON_PX) return src
    val ratio = minOf(MAX_ICON_PX.toFloat() / src.width, MAX_ICON_PX.toFloat() / src.height)
    val w = (src.width * ratio).toInt().coerceAtLeast(1)
    val h = (src.height * ratio).toInt().coerceAtLeast(1)
    return try {
      Bitmap.createScaledBitmap(src, w, h, true)
    } catch (_: Throwable) {
      src
    }
  }
}

/**
 * Displays a website's authentic favicon logo inside a crisp rounded badge,
 * falling back seamlessly to a clean initial monogram or fallback icon while loading / offline.
 */
@Composable
fun SiteFavicon(
  url: String,
  fallbackInitial: String,
  accentColor: Color,
  modifier: Modifier = Modifier,
  containerColor: Color = accentColor.copy(alpha = 0.14f),
  size: Dp = 36.dp,
  cornerRadius: Dp = 11.dp,
  isPrivate: Boolean = false,
  fallbackIcon: ImageVector? = null
) {
  val context = LocalContext.current
  val host = remember(url) { SiteFaviconCache.normalizeHost(url) }
  val revision by SiteFaviconCache.revision.collectAsStateWithLifecycle()
  var iconBitmap by remember(host, isPrivate) {
    mutableStateOf(if (!isPrivate && host.isNotEmpty()) SiteFaviconCache.getFromMemory(host) else null)
  }

  LaunchedEffect(host, isPrivate, revision) {
    if (host.isNotEmpty() && !isPrivate) {
      val cached = SiteFaviconCache.getFromMemory(host)
      if (cached != null) {
        iconBitmap = cached
      } else if (iconBitmap == null) {
        val loaded = SiteFaviconCache.loadOrFetch(context, url, allowNetwork = true)
        if (loaded != null) {
          iconBitmap = loaded
        }
      }
    } else if (host.isEmpty()) {
      iconBitmap = null
    }
  }

  val bmp = iconBitmap
  Surface(
    shape = RoundedCornerShape(cornerRadius),
    color = if (bmp != null) Color(0xFFF1F5F9) else containerColor,
    modifier = modifier.size(size)
  ) {
    Box(contentAlignment = Alignment.Center) {
      if (bmp != null) {
        Image(
          bitmap = bmp,
          contentDescription = null,
          contentScale = ContentScale.Fit,
          modifier = Modifier
            .size(size * 0.68f)
            .clip(RoundedCornerShape(4.dp))
        )
      } else if (fallbackInitial.isNotBlank()) {
        Text(
          text = fallbackInitial,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = accentColor
        )
      } else if (fallbackIcon != null) {
        Icon(
          imageVector = fallbackIcon,
          contentDescription = null,
          tint = accentColor,
          modifier = Modifier.size(size * 0.5f)
        )
      }
    }
  }
}
