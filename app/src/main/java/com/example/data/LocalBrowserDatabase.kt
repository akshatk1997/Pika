package com.example.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.model.BookmarkItem
import com.example.model.HistoryItem
import com.example.security.UrlValidator

class LocalBrowserDatabase(context: Context) :
  SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

  companion object {
    private const val DB_NAME = "feather_local.db"
    private const val DB_VERSION = 1

    private const val TABLE_BOOKMARKS = "bookmarks"
    private const val TABLE_HISTORY = "history"
  }

  override fun onCreate(db: SQLiteDatabase) {
    db.execSQL(
      """
      CREATE TABLE $TABLE_BOOKMARKS (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        title TEXT NOT NULL,
        url TEXT NOT NULL UNIQUE,
        folder TEXT NOT NULL DEFAULT 'Bookmarks',
        created_at INTEGER NOT NULL
      )
      """.trimIndent()
    )
    db.execSQL(
      """
      CREATE TABLE $TABLE_HISTORY (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        title TEXT NOT NULL,
        url TEXT NOT NULL,
        visited_at INTEGER NOT NULL
      )
      """.trimIndent()
    )
    db.execSQL("CREATE INDEX idx_history_visited ON $TABLE_HISTORY(visited_at DESC)")
  }

  override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    // No migrations needed in v1.0
  }

  fun getBookmarks(query: String = ""): List<BookmarkItem> {
    val db = readableDatabase
    val cleanedQuery = UrlValidator.sanitizeControlChars(query)
    val sql: String
    val args: Array<String>
    if (cleanedQuery.isEmpty()) {
      sql = "SELECT id, title, url, folder, created_at FROM $TABLE_BOOKMARKS ORDER BY folder ASC, created_at DESC"
      args = emptyArray()
    } else {
      val like = "%$cleanedQuery%"
      sql = "SELECT id, title, url, folder, created_at FROM $TABLE_BOOKMARKS WHERE title LIKE ? OR url LIKE ? OR folder LIKE ? ORDER BY created_at DESC"
      args = arrayOf(like, like, like)
    }
    val items = mutableListOf<BookmarkItem>()
    db.rawQuery(sql, args).use { cursor ->
      while (cursor.moveToNext()) {
        items += BookmarkItem(
          id = cursor.getLong(0),
          title = cursor.getString(1),
          url = cursor.getString(2),
          folder = cursor.getString(3),
          createdAt = cursor.getLong(4)
        )
      }
    }
    return items
  }

  fun isBookmarked(url: String): Boolean {
    if (url.isBlank()) return false
    readableDatabase.rawQuery(
      "SELECT 1 FROM $TABLE_BOOKMARKS WHERE url = ? LIMIT 1",
      arrayOf(url)
    ).use { return it.moveToFirst() }
  }

  fun upsertBookmark(title: String, url: String, folder: String = "Bookmarks"): Long {
    if (!UrlValidator.isSafeWebUrl(url)) return -1L
    val safeTitle = UrlValidator.sanitizeDisplayText(title, 180).ifEmpty { url }
    val safeFolder = UrlValidator.sanitizeDisplayText(folder, 64).ifEmpty { "Bookmarks" }
    val cv = ContentValues().apply {
      put("title", safeTitle)
      put("url", url)
      put("folder", safeFolder)
      put("created_at", System.currentTimeMillis())
    }
    return writableDatabase.insertWithOnConflict(
      TABLE_BOOKMARKS,
      null,
      cv,
      SQLiteDatabase.CONFLICT_REPLACE
    )
  }

  fun updateBookmark(id: Long, title: String, url: String, folder: String): Boolean {
    if (!UrlValidator.isSafeWebUrl(url)) return false
    val safeTitle = UrlValidator.sanitizeDisplayText(title, 180).ifEmpty { url }
    val safeFolder = UrlValidator.sanitizeDisplayText(folder, 64).ifEmpty { "Bookmarks" }
    val cv = ContentValues().apply {
      put("title", safeTitle)
      put("url", url)
      put("folder", safeFolder)
    }
    return writableDatabase.update(TABLE_BOOKMARKS, cv, "id = ?", arrayOf(id.toString())) > 0
  }

  fun deleteBookmarkById(id: Long) {
    writableDatabase.delete(TABLE_BOOKMARKS, "id = ?", arrayOf(id.toString()))
  }

  fun deleteBookmarkByUrl(url: String) {
    writableDatabase.delete(TABLE_BOOKMARKS, "url = ?", arrayOf(url))
  }

  fun recordHistory(title: String, url: String) {
    if (!UrlValidator.isSafeWebUrl(url) || url == "about:blank") return
    val safeTitle = UrlValidator.sanitizeDisplayText(title, 180).ifEmpty { url }
    val cv = ContentValues().apply {
      put("title", safeTitle)
      put("url", url)
      put("visited_at", System.currentTimeMillis())
    }
    writableDatabase.insert(TABLE_HISTORY, null, cv)
  }

  fun getHistory(query: String = "", limit: Int = 250): List<HistoryItem> {
    val db = readableDatabase
    val cleanedQuery = UrlValidator.sanitizeControlChars(query)
    val sql: String
    val args: Array<String>
    if (cleanedQuery.isEmpty()) {
      sql = "SELECT id, title, url, visited_at FROM $TABLE_HISTORY ORDER BY visited_at DESC LIMIT ?"
      args = arrayOf(limit.toString())
    } else {
      val like = "%$cleanedQuery%"
      sql = "SELECT id, title, url, visited_at FROM $TABLE_HISTORY WHERE title LIKE ? OR url LIKE ? ORDER BY visited_at DESC LIMIT ?"
      args = arrayOf(like, like, limit.toString())
    }
    val items = mutableListOf<HistoryItem>()
    db.rawQuery(sql, args).use { cursor ->
      while (cursor.moveToNext()) {
        items += HistoryItem(
          id = cursor.getLong(0),
          title = cursor.getString(1),
          url = cursor.getString(2),
          visitedAt = cursor.getLong(3)
        )
      }
    }
    return items
  }

  fun deleteHistoryEntry(id: Long) {
    writableDatabase.delete(TABLE_HISTORY, "id = ?", arrayOf(id.toString()))
  }

  fun clearAllHistory() {
    writableDatabase.delete(TABLE_HISTORY, null, null)
  }
}
