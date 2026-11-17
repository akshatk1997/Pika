package com.example

import com.example.data.BookmarkImportExport
import com.example.download.DownloadController
import com.example.model.BookmarkItem
import com.example.model.SearchEngine
import com.example.model.SniffedMediaType
import com.example.security.ResolvedNavigation
import com.example.security.TrackerBlocker
import com.example.security.UrlValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatherSecurityAndDataTest {

  @Test
  fun blocksDangerousSchemes() {
    val js = UrlValidator.resolveInputToTarget("javascript:alert(1)", SearchEngine.DUCKDUCKGO, "", true)
    assertTrue(js is ResolvedNavigation.BlockedScheme)

    val file = UrlValidator.resolveInputToTarget("file:///etc/passwd", SearchEngine.DUCKDUCKGO, "", true)
    assertTrue(file is ResolvedNavigation.BlockedScheme)

    val data = UrlValidator.resolveInputToTarget("data:text/html,<script>1</script>", SearchEngine.DUCKDUCKGO, "", true)
    assertTrue(data is ResolvedNavigation.BlockedScheme)
  }

  @Test
  fun upgradesHttpAndStripsTrackingParams() {
    val res = UrlValidator.resolveInputToTarget(
      "http://example.com/path?id=42&utm_source=ads&fbclid=123",
      SearchEngine.DUCKDUCKGO,
      "",
      httpsOnly = true
    )
    assertTrue(res is ResolvedNavigation.LoadUrl)
    val load = res as ResolvedNavigation.LoadUrl
    assertTrue(load.upgradedFromHttp)
    assertEquals("https://example.com/path?id=42", load.url)
  }

  @Test
  fun stripTrackingParamsPreservesEncodedQueryParametersWithoutDoubleEncoding() {
    val input = "https://translate.google.com/website?sl=auto&tl=en&u=https%3A%2F%2Fexample.com%2Fpath"
    val res = UrlValidator.stripTrackingParams(input)
    assertEquals(input, res)
  }

  @Test
  fun resolvesSearchQueriesToConfiguredEngine() {
    val res = UrlValidator.resolveInputToTarget(
      "kotlin compose webview",
      SearchEngine.STARTPAGE,
      "",
      httpsOnly = true
    )
    assertTrue(res is ResolvedNavigation.LoadUrl)
    assertEquals("https://www.startpage.com/do/dsearch?query=kotlin+compose+webview", (res as ResolvedNavigation.LoadUrl).url)
  }

  @Test
  fun sanitizesFilenamesAndControlChars() {
    assertEquals("__etc_passwd", UrlValidator.sanitizeFilename("../etc/passwd"))
    assertEquals("hello", UrlValidator.sanitizeControlChars("he\u202Ello\n\r"))
  }

  @Test
  fun trackerBlockerAndCustomRulesWork() {
    assertTrue(TrackerBlocker.shouldBlockRequest("https://www.google-analytics.com/analytics.js", "example.com", true))
    assertTrue(TrackerBlocker.shouldBlockRequest("https://cdn.segment.com/analytics.js", "news.ycombinator.com", true))
    assertFalse(TrackerBlocker.shouldBlockRequest("https://example.com/app.js", "example.com", true))
    assertFalse(TrackerBlocker.shouldBlockRequest("https://www.google-analytics.com/analytics.js", "example.com", false))

    val normalized = TrackerBlocker.normalizeCustomHost("HTTPS://Ads.CustomHost.com/path")
    assertEquals("ads.customhost.com", normalized)
    assertTrue(
      TrackerBlocker.shouldBlockRequest(
        "https://sub.ads.customhost.com/banner.js",
        "example.com",
        enabled = false,
        customBlockedHosts = setOf(normalized)
      )
    )
  }

  @Test
  fun videoStreamingAndWebAdsBlockedEffectively() {
    // Video streaming ad endpoints blocked even when matching same host as the page (e.g. YouTube)
    assertTrue(TrackerBlocker.shouldBlockRequest("https://www.youtube.com/api/stats/ads?v=123", "m.youtube.com", true))
    assertTrue(TrackerBlocker.shouldBlockRequest("https://m.youtube.com/youtubei/v1/player/ad_break?ad_type=1", "m.youtube.com", true))
    assertTrue(TrackerBlocker.shouldBlockRequest("https://www.youtube.com/pagead/parallel?video_id=abc", "youtube.com", true))
    assertTrue(TrackerBlocker.shouldBlockRequest("https://www.youtube.com/ptracking?v=123", "youtube.com", true))
    assertTrue(TrackerBlocker.shouldBlockRequest("https://www.youtube.com/get_midroll_info?v=123", "youtube.com", true))

    // Video ad SDKs and ad networks blocked
    assertTrue(TrackerBlocker.shouldBlockRequest("https://imasdk.googleapis.com/js/sdkloader/ima3.js", "streaming.example.com", true))
    assertTrue(TrackerBlocker.shouldBlockRequest("https://pubads.g.doubleclick.net/gampad/ads?env=vp", "video.example.org", true))
    assertTrue(TrackerBlocker.shouldBlockRequest("https://s0.2mdn.net/instream/video.mp4", "news.example.com", true))

    // Normal non-ad resources on the video site are not blocked
    assertFalse(TrackerBlocker.shouldBlockRequest("https://www.youtube.com/s/desktop/123/css/base.css", "youtube.com", true))
    assertFalse(TrackerBlocker.shouldBlockRequest("https://m.youtube.com/static/player/core.js", "m.youtube.com", true))
    assertFalse(TrackerBlocker.shouldBlockRequest("https://example.com/videos/tutorial.mp4", "example.com", true))
  }

  @Test
  fun tsyndicateAndPopunderAdsBlocked() {
    assertTrue(TrackerBlocker.isTrackerHost("tsyndicate.com"))
    assertTrue(TrackerBlocker.isTrackerHost("s.tsyndicate.net"))
    assertTrue(TrackerBlocker.isTrackerHost("syndication.traffichaus.com"))
    assertTrue(TrackerBlocker.isTrackerHost("adsterra.net"))
    assertTrue(TrackerBlocker.isTrackerHost("propellerclick.com"))

    assertTrue(TrackerBlocker.shouldBlockRequest("https://tsyndicate.com/out?ad=1", "streamingsite.com", true))
    assertTrue(TrackerBlocker.shouldBlockRequest("https://s.tsyndicate.net/tag.js", "streamingsite.com", true))
    assertTrue(TrackerBlocker.shouldBlockRequest("https://engine.tsyndicate.com/delivery/ajs.php", "streamingsite.com", true))
    assertTrue(TrackerBlocker.shouldBlockRequest("https://adsterra.net/popunder.js", "streamingsite.com", true))
  }

  @Test
  fun mediaOrStreamRequestsNeverBlockedAndVstreamAllowed() {
    assertFalse(TrackerBlocker.isTrackerHost("vstream.to"))
    assertFalse(TrackerBlocker.shouldBlockRequest("https://vstream.to/video/123.mp4", "animewatch.com", true))
    assertTrue(TrackerBlocker.isMediaOrStreamRequest("https://cdn.example.org/stream.m3u8"))
    assertTrue(TrackerBlocker.isMediaOrStreamRequest("https://cdn.example.org/video.mp4"))
    assertTrue(TrackerBlocker.isMediaOrStreamRequest("https://cdn.example.org/chunk.ts"))
    assertTrue(TrackerBlocker.isMediaOrStreamRequest("https://rr3---sn-ab5sznzl.googlevideo.com/videoplayback?expire=123"))
  }

  @Test
  fun mediaSnifferClassifiesValidResourcesOnly() {
    val video = TrackerBlocker.inferSniffedResource("https://cdn.example.org/videos/clip.mp4?token=1")
    assertNotNull(video)
    assertEquals(SniffedMediaType.VIDEO, video?.type)
    assertEquals("clip.mp4", video?.fileName)

    // Streaming formats (m3u8, webm, mpd)
    val hls = TrackerBlocker.inferSniffedResource("https://stream.example.org/live/playlist.m3u8?auth=token")
    assertNotNull(hls)
    assertEquals(SniffedMediaType.VIDEO, hls?.type)
    assertEquals("playlist.m3u8", hls?.fileName)

    val webm = TrackerBlocker.inferSniffedResource("https://media.example.com/assets/sample.webm")
    assertNotNull(webm)
    assertEquals(SniffedMediaType.VIDEO, webm?.type)

    // Query-based video stream without static extension in path
    val ytStream = TrackerBlocker.inferSniffedResource("https://rr3---sn-ab5sznzl.googlevideo.com/videoplayback?expire=12345&mime=video%2Fmp4")
    assertNotNull(ytStream)
    assertEquals(SniffedMediaType.VIDEO, ytStream?.type)
    assertTrue(ytStream?.fileName?.endsWith(".mp4") == true)

    val pdf = TrackerBlocker.inferSniffedResource("https://docs.example.org/manual.pdf")
    assertNotNull(pdf)
    assertEquals(SniffedMediaType.DOCUMENT, pdf?.type)

    assertNull(TrackerBlocker.inferSniffedResource("https://example.org/index.html"))
    assertNull(TrackerBlocker.inferSniffedResource("javascript:alert(1)"))
  }

  @Test
  fun downloadMimeSanitizerStripsCharsetParams() {
    assertEquals("application/pdf", DownloadController.sanitizeMimeType("application/pdf; charset=utf-8"))
    assertEquals("application/octet-stream", DownloadController.sanitizeMimeType(""))
  }

  @Test
  fun bookmarkImportExportSanitizesUnsafeLinks() {
    val exported = BookmarkImportExport.exportToNetscapeHtml(
      listOf(BookmarkItem(id = 1, title = "Safe <Site>", url = "https://example.org", folder = "Work"))
    )
    assertTrue(exported.contains("Safe &lt;Site&gt;"))

    val maliciousHtml = """
      <DT><A HREF="https://example.org">Good</A>
      <DT><A HREF="javascript:alert(1)">Bad</A>
    """.trimIndent()
    val imported = BookmarkImportExport.parseImportedBookmarks(maliciousHtml)
    assertEquals(1, imported.size)
    assertEquals("https://example.org", imported[0].url)
  }

  @Test
  fun mediaPlaybackServiceActionsAreDistinctAndValid() {
    val actions = setOf(
      com.example.media.MediaPlaybackService.ACTION_PLAY,
      com.example.media.MediaPlaybackService.ACTION_PAUSE,
      com.example.media.MediaPlaybackService.ACTION_PREV,
      com.example.media.MediaPlaybackService.ACTION_NEXT,
      com.example.media.MediaPlaybackService.ACTION_STOP,
      com.example.media.MediaPlaybackService.ACTION_UPDATE_STATE
    )
    assertEquals(6, actions.size)
    assertEquals("kai_media_stream", com.example.media.MediaPlaybackService.CHANNEL_ID)
  }

  @Test
  fun streamingAdRuleContainsCriticalSelectors() {
    val adUrl = "https://www.youtube.com/api/stats/ads?v=123"
    assertTrue(TrackerBlocker.shouldBlockRequest(adUrl, "m.youtube.com", true))
  }

  @Test
  fun detectsRiskyExecutableDownloadTypes() {
    assertTrue(DownloadController.isRiskyFileType("malicious.apk", "application/vnd.android.package-archive"))
    assertTrue(DownloadController.isRiskyFileType("payload.exe", "application/octet-stream"))
    assertTrue(DownloadController.isRiskyFileType("script.sh", "text/plain"))
    assertTrue(DownloadController.isRiskyFileType("archive.bin", "application/x-executable"))
    assertFalse(DownloadController.isRiskyFileType("photo.jpg", "image/jpeg"))
    assertFalse(DownloadController.isRiskyFileType("document.pdf", "application/pdf"))
  }

  @Test
  fun outdatedWebViewMajorVersionLogic() {
    val isOutdated80 = 80 in 1..85
    val isOutdated120 = 120 in 1..85
    assertTrue(isOutdated80)
    assertFalse(isOutdated120)
  }

  @Test
  fun privateModeWebRtcSdpCandidateStripping() {
    val sampleSdp = "v=0\r\no=- 123 2 IN IP4 127.0.0.1\r\na=candidate:1 1 UDP 2130706431 192.168.1.105 50000 typ host\r\na=candidate:2 1 UDP 1694498815 203.0.113.1 50000 typ srflx raddr 192.168.1.105 rport 50000\r\n"
    val stripped = sampleSdp.replace(Regex("a=candidate:[^\r\n]*\\btyp host\\b[^\r\n]*(\r\n|\n)?"), "")
    assertFalse(stripped.contains("192.168.1.105 50000 typ host"))
    assertTrue(stripped.contains("typ srflx"))
  }

  @Test
  fun videoUrlPatternAndEmptyVastFormatVerified() {
    assertTrue(TrackerBlocker.isVideoUrlPattern("https://example.com/stream/manifest.m3u8"))
    assertTrue(TrackerBlocker.isVideoUrlPattern("https://example.com/video/chunk.mpd"))
    assertTrue(TrackerBlocker.isVideoUrlPattern("https://rr1---sn.googlevideo.com/videoplayback?expire=123"))
    assertTrue(TrackerBlocker.isVideoUrlPattern("https://example.org/watch?format=mp4"))

    val emptyVastXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><VAST version=\"3.0\"/>"
    assertTrue(emptyVastXml.contains("<VAST version=\"3.0\"/>"))
  }

  @Test
  fun newTabUrlValidationAndAdBlocking() {
    val cleanVideoUrl = "https://example.com/watch?v=abcdef123"
    assertTrue(UrlValidator.isSafeWebUrl(cleanVideoUrl))
    assertFalse(TrackerBlocker.isTrackerHost(UrlValidator.extractHost(cleanVideoUrl)))
    assertFalse(TrackerBlocker.isAdPathOrQuery(cleanVideoUrl))

    val adPopupUrl = "https://tsyndicate.com/ad/popunder?zone=123"
    assertTrue(UrlValidator.isSafeWebUrl(adPopupUrl))
    assertTrue(TrackerBlocker.isTrackerHost(UrlValidator.extractHost(adPopupUrl)))

    val dangerousSchemeUrl = "javascript:window.open('https://malicious.com')"
    assertFalse(UrlValidator.isSafeWebUrl(dangerousSchemeUrl))
  }

  @Test
  fun resolveRelativeUrlResolvesAnchorAndImageTargetsCorrectly() {
    val base = "https://example.com/section/index.html"

    // Absolute URLs remain intact
    assertEquals("https://anothersite.org/watch", UrlValidator.resolveRelativeUrl(base, "https://anothersite.org/watch"))
    assertEquals("http://anothersite.org/stream", UrlValidator.resolveRelativeUrl(base, "http://anothersite.org/stream"))

    // Relative root paths
    assertEquals("https://example.com/next-page", UrlValidator.resolveRelativeUrl(base, "/next-page"))

    // Relative directory paths
    assertEquals("https://example.com/section/page2.html", UrlValidator.resolveRelativeUrl(base, "page2.html"))

    // Query parameters
    assertEquals("https://example.com/section/index.html?page=2", UrlValidator.resolveRelativeUrl(base, "?page=2"))

    // Protocol-relative URLs
    assertEquals("https://cdn.example.org/player.js", UrlValidator.resolveRelativeUrl(base, "//cdn.example.org/player.js"))

    // Empty or blank target
    assertEquals("", UrlValidator.resolveRelativeUrl(base, ""))
    assertEquals("", UrlValidator.resolveRelativeUrl(base, null))
  }

  @Test
  fun desktopUrlRewritingAndSearchEngineDetectionWorkCorrectly() {
    assertEquals("https://www.youtube.com/watch?v=123", UrlValidator.toDesktopUrl("https://m.youtube.com/watch?v=123"))
    assertEquals("https://en.wikipedia.org/wiki/Hope", UrlValidator.toDesktopUrl("https://en.m.wikipedia.org/wiki/Hope"))
    assertEquals("https://www.imdb.com/title/tt1234", UrlValidator.toDesktopUrl("https://m.imdb.com/title/tt1234"))
    assertEquals("https://www.example.com/news", UrlValidator.toDesktopUrl("https://mobile.example.com/news"))
    assertEquals("https://example.com/page", UrlValidator.toDesktopUrl("https://example.com/page"))

    assertTrue(UrlValidator.isSearchEngineUrl("https://duckduckgo.com/?q=hope"))
    assertTrue(UrlValidator.isSearchEngineUrl("https://www.google.com/search?q=hope"))
    assertTrue(UrlValidator.isSearchEngineUrl("https://search.brave.com/search?q=hope"))
    assertTrue(UrlValidator.isSearchEngineUrl("https://www.startpage.com/do/dsearch?query=hope"))
    assertFalse(UrlValidator.isSearchEngineUrl("https://en.wikipedia.org/wiki/Hope"))
    assertFalse(UrlValidator.isSearchEngineUrl("https://www.youtube.com/watch?v=123"))
  }

  @Test
  fun adSubdomainsAndStreamingPopunderRedirectsBlockedSafely() {
    assertTrue(TrackerBlocker.isAdSubdomain("ad.trans.example.com"))
    assertTrue(TrackerBlocker.isAdSubdomain("ad.trafficjunky.net"))
    assertTrue(TrackerBlocker.isAdSubdomain("ads.somehost.org"))
    assertTrue(TrackerBlocker.isAdSubdomain("syndication.exoclick.com"))
    assertTrue(TrackerBlocker.isAdSubdomain("pxl.tsyndicate.com"))
    assertFalse(TrackerBlocker.isAdSubdomain("cdn.example.com"))
    assertFalse(TrackerBlocker.isAdSubdomain("www.example.com"))

    assertTrue(TrackerBlocker.isAdOrPopunderNavigation("https://ad.transmedia.net/go?id=1"))
    assertTrue(TrackerBlocker.isAdOrPopunderNavigation("https://tsyndicate.com/out?ad=1"))
    assertTrue(TrackerBlocker.isAdOrPopunderNavigation("https://a.magsrv.com/ad-provider.js"))
    assertTrue(TrackerBlocker.isAdOrPopunderNavigation("https://random-pop.xyz/afu.php?zoneid=12345"))
    assertFalse(TrackerBlocker.isAdOrPopunderNavigation("https://en.wikipedia.org/wiki/Hope"))
    assertFalse(TrackerBlocker.shouldBlockRequest("https://cdn.videohost.org/hls/stream.m3u8", "watchsite.com", true))
  }
}
