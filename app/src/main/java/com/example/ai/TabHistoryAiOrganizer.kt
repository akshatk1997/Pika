package com.example.ai

import com.example.model.HistoryItem
import com.example.model.TabInfo
import com.example.security.UrlValidator

object TabHistoryAiOrganizer {

  private val categoryKeywords = mapOf(
    "Development" to setOf("github", "gitlab", "stackoverflow", "kotlin", "android", "developer", "api", "code", "git"),
    "Knowledge & Research" to setOf("wikipedia", "wiki", "docs", "edu", "arxiv", "scholar", "learn", "tutorial", "guide"),
    "Media & Audio" to setOf("youtube", "spotify", "music", "podcast", "video", "stream", "audio", "track"),
    "Shopping" to setOf("amazon", "ebay", "shop", "store", "cart", "product", "price", "order", "deal"),
    "Social & Discussion" to setOf("reddit", "twitter", "x.com", "linkedin", "forum", "community", "threads"),
    "News & Media" to setOf("news", "bbc", "cnn", "nytimes", "reuters", "theverge", "article", "report")
  )

  fun groupTabs(tabs: List<TabInfo>): Map<String, List<TabInfo>> {
    val grouped = LinkedHashMap<String, MutableList<TabInfo>>()

    for (tab in tabs) {
      val host = UrlValidator.extractHost(tab.url).lowercase()
      val title = tab.title.lowercase()
      val target = "$host $title"

      var matchedCategory = "General"
      for ((category, keywords) in categoryKeywords) {
        if (keywords.any { target.contains(it) }) {
          matchedCategory = category
          break
        }
      }
      grouped.getOrPut(matchedCategory) { mutableListOf() }.add(tab)
    }

    return grouped
  }

  fun naturalLanguageHistorySearch(history: List<HistoryItem>, naturalQuery: String): List<HistoryItem> {
    val cleanQ = naturalQuery.trim().lowercase()
    if (cleanQ.isBlank()) return history

    val queryTokens = cleanQ.split(Regex("""\W+""")).filter { it.length > 1 }
    if (queryTokens.isEmpty()) return history

    // Identify if query expresses intent for a specific category (e.g. "videos", "shopping", "dev")
    val intentCategories = mutableSetOf<String>()
    for ((category, keywords) in categoryKeywords) {
      if (queryTokens.any { token -> keywords.contains(token) || category.lowercase().contains(token) }) {
        intentCategories.add(category)
      }
    }

    return history.mapNotNull { item ->
      val title = item.title.lowercase()
      val url = item.url.lowercase()
      val combined = "$title $url"

      var score = 0
      for (token in queryTokens) {
        if (title.contains(token)) score += 3
        if (url.contains(token)) score += 2
      }

      for (cat in intentCategories) {
        val keywords = categoryKeywords[cat] ?: emptySet()
        if (keywords.any { combined.contains(it) }) {
          score += 4
        }
      }

      if (score > 0) item to score else null
    }
      .sortedByDescending { it.second }
      .map { it.first }
  }
}
