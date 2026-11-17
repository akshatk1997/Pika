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
    const val CHROME_WEB_STORE_URL = "https://chromewebstore.google.com/"
    private const val MAX_DOWNLOAD_BYTES = 25 * 1024 * 1024 // 25 MB
    private const val DESKTOP_CHROME_UA =
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
     * Supports <all_urls>, *://*.example.com/*, https://site.org/path*, etc.
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
   * by its store URL or 32-character extension ID.
   */
  fun installFromChromeWebStore(urlOrId: String): ExtensionInstallResult {
    val extId = CrxUnpacker.extractChromeStoreId(urlOrId)
      ?: return ExtensionInstallResult.Failure("Enter a valid Chrome Web Store URL or 32-character Extension ID")

    val downloadUrl = CrxUnpacker.buildChromeWebStoreCrxUrl(extId)
    val bytes = downloadCrxBytes(downloadUrl)
      ?: return ExtensionInstallResult.Failure("Could not download extension package from Chrome Web Store. Check internet connection or install a local .crx/.zip file.")

    return installFromBytes(
      bytes = bytes,
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
   * so content scripts (chrome.runtime.getURL) and extension popups run safely with zero file:// exposure.
   */
  fun serveVirtualExtensionResource(requestUrl: String): WebResourceResponse? {
    val prefix = "https://$EXT_VIRTUAL_HOST/"
    if (!requestUrl.startsWith(prefix, ignoreCase = true)) return null
    val remainder = requestUrl.substring(prefix.length).substringBefore('?').substringBefore('#')
    val slashIdx = remainder.indexOf('/')
    if (slashIdx <= 0) return null
    val extId = remainder.substring(0, slashIdx)
    val relPath = remainder.substring(slashIdx + 1).trimStart('/')
    if (relPath.isEmpty() || relPath.contains("..")) return null

    val ext = getExtension(extId) ?: return null
    val baseDir = File(ext.installDirPath)
    val targetFile = File(baseDir, relPath)
    return try {
      val canonicalBase = baseDir.canonicalPath + File.separator
      val canonicalTarget = targetFile.canonicalPath
      if (!canonicalTarget.startsWith(canonicalBase) || !targetFile.isFile) {
        return WebResourceResponse("text/plain", "utf-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(ByteArray(0)))
      }
      val bytes = targetFile.readBytes()
      val mime = inferMimeType(relPath)
      val headers = mapOf(
        "Access-Control-Allow-Origin" to "*",
        "Cache-Control" to "no-cache"
      )
      WebResourceResponse(mime, if (mime.startsWith("text/") || mime.contains("javascript") || mime.contains("json")) "utf-8" else null, 200, "OK", headers, ByteArrayInputStream(bytes))
    } catch (_: Throwable) {
      null
    }
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
    storageJson: String? = null
  ): String {
    val ext = getExtension(extId)
    val baseDir = ext?.let { File(it.installDirPath) }
    val resolvedManifest = manifestJson ?: baseDir?.let { readManifestJsonSafe(it) } ?: "{}"
    val resolvedLocales = localeJson ?: JSONObject(localesByExt[extId] ?: emptyMap<String, String>()).toString()
    val resolvedStorage = storageJson ?: readStorageJson(extId)
    val quotedId = JSONObject.quote(extId)
    val baseUrl = JSONObject.quote("https://$EXT_VIRTUAL_HOST/$extId/")

    return """
      var __kioExtId = $quotedId;
      var __kioBaseUrl = $baseUrl;
      var __kioManifest = $resolvedManifest;
      var __kioLocales = $resolvedLocales;
      var __kioStoreMem = $resolvedStorage;
      var __kioStorageListeners = [];
      var __kioMessageListeners = [];
      function __kioReadStore() {
        try {
          if (window.__KioExtBridge && window.__KioExtBridge.storageGetAll) {
            var raw = window.__KioExtBridge.storageGetAll(__kioExtId);
            if (raw) __kioStoreMem = JSON.parse(raw);
          }
        } catch(e) {}
        return __kioStoreMem || {};
      }
      function __kioWriteStore(obj) {
        __kioStoreMem = obj;
        try {
          if (window.__KioExtBridge && window.__KioExtBridge.storageSetAll) {
            window.__KioExtBridge.storageSetAll(__kioExtId, JSON.stringify(obj));
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
              __kioWriteStore(all);
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
            for (var i = 0; i < list.length; i++) delete all[list[i]];
            __kioWriteStore(all);
            if (typeof cb === 'function') { try { cb(); } catch(e) {} }
            return Promise.resolve();
          },
          clear: function(cb) {
            __kioWriteStore({});
            if (typeof cb === 'function') { try { cb(); } catch(e) {} }
            return Promise.resolve();
          }
        };
      }
      var __kioArea = __kioMakeArea();
      var __kioChrome = {
        runtime: {
          id: __kioExtId,
          lastError: null,
          getManifest: function() { return __kioManifest; },
          getURL: function(path) {
            var clean = String(path || '').replace(/^\/+/, '');
            return __kioBaseUrl + clean;
          },
          sendMessage: function(msg, cb) {
            var handled = null;
            for (var i = 0; i < __kioMessageListeners.length; i++) {
              try {
                __kioMessageListeners[i](msg, { id: __kioExtId }, function(r) { handled = r; });
              } catch(e) {}
            }
            if (typeof cb === 'function') { try { cb(handled); } catch(e) {} }
            return Promise.resolve(handled);
          },
          onMessage: {
            addListener: function(fn) { if (typeof fn === 'function') __kioMessageListeners.push(fn); },
            removeListener: function(fn) { __kioMessageListeners = __kioMessageListeners.filter(function(f){return f!==fn;}); }
          },
          onInstalled: { addListener: function(){} },
          openOptionsPage: function() {}
        },
        extension: {
          getURL: function(path) {
            var clean = String(path || '').replace(/^\/+/, '');
            return __kioBaseUrl + clean;
          }
        },
        storage: {
          local: __kioArea,
          sync: __kioArea,
          session: __kioArea,
          onChanged: {
            addListener: function(fn) { if (typeof fn === 'function') __kioStorageListeners.push(fn); },
            removeListener: function(fn) { __kioStorageListeners = __kioStorageListeners.filter(function(f){return f!==fn;}); }
          }
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
          getUILanguage: function() { return navigator.language || 'en'; }
        },
        tabs: {
          create: function(opts, cb) {
            var targetUrl = opts && opts.url ? String(opts.url) : '';
            if (targetUrl && window.__KioExtBridge && window.__KioExtBridge.openTab) {
              try { window.__KioExtBridge.openTab(targetUrl); } catch(e) {}
            }
            var tab = { id: 1, url: targetUrl, active: true };
            if (typeof cb === 'function') { try { cb(tab); } catch(e) {} }
            return Promise.resolve(tab);
          },
          query: function(q, cb) {
            var tabUrl = (window.__KioExtBridge && window.__KioExtBridge.getActiveUrl) ? window.__KioExtBridge.getActiveUrl() : window.location.href;
            var tabTitle = (window.__KioExtBridge && window.__KioExtBridge.getActiveTitle) ? window.__KioExtBridge.getActiveTitle() : document.title;
            var list = [{ id: 1, url: tabUrl, title: tabTitle, active: true }];
            if (typeof cb === 'function') { try { cb(list); } catch(e) {} }
            return Promise.resolve(list);
          },
          sendMessage: function(tabId, msg, cb) {
            if (typeof cb === 'function') { try { cb(null); } catch(e) {} }
            return Promise.resolve(null);
          }
        }
      };
      var chrome = __kioChrome;
      var browser = __kioChrome;
      if (!window.chrome || !window.chrome.runtime) { window.chrome = __kioChrome; }
      if (!window.browser || !window.browser.runtime) { window.browser = __kioChrome; }
    """.trimIndent() + "\n"
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

      // Permissions
      val permissions = mutableListOf<String>()
      val permArr = manifest.optJSONArray("permissions")
      if (permArr != null) {
        for (i in 0 until permArr.length()) {
          val p = permArr.optString(i, "").trim()
          if (p.isNotEmpty()) permissions += p
        }
      }

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
      if (f.canonicalPath.startsWith(baseCanon) && f.isFile) f else null
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
    for (hop in 0 until 6) {
      var conn: HttpURLConnection? = null
      try {
        conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
          connectTimeout = 10_000
          readTimeout = 20_000
          instanceFollowRedirects = false
          setRequestProperty("User-Agent", DESKTOP_CHROME_UA)
          setRequestProperty("Accept", "*/*")
        }
        val code = conn.responseCode
        if (code in 300..399) {
          val loc = conn.getHeaderField("Location") ?: return null
          currentUrl = URL(URL(currentUrl), loc).toString()
          continue
        }
        if (code !in 200..299) return null
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
        return out.toByteArray()
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
