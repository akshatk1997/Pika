package com.example.data

import android.content.Context
import com.example.model.SearchEngine
import com.example.model.SettingsState
import com.example.model.SitePermissionRule
import com.example.model.ThemeMode
import com.example.model.WebPermissionKind
import com.example.security.SecureKeystoreStorage
import com.example.security.UrlValidator
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists browser settings and per-site permission rules encrypted via Android Keystore.
 * Zero plaintext preferences or telemetry.
 */
class SettingsRepository(context: Context) {

  private val secureStorage = SecureKeystoreStorage(context.applicationContext)

  companion object {
    private const val SETTINGS_FILE = "feather_settings.enc"
    private const val PERMISSIONS_FILE = "feather_site_perms.enc"
  }

  fun loadSettings(): SettingsState {
    val jsonStr = secureStorage.readEncryptedString(SETTINGS_FILE) ?: return SettingsState()
    return try {
      val obj = JSONObject(jsonStr)
      SettingsState(
        searchEngine = SearchEngine.fromId(obj.optString("searchEngine", SearchEngine.DUCKDUCKGO.id)),
        customSearchTemplate = obj.optString("customSearchTemplate", SearchEngine.DUCKDUCKGO.queryTemplate),
        homepageUrl = UrlValidator.sanitizeControlChars(obj.optString("homepageUrl", "")),
        httpsOnlyEnabled = obj.optBoolean("httpsOnlyEnabled", true),
        blockThirdPartyCookies = obj.optBoolean("blockThirdPartyCookies", true),
        trackerBlockingEnabled = obj.optBoolean("trackerBlockingEnabled", true),
        historyEnabled = obj.optBoolean("historyEnabled", true),
        doNotTrackEnabled = obj.optBoolean("doNotTrackEnabled", true),
        javascriptEnabled = obj.optBoolean("javascriptEnabled", true),
        textZoomPercent = obj.optInt("textZoomPercent", 100).coerceIn(75, 200),
        themeMode = ThemeMode.fromId(obj.optString("themeMode", ThemeMode.SYSTEM.id)),
        readerFontSizeSp = obj.optInt("readerFontSizeSp", 18).coerceIn(14, 28),
        readerDarkMode = obj.optBoolean("readerDarkMode", false)
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
      put("httpsOnlyEnabled", state.httpsOnlyEnabled)
      put("blockThirdPartyCookies", state.blockThirdPartyCookies)
      put("trackerBlockingEnabled", state.trackerBlockingEnabled)
      put("historyEnabled", state.historyEnabled)
      put("doNotTrackEnabled", state.doNotTrackEnabled)
      put("javascriptEnabled", state.javascriptEnabled)
      put("textZoomPercent", state.textZoomPercent)
      put("themeMode", state.themeMode.id)
      put("readerFontSizeSp", state.readerFontSizeSp)
      put("readerDarkMode", state.readerDarkMode)
    }
    secureStorage.writeEncryptedString(SETTINGS_FILE, obj.toString())
  }

  fun loadSitePermissions(): List<SitePermissionRule> {
    val raw = secureStorage.readEncryptedString(PERMISSIONS_FILE) ?: return emptyList()
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
    secureStorage.writeEncryptedString(PERMISSIONS_FILE, arr.toString())
  }

  fun clearAllSitePermissions() {
    secureStorage.deleteEncryptedFile(PERMISSIONS_FILE)
  }
}
