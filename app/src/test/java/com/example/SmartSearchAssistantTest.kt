package com.example

import com.example.ai.SmartSearchAssistant
import com.example.ai.SuggestionKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartSearchAssistantTest {

  @Test
  fun returnsSparkSuggestionsWhenQueryIsEmpty() {
    val sparks = SmartSearchAssistant.getSuggestions("")
    assertTrue(sparks.isNotEmpty())
    assertTrue(sparks.all { it.iconKind == SuggestionKind.SPARK })
  }

  @Test
  fun generatesIntentBasedExpansionsForTopicQueries() {
    val suggestions = SmartSearchAssistant.getSuggestions("artificial intelligence")
    assertTrue(suggestions.any { it.iconKind == SuggestionKind.EXPLAIN })
    assertTrue(suggestions.any { it.iconKind == SuggestionKind.SUMMARY })
    assertTrue(suggestions.any { it.iconKind == SuggestionKind.COMPARE })
  }

  @Test
  fun providesNavigationForUrls() {
    val suggestions = SmartSearchAssistant.getSuggestions("https://github.com")
    assertTrue(suggestions.any { it.iconKind == SuggestionKind.NAVIGATE })
  }
}
