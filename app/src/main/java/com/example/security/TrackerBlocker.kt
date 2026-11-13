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
    "consensu.org",
    // Video Streaming Ad SDKs, Video Ad Networks & Streaming Trackers
    "imasdk.googleapis.com",
    "pubads.g.doubleclick.net",
    "securepubads.g.doubleclick.net",
    "ad.doubleclick.net",
    "static.doubleclick.net",
    "video-stats.l.google.com",
    "s0.2mdn.net",
    "innovid.com",
    "s.innovid.com",
    "fwmrm.net",
    "freewheel.tv",
    "tubemogul.com",
    "tremorhub.com",
    "tremormedia.com",
    "stickyadstv.com",
    "liverail.com",
    "vdo.ai",
    "playwire.com",
    "adplayer.pro",
    "anyclip.com",
    "vidazoo.com",
    "aniview.com",
    "streamrail.com",
    "admanmedia.com",
    "springserve.com",
    "brid.tv",
    "unrulymedia.com",
    "undertone.com",
    "gumgum.com",
    "sovrn.com",
    "adpushup.com",
    // Pop-unders, Video Stream Redirects & Aggressive Ad Networks
    "adcash.com",
    "clickadu.com",
    "hilltopads.com",
    "monetag.com",
    "infolinks.com",
    "bidvertiser.com",
    "yllix.com",
    "ad-delivery.net",
    "a-ads.com",
    "adxprts.com",
    "adblade.com",
    "adtrue.com",
    "admaven.com",
    "yell-ad.com",
    "ero-advertising.com",
    "trafficstars.com"
  )

  private val videoExts = setOf("mp4", "m3u8", "webm", "mov", "mkv", "ts")
  private val audioExts = setOf("mp3", "m4a", "aac", "wav", "ogg", "flac", "opus")
  private val imageExts = setOf("jpg", "jpeg", "png", "webp", "gif")
  private val docExts = setOf("pdf", "zip", "apk", "epub", "csv", "json", "txt")

  private val adUrlPathSignatures = listOf(
    "/api/stats/ads",
    "/pagead/",
    "/ptracking",
    "/youtubei/v1/player/ad_break",
    "/get_midroll_info",
    "/ad_companion",
    "/googleads",
    "/pagead2",
    "/adsystem/",
    "/adservice/",
    "/ad-delivery/",
    "/ad_delivery/",
    "/vast.xml",
    "/vmap.xml",
    "/ads.js",
    "/prebid.js"
  )

  fun isAdPathOrQuery(requestUrl: String): Boolean {
    val lower = requestUrl.lowercase()
    for (sig in adUrlPathSignatures) {
      if (lower.contains(sig)) return true
    }
    return false
  }

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
    if (!enabled) return false
    val reqHost = UrlValidator.extractHost(requestUrl)
    if (reqHost.isEmpty()) return false
    if (customBlockedHosts.isNotEmpty() && isHostMatchingSet(reqHost, customBlockedHosts)) {
      return true
    }
    // Block video ad requests & ad serving endpoints even if hosted on the same domain (e.g. youtube.com/api/stats/ads)
    if (isAdPathOrQuery(requestUrl)) {
      return true
    }
    // Do not block normal site assets on the top-level domain unless matched above
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
