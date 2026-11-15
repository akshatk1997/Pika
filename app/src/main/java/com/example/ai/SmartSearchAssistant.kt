package com.example.ai

import com.example.security.UrlValidator

data class SmartSearchSuggestion(
  val label: String,
  val query: String,
  val iconKind: SuggestionKind
)

enum class SuggestionKind {
  NAVIGATE,
  EXPLAIN,
  SUMMARY,
  COMPARE,
  SPARK
}

object SmartSearchAssistant {

  fun getSuggestions(rawInput: String): List<SmartSearchSuggestion> {
    val trimmed = rawInput.trim()
    if (trimmed.isBlank()) {
      return listOf(
        SmartSearchSuggestion("⚡ Instant Summary", "site:wikipedia.org technology", SuggestionKind.SPARK),
        SmartSearchSuggestion("🔒 Privacy Tools", "best privacy browser android", SuggestionKind.SPARK),
        SmartSearchSuggestion("💡 Explain simply", "how does webview work", SuggestionKind.SPARK),
        SmartSearchSuggestion("📰 Latest Tech", "technology news today", SuggestionKind.SPARK)
      )
    }

    val suggestions = mutableListOf<SmartSearchSuggestion>()

    if (UrlValidator.isSafeWebUrl(trimmed) || trimmed.contains(".") && !trimmed.contains(" ")) {
      val destination = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
      suggestions.add(SmartSearchSuggestion("Go to $trimmed", destination, SuggestionKind.NAVIGATE))
    }

    val cleanQuery = trimmed.replace(Regex("""^(what is|who is|how to|explain)\s+""", RegexOption.IGNORE_CASE), "").trim()

    suggestions.add(SmartSearchSuggestion("💡 Explain \"$cleanQuery\" simply", "explain simply $cleanQuery", SuggestionKind.EXPLAIN))
    suggestions.add(SmartSearchSuggestion("📑 Summary of \"$cleanQuery\"", "summary $cleanQuery key takeaways", SuggestionKind.SUMMARY))
    suggestions.add(SmartSearchSuggestion("⚖️ Compare \"$cleanQuery\"", "compare $cleanQuery alternatives pros cons", SuggestionKind.COMPARE))

    return suggestions
  }
}
