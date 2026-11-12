package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.LocalBrowserDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 36])
class ExampleRobolectricTest {

  @get:Rule
  val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun appNameMatchesPika() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Pika", appName)
  }

  @Test
  fun localDatabaseBookmarkAndHistoryLifecycle() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = LocalBrowserDatabase(context)
    val id = db.upsertBookmark("Example", "https://example.com", "Docs")
    assertTrue(id > 0)
    assertTrue(db.isBookmarked("https://example.com"))
    assertEquals(1, db.getBookmarks("Docs").size)

    db.recordHistory("Example Page", "https://example.com/page")
    assertEquals(1, db.getHistory("Example").size)
    db.clearAllHistory()
    assertTrue(db.getHistory().isEmpty())

    db.deleteBookmarkById(id)
    assertFalse(db.isBookmarked("https://example.com"))
    db.close()
  }

  @Test
  fun fullMainActivityStartPageTabsAndQuickMenuNavigation() {
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Pika").assertIsDisplayed()
    composeTestRule.onNodeWithTag("home_search_input").performTextInput("privacy")
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("home_search_go").assertIsDisplayed()

    // Open Tab Switcher and create a Private Tab
    composeTestRule.onNodeWithTag("nav_tabs_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("tabs_filter_private").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("tabs_new_button").performClick()
    composeTestRule.waitForIdle()

    // Verify Private Mode UI badge and banner
    composeTestRule.onNodeWithText("Private").assertIsDisplayed()
    composeTestRule.onNodeWithText("Ephemeral session", substring = true).assertIsDisplayed()

    // Open Security Site Info dialog from omnibox security badge
    composeTestRule.onNodeWithTag("security_badge_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Private tab:", substring = true).assertIsDisplayed()
    composeTestRule.onNodeWithText("Done").performClick()
    composeTestRule.waitForIdle()

    // Open Quick Action Grid Menu and navigate to Settings
    composeTestRule.onNodeWithTag("nav_menu_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Night Web").assertIsDisplayed()
    composeTestRule.onNodeWithText("No Images").assertIsDisplayed()
    composeTestRule.onNodeWithText("Settings").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Pika Privacy Core").assertIsDisplayed()
  }

  @Test
  @Config(sdk = [26, 36], qualifiers = "sw600dp-w1024dp-h768dp-xhdpi")
  fun tabletNavigationRailAndAdaptiveTabsWorkAcrossAndroidVersions() {
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Pika").assertIsDisplayed()
    // Tablet layout renders persistent NavigationRail shortcuts and single-row top toolbar
    composeTestRule.onNodeWithText("Bookmarks").assertIsDisplayed()
    composeTestRule.onNodeWithText("Downloads").assertIsDisplayed()
    composeTestRule.onNodeWithText("Settings").assertIsDisplayed()

    composeTestRule.onNodeWithTag("nav_tabs_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("tabs_new_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("HTTPS-Only", substring = true).assertIsDisplayed()
  }
}
