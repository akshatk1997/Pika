package com.example.data

import com.example.model.BookmarkItem
import com.example.security.UrlValidator
import org.json.JSONArray
import org.json.JSONObject

/**
 * Imports and exports bookmarks locally in standard Netscape HTML Bookmark format
 * (interoperable with major browsers) and JSON, sanitizing every entry.
 */
object BookmarkImportExport {

  private val htmlAnchorRegex = Regex(
    """<A\s+[^>]*HREF=["']([^"']+)["'][^>]*>(.*?)</A>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
  )

  fun exportToNetscapeHtml(bookmarks: List<BookmarkItem>): String {
    val sb = StringBuilder()
    sb.appendLine("<!DOCTYPE NETSCAPE-Bookmark-file-1>")
    sb.appendLine("<!-- This is an automatically generated file. -->")
    sb.appendLine("<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">")
    sb.appendLine("<TITLE>Bat Bookmarks</TITLE>")
    sb.appendLine("<H1>Bookmarks</H1>")
    sb.appendLine("<DL><p>")

    val byFolder = bookmarks.groupBy { it.folder.ifBlank { "Bookmarks" } }
    for ((folder, list) in byFolder) {
      sb.appendLine("    <DT><H3>${escapeHtml(folder)}</H3>")
      sb.appendLine("    <DL><p>")
      for (b in list) {
        if (UrlValidator.isSafeWebUrl(b.url)) {
          val epochSec = b.createdAt / 1000L
          sb.appendLine(
            "        <DT><A HREF=\"${escapeHtml(b.url)}\" ADD_DATE=\"$epochSec\">${escapeHtml(b.title)}</A>"
          )
        }
      }
      sb.appendLine("    </DL><p>")
    }
    sb.appendLine("</DL><p>")
    return sb.toString()
  }

  fun parseImportedBookmarks(rawContent: String): List<BookmarkItem> {
    val trimmed = rawContent.trim()
    if (trimmed.isEmpty()) return emptyList()

    // Try JSON array first
    if (trimmed.startsWith("[")) {
      return try {
        val arr = JSONArray(trimmed)
        val out = mutableListOf<BookmarkItem>()
        for (i in 0 until arr.length()) {
          val obj = arr.optJSONObject(i) ?: continue
          val url = UrlValidator.sanitizeControlChars(obj.optString("url", ""))
          val title = UrlValidator.sanitizeDisplayText(obj.optString("title", url), 180)
          val folder = UrlValidator.sanitizeDisplayText(obj.optString("folder", "Imported"), 64)
            .ifEmpty { "Imported" }
          if (UrlValidator.isSafeWebUrl(url)) {
            out += BookmarkItem(title = title.ifEmpty { url }, url = url, folder = folder)
          }
        }
        out
      } catch (_: Throwable) {
        emptyList()
      }
    }

    // Parse Netscape HTML format
    val results = mutableListOf<BookmarkItem>()
    for (match in htmlAnchorRegex.findAll(rawContent)) {
      val rawUrl = unescapeHtml(match.groupValues[1])
      val url = UrlValidator.sanitizeControlChars(rawUrl)
      val rawTitle = unescapeHtml(match.groupValues[2].replace(Regex("<[^>]+>"), ""))
      val title = UrlValidator.sanitizeDisplayText(rawTitle, 180).ifEmpty { url }
      if (UrlValidator.isSafeWebUrl(url)) {
        results += BookmarkItem(title = title, url = url, folder = "Imported")
      }
    }
    return results
  }

  fun escapeHtml(input: String): String {
    val cleaned = UrlValidator.sanitizeControlChars(input)
    val sb = StringBuilder(cleaned.length)
    for (c in cleaned) {
      when (c) {
        '&' -> sb.append("&amp;")
        '<' -> sb.append("&lt;")
        '>' -> sb.append("&gt;")
        '"' -> sb.append("&quot;")
        '\'' -> sb.append("&#39;")
        else -> sb.append(c)
      }
    }
    return sb.toString()
  }

  private fun unescapeHtml(input: String): String =
    input
      .replace("&amp;", "&")
      .replace("&lt;", "<")
      .replace("&gt;", ">")
      .replace("&quot;", "\"")
      .replace("&#39;", "'")
}
