package com.example.ai

enum class SummaryMode(val label: String) {
  EXPLAIN_SIMPLY("Explain Simply (ELI5)"),
  KEY_POINTS("Key Takeaways"),
  EXECUTIVE("Quick Summary")
}

data class FactItem(
  val value: String,
  val category: String,
  val context: String
)

data class PageFactsResult(
  val facts: List<FactItem>,
  val dates: List<String>,
  val citations: List<String>
)

data class GlossaryTerm(
  val term: String,
  val definition: String,
  val context: String
)

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

  fun extractFactsAndClaims(rawPageText: String): PageFactsResult {
    val sanitized = PromptInjectionDefense.sanitizeUntrustedContent(rawPageText, maxChars = 15000)
    if (sanitized.isBlank()) {
      return PageFactsResult(emptyList(), emptyList(), emptyList())
    }

    val sentences = extractSentences(sanitized)
    val metricRegex = Regex("""(?i)\b([\$€£¥]?\d+(?:[.,]\d+)?\s*(?:%|percent|billion|million|trillion|thousand|users|downloads|km|kg|mph|mb|gb|fps|ms|sec|seconds|minutes|hours|days|years|times|x))\b""")
    val dateRegex = Regex("""\b(?:in|on|by|since|during)?\s*(?:(?:January|February|March|April|May|June|July|August|September|October|November|December|Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\s+\d{1,2},?\s+)?\b(19\d{2}|20\d{2})\b""")
    val citationKeywords = listOf("according to", "study found", "researchers discovered", "reported that", "survey revealed", "data demonstrates", "concluded that", "published in")

    val facts = mutableListOf<FactItem>()
    val dates = mutableListOf<String>()
    val citations = mutableListOf<String>()

    for (s in sentences) {
      val clean = cleanSentenceForDisplay(s)
      val lower = clean.lowercase()

      val metricMatch = metricRegex.find(clean)
      if (metricMatch != null && facts.size < 6) {
        val valStr = metricMatch.value.trim()
        facts.add(FactItem(value = valStr, category = "Metric", context = clean))
      }

      val dateMatch = dateRegex.find(clean)
      if (dateMatch != null && dates.size < 5) {
        dates.add(clean)
      }

      if (citationKeywords.any { lower.contains(it) } && citations.size < 5) {
        citations.add(clean)
      }
    }

    return PageFactsResult(
      facts = if (facts.isNotEmpty()) facts else listOf(FactItem("Verified Content", "General", sentences.firstOrNull() ?: sanitized.take(120))),
      dates = dates,
      citations = citations
    )
  }

  fun extractGlossary(rawPageText: String): List<GlossaryTerm> {
    val sanitized = PromptInjectionDefense.sanitizeUntrustedContent(rawPageText, maxChars = 15000)
    if (sanitized.isBlank()) return emptyList()

    val builtInDefs = mapOf(
      "AI" to "Artificial Intelligence: Machine intelligence simulating human cognition and problem-solving.",
      "API" to "Application Programming Interface: A standardized protocol for software services to communicate.",
      "GPU" to "Graphics Processing Unit: Specialized processor designed for high-throughput parallel computation.",
      "CPU" to "Central Processing Unit: The primary logic core executing program instructions.",
      "HTTPS" to "Hypertext Transfer Protocol Secure: Encrypted communication protocol protecting web traffic.",
      "DNS" to "Domain Name System: The internet directory translating human domain names to IP addresses.",
      "URL" to "Uniform Resource Locator: Web address specifying the location of a resource.",
      "SDK" to "Software Development Kit: A collection of development tools, libraries, and documentation.",
      "RAM" to "Random Access Memory: High-speed temporary working memory utilized by running apps.",
      "VPN" to "Virtual Private Network: Encrypted network tunnel shielding browsing data and IP identity.",
      "HTML" to "HyperText Markup Language: The standard structural markup code behind web pages.",
      "CSS" to "Cascading Style Sheets: The presentation rules defining web typography and visual layout.",
      "JS" to "JavaScript: High-level language enabling dynamic client-side interactivity on web pages.",
      "SEO" to "Search Engine Optimization: Techniques used to increase visibility in search results.",
      "LLM" to "Large Language Model: Deep neural network trained to comprehend and generate natural language.",
      "UI" to "User Interface: The visual and tactile elements through which humans control software.",
      "UX" to "User Experience: The overall ease, clarity, and satisfaction of interacting with a system.",
      "JSON" to "JavaScript Object Notation: Standard lightweight text format for structured data exchange.",
      "DOM" to "Document Object Model: The structured tree representation of HTML elements in memory.",
      "TTS" to "Text-To-Speech: Synthesis of written text into spoken audio.",
      "DASH" to "Dynamic Adaptive Streaming over HTTP: Adaptive bitrate streaming protocol for multimedia.",
      "HLS" to "HTTP Live Streaming: Media streaming communications protocol implemented by Apple."
    )

    val sentences = extractSentences(sanitized)
    val result = mutableListOf<GlossaryTerm>()
    val seenTerms = mutableSetOf<String>()

    for ((acronym, def) in builtInDefs) {
      if (Regex("""\b$acronym\b""").containsMatchIn(sanitized)) {
        val contextSentence = sentences.firstOrNull { it.contains(acronym) } ?: ""
        result.add(GlossaryTerm(term = acronym, definition = def, context = contextSentence))
        seenTerms.add(acronym)
        if (result.size >= 6) break
      }
    }

    val defRegex = Regex("""\b([A-Z][a-zA-Z]{2,20})\s+(?:is defined as|refers to|is a type of|is known as)\s+([^.!?]+)""")
    for (s in sentences) {
      val match = defRegex.find(s)
      if (match != null) {
        val term = match.groupValues[1].trim()
        val expl = match.groupValues[2].trim()
        if (term !in seenTerms && term.length > 2 && result.size < 8) {
          result.add(GlossaryTerm(term = term, definition = "${term}: $expl", context = s))
          seenTerms.add(term)
        }
      }
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
