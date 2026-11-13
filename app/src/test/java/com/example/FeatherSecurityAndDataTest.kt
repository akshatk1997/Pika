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
  fun mediaSnifferClassifiesValidResourcesOnly() {
    val video = TrackerBlocker.inferSniffedResource("https://cdn.example.org/videos/clip.mp4?token=1")
    assertNotNull(video)
    assertEquals(SniffedMediaType.VIDEO, video?.type)
    assertEquals("clip.mp4", video?.fileName)

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
    assertEquals("shiz_media_stream", com.example.media.MediaPlaybackService.CHANNEL_ID)
  }
}
