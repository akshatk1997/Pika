package com.example

import com.example.ai.PhishingRiskLevel
import com.example.ai.PhishingScamDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhishingScamDetectorTest {

  @Test
  fun legitimateSiteReturnsLowRisk() {
    val result = PhishingScamDetector.analyzeUrl("https://en.wikipedia.org/wiki/Android")
    assertEquals(PhishingRiskLevel.LOW, result.level)
    assertTrue(result.score < 30)
  }

  @Test
  fun brandImpersonationReturnsHighRisk() {
    val result = PhishingScamDetector.analyzeUrl("https://paypal.com.security-alert.xyz/verify")
    assertEquals(PhishingRiskLevel.HIGH_RISK, result.level)
    assertTrue(result.reasons.any { it.contains("Brand impersonation") })
  }

  @Test
  fun ipAddressHostWithKeywordsReturnsHighRisk() {
    val result = PhishingScamDetector.analyzeUrl("http://192.168.1.50/wallet-restore")
    assertEquals(PhishingRiskLevel.HIGH_RISK, result.level)
    assertTrue(result.reasons.any { it.contains("Direct numeric IP") })
  }

  @Test
  fun punycodeHostReturnsElevatedRisk() {
    val result = PhishingScamDetector.analyzeUrl("https://xn--pple-43d.com/login")
    assertTrue(result.score >= 40)
    assertTrue(result.reasons.any { it.contains("Punycode") })
  }
}
