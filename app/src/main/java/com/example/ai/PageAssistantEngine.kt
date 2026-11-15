package com.example.ai

enum class SummaryMode(val label: String) {
  EXPLAIN_SIMPLY("Explain Simply (ELI5)"),
  KEY_POINTS("Key Takeaways"),
  EXECUTIVE("Quick Summary")
}

data class PageSummaryResult(
  val mode: SummaryMode,
  val summaryText: String,
  val keyPoints: List<String>,
  val readingTimeMinutes: Int
)

data class PageAnswerResult(
  val question: String,
  val answer: String,
  val sourceSnippet: String?,
  val foundInPage: Boolean
)

/**
 * On-device, privacy-preserving semantic summarizer and QA engine.
 * Answers strictly from current page content without leaking data off-device.
 */
object PageAssistantEngine {

  fun summarize(rawPageText: String, mode: SummaryMode): PageSummaryResult {
    val sanitized = PromptInjectionDefense.sanitizeUntrustedContent(rawPageText, maxChars = 12000)
    if (sanitized.isBlank()) {
      return PageSummaryResult(mode, "No readable text found on this page.", emptyList(), 0)
    }

    val sentences = extractSentences(sanitized)
    if (sentences.isEmpty()) {
      return PageSummaryResult(mode, sanitized.take(200), emptyList(), 1)
    }

    val wordCount = sanitized.split(Regex("""\s+""")).size
    val readMinutes = (wordCount / 200).coerceAtLeast(1)

    // Score sentences by term frequency and semantic significance
    val termFreq = computeTermFrequencies(sanitized)
    val scored = sentences.mapIndexed { idx, s ->
      var score = 0.0
      val words = s.lowercase().split(Regex("""\W+""")).filter { it.length > 3 }
      for (w in words) {
        score += termFreq[w] ?: 0
      }
      // Position bias (earlier sentences in web articles carry more theme)
      if (idx < 5) score *= 1.4
      s to score
    }

    val topSentences = scored.sortedByDescending { it.second }
      .take(4)
      .map { it.first }

    val keyPoints = topSentences.map { cleanSentenceForDisplay(it) }

    val summaryText = when (mode) {
      SummaryMode.EXPLAIN_SIMPLY -> {
        "In simple terms: " + keyPoints.take(2).joinToString(" ")
      }
      SummaryMode.KEY_POINTS -> {
        keyPoints.joinToString("\n• ", prefix = "• ")
      }
      SummaryMode.EXECUTIVE -> {
        keyPoints.take(3).joinToString(" ")
      }
    }

    return PageSummaryResult(
      mode = mode,
      summaryText = summaryText,
      keyPoints = keyPoints,
      readingTimeMinutes = readMinutes
    )
  }

  fun answerQuestion(rawPageText: String, question: String): PageAnswerResult {
    val sanitizedContent = PromptInjectionDefense.sanitizeUntrustedContent(rawPageText, maxChars = 15000)
    val cleanQ = question.trim().lowercase()

    if (sanitizedContent.isBlank() || cleanQ.isBlank()) {
      return PageAnswerResult(question, "Page content is empty.", null, false)
    }

    val queryTerms = cleanQ.split(Regex("""\W+""")).filter { it.length > 2 && it !in commonStopwords }
    if (queryTerms.isEmpty()) {
      return PageAnswerResult(question, "Please ask a more specific question.", null, false)
    }

    val sentences = extractSentences(sanitizedContent)
    var bestSnippet: String? = null
    var bestScore = 0

    for (sentence in sentences) {
      val lowerS = sentence.lowercase()
      val sentenceTokens = lowerS.split(Regex("""\W+""")).toSet()
      var matchCount = 0
      for (term in queryTerms) {
        if (sentenceTokens.contains(term)) {
          matchCount++
        }
      }
      if (matchCount > bestScore) {
        bestScore = matchCount
        bestSnippet = sentence
      }
    }

    return if (bestScore > 0 && bestSnippet != null) {
      PageAnswerResult(
        question = question,
        answer = cleanSentenceForDisplay(bestSnippet),
        sourceSnippet = bestSnippet,
        foundInPage = true
      )
    } else {
      PageAnswerResult(
        question = question,
        answer = "I could not find an answer to that question in the current page.",
        sourceSnippet = null,
        foundInPage = false
      )
    }
  }

  private fun extractSentences(text: String): List<String> {
    return text.split(Regex("""(?<=[.!?])\s+(?=[A-Z0-9])"""))
      .map { it.trim() }
      .filter { it.length > 25 && !it.startsWith("<") }
  }

  private fun computeTermFrequencies(text: String): Map<String, Int> {
    val map = HashMap<String, Int>()
    val tokens = text.lowercase().split(Regex("""\W+"""))
    for (t in tokens) {
      if (t.length > 3 && t !in commonStopwords) {
        map[t] = (map[t] ?: 0) + 1
      }
    }
    return map
  }

  private fun cleanSentenceForDisplay(s: String): String {
    return s.replace(Regex("""\s+"""), " ").trim()
  }

  private val commonStopwords = setOf(
    "a", "an", "the", "and", "or", "but", "if", "because", "as", "what",
    "which", "this", "that", "these", "those", "then", "just", "so", "than",
    "such", "both", "through", "about", "for", "is", "of", "while", "during",
    "to", "from", "in", "out", "on", "off", "again", "further", "once",
    "here", "there", "when", "where", "why", "how", "all", "any",
    "each", "few", "more", "most", "other", "some", "no", "nor", "not",
    "only", "own", "same", "too", "very", "can", "will", "don", "should", "now",
    "have", "has", "had", "having", "do", "does", "did", "doing", "would", "could",
    "they", "them", "their", "theirs", "themselves", "who", "whom", "am",
    "are", "was", "were", "be", "been", "being", "your", "yours", "yourself", "yourselves"
  )
}
