package com.example.extensions

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipInputStream

/**
 * Zero-dependency binary CRX2/CRX3, ZIP, and Userscript (.user.js) unpacker
 * with strict Zip-Slip path traversal defense and size quotas.
 */
object CrxUnpacker {

  private const val MAX_FILES = 600
  private const val MAX_UNPACKED_BYTES = 30L * 1024L * 1024L // 30 MB
  private const val MAX_SINGLE_FILE_BYTES = 8L * 1024L * 1024L // 8 MB

  private val STORE_ID_IN_URL = Regex("""(?:chromewebstore\.google\.com|chrome\.google\.com/webstore)/detail/(?:[^/?#]+/)?([a-p]{32})""", RegexOption.IGNORE_CASE)
  private val RAW_CHROME_ID = Regex("""^[a-p]{32}$""", RegexOption.IGNORE_CASE)

  /**
   * Extracts a 32-character Chrome Web Store extension ID from a store URL or raw ID string.
   */
  fun extractChromeStoreId(rawInput: String): String? {
    val trimmed = rawInput.trim()
    if (trimmed.isEmpty()) return null
    if (RAW_CHROME_ID.matches(trimmed)) return trimmed.lowercase()
    val match = STORE_ID_IN_URL.find(trimmed) ?: return null
    return match.groupValues[1].lowercase()
  }

  /**
   * Builds Google's official Chrome Web Store CRX download endpoint URL for a desktop extension ID.
   */
  fun buildChromeWebStoreCrxUrl(extensionId: String): String {
    val cleanId = extensionId.trim().lowercase()
    return "https://clients2.google.com/service/update2/crx" +
      "?response=redirect&prodversion=134.0.6998.166&acceptformat=crx2,crx3" +
      "&x=id%3D$cleanId%26installsource%3Dondemand%26uc"
  }

  /**
   * Strips CRX2 or CRX3 binary headers and returns the exact starting offset of the embedded ZIP archive.
   * Returns 0 if the payload is already a standard ZIP archive, or -1 if invalid.
   */
  fun findZipOffsetInBytes(bytes: ByteArray): Int {
    if (bytes.size < 4) return -1
    // Standard ZIP magic: PK\x03\x04
    if (bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() &&
      bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()
    ) {
      return 0
    }
    // CRX magic: "Cr24"
    if (bytes[0] == 0x43.toByte() && bytes[1] == 0x72.toByte() &&
      bytes[2] == 0x32.toByte() && bytes[3] == 0x34.toByte()
    ) {
      if (bytes.size < 12) return -1
      val version = ByteBuffer.wrap(bytes, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
      return when (version) {
        3 -> {
          val headerLen = ByteBuffer.wrap(bytes, 8, 4).order(ByteOrder.LITTLE_ENDIAN).int
          val offset = 12 + headerLen
          if (headerLen >= 0 && offset < bytes.size - 4) offset else -1
        }
        2 -> {
          if (bytes.size < 16) return -1
          val pubKeyLen = ByteBuffer.wrap(bytes, 8, 4).order(ByteOrder.LITTLE_ENDIAN).int
          val sigLen = ByteBuffer.wrap(bytes, 12, 4).order(ByteOrder.LITTLE_ENDIAN).int
          val offset = 16 + pubKeyLen + sigLen
          if (pubKeyLen >= 0 && sigLen >= 0 && offset < bytes.size - 4) offset else -1
        }
        else -> -1
      }
    }
    return -1
  }

  /**
   * Unpacks a .crx, .zip, or .user.js byte payload into [destDir].
   * Automatically handles single top-level wrapper folders inside ZIP files so manifest.json
   * always lands cleanly in [destDir].
   */
  fun unpackPayloadToDir(bytes: ByteArray, fileNameHint: String, destDir: File): Boolean {
    if (bytes.isEmpty()) return false
    if (destDir.exists()) destDir.deleteRecursively()
    destDir.mkdirs()

    val zipOffset = findZipOffsetInBytes(bytes)
    if (zipOffset >= 0) {
      val ok = extractZipSafely(ByteArrayInputStream(bytes, zipOffset, bytes.size - zipOffset), destDir)
      if (!ok) return false
      flattenSingleRootDirectoryIfNeeded(destDir)
      return File(destDir, "manifest.json").isFile
    }

    // Check if it is a standalone .user.js / JavaScript extension
    val lowerName = fileNameHint.lowercase()
    val looksLikeJs = lowerName.endsWith(".js") ||
      String(bytes, 0, minOf(bytes.size, 512), Charsets.UTF_8).contains("==UserScript==")
    if (looksLikeJs) {
      return unpackUserscript(String(bytes, Charsets.UTF_8), fileNameHint, destDir)
    }

    return false
  }

  private fun extractZipSafely(input: InputStream, destDir: File): Boolean {
    val canonicalDest = destDir.canonicalPath + File.separator
    var fileCount = 0
    var totalBytes = 0L
    val buffer = ByteArray(16 * 1024)

    ZipInputStream(input).use { zis ->
      while (true) {
        val entry = zis.nextEntry ?: break
        val entryName = entry.name.replace('\\', '/').trimStart('/')
        if (entryName.isEmpty() || entryName.contains("..")) {
          zis.closeEntry()
          continue
        }
        val outFile = File(destDir, entryName)
        val canonicalOut = outFile.canonicalPath
        if (!canonicalOut.startsWith(canonicalDest)) {
          zis.closeEntry()
          return false // Zip-Slip attempt blocked
        }
        if (entry.isDirectory) {
          outFile.mkdirs()
        } else {
          fileCount++
          if (fileCount > MAX_FILES) return false
          outFile.parentFile?.mkdirs()
          var entryBytes = 0L
          FileOutputStream(outFile).use { fos ->
            while (true) {
              val read = zis.read(buffer)
              if (read <= 0) break
              entryBytes += read
              totalBytes += read
              if (entryBytes > MAX_SINGLE_FILE_BYTES || totalBytes > MAX_UNPACKED_BYTES) {
                return false
              }
              fos.write(buffer, 0, read)
            }
          }
        }
        zis.closeEntry()
      }
    }
    return fileCount > 0
  }

  private fun flattenSingleRootDirectoryIfNeeded(destDir: File) {
    if (File(destDir, "manifest.json").isFile) return
    val children = destDir.listFiles()?.filter { it.name != "__MACOSX" } ?: return
    if (children.size == 1 && children[0].isDirectory) {
      val nested = children[0]
      if (File(nested, "manifest.json").isFile) {
        nested.listFiles()?.forEach { child ->
          child.renameTo(File(destDir, child.name))
        }
        nested.deleteRecursively()
      }
    }
  }

  private fun unpackUserscript(scriptText: String, fileNameHint: String, destDir: File): Boolean {
    var name = fileNameHint.substringBeforeLast('.').ifBlank { "Userscript Extension" }
    var version = "1.0"
    var description = "Installed desktop userscript"
    val matches = mutableListOf<String>()
    val excludes = mutableListOf<String>()
    var runAt = "document_idle"

    val metaRegex = Regex("""//\s*@([a-zA-Z0-9_-]+)\s+(.+)""")
    for (line in scriptText.lineSequence().take(80)) {
      val m = metaRegex.find(line) ?: continue
      val key = m.groupValues[1].lowercase()
      val value = m.groupValues[2].trim()
      when (key) {
        "name" -> if (value.isNotBlank()) name = value
        "version" -> if (value.isNotBlank()) version = value
        "description" -> if (value.isNotBlank()) description = value
        "match", "include" -> if (value.isNotBlank()) matches += value
        "exclude" -> if (value.isNotBlank()) excludes += value
        "run-at" -> when (value.lowercase()) {
          "document-start", "document_start" -> runAt = "document_start"
          "document-end", "document_end" -> runAt = "document_end"
          else -> runAt = "document_idle"
        }
      }
    }
    if (matches.isEmpty()) matches += "<all_urls>"

    File(destDir, "content.js").writeText(scriptText, Charsets.UTF_8)
    val csObj = JSONObject().apply {
      put("matches", JSONArray(matches))
      if (excludes.isNotEmpty()) put("exclude_matches", JSONArray(excludes))
      put("js", JSONArray(listOf("content.js")))
      put("run_at", runAt)
    }
    val manifest = JSONObject().apply {
      put("manifest_version", 3)
      put("name", name)
      put("version", version)
      put("description", description)
      put("content_scripts", JSONArray().put(csObj))
    }
    File(destDir, "manifest.json").writeText(manifest.toString(2), Charsets.UTF_8)
    return true
  }
}
