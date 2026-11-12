package com.example.download

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.example.model.DownloadRecord
import com.example.model.DownloadStatus
import com.example.model.PendingDownloadConsent
import com.example.security.UrlValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Consent-first download manager using standard HttpURLConnection and zero-permission
 * MediaStore.Downloads (API 29+) or app-scoped external Download dir + FileProvider (API 24-28).
 */
class DownloadController(
  private val context: Context,
  private val scope: CoroutineScope
) {

  private val _downloads = MutableStateFlow<List<DownloadRecord>>(emptyList())
  val downloads: StateFlow<List<DownloadRecord>> = _downloads.asStateFlow()

  private val _pendingConsent = MutableStateFlow<PendingDownloadConsent?>(null)
  val pendingConsent: StateFlow<PendingDownloadConsent?> = _pendingConsent.asStateFlow()

  private val activeJobs = ConcurrentHashMap<String, Job>()

  fun requestDownloadConsent(
    url: String,
    contentDisposition: String?,
    mimeType: String?,
    contentLength: Long,
    isPrivateTab: Boolean
  ) {
    if (!UrlValidator.isSafeWebUrl(url)) return
    val guessedName = guessFileName(url, contentDisposition, mimeType)
    val safeName = UrlValidator.sanitizeFilename(guessedName)
    val host = UrlValidator.extractHost(url).ifEmpty { "unknown" }
    _pendingConsent.value = PendingDownloadConsent(
      id = UUID.randomUUID().toString(),
      url = url,
      fileName = safeName,
      host = host,
      mimeType = mimeType?.takeIf { it.isNotBlank() } ?: "application/octet-stream",
      contentLengthBytes = contentLength,
      isHttps = UrlValidator.isHttpsUrl(url),
      isPrivateTab = isPrivateTab
    )
  }

  fun dismissPendingConsent() {
    _pendingConsent.value = null
  }

  fun approvePendingDownload() {
    val consent = _pendingConsent.value ?: return
    _pendingConsent.value = null

    val initialRecord = DownloadRecord(
      id = consent.id,
      url = consent.url,
      fileName = consent.fileName,
      mimeType = consent.mimeType,
      totalBytes = consent.contentLengthBytes,
      downloadedBytes = 0L,
      status = DownloadStatus.DOWNLOADING,
      isPrivate = consent.isPrivateTab
    )
    _downloads.update { listOf(initialRecord) + it }

    val job = scope.launch(Dispatchers.IO) {
      executeDownload(initialRecord)
    }
    activeJobs[initialRecord.id] = job
  }

  fun cancelDownload(id: String) {
    activeJobs.remove(id)?.cancel()
    _downloads.update { list ->
      list.map { if (it.id == id && it.status == DownloadStatus.DOWNLOADING) it.copy(status = DownloadStatus.CANCELLED) else it }
    }
  }

  fun clearPrivateDownloads() {
    val privateIds = _downloads.value.filter { it.isPrivate }.map { it.id }
    privateIds.forEach { activeJobs.remove(it)?.cancel() }
    _downloads.update { list -> list.filterNot { it.isPrivate } }
  }

  fun clearAllDownloads() {
    activeJobs.values.forEach { it.cancel() }
    activeJobs.clear()
    _downloads.value = emptyList()
  }

  fun openDownloadedFile(record: DownloadRecord): Boolean {
    val uri = resolvePlayableUri(record) ?: return false
    return try {
      val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, record.mimeType)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
      context.startActivity(intent)
      true
    } catch (_: Throwable) {
      false
    }
  }

  fun revealDownloadedLocation(record: DownloadRecord): Boolean {
    val uri = resolvePlayableUri(record) ?: return false
    return try {
      val intent = Intent(Intent.ACTION_SEND).apply {
        type = record.mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
      val chooser = Intent.createChooser(intent, record.fileName).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(chooser)
      true
    } catch (_: Throwable) {
      false
    }
  }

  private fun resolvePlayableUri(record: DownloadRecord): Uri? {
    record.contentUriString?.let { return Uri.parse(it) }
    val path = record.localFilePath ?: return null
    val file = File(path)
    if (!file.exists()) return null
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
  }

  private fun executeDownload(record: DownloadRecord) {
    var connection: HttpURLConnection? = null
    var mediaUri: Uri? = null
    var localFile: File? = null
    var output: OutputStream? = null

    try {
      val urlObj = URL(record.url)
      connection = (urlObj.openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 30_000
        instanceFollowRedirects = true
        setRequestProperty("DNT", "1")
      }
      val code = connection.responseCode
      if (code !in 200..299) {
        updateRecord(record.id) {
          it.copy(status = DownloadStatus.FAILED, errorMessage = "HTTP $code")
        }
        return
      }

      val total = connection.contentLengthLong.takeIf { it > 0 } ?: record.totalBytes
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
          put(MediaStore.Downloads.DISPLAY_NAME, record.fileName)
          put(MediaStore.Downloads.MIME_TYPE, record.mimeType)
          put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        mediaUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        if (mediaUri == null) throw IllegalStateException("Unable to create download destination")
        output = resolver.openOutputStream(mediaUri)
      } else {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        if (!dir.exists()) dir.mkdirs()
        val dest = File(dir, "${System.currentTimeMillis()}_${record.fileName}")
        localFile = dest
        output = FileOutputStream(dest)
      }

      val out = output ?: throw IllegalStateException("Unable to open output stream")
      connection.inputStream.use { input ->
        out.use { stream ->
          val buffer = ByteArray(8192)
          var downloaded = 0L
          var lastEmit = 0L
          while (true) {
            if (activeJobs[record.id]?.isCancelled == true) {
              throw kotlinx.coroutines.CancellationException("Cancelled by user")
            }
            val read = input.read(buffer)
            if (read <= 0) break
            stream.write(buffer, 0, read)
            downloaded += read
            val now = System.currentTimeMillis()
            if (now - lastEmit > 150L) {
              lastEmit = now
              updateRecord(record.id) {
                it.copy(totalBytes = total, downloadedBytes = downloaded)
              }
            }
          }
          stream.flush()
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && mediaUri != null) {
            val doneValues = ContentValues().apply {
              put(MediaStore.Downloads.IS_PENDING, 0)
            }
            context.contentResolver.update(mediaUri, doneValues, null, null)
          }
          updateRecord(record.id) {
            it.copy(
              totalBytes = if (total > 0) total else downloaded,
              downloadedBytes = downloaded,
              status = DownloadStatus.COMPLETED,
              contentUriString = mediaUri?.toString(),
              localFilePath = localFile?.absolutePath
            )
          }
        }
      }
    } catch (_: kotlinx.coroutines.CancellationException) {
      cleanupPartial(mediaUri, localFile)
      updateRecord(record.id) { it.copy(status = DownloadStatus.CANCELLED) }
    } catch (t: Throwable) {
      cleanupPartial(mediaUri, localFile)
      updateRecord(record.id) {
        it.copy(
          status = DownloadStatus.FAILED,
          errorMessage = UrlValidator.sanitizeDisplayText(t.message ?: "Download failed", 120)
        )
      }
    } finally {
      activeJobs.remove(record.id)
      connection?.disconnect()
    }
  }

  private fun cleanupPartial(mediaUri: Uri?, localFile: File?) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && mediaUri != null) {
      try {
        context.contentResolver.delete(mediaUri, null, null)
      } catch (_: Throwable) {
      }
    }
    localFile?.delete()
  }

  private fun updateRecord(id: String, transform: (DownloadRecord) -> DownloadRecord) {
    _downloads.update { list -> list.map { if (it.id == id) transform(it) else it } }
  }

  private fun guessFileName(url: String, contentDisposition: String?, mimeType: String?): String {
    if (!contentDisposition.isNullOrBlank()) {
      val match = Regex("""filename\*?=(?:UTF-8'')?["']?([^"';]+)["']?""", RegexOption.IGNORE_CASE)
        .find(contentDisposition)
      if (match != null) return match.groupValues[1]
    }
    val pathSegment = try {
      URL(url).path.substringAfterLast('/').takeIf { it.isNotBlank() }
    } catch (_: Throwable) {
      null
    }
    if (pathSegment != null && pathSegment.contains('.')) return pathSegment
    val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "bin"
    return (pathSegment ?: "download") + "." + ext
  }
}
