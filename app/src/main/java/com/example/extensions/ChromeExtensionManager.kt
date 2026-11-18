package com.example.extensions

import android.content.Context
import android.webkit.WebResourceResponse
import com.example.security.UrlValidator
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Lightweight, zero-dependency Desktop Chrome Extension Engine for Kio Browser.
 *
 * Supports:
 * - Direct installation from Chrome Web Store URLs or 32-character Extension IDs (CRX2/CRX3).
 * - Local installation from .crx, .zip, or .user.js archives.
 * - Manifest V2 & V3 parsing with _locales/__MSG_*__ localization support.
 * - Content Script JS & CSS injection matched against Chrome URL Match Patterns.
 * - Static declarative_net_request (DNR) block rulesets integrated into WebView network filtering.
 * - Persistent chrome.storage.local / sync, chrome.runtime, chrome.i18n, and chrome.tabs bridges.
 * - Virtual origin resource serving (https://kio-ext.invalid/<extId>/<asset>) for popups & assets.
 */
class ChromeExtensionManager(context: Context) {

  companion object {
    const val EXT_VIRTUAL_HOST = "kio-ext.invalid"
    const val BG_HOST_VIRTUAL_FILE = "__kio_bg_host.html"
    const val CHROME_WEB_STORE_URL = "https://chromewebstore.google.com/"
    private const val MAX_DOWNLOAD_BYTES = 25 * 1024 * 1024 // 25 MB
    private const val MAX_PRIVILEGED_FETCH_BYTES = 8 * 1024 * 1024 // 8 MB
    const val DESKTOP_CHROME_UA =
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36"

    val POPULAR_PRESETS: List<PopularExtensionPreset> = listOf(
      PopularExtensionPreset(
        id = "eimadpbcbfnmbkopoojfekhnkhdbieeh",
        name = "Dark Reader",
        summary = "Dark mode for every website with brightness and contrast controls",
        category = "Appearance"
      ),
      PopularExtensionPreset(
        id = "ddkjiahejlhfcafbddmgiahcphecmpfh",
        name = "uBlock Origin Lite",
        summary = "MV3 declarative content & ad blocker with static filter rulesets",
        category = "Privacy"
      ),
      PopularExtensionPreset(
        id = "mnjggcdmjocbbbhaepdhchncahnbgone",
        name = "SponsorBlock for YouTube",
        summary = "Skip sponsored segments, intros, and outros on YouTube videos",
        category = "Video"
      ),
      PopularExtensionPreset(
        id = "gebbhagfogifgggkldgodflihgfeippi",
        name = "Return YouTube Dislike",
        summary = "Restores dislike counts on YouTube video pages",
        category = "Video"
      ),
      PopularExtensionPreset(
        id = "clngdbkpkpeebahjckkjfobafhncgmne",
        name = "Stylus",
        summary = "Customize web page themes with user CSS stylesheets",
        category = "Customization"
      )
    )

    /**
     * Evaluates a Chrome Extension URL match pattern against a target webpage URL.
     * Supports all-URLs, subdomain wildcards, and path globs.
     */
    fun matchesChromePattern(pattern: String, url: String): Boolean {
      val p = pattern.trim()
      if (p.isEmpty() || url.isBlank()) return false
      if (p == "<all_urls>" || p == "*" || p == "*://*/*") {
        return url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)
      }
      val schemeEnd = p.indexOf("://")
      if (schemeEnd <= 0) {
        // Userscript @include style wildcard fallback
        return wildcardToRegex(p).matches(url)
      }
      val patScheme = p.substring(0, schemeEnd).lowercase()
      val rest = p.substring(schemeEnd + 3)
      val pathStart = rest.indexOf('/')
      val patHost = (if (pathStart >= 0) rest.substring(0, pathStart) else rest).lowercase()
      val patPath = if (pathStart >= 0) rest.substring(pathStart) else "/*"

      val urlSchemeEnd = url.indexOf("://")
      if (urlSchemeEnd <= 0) return false
      val urlScheme = url.substring(0, urlSchemeEnd).lowercase()
      if (patScheme == "*") {
        if (urlScheme != "http" && urlScheme != "https") return false
      } else if (patScheme != urlScheme) {
        return false
      }

      val urlRest = url.substring(urlSchemeEnd + 3)
      val urlPathStart = urlRest.indexOf('/')
      val urlHostRaw = (if (urlPathStart >= 0) urlRest.substring(0, urlPathStart) else urlRest).lowercase()
      val urlHost = urlHostRaw.substringBefore(':')
      val urlPath = if (urlPathStart >= 0) urlRest.substring(urlPathStart) else "/"

      val hostOk = when {
        patHost == "*" -> true
        patHost.startsWith("*.") -> {
          val base = patHost.removePrefix("*.")
          urlHost == base || urlHost.endsWith(".$base")
        }
        else -> urlHost == patHost
      }
      if (!hostOk) return false
      return wildcardToRegex(patPath).matches(urlPath)
    }

    private fun wildcardToRegex(glob: String): Regex {
      val sb = StringBuilder("^")
      for (ch in glob) {
        when (ch) {
          '*' -> sb.append(".*")
          '?', '.', '+', '(', ')', '[', ']', '{', '}', '^', '$', '|', '\\' -> sb.append('\\').append(ch)
          else -> sb.append(ch)
        }
      }
      sb.append('$')
      return Regex(sb.toString(), RegexOption.IGNORE_CASE)
    }

    fun inferMimeType(path: String): String {
      return when (path.substringAfterLast('.', "").lowercase()) {
        "html", "htm" -> "text/html"
        "js", "mjs" -> "application/javascript"
        "css" -> "text/css"
        "json" -> "application/json"
        "svg" -> "image/svg+xml"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "ico" -> "image/x-icon"
        "woff" -> "font/woff"
        "woff2" -> "font/woff2"
        "ttf" -> "font/ttf"
        else -> "application/octet-stream"
      }
    }
  }

  private val appContext = context.applicationContext
  private val rootDir: File by lazy { File(appContext.filesDir, "kio_extensions").apply { mkdirs() } }
  private val storageDir: File by lazy { File(rootDir, "storage").apply { mkdirs() } }
  private val registryFile: File by lazy { File(rootDir, "registry.json") }

  private val extensionsMap = LinkedHashMap<String, InstalledExtension>()
  private val dnrRulesByExt = ConcurrentHashMap<String, List<ExtensionDnrRule>>()
  private val localesByExt = ConcurrentHashMap<String, Map<String, String>>()
  private val lock = Any()

  init {
    loadRegistry()
  }

  fun listExtensions(): List<InstalledExtension> = synchronized(lock) {
    extensionsMap.values.sortedByDescending { it.installedAt }
  }

  fun getExtension(extId: String): InstalledExtension? = synchronized(lock) {
    extensionsMap[extId]
  }

  fun setExtensionEnabled(extId: String, enabled: Boolean): List<InstalledExtension> = synchronized(lock) {
    val cur = extensionsMap[extId] ?: return listExtensions()
    extensionsMap[extId] = cur.copy(enabled = enabled)
    saveRegistry()
    listExtensions()
  }

  fun removeExtension(extId: String): List<InstalledExtension> = synchronized(lock) {
    val cur = extensionsMap.remove(extId)
    dnrRulesByExt.remove(extId)
    localesByExt.remove(extId)
    if (cur != null) {
      try {
        File(cur.installDirPath).deleteRecursively()
      } catch (_: Throwable) {
      }
    }
    try {
      File(storageDir, "$extId.json").delete()
    } catch (_: Throwable) {
    }
    saveRegistry()
    listExtensions()
  }

  /**
   * Downloads and installs a desktop Chrome extension directly from the Chrome Web Store
   * by its store URL or 32-character extension ID using resilient Windows/Linux CRX3 endpoints.
   */
  fun installFromChromeWebStore(urlOrId: String): ExtensionInstallResult {
    val extId = CrxUnpacker.extractChromeStoreId(urlOrId)
      ?: return ExtensionInstallResult.Failure("Enter a valid Chrome Web Store URL or 32-character Extension ID")

    val candidateUrls = CrxUnpacker.buildChromeWebStoreCrxCandidateUrls(extId)
    var validBytes: ByteArray? = null
    for (url in candidateUrls) {
      val downloaded = downloadCrxBytes(url)
      if (downloaded != null && downloaded.size >= 64 && CrxUnpacker.findZipOffsetInBytes(downloaded) >= 0) {
        validBytes = downloaded
        break
      }
    }
    if (validBytes == null) {
      return ExtensionInstallResult.Failure("Could not download CRX package from Chrome Web Store. Check internet connection or install a local .crx/.zip file.")
    }

    return installFromBytes(
      bytes = validBytes,
      fileNameHint = "$extId.crx",
      preferredId = extId,
      sourceUrl = "https://chromewebstore.google.com/detail/$extId"
    )
  }

  /**
   * Installs a desktop Chrome extension from a local .crx, .zip, or .user.js byte array.
   */
  fun installFromBytes(
    bytes: ByteArray,
    fileNameHint: String,
    preferredId: String? = null,
    sourceUrl: String = "local"
  ): ExtensionInstallResult {
    if (bytes.isEmpty()) return ExtensionInstallResult.Failure("Selected file is empty")
    if (bytes.size > MAX_DOWNLOAD_BYTES) {
      return ExtensionInstallResult.Failure("Extension package exceeds 25 MB limit")
    }

    val extId = preferredId?.takeIf { it.isNotBlank() }
      ?: ("local_" + UUID.randomUUID().toString().replace("-", "").take(16))
    val destDir = File(rootDir, extId)

    val unpacked = try {
      CrxUnpacker.unpackPayloadToDir(bytes, fileNameHint, destDir)
    } catch (_: Throwable) {
      false
    }
    if (!unpacked) {
      try { destDir.deleteRecursively() } catch (_: Throwable) {}
      return ExtensionInstallResult.Failure("Invalid extension package (expected Chrome .crx, .zip with manifest.json, or .user.js)")
    }

    val parsed = parseInstalledDirectory(extId, destDir, sourceUrl)
    if (parsed == null) {
      try { destDir.deleteRecursively() } catch (_: Throwable) {}
      return ExtensionInstallResult.Failure("Could not parse manifest.json inside extension")
    }

    synchronized(lock) {
      extensionsMap[parsed.id] = parsed
      saveRegistry()
    }
    return ExtensionInstallResult.Success(parsed)
  }

  fun installFromStream(stream: InputStream, fileNameHint: String): ExtensionInstallResult {
    return try {
      val buffer = ByteArrayOutputStream()
      val chunk = ByteArray(16 * 1024)
      var total = 0
      while (true) {
        val read = stream.read(chunk)
        if (read <= 0) break
        total += read
        if (total > MAX_DOWNLOAD_BYTES) {
          return ExtensionInstallResult.Failure("Extension file exceeds 25 MB limit")
        }
        buffer.write(chunk, 0, read)
      }
      installFromBytes(buffer.toByteArray(), fileNameHint)
    } catch (t: Throwable) {
      ExtensionInstallResult.Failure("Failed to read extension file: ${t.message ?: "I/O error"}")
    }
  }

  /**
   * Checks whether any enabled extension's static Declarative Net Request rules block [requestUrl].
   */
  fun shouldBlockByDnrRules(requestUrl: String, pageHost: String): Boolean {
    if (dnrRulesByExt.isEmpty()) return false
    val enabledIds = synchronized(lock) {
      extensionsMap.values.filter { it.enabled && it.dnrRulesCount > 0 }.map { it.id }
    }
    if (enabledIds.isEmpty()) return false
    val lowerUrl = requestUrl.lowercase()
    val reqHost = UrlValidator.extractHost(requestUrl)
    val lowerPageHost = pageHost.lowercase()

    for (extId in enabledIds) {
      val rules = dnrRulesByExt[extId] ?: continue
      for (rule in rules) {
        if (rule.excludedDomains.any { lowerPageHost == it || lowerPageHost.endsWith(".$it") }) {
          continue
        }
        if (rule.requestDomains.isNotEmpty()) {
          if (rule.requestDomains.any { reqHost == it || reqHost.endsWith(".$it") }) {
            return true
          }
        }
        if (rule.urlFilter.isNotEmpty() && matchesSimpleDnrFilter(rule.urlFilter, lowerUrl, reqHost)) {
          return true
        }
      }
    }
    return false
  }

  private fun matchesSimpleDnrFilter(filter: String, lowerUrl: String, reqHost: String): Boolean {
    if (filter.startsWith("||")) {
      val domain = filter.removePrefix("||").substringBefore('^').substringBefore('/')
      if (domain.isNotEmpty()) {
        return reqHost == domain || reqHost.endsWith(".$domain")
      }
    }
    val clean = filter.trim('|', '^', '*')
    return clean.length >= 4 && lowerUrl.contains(clean)
  }

  /**
   * Serves local extension assets mapped to https://kio-ext.invalid/<extId>/<relativePath>
   * OR chrome-extension://<extId>/<relativePath> so content scripts, popups, and options pages
   * run cleanly with synchronous chrome.* API initialization before any page script executes.
   */
  fun serveVirtualExtensionResource(requestUrl: String): WebResourceResponse? {
    val httpsPrefix = "https://$EXT_VIRTUAL_HOST/"
    val chromeExtPrefix = "chrome-extension://"
    val remainder = when {
      requestUrl.startsWith(httpsPrefix, ignoreCase = true) ->
        requestUrl.substring(httpsPrefix.length).substringBefore('?').substringBefore('#')
      requestUrl.startsWith(chromeExtPrefix, ignoreCase = true) ->
        requestUrl.substring(chromeExtPrefix.length).substringBefore('?').substringBefore('#')
      else -> return null
    }
    val slashIdx = remainder.indexOf('/')
    if (slashIdx <= 0) return null
    val extId = remainder.substring(0, slashIdx)
    val relPath = remainder.substring(slashIdx + 1).trimStart('/')
    if (relPath.isEmpty() || relPath.contains("..")) return null

    val ext = getExtension(extId) ?: return null
    val headers = mapOf(
      "Access-Control-Allow-Origin" to "*",
      "Access-Control-Allow-Methods" to "GET, POST, PUT, DELETE, OPTIONS",
      "Access-Control-Allow-Headers" to "*",
      "Cache-Control" to "no-cache",
      "Content-Security-Policy" to "default-src * 'unsafe-inline' 'unsafe-eval' data: blob:;"
    )
    if (relPath == BG_HOST_VIRTUAL_FILE) {
      val bgHtml = buildBackgroundHostHtml(ext)
      return WebResourceResponse("text/html", "utf-8", 200, "OK", headers, ByteArrayInputStream(bgHtml.toByteArray(Charsets.UTF_8)))
    }
    if (relPath == "__kio_prelude.js") {
      val prelude = buildChromeApiPrelude(ext.id, contextKind = "popup")
      return WebResourceResponse("application/javascript", "utf-8", 200, "OK", headers, ByteArrayInputStream(prelude.toByteArray(Charsets.UTF_8)))
    }
    val baseDir = File(ext.installDirPath)
    val targetFile = safeResolveAsset(baseDir, relPath)
      ?: return WebResourceResponse("text/plain", "utf-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(ByteArray(0)))
    return try {
      val mime = inferMimeType(relPath)
      val bytes = if (mime == "text/html") {
        val rawHtml = targetFile.readText(Charsets.UTF_8)
        val isBgPage = ext.backgroundPage?.trimStart('/')?.equals(relPath, ignoreCase = true) == true
        injectPreludeIntoHtml(rawHtml, extId, if (isBgPage) "background" else "popup").toByteArray(Charsets.UTF_8)
      } else {
        targetFile.readBytes()
      }
      WebResourceResponse(mime, if (mime.startsWith("text/") || mime.contains("javascript") || mime.contains("json")) "utf-8" else null, 200, "OK", headers, ByteArrayInputStream(bytes))
    } catch (_: Throwable) {
      null
    }
  }

  private fun buildBackgroundHostHtml(ext: InstalledExtension): String {
    val prelude = buildChromeApiPrelude(ext.id, contextKind = "background")
      .replace("</script>", "<\\/script>", ignoreCase = true)
    val scriptTags = buildString {
      val sw = ext.backgroundServiceWorker?.trimStart('/')
      if (!sw.isNullOrBlank()) {
        val typeAttr = if (ext.backgroundIsModule) " type=\"module\"" else ""
        append("<script$typeAttr src=\"https://$EXT_VIRTUAL_HOST/${ext.id}/$sw\"></script>\n")
      } else {
        for (sc in ext.backgroundScripts) {
          val clean = sc.trimStart('/')
          if (clean.isNotBlank()) {
            append("<script src=\"https://$EXT_VIRTUAL_HOST/${ext.id}/$clean\"></script>\n")
          }
        }
      }
    }
    return """
      <!doctype html>
      <html>
      <head>
        <meta charset="utf-8">
        <script>
        (function(){
          // Service Worker global shims for MV3 background scripts running in Kio Background Host
          try { if (typeof window.global === 'undefined') window.global = window; } catch(e) {}
          try {
            if (typeof window.importScripts !== 'function') {
              window.importScripts = function() {
                for (var i = 0; i < arguments.length; i++) {
                  var p = String(arguments[i] || '');
                  var u = /^https?:\/\//i.test(p) ? p : ('https://$EXT_VIRTUAL_HOST/${ext.id}/' + p.replace(/^\/+/, ''));
                  var xhr = new XMLHttpRequest();
                  xhr.open('GET', u, false);
                  xhr.send(null);
                  if (xhr.status >= 200 && xhr.status < 300 && xhr.responseText) {
                    (0, eval)(xhr.responseText);
                  }
                }
              };
            }
            if (typeof window.clients === 'undefined') {
              window.clients = {
                matchAll: function() { return Promise.resolve([]); },
                claim: function() { return Promise.resolve(); },
                openWindow: function(u) { if (window.chrome && window.chrome.tabs) window.chrome.tabs.create({ url: u }); return Promise.resolve(null); }
              };
            }
            if (typeof window.skipWaiting !== 'function') {
              window.skipWaiting = function() { return Promise.resolve(); };
            }
            if (typeof window.registration === 'undefined') {
              window.registration = { active: { state: 'activated' }, scope: 'https://$EXT_VIRTUAL_HOST/${ext.id}/', update: function(){ return Promise.resolve(); } };
            }
          } catch(e) {}
          $prelude
        })();
        </script>
      </head>
      <body>
        $scriptTags
        <script>
          window.addEventListener('load', function() {
            setTimeout(function() {
              try { if (window.__kioFireBackgroundStartup) window.__kioFireBackgroundStartup(); } catch(e) {}
            }, 60);
          });
        </script>
      </body>
      </html>
    """.trimIndent()
  }

  private fun injectPreludeIntoHtml(html: String, extId: String, contextKind: String = "popup"): String {
    val cleanHtml = html
      .replace(Regex("""<meta\s+[^>]*http-equiv\s*=\s*['"]?Content-Security-Policy['"]?[^>]*>""", RegexOption.IGNORE_CASE), "")
      .replace(Regex("""<meta\s+[^>]*http-equiv\s*=\s*['"]?X-Frame-Options['"]?[^>]*>""", RegexOption.IGNORE_CASE), "")
    val prelude = buildChromeApiPrelude(extId, contextKind = contextKind)
    val safePrelude = prelude.replace("</script>", "<\\/script>", ignoreCase = true)
    val scriptTag = "<script>(function(){\n$safePrelude\n})();</script>"
    val headMatch = Regex("""<head\b[^>]*>""", RegexOption.IGNORE_CASE).find(cleanHtml)
    return if (headMatch != null) {
      val insertPos = headMatch.range.last + 1
      cleanHtml.substring(0, insertPos) + "\n" + scriptTag + "\n" + cleanHtml.substring(insertPos)
    } else {
      scriptTag + "\n" + cleanHtml
    }
  }

  fun readExtensionFileText(extId: String, relativePath: String): String? {
    val ext = getExtension(extId) ?: return null
    val file = safeResolveAsset(File(ext.installDirPath), relativePath) ?: return null
    return try {
      file.readText(Charsets.UTF_8)
    } catch (_: Throwable) {
      null
    }
  }

  fun buildStorageChangeBroadcastScript(extId: String, changesJson: String): String {
    val quotedId = JSONObject.quote(extId)
    val safeChanges = try { JSONObject(changesJson).toString() } catch (_: Throwable) { "{}" }
    return "(function(){try{if(window.__kioExtNotifyStorage){window.__kioExtNotifyStorage($quotedId,$safeChanges);}}catch(e){}})();"
  }

  fun buildMessageDispatchScript(extId: String, msgJson: String): String {
    val quotedId = JSONObject.quote(extId)
    val safeMsg = try {
      val trimmed = msgJson.trim()
      if (trimmed.startsWith("{") || trimmed.startsWith("[") || trimmed.startsWith("\"") ||
        trimmed == "true" || trimmed == "false" || trimmed == "null" || trimmed.toDoubleOrNull() != null
      ) trimmed else JSONObject.quote(trimmed)
    } catch (_: Throwable) {
      "null"
    }
    return "(function(){try{if(window.__kioExtDeliverMessage){window.__kioExtDeliverMessage($quotedId,$safeMsg);}}catch(e){}})();"
  }

  /**
   * Builds the Chrome Extension runtime + content scripts injection JS for [pageUrl] and [stage] ("start" or "end").
   * Returns null if no enabled extension matches this page and stage.
   */
  fun buildPageInjectionScript(pageUrl: String, stage: String): String? {
    if (!UrlValidator.isSafeWebUrl(pageUrl)) return null
    val activeExts = synchronized(lock) {
      extensionsMap.values.filter { it.enabled && it.contentScripts.isNotEmpty() }
    }
    if (activeExts.isEmpty()) return null

    val sb = StringBuilder()
    var injectedAny = false

    for (ext in activeExts) {
      val matchingBlocks = ext.contentScripts.filter { cs ->
        val stageMatches = when (stage) {
          "start" -> cs.runAt == "document_start"
          else -> cs.runAt != "document_start"
        }
        stageMatches &&
          cs.matches.any { matchesChromePattern(it, pageUrl) } &&
          cs.excludeMatches.none { matchesChromePattern(it, pageUrl) }
      }
      if (matchingBlocks.isEmpty()) continue

      val guardKey = "__kio_ext_${ext.id.replace(Regex("[^a-zA-Z0-9_]"), "_")}_$stage"
      val baseDir = File(ext.installDirPath)
      val manifestJson = readManifestJsonSafe(baseDir)
      val localeJson = JSONObject(localesByExt[ext.id] ?: emptyMap<String, String>()).toString()
      val storageJson = readStorageJson(ext.id)

      val blockSb = StringBuilder()
      blockSb.append("(function(){\n")
      blockSb.append("if(window.").append(guardKey).append(")return;\n")
      blockSb.append("window.").append(guardKey).append("=true;\n")
      blockSb.append(buildChromeApiPrelude(ext.id, manifestJson, localeJson, storageJson))

      var blockHasContent = false
      for (cs in matchingBlocks) {
        // Inject CSS stylesheets
        for (cssRel in cs.cssFiles) {
          val cssFile = safeResolveAsset(baseDir, cssRel) ?: continue
          val cssText = try { cssFile.readText(Charsets.UTF_8) } catch (_: Throwable) { continue }
          if (cssText.isNotBlank()) {
            blockHasContent = true
            blockSb.append("try{var st=document.createElement('style');st.setAttribute('data-kio-ext',")
              .append(JSONObject.quote(ext.id))
              .append(");st.textContent=")
              .append(JSONObject.quote(cssText))
              .append(";(document.head||document.documentElement).appendChild(st);}catch(e){}\n")
          }
        }
        // Inject JS content scripts
        for (jsRel in cs.jsFiles) {
          val jsFile = safeResolveAsset(baseDir, jsRel) ?: continue
          val jsText = try { jsFile.readText(Charsets.UTF_8) } catch (_: Throwable) { continue }
          if (jsText.isNotBlank()) {
            blockHasContent = true
            blockSb.append("try{\n").append(jsText).append("\n}catch(e){}\n")
          }
        }
      }
      blockSb.append("})();\n")

      if (blockHasContent) {
        injectedAny = true
        sb.append(blockSb)
      }
    }

    return if (injectedAny) sb.toString() else null
  }

  /**
   * Generates the chrome.* / browser.* WebExtension API polyfill script used inside both
   * injected content scripts and interactive Extension Popups / Options pages.
   */
  fun buildChromeApiPrelude(
    extId: String,
    manifestJson: String? = null,
    localeJson: String? = null,
    storageJson: String? = null,
    contextKind: String = "content"
  ): String {
    val ext = getExtension(extId)
    val baseDir = ext?.let { File(it.installDirPath) }
    val resolvedManifest = manifestJson ?: baseDir?.let { readManifestJsonSafe(it) } ?: "{}"
    val resolvedLocales = localeJson ?: JSONObject(localesByExt[extId] ?: emptyMap<String, String>()).toString()
    val resolvedStorage = storageJson ?: readStorageJson(extId)
    val quotedId = JSONObject.quote(extId)
    val quotedCtx = JSONObject.quote(contextKind)
    val baseUrl = JSONObject.quote("https://$EXT_VIRTUAL_HOST/$extId/")
    val quotedManifest = JSONObject.quote(resolvedManifest)
    val quotedLocales = JSONObject.quote(resolvedLocales)
    val quotedStorage = JSONObject.quote(resolvedStorage)

    return """
      var __kioExtId = $quotedId;
      var __kioCtxKind = $quotedCtx;
      var __kioBaseUrl = $baseUrl;
      var __kioManifest = {};
      try { __kioManifest = JSON.parse($quotedManifest); } catch(e) {}
      var __kioLocales = {};
      try { __kioLocales = JSON.parse($quotedLocales); } catch(e) {}
      var __kioStoreMem = {};
      try { __kioStoreMem = JSON.parse($quotedStorage); } catch(e) {}
      var __kioStorageListeners = [];
      var __kioMessageListeners = [];
      var __kioConnectListeners = [];
      var __kioInstalledListeners = [];
      var __kioStartupListeners = [];
      var __kioAuthListeners = [];
      var __kioAlarmListeners = [];
      var __kioAlarms = {};
      var __kioProxyValue = { mode: 'system' };
      var __kioRpcSeq = 1;
      var __kioPendingRpc = {};
      var __kioPorts = {};
      var __kioFetchSeq = 1;
      var __kioPendingFetch = {};
      window.__kioExtStorageBus = window.__kioExtStorageBus || {};
      window.__kioExtStorageBus[__kioExtId] = __kioStorageListeners;
      window.__kioExtMessageBus = window.__kioExtMessageBus || {};
      window.__kioExtMessageBus[__kioExtId] = __kioMessageListeners;
      window.__kioExtConnectBus = window.__kioExtConnectBus || {};
      window.__kioExtConnectBus[__kioExtId] = __kioConnectListeners;
      window.__kioExtAuthBus = window.__kioExtAuthBus || {};
      window.__kioExtAuthBus[__kioExtId] = __kioAuthListeners;
      window.__kioExtNotifyStorage = function(id, changes) {
        var list = (window.__kioExtStorageBus && window.__kioExtStorageBus[id]) || [];
        for (var i = 0; i < list.length; i++) {
          try { list[i](changes, 'local'); } catch(e) {}
        }
      };
      window.__kioExtDeliverMessage = function(id, msg) {
        var list = (window.__kioExtMessageBus && window.__kioExtMessageBus[id]) || [];
        for (var i = 0; i < list.length; i++) {
          try { list[i](msg, { id: id, tab: __kioActiveTabObj() }, function(){}); } catch(e) {}
        }
      };
      window.__kioReceiveRpcReply = function(reqId, replyJson) {
        var entry = __kioPendingRpc[reqId];
        if (!entry) return;
        delete __kioPendingRpc[reqId];
        if (entry.timer) clearTimeout(entry.timer);
        var val = undefined;
        try { if (replyJson && replyJson !== 'undefined') val = JSON.parse(replyJson); } catch(e) { val = replyJson; }
        try { if (typeof entry.cb === 'function') entry.cb(val); } catch(e) {}
        try { if (typeof entry.resolve === 'function') entry.resolve(val); } catch(e) {}
      };
      window.__kioHandleIncomingRpc = function(id, fromCtx, reqId, msgJson) {
        var list = (window.__kioExtMessageBus && window.__kioExtMessageBus[id]) || [];
        var msg = null;
        try { msg = JSON.parse(msgJson); } catch(e) { msg = msgJson; }
        var replied = false;
        function sendResponse(resp) {
          if (replied) return;
          replied = true;
          try {
            if (window.__KioExtBridge && window.__KioExtBridge.replyRuntimeMessage) {
              window.__KioExtBridge.replyRuntimeMessage(id, fromCtx, reqId, JSON.stringify(resp === undefined ? null : resp));
            }
          } catch(e) {}
        }
        var sender = { id: id, url: __kioBaseUrl, tab: fromCtx === 'content' ? __kioActiveTabObj() : undefined };
        var asyncWait = false;
        for (var i = 0; i < list.length; i++) {
          try {
            var ret = list[i](msg, sender, sendResponse);
            if (ret === true) {
              asyncWait = true;
            } else if (ret && typeof ret.then === 'function') {
              asyncWait = true;
              ret.then(function(r) { sendResponse(r); }, function() { sendResponse(null); });
            }
          } catch(e) {}
        }
        if (!replied && !asyncWait) {
          sendResponse(null);
        }
      };
      function __kioCreatePortObject(portId, name, peerCtx) {
        var pMsg = [];
        var pDisc = [];
        var port = {
          name: String(name || ''),
          sender: { id: __kioExtId, url: __kioBaseUrl, tab: peerCtx === 'content' ? __kioActiveTabObj() : undefined },
          postMessage: function(m) {
            try {
              if (window.__KioExtBridge && window.__KioExtBridge.routePortMessage) {
                window.__KioExtBridge.routePortMessage(__kioExtId, __kioCtxKind, portId, JSON.stringify(m === undefined ? null : m));
              }
            } catch(e) {}
          },
          disconnect: function() {
            delete __kioPorts[portId];
            try {
              if (window.__KioExtBridge && window.__KioExtBridge.routePortDisconnect) {
                window.__KioExtBridge.routePortDisconnect(__kioExtId, __kioCtxKind, portId);
              }
            } catch(e) {}
            for (var i = 0; i < pDisc.length; i++) { try { pDisc[i](port); } catch(e) {} }
          },
          onMessage: __kioMakeEvent(pMsg),
          onDisconnect: __kioMakeEvent(pDisc),
          __deliver: function(m) { for (var i = 0; i < pMsg.length; i++) { try { pMsg[i](m, port); } catch(e) {} } },
          __disconnected: function() { delete __kioPorts[portId]; for (var i = 0; i < pDisc.length; i++) { try { pDisc[i](port); } catch(e) {} } }
        };
        __kioPorts[portId] = port;
        return port;
      }
      window.__kioHandleIncomingPortConnect = function(id, fromCtx, portId, name) {
        var port = __kioCreatePortObject(portId, name, fromCtx);
        var list = (window.__kioExtConnectBus && window.__kioExtConnectBus[id]) || [];
        for (var i = 0; i < list.length; i++) {
          try { list[i](port); } catch(e) {}
        }
      };
      window.__kioHandleIncomingPortMessage = function(portId, msgJson) {
        var port = __kioPorts[portId];
        if (!port) return;
        var msg = null;
        try { msg = JSON.parse(msgJson); } catch(e) { msg = msgJson; }
        port.__deliver(msg);
      };
      window.__kioHandleIncomingPortDisconnect = function(portId) {
        var port = __kioPorts[portId];
        if (!port) return;
        port.__disconnected();
      };
      window.__kioDeliverNativeFetch = function(fetchId, status, statusText, headersJson, bodyBase64, errorMsg) {
        var entry = __kioPendingFetch[fetchId];
        if (!entry) return;
        delete __kioPendingFetch[fetchId];
        if (errorMsg) {
          entry.reject(new TypeError(errorMsg));
          return;
        }
        try {
          var raw = bodyBase64 ? atob(bodyBase64) : '';
          var bytes = new Uint8Array(raw.length);
          for (var i = 0; i < raw.length; i++) bytes[i] = raw.charCodeAt(i);
          var hdrs = new Headers();
          try {
            var parsed = JSON.parse(headersJson || '{}');
            for (var k in parsed) {
              try { hdrs.set(k, String(parsed[k])); } catch(e) {}
            }
          } catch(e) {}
          var resp = new Response(status === 204 || status === 304 ? null : bytes, {
            status: status || 200,
            statusText: statusText || 'OK',
            headers: hdrs
          });
          entry.resolve(resp);
        } catch(e) {
          entry.reject(e);
        }
      };
      window.__kioQueryProxyAuth = function(id, host, port, realm) {
        var list = (window.__kioExtAuthBus && window.__kioExtAuthBus[id]) || [];
        var details = { isProxy: true, challenger: { host: host, port: port }, realm: realm || '', scheme: 'basic', url: 'https://' + host };
        for (var i = 0; i < list.length; i++) {
          try {
            var res = list[i](details, function(asyncRes) {
              if (asyncRes && asyncRes.authCredentials && window.__KioExtBridge && window.__KioExtBridge.deliverProxyAuth) {
                window.__KioExtBridge.deliverProxyAuth(host, asyncRes.authCredentials.username || '', asyncRes.authCredentials.password || '');
              }
            });
            if (res && res.authCredentials) {
              return JSON.stringify(res.authCredentials);
            }
          } catch(e) {}
        }
        return '';
      };
      window.__kioFireBackgroundStartup = function() {
        if (window.__kioBgStarted) return;
        window.__kioBgStarted = true;
        for (var i = 0; i < __kioInstalledListeners.length; i++) {
          try { __kioInstalledListeners[i]({ reason: 'install' }); } catch(e) {}
        }
        for (var j = 0; j < __kioStartupListeners.length; j++) {
          try { __kioStartupListeners[j](); } catch(e) {}
        }
        try {
          if (window.__KioExtBridge && window.__KioExtBridge.onBackgroundReady) {
            window.__KioExtBridge.onBackgroundReady(__kioExtId);
          }
        } catch(e) {}
      };
      // CORS-free privileged fetch & XHR bridge for extension background/popup contexts
      if ((__kioCtxKind === 'background' || __kioCtxKind === 'popup') && typeof window.fetch === 'function' && !window.__kioFetchPatched) {
        window.__kioFetchPatched = true;
        var __kioOrigFetch = window.fetch.bind(window);
        window.fetch = function(input, init) {
          try {
            var reqUrl = typeof input === 'string' ? input : (input && input.url ? String(input.url) : '');
            var isExternal = /^https?:\/\//i.test(reqUrl) &&
              reqUrl.indexOf('https://$EXT_VIRTUAL_HOST/') !== 0 &&
              reqUrl.indexOf('chrome-extension://') !== 0;
            if (!isExternal || !window.__KioExtBridge || !window.__KioExtBridge.nativeFetch) {
              return __kioOrigFetch(input, init);
            }
            var method = (init && init.method) || (input && input.method) || 'GET';
            var hdrObj = {};
            var rawHdrs = (init && init.headers) || (input && input.headers);
            if (rawHdrs) {
              if (typeof rawHdrs.forEach === 'function') {
                rawHdrs.forEach(function(v, k) { hdrObj[k] = String(v); });
              } else if (Array.isArray(rawHdrs)) {
                for (var h = 0; h < rawHdrs.length; h++) { if (rawHdrs[h] && rawHdrs[h].length >= 2) hdrObj[rawHdrs[h][0]] = String(rawHdrs[h][1]); }
              } else if (typeof rawHdrs === 'object') {
                for (var hk in rawHdrs) { hdrObj[hk] = String(rawHdrs[hk]); }
              }
            }
            var bodyStr = '';
            if (init && init.body != null) {
              bodyStr = typeof init.body === 'string' ? init.body : String(init.body);
            }
            var fid = 'f_' + (__kioFetchSeq++);
            return new Promise(function(resolve, reject) {
              __kioPendingFetch[fid] = { resolve: resolve, reject: reject };
              window.__KioExtBridge.nativeFetch(
                __kioExtId,
                __kioCtxKind,
                fid,
                reqUrl,
                JSON.stringify({ method: String(method).toUpperCase(), headers: hdrObj, body: bodyStr })
              );
            });
          } catch(e) {
            return __kioOrigFetch(input, init);
          }
        };
        // Also bridge external XMLHttpRequest (used by Axios / React extension popups) through CORS-free nativeFetch
        var __kioOrigXhr = window.XMLHttpRequest;
        if (__kioOrigXhr && !window.__kioXhrPatched) {
          window.__kioXhrPatched = true;
          var __kioOrigOpen = __kioOrigXhr.prototype.open;
          var __kioOrigSetHdr = __kioOrigXhr.prototype.setRequestHeader;
          var __kioOrigSend = __kioOrigXhr.prototype.send;
          __kioOrigXhr.prototype.open = function(method, url, async) {
            this.__kioMethod = String(method || 'GET').toUpperCase();
            this.__kioUrl = String(url || '');
            this.__kioAsync = async !== false;
            this.__kioHdrs = {};
            this.__kioExternal = this.__kioAsync && /^https?:\/\//i.test(this.__kioUrl) &&
              this.__kioUrl.indexOf('https://$EXT_VIRTUAL_HOST/') !== 0 &&
              this.__kioUrl.indexOf('chrome-extension://') !== 0;
            if (!this.__kioExternal) return __kioOrigOpen.apply(this, arguments);
          };
          __kioOrigXhr.prototype.setRequestHeader = function(k, v) {
            if (this.__kioExternal) { this.__kioHdrs[k] = String(v); return; }
            return __kioOrigSetHdr.apply(this, arguments);
          };
          __kioOrigXhr.prototype.send = function(body) {
            if (!this.__kioExternal) return __kioOrigSend.apply(this, arguments);
            var selfXhr = this;
            window.fetch(selfXhr.__kioUrl, { method: selfXhr.__kioMethod, headers: selfXhr.__kioHdrs, body: body != null ? String(body) : undefined })
              .then(function(resp) {
                return resp.text().then(function(txt) {
                  var respHeaders = {};
                  resp.headers.forEach(function(v, k) { respHeaders[k.toLowerCase()] = v; });
                  Object.defineProperty(selfXhr, 'readyState', { value: 4, configurable: true });
                  Object.defineProperty(selfXhr, 'status', { value: resp.status, configurable: true });
                  Object.defineProperty(selfXhr, 'statusText', { value: resp.statusText, configurable: true });
                  Object.defineProperty(selfXhr, 'responseText', { value: txt, configurable: true });
                  Object.defineProperty(selfXhr, 'response', { value: selfXhr.responseType === 'json' ? (function(){try{return JSON.parse(txt);}catch(e){return null;}})() : txt, configurable: true });
                  Object.defineProperty(selfXhr, 'responseURL', { value: selfXhr.__kioUrl, configurable: true });
                  selfXhr.getResponseHeader = function(n) { return respHeaders[String(n || '').toLowerCase()] || null; };
                  selfXhr.getAllResponseHeaders = function() { var s = ''; for (var k in respHeaders) s += k + ': ' + respHeaders[k] + '\r\n'; return s; };
                  if (typeof selfXhr.onreadystatechange === 'function') try { selfXhr.onreadystatechange(); } catch(e) {}
                  if (typeof selfXhr.onload === 'function') try { selfXhr.onload(); } catch(e) {}
                  if (typeof selfXhr.onloadend === 'function') try { selfXhr.onloadend(); } catch(e) {}
                });
              })
              .catch(function() {
                Object.defineProperty(selfXhr, 'readyState', { value: 4, configurable: true });
                Object.defineProperty(selfXhr, 'status', { value: 0, configurable: true });
                if (typeof selfXhr.onerror === 'function') try { selfXhr.onerror(new Event('error')); } catch(e) {}
                if (typeof selfXhr.onloadend === 'function') try { selfXhr.onloadend(); } catch(e) {}
              });
          };
        }
      }
      function __kioMakeEvent(arr) {
        var list = arr || [];
        return {
          addListener: function(fn) { if (typeof fn === 'function' && list.indexOf(fn) === -1) list.push(fn); },
          removeListener: function(fn) { var idx = list.indexOf(fn); if (idx >= 0) list.splice(idx, 1); },
          hasListener: function(fn) { return list.indexOf(fn) >= 0; },
          hasListeners: function() { return list.length > 0; }
        };
      }
      function __kioMakeSetting(initialVal) {
        var val = initialVal;
        return {
          get: function(d, cb) { var r = { value: val, levelOfControl: 'controllable_by_this_extension' }; if (typeof cb === 'function') try{cb(r);}catch(e){} return Promise.resolve(r); },
          set: function(d, cb) { if (d && 'value' in d) val = d.value; if (typeof cb === 'function') try{cb();}catch(e){} return Promise.resolve(); },
          clear: function(d, cb) { val = initialVal; if (typeof cb === 'function') try{cb();}catch(e){} return Promise.resolve(); },
          onChange: __kioMakeEvent([])
        };
      }
      function __kioReadStore() {
        try {
          if (window.__KioExtBridge && window.__KioExtBridge.storageGetAll) {
            var raw = window.__KioExtBridge.storageGetAll(__kioExtId);
            if (raw) __kioStoreMem = JSON.parse(raw);
          }
        } catch(e) {}
        return __kioStoreMem || {};
      }
      function __kioWriteStore(obj, changes) {
        __kioStoreMem = obj;
        try {
          if (window.__KioExtBridge && window.__KioExtBridge.storageSetAll) {
            window.__KioExtBridge.storageSetAll(__kioExtId, JSON.stringify(obj));
          }
          if (changes && window.__KioExtBridge && window.__KioExtBridge.broadcastStorageChange) {
            window.__KioExtBridge.broadcastStorageChange(__kioExtId, JSON.stringify(changes));
          }
        } catch(e) {}
      }
      function __kioMakeArea() {
        return {
          get: function(keys, cb) {
            var all = __kioReadStore();
            var res = {};
            if (keys == null) {
              res = Object.assign({}, all);
            } else if (typeof keys === 'string') {
              if (keys in all) res[keys] = all[keys];
            } else if (Array.isArray(keys)) {
              for (var i = 0; i < keys.length; i++) {
                if (keys[i] in all) res[keys[i]] = all[keys[i]];
              }
            } else if (typeof keys === 'object') {
              for (var k in keys) {
                res[k] = (k in all) ? all[k] : keys[k];
              }
            }
            if (typeof cb === 'function') { try { cb(res); } catch(e) {} }
            return Promise.resolve(res);
          },
          set: function(items, cb) {
            var all = __kioReadStore();
            var changes = {};
            if (items && typeof items === 'object') {
              for (var k in items) {
                changes[k] = { oldValue: all[k], newValue: items[k] };
                all[k] = items[k];
              }
              __kioWriteStore(all, changes);
              for (var i = 0; i < __kioStorageListeners.length; i++) {
                try { __kioStorageListeners[i](changes, 'local'); } catch(e) {}
              }
            }
            if (typeof cb === 'function') { try { cb(); } catch(e) {} }
            return Promise.resolve();
          },
          remove: function(keys, cb) {
            var all = __kioReadStore();
            var list = Array.isArray(keys) ? keys : [keys];
            var changes = {};
            for (var i = 0; i < list.length; i++) {
              if (list[i] in all) {
                changes[list[i]] = { oldValue: all[list[i]], newValue: undefined };
                delete all[list[i]];
              }
            }
            __kioWriteStore(all, changes);
            if (typeof cb === 'function') { try { cb(); } catch(e) {} }
            return Promise.resolve();
          },
          clear: function(cb) {
            __kioWriteStore({}, {});
            if (typeof cb === 'function') { try { cb(); } catch(e) {} }
            return Promise.resolve();
          },
          setAccessLevel: function(o, cb) {
            if (typeof cb === 'function') { try { cb(); } catch(e) {} }
            return Promise.resolve();
          },
          getBytesInUse: function(keys, cb) {
            var sz = JSON.stringify(__kioReadStore()).length;
            if (typeof keys === 'function') { try { keys(sz); } catch(e) {} }
            else if (typeof cb === 'function') { try { cb(sz); } catch(e) {} }
            return Promise.resolve(sz);
          }
        };
      }
      var __kioArea = __kioMakeArea();
      function __kioActiveTabObj() {
        var tabUrl = (window.__KioExtBridge && window.__KioExtBridge.getActiveUrl) ? window.__KioExtBridge.getActiveUrl() : window.location.href;
        var tabTitle = (window.__KioExtBridge && window.__KioExtBridge.getActiveTitle) ? window.__KioExtBridge.getActiveTitle() : document.title;
        return { id: 1, index: 0, windowId: 1, url: tabUrl || '', title: tabTitle || '', active: true, highlighted: true, status: 'complete', incognito: false };
      }
      var __kioActionStub = {
        setBadgeText: function(d, cb) { if (typeof cb === 'function') try{cb();}catch(e){} return Promise.resolve(); },
        getBadgeText: function(d, cb) { if (typeof cb === 'function') try{cb('');}catch(e){} return Promise.resolve(''); },
        setBadgeBackgroundColor: function(d, cb) { if (typeof cb === 'function') try{cb();}catch(e){} return Promise.resolve(); },
        setBadgeTextColor: function(d, cb) { if (typeof cb === 'function') try{cb();}catch(e){} return Promise.resolve(); },
        setIcon: function(d, cb) { if (typeof cb === 'function') try{cb();}catch(e){} return Promise.resolve(); },
        setTitle: function(d, cb) { if (typeof cb === 'function') try{cb();}catch(e){} return Promise.resolve(); },
        setPopup: function(d, cb) { if (typeof cb === 'function') try{cb();}catch(e){} return Promise.resolve(); },
        getUserSettings: function(cb) { var r = { isOnToolbar: true }; if (typeof cb === 'function') try{cb(r);}catch(e){} return Promise.resolve(r); },
        openPopup: function(cb) { if (window.__KioExtBridge && window.__KioExtBridge.openPopup) try{window.__KioExtBridge.openPopup(__kioExtId);}catch(e){} if (typeof cb === 'function') try{cb();}catch(e){} return Promise.resolve(); },
        isEnabled: function(d, cb) { var fn = typeof d === 'function' ? d : cb; if (typeof fn === 'function') try{fn(true);}catch(e){} return Promise.resolve(true); },
        getTitle: function(d, cb) { var fn = typeof d === 'function' ? d : cb; if (typeof fn === 'function') try{fn('');}catch(e){} return Promise.resolve(''); },
        getPopup: function(d, cb) { var fn = typeof d === 'function' ? d : cb; if (typeof fn === 'function') try{fn('');}catch(e){} return Promise.resolve(''); },
        enable: function() { return Promise.resolve(); },
        disable: function() { return Promise.resolve(); },
        onClicked: __kioMakeEvent([])
      };
      var __kioChrome = {
        runtime: {
          id: __kioExtId,
          lastError: null,
          getManifest: function() { return __kioManifest; },
          getURL: function(path) {
            var clean = String(path || '').replace(/^\/+/, '');
            return __kioBaseUrl + clean;
          },
          getPlatformInfo: function(cb) {
            var info = { os: 'win', arch: 'x86-64', nacl_arch: 'x86-64' };
            if (typeof cb === 'function') { try { cb(info); } catch(e) {} }
            return Promise.resolve(info);
          },
          getBackgroundPage: function(cb) {
            if (typeof cb === 'function') { try { cb(window); } catch(e) {} }
            return Promise.resolve(window);
          },
          setUninstallURL: function(u, cb) {
            if (typeof cb === 'function') { try { cb(); } catch(e) {} }
            return Promise.resolve();
          },
          sendMessage: function(extOrMsg, msgOrOpts, optsOrCb, maybeCb) {
            var msg = extOrMsg;
            var cb = typeof msgOrOpts === 'function' ? msgOrOpts : (typeof optsOrCb === 'function' ? optsOrCb : maybeCb);
            if (typeof extOrMsg === 'string' && msgOrOpts !== undefined && typeof msgOrOpts !== 'function') {
              msg = msgOrOpts;
            }
            if (window.__KioExtBridge && window.__KioExtBridge.routeRuntimeMessage) {
              var reqId = __kioCtxKind + '_rpc_' + (__kioRpcSeq++);
              return new Promise(function(resolve) {
                var timer = setTimeout(function() {
                  if (__kioPendingRpc[reqId]) {
                    delete __kioPendingRpc[reqId];
                    if (typeof cb === 'function') { try { cb(undefined); } catch(e) {} }
                    resolve(undefined);
                  }
                }, 4500);
                __kioPendingRpc[reqId] = { cb: cb, resolve: resolve, timer: timer };
                try {
                  window.__KioExtBridge.routeRuntimeMessage(__kioExtId, __kioCtxKind, reqId, JSON.stringify(msg === undefined ? null : msg));
                } catch(e) {
                  clearTimeout(timer);
                  delete __kioPendingRpc[reqId];
                  if (typeof cb === 'function') { try { cb(undefined); } catch(e2) {} }
                  resolve(undefined);
                }
              });
            }
            var handled = null;
            for (var i = 0; i < __kioMessageListeners.length; i++) {
              try {
                __kioMessageListeners[i](msg, { id: __kioExtId, tab: __kioActiveTabObj() }, function(r) { handled = r; });
              } catch(e) {}
            }
            if (typeof cb === 'function') { try { cb(handled); } catch(e) {} }
            return Promise.resolve(handled);
          },
          connect: function(opts) {
            var portId = __kioCtxKind + '_port_' + (__kioRpcSeq++);
            var pName = (opts && opts.name) ? String(opts.name) : '';
            var port = __kioCreatePortObject(portId, pName, __kioCtxKind === 'background' ? 'popup' : 'background');
            try {
              if (window.__KioExtBridge && window.__KioExtBridge.routePortConnect) {
                window.__KioExtBridge.routePortConnect(__kioExtId, __kioCtxKind, portId, pName);
              }
            } catch(e) {}
            return port;
          },
          onMessage: __kioMakeEvent(__kioMessageListeners),
          onConnect: __kioMakeEvent(__kioConnectListeners),
          onInstalled: __kioMakeEvent(__kioInstalledListeners),
          onStartup: __kioMakeEvent(__kioStartupListeners),
          onSuspend: __kioMakeEvent([]),
          openOptionsPage: function(cb) {
            try {
              if (window.__KioExtBridge && window.__KioExtBridge.openOptionsPage) {
                window.__KioExtBridge.openOptionsPage(__kioExtId);
              }
            } catch(e) {}
            if (typeof cb === 'function') { try { cb(); } catch(e) {} }
            return Promise.resolve();
          }
        },
        extension: {
          getURL: function(path) {
            var clean = String(path || '').replace(/^\/+/, '');
            return __kioBaseUrl + clean;
          },
          getBackgroundPage: function() { return window; },
          getViews: function(o) { return [window]; },
          isAllowedIncognitoAccess: function(cb) { if (typeof cb === 'function') try{cb(false);}catch(e){} return Promise.resolve(false); },
          isAllowedFileSchemeAccess: function(cb) { if (typeof cb === 'function') try{cb(false);}catch(e){} return Promise.resolve(false); },
          sendRequest: function(msg, cb) { return __kioChrome.runtime.sendMessage(msg, cb); },
          onRequest: __kioMakeEvent([]),
          inIncognitoContext: false
        },
        storage: {
          local: __kioArea,
          sync: __kioArea,
          session: __kioArea,
          managed: __kioArea,
          onChanged: __kioMakeEvent(__kioStorageListeners)
        },
        i18n: {
          getMessage: function(key, subs) {
            var val = __kioLocales[key] || '';
            if (val && subs) {
              var arr = Array.isArray(subs) ? subs : [subs];
              for (var i = 0; i < arr.length; i++) {
                val = val.replace('$' + (i + 1), String(arr[i]));
              }
            }
            return val;
          },
          getUILanguage: function() { return navigator.language || 'en'; },
          getAcceptLanguages: function(cb) {
            var langs = [navigator.language || 'en'];
            if (typeof cb === 'function') { try { cb(langs); } catch(e) {} }
            return Promise.resolve(langs);
          },
          detectLanguage: function(text, cb) {
            var r = { isReliable: true, languages: [{ language: 'en', percentage: 100 }] };
            if (typeof cb === 'function') { try { cb(r); } catch(e) {} }
            return Promise.resolve(r);
          }
        },
        tabs: {
          create: function(opts, cb) {
            var targetUrl = opts && opts.url ? String(opts.url) : '';
            if (targetUrl && window.__KioExtBridge && window.__KioExtBridge.openTab) {
              try { window.__KioExtBridge.openTab(targetUrl); } catch(e) {}
            }
            var tab = Object.assign(__kioActiveTabObj(), { url: targetUrl });
            if (typeof cb === 'function') { try { cb(tab); } catch(e) {} }
            return Promise.resolve(tab);
          },
          query: function(q, cb) {
            var list = [__kioActiveTabObj()];
            if (typeof cb === 'function') { try { cb(list); } catch(e) {} }
            return Promise.resolve(list);
          },
          get: function(tabId, cb) {
            var t = __kioActiveTabObj();
            if (typeof cb === 'function') { try { cb(t); } catch(e) {} }
            return Promise.resolve(t);
          },
          update: function(tabIdOrProps, propsOrCb, maybeCb) {
            var props = typeof tabIdOrProps === 'object' ? tabIdOrProps : propsOrCb;
            var cb = typeof propsOrCb === 'function' ? propsOrCb : maybeCb;
            if (props && props.url && window.__KioExtBridge && window.__KioExtBridge.openTab) {
              try { window.__KioExtBridge.openTab(String(props.url)); } catch(e) {}
            }
            var t = __kioActiveTabObj();
            if (typeof cb === 'function') { try { cb(t); } catch(e) {} }
            return Promise.resolve(t);
          },
          reload: function(tabIdOrProps, propsOrCb, maybeCb) {
            var cb = typeof tabIdOrProps === 'function' ? tabIdOrProps : (typeof propsOrCb === 'function' ? propsOrCb : maybeCb);
            try { if (window.__KioExtBridge && window.__KioExtBridge.reloadActiveTab) window.__KioExtBridge.reloadActiveTab(); } catch(e) {}
            if (typeof cb === 'function') { try { cb(); } catch(e) {} }
            return Promise.resolve();
          },
          remove: function(ids, cb) { if (typeof cb === 'function') try{cb();}catch(e){} return Promise.resolve(); },
          sendMessage: function(tabId, msg, optsOrCb, maybeCb) {
            var cb = typeof optsOrCb === 'function' ? optsOrCb : maybeCb;
            if (window.__KioExtBridge && window.__KioExtBridge.routeRuntimeMessage) {
              var reqId = __kioCtxKind + '_tabrpc_' + (__kioRpcSeq++);
              return new Promise(function(resolve) {
                var timer = setTimeout(function() {
                  if (__kioPendingRpc[reqId]) {
                    delete __kioPendingRpc[reqId];
                    if (typeof cb === 'function') { try { cb(undefined); } catch(e) {} }
                    resolve(undefined);
                  }
                }, 4500);
                __kioPendingRpc[reqId] = { cb: cb, resolve: resolve, timer: timer };
                try {
                  window.__KioExtBridge.routeRuntimeMessage(__kioExtId, __kioCtxKind, reqId, JSON.stringify(msg === undefined ? null : msg));
                } catch(e) {
                  clearTimeout(timer);
                  delete __kioPendingRpc[reqId];
                  if (typeof cb === 'function') { try { cb(undefined); } catch(e2) {} }
                  resolve(undefined);
                }
              });
            }
            if (typeof cb === 'function') { try { cb(null); } catch(e) {} }
            return Promise.resolve(null);
          },
          connect: function(tabId, opts) {
            var portId = __kioCtxKind + '_tabport_' + (__kioRpcSeq++);
            var pName = (opts && opts.name) ? String(opts.name) : '';
            var port = __kioCreatePortObject(portId, pName, 'content');
            try {
              if (window.__KioExtBridge && window.__KioExtBridge.routePortConnect) {
                window.__KioExtBridge.routePortConnect(__kioExtId, __kioCtxKind, portId, pName);
              }
            } catch(e) {}
            return port;
          },
          insertCSS: function(tabIdOrDetails, detailsOrCb, maybeCb) {
            var d = typeof tabIdOrDetails === 'object' ? tabIdOrDetails : detailsOrCb;
            var cb = typeof detailsOrCb === 'function' ? detailsOrCb : maybeCb;
            try {
              if (d && d.code && window.__KioExtBridge && window.__KioExtBridge.insertCssInActiveTab) {
                window.__KioExtBridge.insertCssInActiveTab(__kioExtId, String(d.code));
              } else if (d && d.file && window.__KioExtBridge && window.__KioExtBridge.insertCssFileInActiveTab) {
                window.__KioExtBridge.insertCssFileInActiveTab(__kioExtId, String(d.file));
              }
            } catch(e) {}
            if (typeof cb === 'function') { try { cb(); } catch(e) {} }
            return Promise.resolve();
          },
          removeCSS: function(tabIdOrDetails, detailsOrCb, maybeCb) {
            var cb = typeof detailsOrCb === 'function' ? detailsOrCb : maybeCb;
            try {
              if (window.__KioExtBridge && window.__KioExtBridge.removeCssFromActiveTab) {
                window.__KioExtBridge.removeCssFromActiveTab(__kioExtId);
              }
            } catch(e) {}
            if (typeof cb === 'function') { try { cb(); } catch(e) {} }
            return Promise.resolve();
          },
          executeScript: function(tabIdOrDetails, detailsOrCb, maybeCb) {
            var d = typeof tabIdOrDetails === 'object' ? tabIdOrDetails : detailsOrCb;
            var cb = typeof detailsOrCb === 'function' ? detailsOrCb : maybeCb;
            try {
              if (d && d.code && window.__KioExtBridge && window.__KioExtBridge.executeScriptInActiveTab) {
                window.__KioExtBridge.executeScriptInActiveTab(__kioExtId, String(d.code));
              } else if (d && d.file && window.__KioExtBridge && window.__KioExtBridge.executeScriptFileInActiveTab) {
                window.__KioExtBridge.executeScriptFileInActiveTab(__kioExtId, String(d.file));
              }
            } catch(e) {}
            var res = [{ frameId: 0, result: null }];
            if (typeof cb === 'function') { try { cb(res); } catch(e) {} }
            return Promise.resolve(res);
          },
          onUpdated: __kioMakeEvent([]),
          onActivated: __kioMakeEvent([]),
          onCreated: __kioMakeEvent([]),
          onRemoved: __kioMakeEvent([])
        },
        scripting: {
          executeScript: function(inj, cb) {
            try {
              if (inj && typeof inj.func === 'function' && window.__KioExtBridge && window.__KioExtBridge.executeScriptInActiveTab) {
                var argsJson = JSON.stringify(inj.args || []);
                window.__KioExtBridge.executeScriptInActiveTab(__kioExtId, '(' + inj.func.toString() + ').apply(null,' + argsJson + ');');
              } else if (inj && Array.isArray(inj.files) && window.__KioExtBridge && window.__KioExtBridge.executeScriptFileInActiveTab) {
                for (var i = 0; i < inj.files.length; i++) {
                  window.__KioExtBridge.executeScriptFileInActiveTab(__kioExtId, String(inj.files[i]));
                }
              }
            } catch(e) {}
            var res = [{ frameId: 0, result: null }];
            if (typeof cb === 'function') { try { cb(res); } catch(e) {} }
            return Promise.resolve(res);
          },
          insertCSS: function(inj, cb) {
            try {
              if (inj && inj.css && window.__KioExtBridge && window.__KioExtBridge.insertCssInActiveTab) {
                window.__KioExtBridge.insertCssInActiveTab(__kioExtId, String(inj.css));
              } else if (inj && Array.isArray(inj.files) && window.__KioExtBridge && window.__KioExtBridge.insertCssFileInActiveTab) {
                for (var i = 0; i < inj.files.length; i++) {
                  window.__KioExtBridge.insertCssFileInActiveTab(__kioExtId, String(inj.files[i]));
                }
              }
            } catch(e) {}
            if (typeof cb === 'function') { try { cb(); } catch(e) {} }
            return Promise.resolve();
          },
          removeCSS: function(inj, cb) {
            try {
              if (window.__KioExtBridge && window.__KioExtBridge.removeCssFromActiveTab) {
                window.__KioExtBridge.removeCssFromActiveTab(__kioExtId);
              }
            } catch(e) {}
            if (typeof cb === 'function') { try { cb(); } catch(e) {} }
            return Promise.resolve();
          },
          registerContentScripts: function(s, cb) { if (typeof cb === 'function') try{cb();}catch(e){} return Promise.resolve(); },
          unregisterContentScripts: function(s, cb) { if (typeof cb === 'function') try{cb();}catch(e){} return Promise.resolve(); }
        },
        action: __kioActionStub,
        browserAction: __kioActionStub,
        pageAction: __kioActionStub,
        permissions: {
          contains: function(p, cb) { if (typeof cb === 'function') try{cb(true);}catch(e){} return Promise.resolve(true); },
          request: function(p, cb) { if (typeof cb === 'function') try{cb(true);}catch(e){} return Promise.resolve(true); },
          getAll: function(cb) { var r = { permissions: __kioManifest.permissions || [], origins: ['<all_urls>'] }; if (typeof cb === 'function') try{cb(r);}catch(e){} return Promise.resolve(r); },
          remove: function(p, cb) { if (typeof cb === 'function') try{cb(true);}catch(e){} return Promise.resolve(true); }
        },
        alarms: {
          create: function(nameOrInfo, maybeInfo) {
            var n = typeof nameOrInfo === 'string' ? nameOrInfo : '';
            var info = typeof nameOrInfo === 'object' ? nameOrInfo : (maybeInfo || {});
            if (__kioAlarms[n] && __kioAlarms[n].timer) clearTimeout(__kioAlarms[n].timer);
            var delayMs = Math.max(1000, ((info.delayInMinutes || info.periodInMinutes || 1) * 60000));
            var alarmObj = { name: n, scheduledTime: Date.now() + delayMs, periodInMinutes: info.periodInMinutes };
            var fire = function() {
              for (var i = 0; i < __kioAlarmListeners.length; i++) { try { __kioAlarmListeners[i](alarmObj); } catch(e) {} }
              if (info.periodInMinutes && __kioAlarms[n]) {
                __kioAlarms[n].timer = setTimeout(fire, Math.max(1000, info.periodInMinutes * 60000));
              }
            };
            __kioAlarms[n] = { alarm: alarmObj, timer: setTimeout(fire, delayMs) };
          },
          get: function(n, cb) { var a = __kioAlarms[n] ? __kioAlarms[n].alarm : undefined; if (typeof cb === 'function') try{cb(a);}catch(e){} return Promise.resolve(a); },
          getAll: function(cb) { var list = Object.keys(__kioAlarms).map(function(k){return __kioAlarms[k].alarm;}); if (typeof cb === 'function') try{cb(list);}catch(e){} return Promise.resolve(list); },
          clear: function(n, cb) { var ok = !!__kioAlarms[n]; if (ok) { clearTimeout(__kioAlarms[n].timer); delete __kioAlarms[n]; } if (typeof cb === 'function') try{cb(ok);}catch(e){} return Promise.resolve(ok); },
          clearAll: function(cb) { for (var k in __kioAlarms) clearTimeout(__kioAlarms[k].timer); __kioAlarms = {}; if (typeof cb === 'function') try{cb(true);}catch(e){} return Promise.resolve(true); },
          onAlarm: __kioMakeEvent(__kioAlarmListeners)
        },
        contextMenus: { create: function(){}, removeAll: function(cb){ if(typeof cb==='function')try{cb();}catch(e){} }, onClicked: __kioMakeEvent([]) },
        notifications: { create: function(id, o, cb){ if(typeof cb==='function')try{cb(id||'1');}catch(e){} return Promise.resolve(id||'1'); }, clear: function(){ return Promise.resolve(true); }, onClicked: __kioMakeEvent([]) },
        webNavigation: { onCommitted: __kioMakeEvent([]), onCompleted: __kioMakeEvent([]), onHistoryStateUpdated: __kioMakeEvent([]), onBeforeNavigate: __kioMakeEvent([]) },
        webRequest: {
          handlerBehaviorChanged: function(cb){ if(typeof cb==='function')try{cb();}catch(e){} return Promise.resolve(); },
          ResourceType: { MAIN_FRAME: 'main_frame', SUB_FRAME: 'sub_frame', STYLESHEET: 'stylesheet', SCRIPT: 'script', IMAGE: 'image', FONT: 'font', OBJECT: 'object', XMLHTTPREQUEST: 'xmlhttprequest', PING: 'ping', CSP_REPORT: 'csp_report', MEDIA: 'media', WEBSOCKET: 'websocket', OTHER: 'other' },
          onBeforeRequest: __kioMakeEvent([]),
          onBeforeSendHeaders: __kioMakeEvent([]),
          onHeadersReceived: __kioMakeEvent([]),
          onCompleted: __kioMakeEvent([]),
          onErrorOccurred: __kioMakeEvent([]),
          onAuthRequired: __kioMakeEvent(__kioAuthListeners)
        },
        cookies: {
          get: function(d, cb){ if(typeof cb==='function')try{cb(null);}catch(e){} return Promise.resolve(null); },
          getAll: function(d, cb){ if(typeof cb==='function')try{cb([]);}catch(e){} return Promise.resolve([]); },
          set: function(d, cb){ if(typeof cb==='function')try{cb(null);}catch(e){} return Promise.resolve(null); },
          remove: function(d, cb){ if(typeof cb==='function')try{cb(null);}catch(e){} return Promise.resolve(null); },
          getAllCookieStores: function(cb){ var r = [{ id: '0', tabIds: [1], incognito: false }]; if(typeof cb==='function')try{cb(r);}catch(e){} return Promise.resolve(r); },
          onChanged: __kioMakeEvent([])
        },
        windows: {
          getCurrent: function(cb){ var w = { id: 1, focused: true, type: 'normal', tabs: [__kioActiveTabObj()] }; if(typeof cb==='function')try{cb(w);}catch(e){} return Promise.resolve(w); },
          getLastFocused: function(cb){ var w = { id: 1, focused: true, type: 'normal', tabs: [__kioActiveTabObj()] }; if(typeof cb==='function')try{cb(w);}catch(e){} return Promise.resolve(w); },
          getAll: function(o, cb){ var fn = typeof o==='function'?o:cb; var list = [{ id: 1, focused: true, type: 'normal', tabs: [__kioActiveTabObj()] }]; if(typeof fn==='function')try{fn(list);}catch(e){} return Promise.resolve(list); },
          create: function(o, cb){ var w = { id: 1, focused: true, type: 'normal', tabs: [__kioActiveTabObj()] }; if(typeof cb==='function')try{cb(w);}catch(e){} return Promise.resolve(w); },
          onFocusChanged: __kioMakeEvent([]),
          onRemoved: __kioMakeEvent([])
        },
        commands: { onCommand: __kioMakeEvent([]), getAll: function(cb){ if(typeof cb==='function')try{cb([]);}catch(e){} return Promise.resolve([]); } },
        declarativeNetRequest: {
          MAX_NUMBER_OF_DYNAMIC_AND_SESSION_RULES: 5000,
          updateEnabledRulesets: function(o, cb){ if(typeof cb==='function')try{cb();}catch(e){} return Promise.resolve(); },
          getEnabledRulesets: function(cb){ if(typeof cb==='function')try{cb([]);}catch(e){} return Promise.resolve([]); },
          updateDynamicRules: function(o, cb){ if(typeof cb==='function')try{cb();}catch(e){} return Promise.resolve(); },
          getDynamicRules: function(cb){ if(typeof cb==='function')try{cb([]);}catch(e){} return Promise.resolve([]); },
          updateSessionRules: function(o, cb){ if(typeof cb==='function')try{cb();}catch(e){} return Promise.resolve(); },
          getSessionRules: function(cb){ if(typeof cb==='function')try{cb([]);}catch(e){} return Promise.resolve([]); },
          getDisabledRuleIds: function(o, cb){ if(typeof cb==='function')try{cb([]);}catch(e){} return Promise.resolve([]); },
          updateStaticRules: function(o, cb){ if(typeof cb==='function')try{cb();}catch(e){} return Promise.resolve(); },
          setExtensionActionOptions: function(o, cb){ if(typeof cb==='function')try{cb();}catch(e){} return Promise.resolve(); },
          getMatchedStyles: function(cb){ if(typeof cb==='function')try{cb([]);}catch(e){} return Promise.resolve([]); },
          testMatchOutcome: function(o, cb){ var r = { matchedRules: [] }; if(typeof cb==='function')try{cb(r);}catch(e){} return Promise.resolve(r); },
          onRuleMatchedDebug: __kioMakeEvent([]),
          RuleActionType: { BLOCK: 'block', REDIRECT: 'redirect', ALLOW: 'allow', UPGRADE_SCHEME: 'upgradeScheme', MODIFY_HEADERS: 'modifyHeaders', ALLOW_ALL_REQUESTS: 'allowAllRequests' },
          HeaderOperation: { APPEND: 'append', SET: 'set', REMOVE: 'remove' },
          DomainType: { FIRST_PARTY: 'firstParty', THIRD_PARTY: 'thirdParty' },
          isRegexSupported: function(o, cb){ var r = { isSupported: true }; if(typeof cb==='function')try{cb(r);}catch(e){} return Promise.resolve(r); }
        },
        proxy: {
          settings: {
            get: function(d, cb){ var r = { value: __kioProxyValue, levelOfControl: 'controllable_by_this_extension' }; if(typeof cb==='function')try{cb(r);}catch(e){} return Promise.resolve(r); },
            set: function(d, cb){
              if (d && d.value) { __kioProxyValue = d.value; }
              try {
                if (window.__KioExtBridge && window.__KioExtBridge.proxySet) {
                  window.__KioExtBridge.proxySet(__kioExtId, JSON.stringify(d || {}));
                }
              } catch(e) {}
              if(typeof cb==='function')try{cb();}catch(e){}
              return Promise.resolve();
            },
            clear: function(d, cb){
              __kioProxyValue = { mode: 'system' };
              try {
                if (window.__KioExtBridge && window.__KioExtBridge.proxyClear) {
                  window.__KioExtBridge.proxyClear(__kioExtId);
                }
              } catch(e) {}
              if(typeof cb==='function')try{cb();}catch(e){}
              return Promise.resolve();
            },
            onChange: __kioMakeEvent([])
          },
          onProxyError: __kioMakeEvent([])
        },
        privacy: {
          network: {
            webRTCIPHandlingPolicy: __kioMakeSetting('default_public_interface_only'),
            networkPredictionEnabled: __kioMakeSetting(false)
          },
          websites: {
            hyperlinkAuditingEnabled: __kioMakeSetting(false),
            referrersEnabled: __kioMakeSetting(true),
            thirdPartyCookiesAllowed: __kioMakeSetting(true)
          },
          services: {
            autofillEnabled: __kioMakeSetting(false),
            passwordSavingEnabled: __kioMakeSetting(false)
          }
        },
        offscreen: {
          Reason: { AUDIO_PLAYBACK: 'AUDIO_PLAYBACK', BLOBS: 'BLOBS', CLIPBOARD: 'CLIPBOARD', DOM_PARSER: 'DOM_PARSER', DOM_SCRAPING: 'DOM_SCRAPING', IFRAME_SCRIPTING: 'IFRAME_SCRIPTING', LOCAL_STORAGE: 'LOCAL_STORAGE', MATCH_MEDIA: 'MATCH_MEDIA', TESTING: 'TESTING', USER_MEDIA: 'USER_MEDIA', WEB_RTC: 'WEB_RTC', WORKERS: 'WORKERS' },
          createDocument: function(o, cb){ if(typeof cb==='function')try{cb();}catch(e){} return Promise.resolve(); },
          closeDocument: function(cb){ if(typeof cb==='function')try{cb();}catch(e){} return Promise.resolve(); },
          hasDocument: function(cb){ if(typeof cb==='function')try{cb(false);}catch(e){} return Promise.resolve(false); }
        },
        dom: {
          openOrClosedShadowRoot: function(el) { return (el && (el.shadowRoot || el.openOrClosedShadowRoot)) || null; }
        },
        system: {
          cpu: { getInfo: function(cb) { var r = { numOfProcessors: 4, archName: 'arm64', modelName: 'ARM', features: [], processors: [{ usage: { user: 0, kernel: 0, idle: 100, total: 100 } }] }; if (typeof cb === 'function') try{cb(r);}catch(e){} return Promise.resolve(r); } },
          memory: { getInfo: function(cb) { var r = { capacity: 4294967296, availableCapacity: 2147483648 }; if (typeof cb === 'function') try{cb(r);}catch(e){} return Promise.resolve(r); } },
          storage: { getInfo: function(cb) { if (typeof cb === 'function') try{cb([]);}catch(e){} return Promise.resolve([]); } }
        },
        management: {
          getSelf: function(cb){ var m = { id: __kioExtId, name: __kioManifest.name || '', version: __kioManifest.version || '1.0', enabled: true, installType: 'normal', type: 'extension' }; if(typeof cb==='function')try{cb(m);}catch(e){} return Promise.resolve(m); },
          getAll: function(cb){ if(typeof cb==='function')try{cb([]);}catch(e){} return Promise.resolve([]); },
          get: function(id, cb){ var m = { id: id || __kioExtId, name: __kioManifest.name || '', version: __kioManifest.version || '1.0', enabled: true }; if(typeof cb==='function')try{cb(m);}catch(e){} return Promise.resolve(m); }
        },
        browsingData: { remove: function(o, d, cb){ if(typeof cb==='function')try{cb();}catch(e){} return Promise.resolve(); } },
        idle: { queryState: function(i, cb){ if(typeof cb==='function')try{cb('active');}catch(e){} return Promise.resolve('active'); }, onStateChanged: __kioMakeEvent([]) },
        power: { requestKeepAwake: function(){}, releaseKeepAwake: function(){} }
      };
      if (__kioCtxKind === 'popup') {
        window.close = function() {
          try {
            if (window.__KioExtBridge && window.__KioExtBridge.closePopup) {
              window.__KioExtBridge.closePopup(__kioExtId);
            }
          } catch(e) {}
        };
      }
      var chrome = __kioChrome;
      var browser = __kioChrome;
      try { window.chrome = __kioChrome; } catch(e) {}
      try { window.browser = __kioChrome; } catch(e) {}
      try { self.chrome = __kioChrome; } catch(e) {}
      try { self.browser = __kioChrome; } catch(e) {}
      try { if (typeof globalThis !== 'undefined') { globalThis.chrome = __kioChrome; globalThis.browser = __kioChrome; } } catch(e) {}
      try { if (typeof top !== 'undefined' && top !== window) { top.chrome = __kioChrome; top.browser = __kioChrome; } } catch(e) {}
      // Tampermonkey / Greasemonkey userscript compatibility helpers
      if (typeof window.GM_addStyle !== 'function') {
        window.GM_addStyle = function(css) {
          try { var s = document.createElement('style'); s.textContent = String(css || ''); (document.head || document.documentElement).appendChild(s); return s; } catch(e) { return null; }
        };
      }
      if (typeof window.GM_getValue !== 'function') {
        window.GM_getValue = function(k, def) { var all = __kioReadStore(); return (k in all) ? all[k] : def; };
        window.GM_setValue = function(k, v) { var all = __kioReadStore(); all[k] = v; __kioWriteStore(all, null); };
        window.GM_deleteValue = function(k) { var all = __kioReadStore(); delete all[k]; __kioWriteStore(all, null); };
        window.GM_listValues = function() { return Object.keys(__kioReadStore()); };
        window.GM_openInTab = function(u) { __kioChrome.tabs.create({ url: u }); };
        window.GM_info = { script: { name: __kioManifest.name || '', version: __kioManifest.version || '1.0' }, scriptHandler: 'Kio' };
      }
    """.trimIndent() + "\n"
  }

  /**
   * Executes a CORS-free privileged HTTP request on behalf of an installed extension's
   * background worker or popup (matching Desktop Chrome's host_permissions fetch behavior).
   */
  fun performPrivilegedFetch(url: String, optionsJson: String): PrivilegedFetchResult {
    if (!UrlValidator.isSafeWebUrl(url) || url == "about:blank") {
      return PrivilegedFetchResult(error = "Blocked unsafe extension fetch URL")
    }
    var conn: HttpURLConnection? = null
    return try {
      val opts = try { JSONObject(optionsJson) } catch (_: Throwable) { JSONObject() }
      val method = opts.optString("method", "GET").uppercase().ifBlank { "GET" }
      val headersObj = opts.optJSONObject("headers")
      val bodyText = opts.optString("body", "")

      conn = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = method
        connectTimeout = 12_000
        readTimeout = 20_000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", DESKTOP_CHROME_UA)
        if (headersObj != null) {
          val keys = headersObj.keys()
          while (keys.hasNext()) {
            val k = keys.next()
            if (k.equals("host", true) || k.equals("origin", true) || k.equals("content-length", true)) continue
            setRequestProperty(k, headersObj.optString(k, ""))
          }
        }
      }
      if (bodyText.isNotEmpty() && (method == "POST" || method == "PUT" || method == "PATCH" || method == "DELETE")) {
        conn.doOutput = true
        val bytes = bodyText.toByteArray(Charsets.UTF_8)
        conn.outputStream.use { it.write(bytes) }
      }
      val status = conn.responseCode
      val statusText = conn.responseMessage ?: "OK"
      val stream = try {
        if (status in 200..399) conn.inputStream else (conn.errorStream ?: conn.inputStream)
      } catch (_: Throwable) {
        conn.errorStream
      }
      val out = ByteArrayOutputStream()
      if (stream != null) {
        val buf = ByteArray(16 * 1024)
        var total = 0
        stream.use { s ->
          while (true) {
            val r = s.read(buf)
            if (r <= 0) break
            total += r
            if (total > MAX_PRIVILEGED_FETCH_BYTES) break
            out.write(buf, 0, r)
          }
        }
      }
      val respHeaders = JSONObject()
      conn.headerFields?.forEach { (k, v) ->
        if (!k.isNullOrBlank() && !v.isNullOrEmpty()) {
          respHeaders.put(k, v.joinToString(", "))
        }
      }
      val b64 = android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
      PrivilegedFetchResult(
        status = status,
        statusText = statusText,
        headersJson = respHeaders.toString(),
        bodyBase64 = b64,
        error = null
      )
    } catch (t: Throwable) {
      PrivilegedFetchResult(error = t.message ?: "Network error")
    } finally {
      try { conn?.disconnect() } catch (_: Throwable) {}
    }
  }

  /**
   * Parses chrome.proxy.settings.set({ value: { mode, rules, pacScript } }) into native proxy rules.
   */
  fun parseProxyConfigFromJson(extId: String, detailsJson: String): ExtensionProxyConfig? {
    return try {
      val root = JSONObject(detailsJson)
      val value = root.optJSONObject("value") ?: root
      val mode = value.optString("mode", "system").lowercase()
      if (mode == "direct" || mode == "system" || mode == "auto_detect") {
        return ExtensionProxyConfig(extId, mode, emptyList(), emptyList())
      }
      val proxyRules = mutableListOf<String>()
      val bypass = mutableListOf<String>()
      if (mode == "fixed_servers") {
        val rulesObj = value.optJSONObject("rules")
        if (rulesObj != null) {
          fun addRule(obj: JSONObject?) {
            if (obj == null) return
            val scheme = obj.optString("scheme", "http").lowercase()
            val host = obj.optString("host", "").trim()
            val port = obj.optInt("port", if (scheme == "https") 443 else 80)
            if (host.isNotEmpty() && port in 1..65535) {
              proxyRules += "$scheme://$host:$port"
            }
          }
          addRule(rulesObj.optJSONObject("singleProxy"))
          if (proxyRules.isEmpty()) {
            addRule(rulesObj.optJSONObject("proxyForHttps"))
            addRule(rulesObj.optJSONObject("proxyForHttp"))
            addRule(rulesObj.optJSONObject("fallbackProxy"))
          }
          bypass += jsonStringList(rulesObj.optJSONArray("bypassList"))
        }
      } else if (mode == "pac_script") {
        val pacData = value.optJSONObject("pacScript")?.optString("data", "").orEmpty()
        val matcher = Regex("""\b(HTTPS|PROXY|HTTP|SOCKS5|SOCKS)\s+([a-zA-Z0-9._-]+:\d{2,5})""", RegexOption.IGNORE_CASE)
        matcher.findAll(pacData).forEach { m ->
          val rawKind = m.groupValues[1].uppercase()
          val hostPort = m.groupValues[2]
          val scheme = when (rawKind) {
            "HTTPS" -> "https"
            "SOCKS5", "SOCKS" -> "socks5"
            else -> "http"
          }
          val rule = "$scheme://$hostPort"
          if (rule !in proxyRules) proxyRules += rule
        }
      }
      ExtensionProxyConfig(extId, mode, proxyRules, bypass)
    } catch (_: Throwable) {
      null
    }
  }

  // Native storage helpers invoked by @JavascriptInterface bridge
  fun readStorageJson(extId: String): String {
    val safeId = extId.replace(Regex("[^a-zA-Z0-9_-]"), "")
    if (safeId.isEmpty()) return "{}"
    val file = File(storageDir, "$safeId.json")
    if (!file.isFile) return "{}"
    return try {
      val text = file.readText(Charsets.UTF_8).trim()
      if (text.startsWith("{")) text else "{}"
    } catch (_: Throwable) {
      "{}"
    }
  }

  fun writeStorageJson(extId: String, json: String) {
    val safeId = extId.replace(Regex("[^a-zA-Z0-9_-]"), "")
    if (safeId.isEmpty() || json.length > 512 * 1024) return
    try {
      JSONObject(json) // Validate JSON
      File(storageDir, "$safeId.json").writeText(json, Charsets.UTF_8)
    } catch (_: Throwable) {
    }
  }

  private fun parseInstalledDirectory(extId: String, dir: File, sourceUrl: String): InstalledExtension? {
    val manifestFile = File(dir, "manifest.json")
    if (!manifestFile.isFile) return null
    return try {
      val manifest = JSONObject(manifestFile.readText(Charsets.UTF_8))
      val defaultLocale = manifest.optString("default_locale", "en")
      val locales = loadLocaleMessages(dir, defaultLocale)
      localesByExt[extId] = locales

      val rawName = manifest.optString("name", "").ifBlank { extId }
      val name = resolveMsg(rawName, locales).ifBlank { rawName }
      val version = manifest.optString("version", "1.0")
      val rawDesc = manifest.optString("description", "")
      val description = resolveMsg(rawDesc, locales)
      val mv = manifest.optInt("manifest_version", 3)

      // Icons (prefer 128 -> 64 -> 48 -> 32 -> 16)
      val iconsObj = manifest.optJSONObject("icons")
      val iconPath = listOf("128", "96", "64", "48", "32", "16")
        .firstNotNullOfOrNull { sizeKey -> iconsObj?.optString(sizeKey, "")?.takeIf { it.isNotBlank() } }

      // Popup / Action
      val actionObj = manifest.optJSONObject("action")
        ?: manifest.optJSONObject("browser_action")
        ?: manifest.optJSONObject("page_action")
      val defaultPopup = actionObj?.optString("default_popup", "")?.takeIf { it.isNotBlank() }
      val rawTitle = actionObj?.optString("default_title", "")?.takeIf { it.isNotBlank() }
      val defaultTitle = rawTitle?.let { resolveMsg(it, locales) }

      // Options page
      val optionsUi = manifest.optJSONObject("options_ui")?.optString("page", "")?.takeIf { it.isNotBlank() }
      val optionsPage = optionsUi ?: manifest.optString("options_page", "").takeIf { it.isNotBlank() }

      // Permissions & Host Permissions
      val permissions = mutableListOf<String>()
      val permArr = manifest.optJSONArray("permissions")
      if (permArr != null) {
        for (i in 0 until permArr.length()) {
          val p = permArr.optString(i, "").trim()
          if (p.isNotEmpty()) permissions += p
        }
      }
      val hostPermissions = jsonStringList(manifest.optJSONArray("host_permissions"))

      // Background Service Worker (MV3) or Background Scripts / Page (MV2)
      val bgObj = manifest.optJSONObject("background")
      val bgServiceWorker = bgObj?.optString("service_worker", "")?.takeIf { it.isNotBlank() }
      val bgIsModule = bgObj?.optString("type", "")?.equals("module", ignoreCase = true) == true
      val bgScripts = jsonStringList(bgObj?.optJSONArray("scripts"))
      val bgPage = bgObj?.optString("page", "")?.takeIf { it.isNotBlank() }

      // Content Scripts
      val contentScripts = mutableListOf<ExtensionContentScript>()
      val csArr = manifest.optJSONArray("content_scripts")
      if (csArr != null) {
        for (i in 0 until csArr.length()) {
          val obj = csArr.optJSONObject(i) ?: continue
          val matches = jsonStringList(obj.optJSONArray("matches"))
          if (matches.isEmpty()) continue
          contentScripts += ExtensionContentScript(
            matches = matches,
            excludeMatches = jsonStringList(obj.optJSONArray("exclude_matches")),
            jsFiles = jsonStringList(obj.optJSONArray("js")),
            cssFiles = jsonStringList(obj.optJSONArray("css")),
            runAt = obj.optString("run_at", "document_idle"),
            allFrames = obj.optBoolean("all_frames", false)
          )
        }
      }

      // Declarative Net Request static rulesets (MV3 adblockers like uBlock Origin Lite)
      val dnrRules = parseDnrRules(dir, manifest)
      if (dnrRules.isNotEmpty()) {
        dnrRulesByExt[extId] = dnrRules
      } else {
        dnrRulesByExt.remove(extId)
      }

      InstalledExtension(
        id = extId,
        name = UrlValidator.sanitizeDisplayText(name, 80),
        version = UrlValidator.sanitizeDisplayText(version, 24),
        description = UrlValidator.sanitizeDisplayText(description, 220),
        enabled = true,
        installDirPath = dir.absolutePath,
        iconRelativePath = iconPath,
        defaultPopup = defaultPopup,
        defaultTitle = defaultTitle,
        optionsPage = optionsPage,
        manifestVersion = mv,
        permissions = permissions,
        hostPermissions = hostPermissions,
        backgroundServiceWorker = bgServiceWorker,
        backgroundIsModule = bgIsModule,
        backgroundScripts = bgScripts,
        backgroundPage = bgPage,
        contentScripts = contentScripts,
        dnrRulesCount = dnrRules.size,
        sourceUrl = sourceUrl
      )
    } catch (_: Throwable) {
      null
    }
  }

  private fun parseDnrRules(dir: File, manifest: JSONObject): List<ExtensionDnrRule> {
    val dnrObj = manifest.optJSONObject("declarative_net_request") ?: return emptyList()
    val resources = dnrObj.optJSONArray("rule_resources") ?: return emptyList()
    val rules = mutableListOf<ExtensionDnrRule>()
    for (i in 0 until resources.length()) {
      val res = resources.optJSONObject(i) ?: continue
      if (!res.optBoolean("enabled", false)) continue
      val path = res.optString("path", "").trim()
      if (path.isEmpty()) continue
      val file = safeResolveAsset(dir, path) ?: continue
      try {
        val arr = JSONArray(file.readText(Charsets.UTF_8))
        for (j in 0 until minOf(arr.length(), 1500)) {
          val r = arr.optJSONObject(j) ?: continue
          val actionType = r.optJSONObject("action")?.optString("type", "")
          if (actionType != "block") continue
          val cond = r.optJSONObject("condition") ?: continue
          val urlFilter = cond.optString("urlFilter", "").lowercase()
          val reqDomains = jsonStringList(cond.optJSONArray("requestDomains")).map { it.lowercase() }
          val exclDomains = jsonStringList(cond.optJSONArray("excludedInitiatorDomains")).map { it.lowercase() }
          if (urlFilter.isNotEmpty() || reqDomains.isNotEmpty()) {
            rules += ExtensionDnrRule(urlFilter, reqDomains, exclDomains)
          }
          if (rules.size >= 2500) break
        }
      } catch (_: Throwable) {
      }
      if (rules.size >= 2500) break
    }
    return rules
  }

  private fun loadLocaleMessages(dir: File, defaultLocale: String): Map<String, String> {
    val deviceLang = Locale.getDefault().language.lowercase()
    val candidates = listOf(deviceLang, defaultLocale, "en", "en_US", "en_GB").distinct()
    val localesDir = File(dir, "_locales")
    if (!localesDir.isDirectory) return emptyMap()
    val msgFile = candidates.firstNotNullOfOrNull { code ->
      val f = File(File(localesDir, code), "messages.json")
      if (f.isFile) f else null
    } ?: return emptyMap()

    return try {
      val root = JSONObject(msgFile.readText(Charsets.UTF_8))
      val map = HashMap<String, String>()
      val keys = root.keys()
      while (keys.hasNext()) {
        val k = keys.next()
        val msg = root.optJSONObject(k)?.optString("message", "")
        if (!msg.isNullOrBlank()) {
          map[k] = msg
        }
      }
      map
    } catch (_: Throwable) {
      emptyMap()
    }
  }

  private fun resolveMsg(input: String, locales: Map<String, String>): String {
    if (!input.contains("__MSG_")) return input
    return Regex("""__MSG_([a-zA-Z0-9_@]+)__""").replace(input) { m ->
      val key = m.groupValues[1]
      locales[key] ?: locales.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value ?: m.value
    }
  }

  private fun safeResolveAsset(baseDir: File, relativePath: String): File? {
    val clean = relativePath.trimStart('/')
    if (clean.isEmpty() || clean.contains("..")) return null
    val f = File(baseDir, clean)
    return try {
      val baseCanon = baseDir.canonicalPath + File.separator
      if (f.canonicalPath.startsWith(baseCanon) && f.isFile) return f
      // Case-insensitive lookup fallback for Android ext4 / f2fs
      val parts = clean.split('/')
      var curr: File = baseDir
      for (part in parts) {
        val match = curr.listFiles()?.firstOrNull { it.name.equals(part, ignoreCase = true) } ?: return null
        curr = match
      }
      if (curr.canonicalPath.startsWith(baseCanon) && curr.isFile) curr else null
    } catch (_: Throwable) {
      null
    }
  }

  private fun readManifestJsonSafe(baseDir: File): String {
    return try {
      val f = File(baseDir, "manifest.json")
      if (f.isFile) JSONObject(f.readText(Charsets.UTF_8)).toString() else "{}"
    } catch (_: Throwable) {
      "{}"
    }
  }

  private fun jsonStringList(arr: JSONArray?): List<String> {
    if (arr == null) return emptyList()
    val out = mutableListOf<String>()
    for (i in 0 until arr.length()) {
      val v = arr.optString(i, "").trim()
      if (v.isNotEmpty()) out += v
    }
    return out
  }

  private fun downloadCrxBytes(initialUrl: String): ByteArray? {
    var currentUrl = initialUrl
    for (hop in 0 until 8) {
      var conn: HttpURLConnection? = null
      try {
        conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
          connectTimeout = 12_000
          readTimeout = 25_000
          instanceFollowRedirects = false
          setRequestProperty("User-Agent", DESKTOP_CHROME_UA)
          setRequestProperty("Accept", "application/x-chrome-extension,application/octet-stream,*/*")
          setRequestProperty("Accept-Language", "en-US,en;q=0.9")
        }
        val code = conn.responseCode
        if (code in 300..399) {
          val loc = conn.getHeaderField("Location") ?: return null
          currentUrl = URL(URL(currentUrl), loc).toString()
          continue
        }
        // Reject HTTP 204 No Content and non-200 error responses so fallback endpoints run cleanly
        if (code != HttpURLConnection.HTTP_OK) return null
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var total = 0
        conn.inputStream.use { stream ->
          while (true) {
            val r = stream.read(buf)
            if (r <= 0) break
            total += r
            if (total > MAX_DOWNLOAD_BYTES) return null
            out.write(buf, 0, r)
          }
        }
        val result = out.toByteArray()
        return if (result.size >= 64) result else null
      } catch (_: Throwable) {
        return null
      } finally {
        try { conn?.disconnect() } catch (_: Throwable) {}
      }
    }
    return null
  }

  private fun loadRegistry() {
    if (!registryFile.isFile) return
    try {
      val arr = JSONArray(registryFile.readText(Charsets.UTF_8))
      synchronized(lock) {
        for (i in 0 until arr.length()) {
          val obj = arr.optJSONObject(i) ?: continue
          val extId = obj.optString("id", "").ifBlank { continue }
          val dirPath = obj.optString("installDirPath", "").ifBlank { continue }
          val dir = File(dirPath)
          if (!dir.isDirectory || !File(dir, "manifest.json").isFile) continue
          val enabled = obj.optBoolean("enabled", true)
          val sourceUrl = obj.optString("sourceUrl", "")
          val installedAt = obj.optLong("installedAt", System.currentTimeMillis())
          val parsed = parseInstalledDirectory(extId, dir, sourceUrl) ?: continue
          extensionsMap[extId] = parsed.copy(enabled = enabled, installedAt = installedAt)
        }
      }
    } catch (_: Throwable) {
    }
  }

  private fun saveRegistry() {
    try {
      val arr = JSONArray()
      for (ext in extensionsMap.values) {
        arr.put(
          JSONObject().apply {
            put("id", ext.id)
            put("enabled", ext.enabled)
            put("installDirPath", ext.installDirPath)
            put("sourceUrl", ext.sourceUrl)
            put("installedAt", ext.installedAt)
          }
        )
      }
      registryFile.writeText(arr.toString(), Charsets.UTF_8)
    } catch (_: Throwable) {
    }
  }
}

data class PrivilegedFetchResult(
  val status: Int = 0,
  val statusText: String = "",
  val headersJson: String = "{}",
  val bodyBase64: String = "",
  val error: String? = null
)
