package com.example.security

import com.example.model.SniffedMediaType
import com.example.model.SniffedResource
import java.net.URI

/**
 * Compact, zero-dependency ad, popup & third-party tracker blocker + media resource classifier.
 * Checked inside WebViewClient.shouldInterceptRequest for subresource requests.
 */
object TrackerBlocker {

  private val blockedDomains: HashSet<String> = hashSetOf(
    // Analytics & Telemetry
    "doubleclick.net",
    "googlesyndication.com",
    "google-analytics.com",
    "googletagmanager.com",
    "googletagservices.com",
    "adservice.google.com",
    "pagead2.googlesyndication.com",
    "tpc.googlesyndication.com",
    "googleadservices.com",
    "analytics.google.com",
    "connect.facebook.net",
    "pixel.facebook.com",
    "an.facebook.com",
    "ads-twitter.com",
    "analytics.twitter.com",
    "static.ads-twitter.com",
    "ads.linkedin.com",
    "px.ads.linkedin.com",
    "snap.licdn.com",
    "bat.bing.com",
    "clarity.ms",
    "c.msn.com",
    "scorecardresearch.com",
    "quantserve.com",
    "quantcount.com",
    "hotjar.com",
    "static.hotjar.com",
    "script.hotjar.com",
    "mixpanel.com",
    "api.mixpanel.com",
    "cdn.mxpnl.com",
    "segment.io",
    "api.segment.io",
    "cdn.segment.com",
    "amplitude.com",
    "api.amplitude.com",
    "cdn.amplitude.com",
    "sentry.io",
    "browser.sentry-cdn.com",
    "bugsnag.com",
    "notify.bugsnag.com",
    "nr-data.net",
    "bam.nr-data.net",
    "js-agent.newrelic.com",
    "chartbeat.com",
    "pingdom.net",
    "fullstory.com",
    "rs.fullstory.com",
    "heapanalytics.com",
    "cdn.heapanalytics.com",
    "intercom.io",
    "widget.intercom.io",
    "branch.io",
    "app.link",
    "appsflyer.com",
    "adjust.com",
    "kochava.com",
    "analytics.tiktok.com",
    "business-api.tiktok.com",
    "mc.yandex.ru",
    "metrika.yandex.ru",
    // Ad Networks, Pop-unders & Redirect Ads
    "taboola.com",
    "cdn.taboola.com",
    "outbrain.com",
    "widgets.outbrain.com",
    "criteo.com",
    "static.criteo.net",
    "casalemedia.com",
    "rubiconproject.com",
    "pubmatic.com",
    "openx.net",
    "adnxs.com",
    "ib.adnxs.com",
    "adsrvr.org",
    "demdex.net",
    "omtrdc.net",
    "2o7.net",
    "krxd.net",
    "bluekai.com",
    "exelator.com",
    "moatads.com",
    "doubleverify.com",
    "adsafeprotected.com",
    "amazon-adsystem.com",
    "fls-na.amazon-adsystem.com",
    "adcolony.com",
    "applovin.com",
    "vungle.com",
    "unityads.unity3d.com",
    "chartboost.com",
    "inmobi.com",
    "smaato.net",
    "mediavine.com",
    "adthrive.com",
    "ezoic.net",
    "mgid.com",
    "revcontent.com",
    "zergnet.com",
    "popads.net",
    "popcash.net",
    "propellerads.com",
    "exoclick.com",
    "juicyads.com",
    "trafficjunky.net",
    "adsterra.com",
    "acscdn.com",
    "histats.com",
    "statcounter.com",
    "addthis.com",
    "sharethis.com",
    "teads.tv",
    "smartadserver.com",
    "indexexchange.com",
    "bidswitch.net",
    "triplelift.com",
    "3lift.com",
    "spotxchange.com",
    "yieldmo.com",
    "media.net",
    "contextweb.com",
    "pulsepoint.com",
    "advertising.com",
    "admob.com",
    "adform.net",
    "ads.yahoo.com",
    "gemini.yahoo.com",
    "yandexadexchange.net",
    "an.yandex.ru",
    "adsymptotic.com",
    "tapad.com",
    "lotame.com",
    "crwdcntrl.net",
    "liveramp.com",
    "rlcdn.com",
    "eyeota.net",
    "quantcast.com",
    "mouseflow.com",
    "crazyegg.com",
    "luckyorange.com",
    "logrocket.com",
    "pendo.io",
    "onetrust.com",
    "cookielaw.org",
    "consensu.org"
  )

  private val videoExts = setOf("mp4", "m3u8", "webm", "mov", "mkv", "ts")
  private val audioExts = setOf("mp3", "m4a", "aac", "wav", "ogg", "flac", "opus")
  private val imageExts = setOf("jpg", "jpeg", "png", "webp", "gif")
  private val docExts = setOf("pdf", "zip", "apk", "epub", "csv", "json", "txt")

  fun isHostMatchingSet(host: String, domainSet: Set<String>): Boolean {
    val clean = host.lowercase().trimEnd('.')
    if (clean.isEmpty() || domainSet.isEmpty()) return false
    var current = clean
    while (true) {
      if (current in domainSet) return true
      val dot = current.indexOf('.')
      if (dot < 0 || dot == current.length - 1) break
      current = current.substring(dot + 1)
    }
    return false
  }

  fun isTrackerHost(host: String): Boolean = isHostMatchingSet(host, blockedDomains)

  fun shouldBlockRequest(
    requestUrl: String,
    pageHost: String,
    enabled: Boolean,
    customBlockedHosts: Set<String> = emptySet()
  ): Boolean {
    val reqHost = UrlValidator.extractHost(requestUrl)
    if (reqHost.isEmpty()) return false
    if (customBlockedHosts.isNotEmpty() && isHostMatchingSet(reqHost, customBlockedHosts)) {
      return true
    }
    if (!enabled) return false
    // Do not block if the user explicitly navigated directly to that same host as top-level page
    val cleanPage = pageHost.lowercase().trimEnd('.')
    if (cleanPage.isNotEmpty() && (reqHost == cleanPage || reqHost.endsWith(".$cleanPage"))) {
      return false
    }
    return isTrackerHost(reqHost)
  }

  fun normalizeCustomHost(raw: String): String {
    val cleaned = UrlValidator.sanitizeControlChars(raw).lowercase()
      .removePrefix("https://")
      .removePrefix("http://")
      .substringBefore('/')
      .substringBefore(':')
      .trim('.', ' ')
    return if (cleaned.contains('.') && !cleaned.contains(' ')) cleaned else ""
  }

  fun inferSniffedResource(requestUrl: String): SniffedResource? {
    if (!UrlValidator.isSafeWebUrl(requestUrl)) return null
    val uri = try {
      URI(requestUrl)
    } catch (_: Throwable) {
      return null
    }
    val host = uri.host?.lowercase() ?: return null
    val path = uri.path ?: return null
    val lastSeg = path.substringAfterLast('/')
    val ext = lastSeg.substringAfterLast('.', "").lowercase()
    if (ext.isEmpty()) return null
    val type = when (ext) {
      in videoExts -> SniffedMediaType.VIDEO
      in audioExts -> SniffedMediaType.AUDIO
      in imageExts -> SniffedMediaType.IMAGE
      in docExts -> SniffedMediaType.DOCUMENT
      else -> return null
    }
    val fileName = UrlValidator.sanitizeFilename(lastSeg.ifBlank { "media.$ext" })
    return SniffedResource(
      url = requestUrl,
      host = host,
      fileName = fileName,
      type = type
    )
  }
}
