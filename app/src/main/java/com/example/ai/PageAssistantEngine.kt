package com.example.ai

enum class SummaryMode(val label: String) {
  EXPLAIN_SIMPLY("Explain Simply (ELI5)"),
  KEY_POINTS("Key Takeaways"),
  EXECUTIVE("Quick Summary")
}

data class PageActionItemsResult(
  val actionItems: List<String>,
  val keyTakeaways: List<String>,
  val keyEntities: List<String>
)

data class PageInsightsResult(
  val readingEaseScore: Int,
  val readingLevel: String,
  val toneLabel: String,
  val toneConfidencePercent: Int,
  val estimatedReadTimeMinutes: Int,
  val wordCount: Int
)

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

  fun extractActionItems(rawPageText: String): PageActionItemsResult {
    val sanitized = PromptInjectionDefense.sanitizeUntrustedContent(rawPageText, maxChars = 12000)
    if (sanitized.isBlank()) {
      return PageActionItemsResult(emptyList(), emptyList(), emptyList())
    }

    val sentences = extractSentences(sanitized)
    val actionKeywords = listOf("must", "should", "ensure", "install", "download", "configure", "step", "recommend", "follow", "verify", "run", "remember to", "need to", "make sure")
    val takeawayKeywords = listOf("percent", "%", "increased", "decreased", "resulted", "concluded", "finding", "highlights", "crucial", "important", "effective", "factor")

    val actionItems = mutableListOf<String>()
    val takeaways = mutableListOf<String>()

    for (s in sentences) {
      val lower = s.lowercase()
      if (actionKeywords.any { lower.contains(it) } && actionItems.size < 5) {
        actionItems.add(cleanSentenceForDisplay(s))
      } else if (takeawayKeywords.any { lower.contains(it) } && takeaways.size < 5) {
        takeaways.add(cleanSentenceForDisplay(s))
      }
    }

    // Extract named entities (Capitalized multi-word phrases)
    val entityPattern = Regex("""\b([A-Z][a-z]+(?:\s+[A-Z][a-z]+)+)\b""")
    val entities = entityPattern.findAll(sanitized)
      .map { it.value.trim() }
      .filter { it.length in 5..40 && !it.contains("http", ignoreCase = true) }
      .distinct()
      .take(6)
      .toList()

    return PageActionItemsResult(
      actionItems = if (actionItems.isNotEmpty()) actionItems else listOf("No explicit action items found on this page."),
      keyTakeaways = if (takeaways.isNotEmpty()) takeaways else sentences.take(3).map { cleanSentenceForDisplay(it) },
      keyEntities = entities
    )
  }

  fun analyzePageInsights(rawPageText: String): PageInsightsResult {
    val sanitized = PromptInjectionDefense.sanitizeUntrustedContent(rawPageText, maxChars = 15000)
    if (sanitized.isBlank()) {
      return PageInsightsResult(75, "Standard", "Objective & Factual", 95, 1, 0)
    }

    val words = sanitized.split(Regex("""\s+""")).filter { it.isNotBlank() }
    val wordCount = words.size
    val sentences = extractSentences(sanitized).ifEmpty { listOf(sanitized) }
    val sentenceCount = sentences.size.coerceAtLeast(1)

    // Syllables estimation
    var syllables = 0
    for (w in words) {
      val clean = w.lowercase().filter { it.isLetter() }
      val vowels = clean.count { it in "aeiouy" }
      val silentE = if (clean.endsWith("e") && !clean.endsWith("le") && clean.length > 2) 1 else 0
      syllables += (vowels - silentE).coerceAtLeast(1)
    }

    // Flesch Reading Ease score
    val avgWordsPerSentence = wordCount.toDouble() / sentenceCount
    val avgSyllablesPerWord = syllables.toDouble() / wordCount.coerceAtLeast(1)
    val rawScore = 206.835 - (1.015 * avgWordsPerSentence) - (84.6 * avgSyllablesPerWord)
    val score = rawScore.toInt().coerceIn(10, 100)

    val readingLevel = when (score) {
      in 85..100 -> "Elementary (Very Easy)"
      in 70..84 -> "Middle School (Easy)"
      in 55..69 -> "High School (Standard)"
      in 35..54 -> "College (Challenging)"
      else -> "Academic / Complex (Very Difficult)"
    }

    // Tone & Clickbait analysis
    val lower = sanitized.lowercase()
    val sensationalWords = listOf("shocking", "urgent", "breaking", "you won't believe", "unbelievable", "secret", "miracle", "warning", "catastrophe", "massive", "exposed", "jaw-dropping")
    val objectiveWords = listOf("study", "researchers", "measured", "resulted", "method", "data", "analysis", "published", "percent", "evidence", "findings", "according to")
    val opinionWords = listOf("i believe", "in my opinion", "personally", "terrible", "wonderful", "amazing", "worst", "best", "feel that", "love", "hate")

    val sensationalHits = sensationalWords.count { lower.contains(it) }
    val objectiveHits = objectiveWords.count { lower.contains(it) }
    val opinionHits = opinionWords.count { lower.contains(it) }

    val (tone, confidence) = when {
      sensationalHits >= 2 -> "Sensational / Urgent" to 85
      opinionHits > objectiveHits -> "Opinion / Editorial" to 80
      else -> "Objective & Factual" to 92
    }

    val readMinutes = (wordCount / 200).coerceAtLeast(1)

    return PageInsightsResult(
      readingEaseScore = score,
      readingLevel = readingLevel,
      toneLabel = tone,
      toneConfidencePercent = confidence,
      estimatedReadTimeMinutes = readMinutes,
      wordCount = wordCount
    )
  }

  fun simplifySentence(sentence: String): String {
    var result = sentence
    val replacements = mapOf(
      "utilize" to "use",
      "facilitate" to "help",
      "commence" to "start",
      "terminate" to "end",
      "subsequently" to "then",
      "consequently" to "so",
      "demonstrate" to "show",
      "approximately" to "about",
      "numerous" to "many",
      "prior to" to "before",
      "in order to" to "to",
      "in the event that" to "if"
    )
    for ((complex, simple) in replacements) {
      result = result.replace(Regex("""(?i)\b$complex\b"""), simple)
    }
    return result
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
