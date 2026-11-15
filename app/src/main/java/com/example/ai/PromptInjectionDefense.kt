package com.example.ai

import java.util.regex.Pattern

/**
 * Defensive prompt-injection sanitizer and PII scrubber.
 * Treats all web page text as untrusted data before processing by AI.
 */
object PromptInjectionDefense {

  private val injectionPatterns = listOf(
    Pattern.compile("""(?i)(?:ignore|disregard|forget)\s+(?:all\s+)?(?:previous|prior|above)\s+instructions"""),
    Pattern.compile("""(?i)(?:you\s+are\s+now|act\s+as)\s+(?:an?\s+)?(?:unfiltered|unrestricted|jailbreak|dan|evil|admin)"""),
    Pattern.compile("""(?i)<\|(?:im_start|im_end|endoftext)\|?>"""),
    Pattern.compile("""(?i)\[\s*system\s*prompt\s*\]"""),
    Pattern.compile("""(?i)system\s*:\s*you\s+must"""),
    Pattern.compile("""(?i)new\s+rule\s*:"""),
    Pattern.compile("""(?i)reveal\s+(?:the\s+)?(?:system\s+)?prompt"""),
    Pattern.compile("""(?i)do\s+not\s+follow\s+the\s+original\s+instructions""")
  )

  private val creditCardPattern = Pattern.compile("""\b(?:4[0-9]{12}(?:[0-9]{3})?|5[1-5][0-9]{14}|3[47][0-9]{13}|6(?:011|5[0-9]{2})[0-9]{12})\b""")
  private val emailPattern = Pattern.compile("""\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}\b""")
  private val phonePattern = Pattern.compile("""\b(?:\+?(\d{1,3}))?[-. (]*(\d{3})[-. )]*(\d{3})[-. ]*(\d{4})\b""")

  fun sanitizeUntrustedContent(rawText: String, maxChars: Int = 8000): String {
    if (rawText.isBlank()) return ""

    var cleaned = rawText
      .replace(Regex("""<!--[\s\S]*?-->"""), " ")
      .replace(Regex("""[\u200B-\u200D\uFEFF\u0000-\u001F\u007F]"""), " ")

    for (pattern in injectionPatterns) {
      cleaned = pattern.matcher(cleaned).replaceAll("[REDACTED_DIRECTIVE]")
    }

    cleaned = creditCardPattern.matcher(cleaned).replaceAll("[CARD_REDACTED]")
    cleaned = emailPattern.matcher(cleaned).replaceAll("[EMAIL_REDACTED]")
    cleaned = phonePattern.matcher(cleaned).replaceAll("[PHONE_REDACTED]")

    cleaned = cleaned.replace(Regex("""\s+"""), " ").trim()

    return if (cleaned.length > maxChars) cleaned.take(maxChars) + "..." else cleaned
  }

  fun wrapAsUntrustedContent(sanitizedText: String): String {
    return "<UNTRUSTED_WEB_CONTENT>\n$sanitizedText\n</UNTRUSTED_WEB_CONTENT>"
  }
}
