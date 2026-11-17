package com.example.security

import java.net.URI

enum class PhishingRiskLevel {
  LOW,
  SUSPICIOUS,
  HIGH_RISK
}

data class PhishingRiskAssessment(
  val url: String,
  val score: Int,
  val level: PhishingRiskLevel,
  val reasons: List<String>
)

/**
 * On-device heuristic phishing and scam URL detector.
 * Operates instantaneously locally without transmitting URLs to third parties.
 */
object PhishingScamDetector {

  private val targetBrands = listOf(
    "paypal", "google", "apple", "microsoft", "amazon", "netflix",
    "facebook", "instagram", "chase", "bankofamerica", "wellsfargo",
    "binance", "coinbase", "metamask", "whatsapp", "telegram"
  )

  private val suspiciousKeywords = listOf(
    "verify", "suspended", "account-alert", "security-update",
    "wallet-restore", "seed-phrase", "login-attempt", "billing-update",
    "urgent-action", "claim-reward", "airdrop"
  )

  private val highRiskTlds = setOf(
    "xyz", "top", "work", "click", "buzz", "surf", "cfd", "rest", "cam", "icu"
  )

  fun analyzeUrl(rawUrl: String): PhishingRiskAssessment {
    val cleanUrl = UrlValidator.sanitizeControlChars(rawUrl)
    if (cleanUrl.isBlank() || cleanUrl == "about:blank") {
      return PhishingRiskAssessment(rawUrl, 0, PhishingRiskLevel.LOW, emptyList())
    }

    val reasons = mutableListOf<String>()
    var riskScore = 0

    val uri = try { URI(cleanUrl) } catch (_: Throwable) { null }
    val host = uri?.host?.lowercase() ?: UrlValidator.extractHost(cleanUrl)
    val path = uri?.rawPath?.lowercase() ?: ""
    val query = uri?.rawQuery?.lowercase() ?: ""
    val fullTarget = "$path?$query"

    if (cleanUrl.startsWith("http://", ignoreCase = true) && !host.startsWith("localhost")) {
      riskScore += 25
      reasons.add("Unencrypted connection (HTTP)")
    }

    if (host.contains("xn--")) {
      riskScore += 40
      reasons.add("Internationalized homograph characters detected (Punycode)")
    }

    if (host.matches(Regex("""^(?:[0-9]{1,3}\.){3}[0-9]{1,3}$"""))) {
      riskScore += 35
      reasons.add("Direct numeric IP address host")
    }

    val segments = host.split('.')
    val primaryDomain = if (segments.size >= 2) "${segments[segments.size - 2]}.${segments.last()}" else host
    for (brand in targetBrands) {
      if (host.contains(brand) && !primaryDomain.startsWith("$brand.")) {
        riskScore += 50
        reasons.add("Brand impersonation detected: claims to be $brand on unverified domain")
        break
      }
    }

    if (segments.size > 4) {
      riskScore += 15
      reasons.add("Excessive subdomain depth (${segments.size} levels)")
    }

    val tld = segments.lastOrNull().orEmpty()
    val hasSuspiciousKeyword = suspiciousKeywords.any { fullTarget.contains(it) || host.contains(it) }
    if (tld in highRiskTlds && hasSuspiciousKeyword) {
      riskScore += 30
      reasons.add("Suspicious top-level domain (.$tld) paired with credential/alert keywords")
    } else if (hasSuspiciousKeyword) {
      riskScore += 15
      reasons.add("URL contains urgency or verification keywords")
    }

    val cappedScore = riskScore.coerceIn(0, 100)
    val level = when {
      cappedScore >= 60 -> PhishingRiskLevel.HIGH_RISK
      cappedScore >= 30 -> PhishingRiskLevel.SUSPICIOUS
      else -> PhishingRiskLevel.LOW
    }

    return PhishingRiskAssessment(
      url = rawUrl,
      score = cappedScore,
      level = level,
      reasons = reasons
    )
  }
}
