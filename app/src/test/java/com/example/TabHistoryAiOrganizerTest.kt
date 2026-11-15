package com.example

import com.example.ai.TabHistoryAiOrganizer
import com.example.model.HistoryItem
import com.example.model.TabInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TabHistoryAiOrganizerTest {

  @Test
  fun groupsTabsIntoSemanticCategories() {
    val tabs = listOf(
      TabInfo(id = "1", title = "GitHub - Android Repo", url = "https://github.com/android"),
      TabInfo(id = "2", title = "YouTube - Music Video", url = "https://youtube.com/watch?v=1"),
      TabInfo(id = "3", title = "Random Personal Blog", url = "https://myblog.example.com")
    )

    val grouped = TabHistoryAiOrganizer.groupTabs(tabs)
    assertTrue(grouped.containsKey("Development"))
    assertEquals("1", grouped["Development"]?.first()?.id)

    assertTrue(grouped.containsKey("Media & Audio"))
    assertEquals("2", grouped["Media & Audio"]?.first()?.id)

    assertTrue(grouped.containsKey("General"))
    assertEquals("3", grouped["General"]?.first()?.id)
  }

  @Test
  fun naturalLanguageHistorySearchMatchesIntent() {
    val history = listOf(
      HistoryItem(id = 1, title = "Jetpack Compose Tutorial", url = "https://developer.android.com"),
      HistoryItem(id = 2, title = "Lofi Hip Hop Stream", url = "https://youtube.com"),
      HistoryItem(id = 3, title = "Amazon Order Summary", url = "https://amazon.com/orders")
    )

    val searchMusic = TabHistoryAiOrganizer.naturalLanguageHistorySearch(history, "music streams")
    assertTrue(searchMusic.isNotEmpty())
    assertEquals(2L, searchMusic.first().id)

    val searchDev = TabHistoryAiOrganizer.naturalLanguageHistorySearch(history, "developer guide")
    assertTrue(searchDev.isNotEmpty())
    assertEquals(1L, searchDev.first().id)
  }
}
