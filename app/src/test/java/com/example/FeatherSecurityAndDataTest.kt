package com.example

import com.example.data.BookmarkImportExport
import com.example.model.BookmarkItem
import com.example.model.SearchEngine
import com.example.security.ResolvedNavigation
import com.example.security.TrackerBlocker
import com.example.security.UrlValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
  fun trackerBlockerBlocksKnownThirdPartyTrackersOnly() {
    assertTrue(TrackerBlocker.shouldBlockRequest("https://www.google-analytics.com/analytics.js", "example.com", true))
    assertTrue(TrackerBlocker.shouldBlockRequest("https://cdn.segment.com/analytics.js", "news.ycombinator.com", true))
    assertFalse(TrackerBlocker.shouldBlockRequest("https://example.com/app.js", "example.com", true))
    assertFalse(TrackerBlocker.shouldBlockRequest("https://www.google-analytics.com/analytics.js", "example.com", false))
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
}
