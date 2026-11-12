package com.example.security

/**
 * Compact, zero-dependency third-party tracker & telemetry domain blocker.
 * Checked inside WebViewClient.shouldInterceptRequest for subresource requests.
 */
object TrackerBlocker {

  private val blockedDomains: HashSet<String> = hashSetOf(
    "doubleclick.net",
    "googlesyndication.com",
    "google-analytics.com",
    "googletagmanager.com",
    "googletagservices.com",
    "adservice.google.com",
    "pagead2.googlesyndication.com",
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
    "amazon-adsystem.com",
    "fls-na.amazon-adsystem.com",
    "tiktok.com",
    "analytics.tiktok.com",
    "business-api.tiktok.com",
    "mc.yandex.ru",
    "metrika.yandex.ru"
  )

  fun isTrackerHost(host: String): Boolean {
    val clean = host.lowercase().trimEnd('.')
    if (clean.isEmpty()) return false
    var current = clean
    while (true) {
      if (current in blockedDomains) return true
      val dot = current.indexOf('.')
      if (dot < 0 || dot == current.length - 1) break
      current = current.substring(dot + 1)
    }
    return false
  }

  fun shouldBlockRequest(requestUrl: String, pageHost: String, enabled: Boolean): Boolean {
    if (!enabled) return false
    val reqHost = UrlValidator.extractHost(requestUrl)
    if (reqHost.isEmpty()) return false
    // Do not block if the user explicitly navigated directly to that same host as top-level page
    val cleanPage = pageHost.lowercase().trimEnd('.')
    if (cleanPage.isNotEmpty() && (reqHost == cleanPage || reqHost.endsWith(".$cleanPage"))) {
      return false
    }
    return isTrackerHost(reqHost)
  }
}
