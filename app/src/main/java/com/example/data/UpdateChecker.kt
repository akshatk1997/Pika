package com.example.data

import com.example.model.AppUpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {
  private const val RAW_METADATA_URL =
    "https://raw.githubusercontent.com/akshatk1997/Pika/main/releases/latest.json"
  private const val GITHUB_API_URL =
    "https://api.github.com/repos/akshatk1997/Pika/releases/latest"

  fun isNewerVersion(remoteTag: String, currentVersion: String): Boolean {
    val cleanRemote = remoteTag.removePrefix("v").trim()
    val cleanCurrent = currentVersion.removePrefix("v").trim()
    if (cleanRemote.equals(cleanCurrent, ignoreCase = true)) return false

    val remoteParts = cleanRemote.split(".").mapNotNull { it.toIntOrNull() }
    val currentParts = cleanCurrent.split(".").mapNotNull { it.toIntOrNull() }
    val maxLen = maxOf(remoteParts.size, currentParts.size)
    for (i in 0 until maxLen) {
      val r = remoteParts.getOrElse(i) { 0 }
      val c = currentParts.getOrElse(i) { 0 }
      if (r > c) return true
      if (r < c) return false
    }
    return false
  }

  suspend fun checkUpdate(currentVersion: String): AppUpdateInfo? = withContext(Dispatchers.IO) {
    // 1. Primary check: static metadata on raw GitHub (zero rate-limits)
    try {
      val rawInfo = queryRawMetadata(currentVersion)
      if (rawInfo != null) return@withContext rawInfo
    } catch (_: Throwable) {
    }

    // 2. Secondary check: GitHub Releases API
    try {
      val apiInfo = queryGitHubApi(currentVersion)
      if (apiInfo != null) return@withContext apiInfo
    } catch (_: Throwable) {
    }

    null
  }

  private fun queryRawMetadata(currentVersion: String): AppUpdateInfo? {
    val url = URL(RAW_METADATA_URL)
    val conn = (url.openConnection() as HttpURLConnection).apply {
      connectTimeout = 4000
      readTimeout = 4000
      requestMethod = "GET"
      setRequestProperty("User-Agent", "Kio-Browser")
    }
    try {
      if (conn.responseCode in 200..299) {
        val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
        val root = JSONObject(jsonStr)
        val ver = root.optString("version", "").removePrefix("v").trim()
        val dl = root.optString("downloadUrl", "")
        if (ver.isNotEmpty() && dl.isNotEmpty() && isNewerVersion(ver, currentVersion)) {
          return AppUpdateInfo(
            versionName = ver,
            downloadUrl = dl,
            releaseNotes = root.optString("notes", "New Kio Browser update available.")
          )
        }
      }
    } finally {
      conn.disconnect()
    }
    return null
  }

  private fun queryGitHubApi(currentVersion: String): AppUpdateInfo? {
    val url = URL(GITHUB_API_URL)
    val conn = (url.openConnection() as HttpURLConnection).apply {
      connectTimeout = 5000
      readTimeout = 5000
      requestMethod = "GET"
      setRequestProperty("User-Agent", "Kio-Browser")
      setRequestProperty("Accept", "application/vnd.github+json")
    }
    try {
      if (conn.responseCode in 200..299) {
        val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
        val root = JSONObject(jsonStr)
        val tagName = root.optString("tag_name", "").removePrefix("v").trim()
        if (tagName.isNotEmpty() && isNewerVersion(tagName, currentVersion)) {
          var downloadUrl = ""
          var sizeBytes = 0L
          val assets = root.optJSONArray("assets")
          if (assets != null) {
            for (i in 0 until assets.length()) {
              val asset = assets.getJSONObject(i)
              val name = asset.optString("name", "")
              if (name.endsWith(".apk", ignoreCase = true)) {
                downloadUrl = asset.optString("browser_download_url", "")
                sizeBytes = asset.optLong("size", 0L)
                break
              }
            }
          }
          if (downloadUrl.isBlank()) {
            downloadUrl = "https://github.com/akshatk1997/Pika/releases/download/v$tagName/Kio-v$tagName.apk"
          }
          return AppUpdateInfo(
            versionName = tagName,
            downloadUrl = downloadUrl,
            releaseNotes = root.optString("body", "New Kio Browser update available."),
            sizeBytes = sizeBytes
          )
        }
      }
    } finally {
      conn.disconnect()
    }
    return null
  }
}
