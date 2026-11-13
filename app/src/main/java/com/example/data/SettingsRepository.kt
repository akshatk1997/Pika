package com.example.data

import android.content.Context
import com.example.model.SearchEngine
import com.example.model.SecurityState
import com.example.model.SettingsState
import com.example.model.SitePermissionRule
import com.example.model.TabInfo
import com.example.model.ThemeMode
import com.example.model.UserAgentMode
import com.example.model.WebPermissionKind
import com.example.security.SecureKeystoreStorage
import com.example.security.TrackerBlocker
import com.example.security.UrlValidator
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists browser settings, non-private retained session tabs, and per-site permission rules
 * using Android Keystore (AES-256-GCM), with private local fallback if Keystore HAL is unavailable.
 */
class SettingsRepository(context: Context) {

  private val appContext = context.applicationContext
  private val secureStorage = SecureKeystoreStorage(appContext)
  private val fallbackPrefs by lazy {
    appContext.getSharedPreferences(FALLBACK_PREFS, Context.MODE_PRIVATE)
  }

  companion object {
    private const val SETTINGS_FILE = "shiz_settings.enc"
    private const val PERMISSIONS_FILE = "shiz_site_perms.enc"
    private const val RETAINED_TABS_FILE = "shiz_retained_tabs.enc"
    private const val FALLBACK_PREFS = "shiz_local_state_fallback"
    private const val PREF_INTRO_SEEN = "shiz_intro_seen"
    private const val MAX_RETAINED_TABS = 24
  }

  fun hasSeenIntro(): Boolean {
    return try {
      fallbackPrefs.getBoolean(PREF_INTRO_SEEN, false)
    } catch (_: Throwable) {
      false
    }
  }

  fun setIntroSeen(seen: Boolean = true) {
    try {
      fallbackPrefs.edit().putBoolean(PREF_INTRO_SEEN, seen).apply()
    } catch (_: Throwable) {
    }
  }

  private fun writePayload(fileName: String, json: String) {
    if (secureStorage.writeEncryptedString(fileName, json)) {
      try {
        fallbackPrefs.edit().remove(fileName).apply()
      } catch (_: Throwable) {
      }
    } else {
      try {
        fallbackPrefs.edit().putString(fileName, json).apply()
      } catch (_: Throwable) {
      }
    }
  }

  private fun readPayload(fileName: String): String? {
    secureStorage.readEncryptedString(fileName)?.let { return it }
    return try {
      fallbackPrefs.getString(fileName, null)
    } catch (_: Throwable) {
      null
    }
  }

  private fun deletePayload(fileName: String) {
    secureStorage.deleteEncryptedFile(fileName)
    try {
      fallbackPrefs.edit().remove(fileName).apply()
    } catch (_: Throwable) {
    }
  }

  fun loadSettings(): SettingsState {
    val jsonStr = readPayload(SETTINGS_FILE) ?: readPayload("feather_settings.enc") ?: return SettingsState()
    return try {
      val obj = JSONObject(jsonStr)
      val customArr = obj.optJSONArray("customBlockedHosts")
      val customHosts = mutableListOf<String>()
      if (customArr != null) {
        for (i in 0 until customArr.length()) {
          val normalized = TrackerBlocker.normalizeCustomHost(customArr.optString(i, ""))
          if (normalized.isNotEmpty() && normalized !in customHosts) {
            customHosts += normalized
          }
        }
      }
      SettingsState(
        searchEngine = SearchEngine.fromId(obj.optString("searchEngine", SearchEngine.DUCKDUCKGO.id)),
        customSearchTemplate = obj.optString("customSearchTemplate", SearchEngine.DUCKDUCKGO.queryTemplate),
        homepageUrl = UrlValidator.sanitizeControlChars(obj.optString("homepageUrl", "")),
        retainOpenTabs = obj.optBoolean("retainOpenTabs", true),
        httpsOnlyEnabled = obj.optBoolean("httpsOnlyEnabled", true),
        blockThirdPartyCookies = obj.optBoolean("blockThirdPartyCookies", true),
        trackerBlockingEnabled = obj.optBoolean("trackerBlockingEnabled", true),
        historyEnabled = obj.optBoolean("historyEnabled", true),
        doNotTrackEnabled = obj.optBoolean("doNotTrackEnabled", true),
        javascriptEnabled = obj.optBoolean("javascriptEnabled", true),
        textZoomPercent = obj.optInt("textZoomPercent", 100).coerceIn(75, 200),
        themeMode = ThemeMode.fromId(obj.optString("themeMode", ThemeMode.SYSTEM.id)),
        dynamicColor = obj.optBoolean("dynamicColor", false),
        nightWebMode = obj.optBoolean("nightWebMode", false),
        noImageMode = obj.optBoolean("noImageMode", false),
        customBlockedHosts = customHosts,
        readerFontSizeSp = obj.optInt("readerFontSizeSp", 18).coerceIn(14, 28),
        readerDarkMode = obj.optBoolean("readerDarkMode", false),
        backgroundPlayEnabled = obj.optBoolean("backgroundPlayEnabled", true)
      )
    } catch (_: Throwable) {
      SettingsState()
    }
  }

  fun saveSettings(state: SettingsState) {
    val obj = JSONObject().apply {
      put("searchEngine", state.searchEngine.id)
      put("customSearchTemplate", state.customSearchTemplate)
      put("homepageUrl", state.homepageUrl)
      put("retainOpenTabs", state.retainOpenTabs)
      put("httpsOnlyEnabled", state.httpsOnlyEnabled)
      put("blockThirdPartyCookies", state.blockThirdPartyCookies)
      put("trackerBlockingEnabled", state.trackerBlockingEnabled)
      put("historyEnabled", state.historyEnabled)
      put("doNotTrackEnabled", state.doNotTrackEnabled)
      put("javascriptEnabled", state.javascriptEnabled)
      put("textZoomPercent", state.textZoomPercent)
      put("themeMode", state.themeMode.id)
      put("dynamicColor", state.dynamicColor)
      put("nightWebMode", state.nightWebMode)
      put("noImageMode", state.noImageMode)
      put("customBlockedHosts", JSONArray(state.customBlockedHosts))
      put("readerFontSizeSp", state.readerFontSizeSp)
      put("readerDarkMode", state.readerDarkMode)
      put("backgroundPlayEnabled", state.backgroundPlayEnabled)
    }
    writePayload(SETTINGS_FILE, obj.toString())
  }

  fun saveRetainedTabs(tabs: List<TabInfo>, activeTabId: String) {
    val normalTabs = tabs.filter { !it.isPrivate }.takeLast(MAX_RETAINED_TABS)
    if (normalTabs.isEmpty()) {
      deletePayload(RETAINED_TABS_FILE)
      return
    }
    val arr = JSONArray()
    for (t in normalTabs) {
      val safeUrl = if (UrlValidator.isSafeWebUrl(t.url) && t.url != "about:blank") t.url else ""
      arr.put(
        JSONObject().apply {
          put("id", t.id)
          put("title", UrlValidator.sanitizeDisplayText(t.title, 180).ifEmpty { "New Tab" })
          put("url", safeUrl)
          put("ua", t.userAgentMode.id)
        }
      )
    }
    val root = JSONObject().apply {
      put("activeTabId", activeTabId)
      put("tabs", arr)
    }
    writePayload(RETAINED_TABS_FILE, root.toString())
  }

  fun loadRetainedTabs(): Pair<List<TabInfo>, String>? {
    val raw = readPayload(RETAINED_TABS_FILE) ?: readPayload("bat_retained_tabs.enc") ?: return null
    return try {
      val root = JSONObject(raw)
      val arr = root.optJSONArray("tabs") ?: return null
      val list = mutableListOf<TabInfo>()
      for (i in 0 until arr.length()) {
        val obj = arr.optJSONObject(i) ?: continue
        val id = obj.optString("id", "").ifBlank { continue }
        val rawUrl = UrlValidator.sanitizeControlChars(obj.optString("url", ""))
        val safeUrl = if (UrlValidator.isSafeWebUrl(rawUrl) && rawUrl != "about:blank") rawUrl else ""
        val defaultTitle = if (safeUrl.isNotEmpty()) {
          UrlValidator.extractHost(safeUrl).ifEmpty { "Page" }
        } else {
          "New Tab"
        }
        val title = UrlValidator.sanitizeDisplayText(obj.optString("title", ""), 180)
          .ifEmpty { defaultTitle }
        val sec = when {
          safeUrl.isEmpty() -> SecurityState.HOME
          UrlValidator.isHttpsUrl(safeUrl) -> SecurityState.SECURE_HTTPS
          else -> SecurityState.INSECURE_HTTP
        }
        list += TabInfo(
          id = id,
          title = title,
          url = safeUrl,
          isPrivate = false,
          userAgentMode = UserAgentMode.fromId(obj.optString("ua", UserAgentMode.MOBILE.id)),
          securityState = sec,
          faviconInitial = UrlValidator.extractHost(safeUrl).firstOrNull()?.uppercase() ?: ""
        )
      }
      if (list.isEmpty()) return null
      val savedActive = root.optString("activeTabId", "")
      val resolvedActive = if (list.any { it.id == savedActive }) savedActive else list.first().id
      list to resolvedActive
    } catch (_: Throwable) {
      null
    }
  }

  fun clearRetainedTabs() {
    deletePayload(RETAINED_TABS_FILE)
  }

  fun loadSitePermissions(): List<SitePermissionRule> {
    val raw = readPayload(PERMISSIONS_FILE) ?: return emptyList()
    return try {
      val arr = JSONArray(raw)
      val out = mutableListOf<SitePermissionRule>()
      for (i in 0 until arr.length()) {
        val obj = arr.optJSONObject(i) ?: continue
        val origin = obj.optString("origin", "")
        val kindName = obj.optString("kind", "")
        val allowed = obj.optBoolean("allowed", false)
        val kind = WebPermissionKind.entries.firstOrNull { it.name == kindName } ?: continue
        if (origin.isNotEmpty()) {
          out += SitePermissionRule(origin = origin, kind = kind, allowed = allowed)
        }
      }
      out
    } catch (_: Throwable) {
      emptyList()
    }
  }

  fun saveSitePermissions(rules: List<SitePermissionRule>) {
    val arr = JSONArray()
    for (rule in rules) {
      arr.put(
        JSONObject().apply {
          put("origin", rule.origin)
          put("kind", rule.kind.name)
          put("allowed", rule.allowed)
        }
      )
    }
    writePayload(PERMISSIONS_FILE, arr.toString())
  }

  fun clearAllSitePermissions() {
    deletePayload(PERMISSIONS_FILE)
  }
}
