package com.example.security

import com.example.model.SearchEngine
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed interface ResolvedNavigation {
  data class LoadUrl(val url: String, val upgradedFromHttp: Boolean = false) : ResolvedNavigation
  data class BlockedScheme(val rawInput: String, val scheme: String) : ResolvedNavigation
  data object Empty : ResolvedNavigation
}

object UrlValidator {

  private val dangerousSchemes = setOf(
    "javascript", "data", "file", "content", "vbscript", "blob", "filesystem", "jar"
  )

  private val externalSchemes = setOf(
    "mailto", "tel", "sms", "smsto", "geo", "intent", "market"
  )

  private val trackingQueryKeys = setOf(
    "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
    "utm_id", "fbclid", "gclid", "dclid", "msclkid", "mc_eid", "mc_cid",
    "igshid", "yclid", "_hsenc", "_hsmi", "spm"
  )

  private val domainRegex = Regex(
    "^(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,63}(?::\\d{1,5})?(?:/.*)?$"
  )

  private val ipv4Regex = Regex(
    "^(?:(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)(?::\\d{1,5})?(?:/.*)?$"
  )

  fun sanitizeControlChars(raw: String): String {
    val sb = StringBuilder(raw.length)
    for (ch in raw) {
      val code = ch.code
      val isControl = code < 0x20 || code == 0x7F
      val isBidiOrZeroWidth = (code in 0x200B..0x200F) || (code in 0x202A..0x202E) || (code in 0x2066..0x2069)
      if (!isControl && !isBidiOrZeroWidth) {
        sb.append(ch)
      }
    }
    return sb.toString().trim()
  }

  fun sanitizeDisplayText(raw: String?, maxLen: Int = 256): String {
    if (raw.isNullOrBlank()) return ""
    val cleaned = sanitizeControlChars(raw)
    return if (cleaned.length > maxLen) cleaned.take(maxLen) + "…" else cleaned
  }

  fun sanitizeFilename(raw: String?): String {
    val cleaned = sanitizeControlChars(raw ?: "")
      .replace("/", "_")
      .replace("\\", "_")
      .replace("..", "_")
      .replace(":", "_")
      .trim('.', ' ')
    if (cleaned.isBlank()) return "download.bin"
    return cleaned.take(120)
  }

  fun resolveInputToTarget(
    rawInput: String,
    searchEngine: SearchEngine,
    customTemplate: String,
    httpsOnly: Boolean
  ): ResolvedNavigation {
    val cleaned = sanitizeControlChars(rawInput)
    if (cleaned.isEmpty()) return ResolvedNavigation.Empty

    val lower = cleaned.lowercase()
    val colonIdx = lower.indexOf(':')
    if (colonIdx > 0 && !cleaned.substring(0, colonIdx).contains(' ')) {
      val scheme = lower.substring(0, colonIdx)
      if (scheme in dangerousSchemes) {
        return ResolvedNavigation.BlockedScheme(cleaned, scheme)
      }
      if (scheme == "http" || scheme == "https") {
        val normalized = if (httpsOnly && scheme == "http") {
          "https://" + cleaned.substring(7)
        } else {
          cleaned
        }
        val upgraded = httpsOnly && scheme == "http"
        return ResolvedNavigation.LoadUrl(stripTrackingParams(normalized), upgradedFromHttp = upgraded)
      }
      // Unknown scheme with whitespace-free prefix (e.g., about:config) -> block unless about:blank
      if (scheme == "about") {
        return if (lower == "about:blank") {
          ResolvedNavigation.LoadUrl("about:blank")
        } else {
          ResolvedNavigation.BlockedScheme(cleaned, scheme)
        }
      }
    }

    // Check if input looks like a domain or IPv4 or localhost without spaces
    val looksLikeHost = !cleaned.contains(' ') && (
      domainRegex.matches(cleaned) ||
        ipv4Regex.matches(cleaned) ||
        lower == "localhost" ||
        lower.startsWith("localhost:") ||
        lower.startsWith("localhost/")
      )

    if (looksLikeHost) {
      val scheme = if (lower.startsWith("localhost") || ipv4Regex.matches(cleaned)) {
        if (httpsOnly) "https://" else "http://"
      } else {
        "https://"
      }
      return ResolvedNavigation.LoadUrl(stripTrackingParams("$scheme$cleaned"))
    }

    // Otherwise treat as a search query
    val encodedQuery = URLEncoder.encode(cleaned, StandardCharsets.UTF_8.name())
    val template = if (searchEngine == SearchEngine.CUSTOM && isValidCustomSearchTemplate(customTemplate)) {
      customTemplate
    } else if (searchEngine == SearchEngine.CUSTOM) {
      SearchEngine.DUCKDUCKGO.queryTemplate
    } else {
      searchEngine.queryTemplate
    }
    return ResolvedNavigation.LoadUrl(template.replace("%s", encodedQuery))
  }

  fun isValidCustomSearchTemplate(template: String): Boolean {
    val cleaned = sanitizeControlChars(template)
    return cleaned.startsWith("https://", ignoreCase = true) && cleaned.contains("%s")
  }

  fun isSafeWebUrl(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    val lower = sanitizeControlChars(url).lowercase()
    return lower.startsWith("https://") || lower.startsWith("http://") || lower == "about:blank"
  }

  fun isHttpUrl(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    return sanitizeControlChars(url).lowercase().startsWith("http://")
  }

  fun isHttpsUrl(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    return sanitizeControlChars(url).lowercase().startsWith("https://")
  }

  fun isExternalAppScheme(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    val lower = sanitizeControlChars(url).lowercase()
    val colonIdx = lower.indexOf(':')
    if (colonIdx <= 0) return false
    return lower.substring(0, colonIdx) in externalSchemes
  }

  fun extractScheme(url: String?): String {
    if (url.isNullOrBlank()) return ""
    val lower = sanitizeControlChars(url).lowercase()
    val idx = lower.indexOf(':')
    return if (idx > 0) lower.substring(0, idx) else ""
  }

  fun extractHost(url: String?): String {
    if (url.isNullOrBlank()) return ""
    return try {
      val uri = URI(sanitizeControlChars(url))
      uri.host?.lowercase() ?: ""
    } catch (_: Throwable) {
      ""
    }
  }

  fun extractOrigin(url: String?): String {
    if (url.isNullOrBlank()) return ""
    return try {
      val uri = URI(sanitizeControlChars(url))
      val scheme = uri.scheme?.lowercase() ?: return ""
      val host = uri.host?.lowercase() ?: return ""
      if (uri.port != -1) "$scheme://$host:${uri.port}" else "$scheme://$host"
    } catch (_: Throwable) {
      ""
    }
  }

  fun stripTrackingParams(url: String): String {
    return try {
      val uri = URI(url)
      val rawQuery = uri.rawQuery ?: return url
      val keptParams = rawQuery.split("&").filter { param ->
        if (param.isEmpty()) return@filter false
        val key = param.substringBefore("=").lowercase()
        key !in trackingQueryKeys
      }
      val newQuery = if (keptParams.isEmpty()) null else keptParams.joinToString("&")
      val base = url.substringBefore('?').substringBefore('#')
      val fragment = if (url.contains('#')) "#" + url.substringAfter('#') else ""
      if (newQuery != null) "$base?$newQuery$fragment" else "$base$fragment"
    } catch (_: Throwable) {
      url
    }
  }
}
