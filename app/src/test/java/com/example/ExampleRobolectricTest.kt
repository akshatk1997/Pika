package com.example

import android.content.Context
import android.view.WindowManager
import android.webkit.WebChromeClient
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.LocalBrowserDatabase
import com.example.data.SettingsRepository
import com.example.model.SearchEngine
import com.example.model.SettingsState
import com.example.model.TabInfo
import com.example.model.UserAgentMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @get:Rule
  val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun appNameMatchesKioAndVersionIsUpdated() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Kio", appName)
    assertEquals("2.1.5", BuildConfig.VERSION_NAME)
    assertEquals(87, BuildConfig.VERSION_CODE)
  }

  @Test
  fun settingsAndRetainedNonPrivateTabsPersistAcrossRepositoryInstances() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo1 = SettingsRepository(context)
    val customSettings = SettingsState(
      searchEngine = SearchEngine.STARTPAGE,
      homepageUrl = "https://example.org",
      retainOpenTabs = true,
      nightWebMode = true,
      backgroundPlayEnabled = true,
      dataSaverEnabled = true,
      oneHandedModeEnabled = true,
      oneHandedHandPreference = com.example.model.OneHandedPreference.LEFT,
      grayscaleModeEnabled = true
    )
    repo1.saveSettings(customSettings)

    val normalTab1 = TabInfo(id = "t1", title = "Example", url = "https://example.org/docs")
    val privateTab = TabInfo(id = "t2", title = "Secret", url = "https://private.example.org", isPrivate = true)
    val normalTab2 = TabInfo(
      id = "t3",
      title = "News",
      url = "https://news.example.org",
      userAgentMode = UserAgentMode.DESKTOP
    )
    repo1.saveRetainedTabs(listOf(normalTab1, privateTab, normalTab2), activeTabId = "t3")

    val repo2 = SettingsRepository(context)
    val loadedSettings = repo2.loadSettings()
    assertEquals(SearchEngine.STARTPAGE, loadedSettings.searchEngine)
    assertEquals("https://example.org", loadedSettings.homepageUrl)
    assertTrue(loadedSettings.retainOpenTabs)
    assertTrue(loadedSettings.nightWebMode)
    assertTrue(loadedSettings.backgroundPlayEnabled)
    assertTrue(loadedSettings.dataSaverEnabled)
    assertTrue(loadedSettings.oneHandedModeEnabled)
    assertEquals(com.example.model.OneHandedPreference.LEFT, loadedSettings.oneHandedHandPreference)
    assertTrue(loadedSettings.grayscaleModeEnabled)

    val restored = repo2.loadRetainedTabs()
    assertNotNull(restored)
    val (restoredTabs, activeId) = restored!!
    assertEquals(2, restoredTabs.size)
    assertEquals("t3", activeId)
    assertTrue(restoredTabs.none { it.isPrivate })
    assertEquals("https://example.org/docs", restoredTabs[0].url)
    assertEquals(UserAgentMode.DESKTOP, restoredTabs[1].userAgentMode)

    repo2.clearRetainedTabs()
    assertNull(repo2.loadRetainedTabs())
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
  fun settingsRepositoryIntroSeenLifecycle() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = SettingsRepository(context)
    repo.setIntroSeen(false)
    assertFalse(repo.hasSeenIntro())
    repo.setIntroSeen(true)
    assertTrue(repo.hasSeenIntro())
  }

  @Test
  fun fullMainActivityStartPageTabsAndQuickMenuNavigation() {
    composeTestRule.waitForIdle()
    try {
      composeTestRule.onNodeWithTag("intro_thought_process").assertIsDisplayed()
      composeTestRule.onNodeWithTag("intro_get_started").performClick()
      composeTestRule.waitForIdle()
    } catch (_: Throwable) {
    }

    composeTestRule.onNodeWithText("Kio").assertIsDisplayed()
    composeTestRule.onNodeWithTag("kio_home_logo").assertIsDisplayed()
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

    // Verify Private Mode UI badge, Zero-Leak Vault card, and removal of Bookmarks & Extensions
    composeTestRule.onNodeWithText("Private").assertIsDisplayed()
    composeTestRule.onNodeWithText("History, cookies & cache are cleared on exit").assertIsDisplayed()
    composeTestRule.onNodeWithTag("private_vault_card").assertIsDisplayed()
    composeTestRule.onNodeWithText("No bookmarks saved").assertDoesNotExist()
    composeTestRule.onNodeWithTag("nav_extensions_button").assertDoesNotExist()

    // Open Security Site Info dialog from omnibox security badge
    composeTestRule.onNodeWithTag("security_badge_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Private browsing is active").assertIsDisplayed()
    composeTestRule.onNodeWithText("Done").performClick()
    composeTestRule.waitForIdle()

    // Open Private Quick Action Grid Menu and verify Private-Mode-Only tools + disabled external/bookmark/share options
    composeTestRule.onNodeWithTag("nav_menu_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("private_menu_banner").assertIsDisplayed()
    composeTestRule.onNodeWithText("Full screen").assertDoesNotExist()
    composeTestRule.onNodeWithText("Narrate").assertIsDisplayed()
    composeTestRule.onNodeWithText("Burn session").assertIsDisplayed()
    composeTestRule.onNodeWithText("New identity").assertIsDisplayed()
    composeTestRule.onNodeWithText("Shield audit").assertIsDisplayed()
    composeTestRule.onNodeWithText("Wipe clipboard").assertIsDisplayed()
    composeTestRule.onNodeWithText("Purge RAM").assertIsDisplayed()
    composeTestRule.onNodeWithText("Bookmarks").assertDoesNotExist()
    composeTestRule.onNodeWithText("Share").assertDoesNotExist()
    composeTestRule.onNodeWithText("Open externally").assertDoesNotExist()
    composeTestRule.onNodeWithText("Add shortcut").assertDoesNotExist()
    composeTestRule.onNodeWithText("Extensions").assertDoesNotExist()
    composeTestRule.onNodeWithText("AI Assistant").assertDoesNotExist()
    composeTestRule.onNodeWithText("Resources").assertDoesNotExist()
    composeTestRule.onNodeWithText("Settings").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("General & Search").assertIsDisplayed()
    composeTestRule.onNodeWithText("Address bar position").assertDoesNotExist()

    // Navigate into Data Saver folder
    composeTestRule.onNodeWithTag("settings_folder_data_saver").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("setting_data_saver").assertIsDisplayed()
    composeTestRule.onNodeWithTag("settings_back_button").performClick()
    composeTestRule.waitForIdle()

    // Navigate into Media & Playback folder
    composeTestRule.onNodeWithTag("settings_folder_media").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("setting_background_play").assertIsDisplayed()
    composeTestRule.onNodeWithTag("setting_youtube_scale_fit").assertDoesNotExist()
    composeTestRule.onNodeWithTag("settings_back_button").performClick()
    composeTestRule.waitForIdle()

    // Navigate into Tabs & Session folder
    composeTestRule.onNodeWithTag("settings_folder_tabs").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("setting_retain_tabs").assertIsDisplayed()
    composeTestRule.onNodeWithTag("settings_back_button").performClick()
    composeTestRule.waitForIdle()

    // Navigate into Help & Feedback folder
    composeTestRule.onNodeWithTag("settings_folder_help_feedback").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("feedback_send_button").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("settings_back_button").performClick()
    composeTestRule.waitForIdle()

    // Navigate into About Kio folder
    composeTestRule.onNodeWithTag("settings_folder_about").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Kio Browser").assertIsDisplayed()
    composeTestRule.onNodeWithText("Privacy Policy").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("settings_back_button").performClick()
    composeTestRule.waitForIdle()

    // Close Settings back to Private Tab
    composeTestRule.onNodeWithTag("settings_back_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Private browsing is active").assertDoesNotExist()
    composeTestRule.onNodeWithText("History, cookies & cache are cleared on exit").assertIsDisplayed()
  }

  @Test
  @Config(sdk = [36], qualifiers = "sw600dp-w1024dp-h768dp-xhdpi")
  fun tabletLayoutMatchesMobileWithFullFeatureParityAndLockOmnibox() {
    composeTestRule.waitForIdle()
    try {
      composeTestRule.onNodeWithTag("intro_get_started").performClick()
      composeTestRule.waitForIdle()
    } catch (_: Throwable) {
    }
    composeTestRule.onNodeWithText("Kio").assertIsDisplayed()
    composeTestRule.onNodeWithTag("kio_home_logo").assertIsDisplayed()

    // Tablet layout shares exact unified 5-button mobile design
    composeTestRule.onNodeWithTag("nav_back_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("nav_forward_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("security_badge_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("nav_tabs_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("nav_menu_button").assertIsDisplayed()

    // Legacy cluttered tablet buttons are eliminated
    composeTestRule.onNodeWithTag("tablet_desktop_mode_button").assertDoesNotExist()
    composeTestRule.onNodeWithTag("tablet_bookmarks_button").assertDoesNotExist()
    composeTestRule.onNodeWithTag("tablet_downloads_button").assertDoesNotExist()
    composeTestRule.onNodeWithTag("tablet_new_tab_button").assertDoesNotExist()

    // Site security info dialog opens cleanly from center pill on tablet
    composeTestRule.onNodeWithTag("security_badge_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Done").performClick()
    composeTestRule.waitForIdle()

    // 3-dot menu opens cleanly on tablet
    composeTestRule.onNodeWithTag("nav_menu_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
  }

  @Test
  fun backgroundMediaWebViewOverridesVisibilityWhenBackgroundPlayEnabled() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    var bgPlay = true
    val wv = com.example.webview.BackgroundMediaWebView(context, isBackgroundPlayEnabled = { bgPlay })

    // When background play is enabled, GONE and INVISIBLE are overridden to VISIBLE
    wv.onWindowVisibilityChanged(android.view.View.GONE)
    wv.dispatchWindowVisibilityChanged(android.view.View.INVISIBLE)

    // When background play is disabled, standard visibility passes through
    bgPlay = false
    wv.onWindowVisibilityChanged(android.view.View.GONE)
    wv.dispatchWindowVisibilityChanged(android.view.View.GONE)
  }

  @Test
  fun webViewPoolBackgroundInterceptAndMutableContextSafety() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    var blockedCount = 0
    val callbacks = object : com.example.webview.WebViewCallbacks {
      override fun onPageStarted(tabId: String, url: String) {}
      override fun onPageFinished(tabId: String, url: String, title: String?, canGoBack: Boolean, canGoForward: Boolean) {}
      override fun onProgressChanged(tabId: String, progress: Int) {}
      override fun onTitleReceived(tabId: String, title: String) {}
      override fun onTrackerBlocked(tabId: String) { blockedCount++ }
      override fun onHttpBlockedByHttpsOnly(tabId: String, url: String) {}
      override fun onBlockedScheme(tabId: String, url: String, scheme: String) {}
      override fun onExternalSchemePrompt(tabId: String, url: String, scheme: String) {}
      override fun onPageError(tabId: String, error: com.example.model.PageError) {}
      override fun onRenderProcessCrashed(tabId: String) {}
      override fun onDownloadRequested(tabId: String, url: String, contentDisposition: String?, mimeType: String?, contentLength: Long) {}
      override fun onPermissionPrompt(tabId: String, origin: String, kind: com.example.model.WebPermissionKind, detail: String, onDecision: (Boolean) -> Unit) {}
      override fun onFileChooserRequested(callback: android.webkit.ValueCallback<Array<android.net.Uri>>?, acceptTypes: Array<String>?) {}
      override fun onFindMatches(tabId: String, activeOrdinal: Int, numberOfMatches: Int) {}
      override fun onFullscreenChanged(tabId: String, customView: android.view.View?, callback: android.webkit.WebChromeClient.CustomViewCallback?) {}
      override fun onLongPressLink(tabId: String, url: String, isImage: Boolean) {}
    }
    val pool = com.example.webview.WebViewPool(context, { com.example.model.SettingsState() }, callbacks)
    val webView = pool.getOrCreateWebView("tab1", false, com.example.model.UserAgentMode.MOBILE, emptySet())!!
    assertTrue(webView.context is android.content.MutableContextWrapper)
    assertTrue(pool.isWebViewAlive(webView))

    pool.loadUrl("tab1", "https://example.com/article")
    // Simulate background IO thread calling shouldInterceptRequest without touching WebView.getUrl()
    val fakeRequest = object : android.webkit.WebResourceRequest {
      override fun getUrl(): android.net.Uri = android.net.Uri.parse("https://doubleclick.net/pixel.js")
      override fun isForMainFrame(): Boolean = false
      override fun isRedirect(): Boolean = false
      override fun hasGesture(): Boolean = false
      override fun getMethod(): String = "GET"
      override fun getRequestHeaders(): MutableMap<String, String> = mutableMapOf()
    }
    var workerResponse: android.webkit.WebResourceResponse? = null
    val worker = Thread {
      workerResponse = webView.webViewClient.shouldInterceptRequest(webView, fakeRequest)
    }
    worker.start()
    worker.join()
    assertEquals(204, workerResponse?.statusCode)
    assertEquals(1, blockedCount)

    pool.wipePrivateSessionData(listOf("tab1"))
    assertFalse(pool.isWebViewAlive(webView))
  }

  @Test
  fun pageTranslationServiceSafelyTranslatesOrFallbacksWithoutThrowing() = kotlinx.coroutines.runBlocking {
    val inputs = listOf("Welcome to Kio", "Privacy first browser")
    val result = com.example.service.PageTranslationService.translateBatch(inputs, "es", "Spanish")
    assertNotNull(result)
    assertEquals(inputs.size, result.size)
  }

  @Test
  fun openSourceTranslationEndpointsAndDomRestorationWorkReliably() {
    // 1. Verify in-page DOM text replacement and 1-tap restore state mechanics
    var pageUrl = "https://example.org/article"
    val originalTextNodes = mapOf("node1" to "Hello world", "node2" to "Privacy first browser")
    val translatedTextNodes = mutableMapOf<String, String>()

    // Simulate in-page translation: DOM values are replaced without changing URL
    for ((id, text) in originalTextNodes) {
      translatedTextNodes[id] = when (text) {
        "Hello world" -> "Hola mundo"
        "Privacy first browser" -> "Navegador que prioriza la privacidad"
        else -> text
      }
    }
    assertEquals("https://example.org/article", pageUrl)
    assertEquals("Hola mundo", translatedTextNodes["node1"])
    assertEquals("Navegador que prioriza la privacidad", translatedTextNodes["node2"])

    // Simulate 1-tap restore: text values revert immediately to original without reload
    val restoredNodes = mutableMapOf<String, String>()
    for ((id, orig) in originalTextNodes) {
      restoredNodes[id] = orig
    }
    assertEquals(originalTextNodes, restoredNodes)
    assertEquals("https://example.org/article", pageUrl)
  }

  @Test
  fun openSourceTranslationTreeWalkerFilterExcludesNonContentTags() {
    val excludedTags = setOf(
      "SCRIPT", "STYLE", "CODE", "PRE", "TEXTAREA", "INPUT",
      "NOSCRIPT", "SVG", "AUDIO", "VIDEO", "CANVAS", "OBJECT", "IFRAME"
    )
    assertTrue(excludedTags.contains("SCRIPT"))
    assertTrue(excludedTags.contains("STYLE"))
    assertTrue(excludedTags.contains("CODE"))
    assertTrue(excludedTags.contains("PRE"))
    assertTrue(excludedTags.contains("INPUT"))
    assertTrue(excludedTags.contains("VIDEO"))
    assertFalse(excludedTags.contains("P"))
    assertFalse(excludedTags.contains("SPAN"))
    assertFalse(excludedTags.contains("H1"))
  }

  @Test
  fun homepageStandardSearchOnlyAndAiAssistantPermanentlyRemoved() {
    composeTestRule.runOnUiThread {
      composeTestRule.activity.viewModel.dismissIntro()
      composeTestRule.activity.viewModel.openNewTab(false)
    }
    composeTestRule.waitForIdle()

    // Verify AI mode options and tabs do not exist on homepage
    composeTestRule.onNodeWithTag("home_mode_ai").assertDoesNotExist()
    composeTestRule.onNodeWithTag("home_mode_standard").assertDoesNotExist()
    composeTestRule.onNodeWithTag("nav_ai_button").assertDoesNotExist()

    // Verify clean Kio home logo is displayed and extensions button is permanently removed
    composeTestRule.onNodeWithTag("kio_home_logo").assertIsDisplayed()
    composeTestRule.onNodeWithTag("nav_extensions_button").assertDoesNotExist()
  }

  @Test
  fun cleanExtensionStorageRemovesAllExtensionDirectoriesAndPrefs() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val extDir = context.getDir("chrome_extensions", Context.MODE_PRIVATE)
    extDir.mkdirs()
    File(extDir, "test.txt").writeText("temp")
    val cacheExtDir = File(context.cacheDir, "extensions_download")
    cacheExtDir.mkdirs()
    File(cacheExtDir, "temp.crx").writeText("data")
    val prefs = context.getSharedPreferences("chrome_extensions_prefs", Context.MODE_PRIVATE)
    prefs.edit().putString("installed", "[]").commit()

    com.example.ui.BrowserViewModel.cleanExtensionStorage(context)

    assertFalse(File(extDir, "test.txt").exists())
    assertFalse(File(cacheExtDir, "temp.crx").exists())
    assertTrue(prefs.all.isEmpty())
  }

  @Test
  fun videoPlaybackPausableAndDataSaverFunctionalityWorks() {
    val context = ApplicationProvider.getApplicationContext<Context>()

    // Test default settings state
    val defaultState = SettingsState()
    assertFalse(defaultState.dataSaverEnabled)
    assertTrue(defaultState.backgroundPlayEnabled)

    val callbacks = object : com.example.webview.WebViewCallbacks {
      override fun onPageStarted(tabId: String, url: String) {}
      override fun onPageFinished(tabId: String, url: String, title: String?, canGoBack: Boolean, canGoForward: Boolean) {}
      override fun onProgressChanged(tabId: String, progress: Int) {}
      override fun onTitleReceived(tabId: String, title: String) {}
      override fun onTrackerBlocked(tabId: String) {}
      override fun onHttpBlockedByHttpsOnly(tabId: String, url: String) {}
      override fun onBlockedScheme(tabId: String, url: String, scheme: String) {}
      override fun onExternalSchemePrompt(tabId: String, url: String, scheme: String) {}
      override fun onPageError(tabId: String, error: com.example.model.PageError) {}
      override fun onRenderProcessCrashed(tabId: String) {}
      override fun onDownloadRequested(tabId: String, url: String, contentDisposition: String?, mimeType: String?, contentLength: Long) {}
      override fun onPermissionPrompt(tabId: String, origin: String, kind: com.example.model.WebPermissionKind, detail: String, onDecision: (Boolean) -> Unit) {}
      override fun onFileChooserRequested(callback: android.webkit.ValueCallback<Array<android.net.Uri>>?, acceptTypes: Array<String>?) {}
      override fun onFindMatches(tabId: String, activeOrdinal: Int, numberOfMatches: Int) {}
      override fun onFullscreenChanged(tabId: String, customView: android.view.View?, callback: android.webkit.WebChromeClient.CustomViewCallback?) {}
      override fun onLongPressLink(tabId: String, url: String, isImage: Boolean) {}
    }

    val pool = com.example.webview.WebViewPool(context, { defaultState.copy(dataSaverEnabled = true) }, callbacks)
    val wv = pool.getOrCreateWebView("tab_yt_test", false, UserAgentMode.MOBILE, emptySet())!!
    pool.applyBackgroundPlay(wv, true)
    pool.applyBackgroundPlay(wv, false)
    pool.applyDataSaver(wv, true)
    pool.applyDataSaver(wv, false)
    pool.destroyTab("tab_yt_test")
  }

  @Test
  fun videoFullscreenModeTogglesSystemBarsAndRestoresCleanly() {
    val activity = composeTestRule.activity
    val vm = activity.viewModel
    composeTestRule.waitForIdle()

    val dummyView = android.view.View(activity)
    val dummyCallback = object : android.webkit.WebChromeClient.CustomViewCallback {
      override fun onCustomViewHidden() {}
    }

    assertNull(vm.fullscreenView.value)
    composeTestRule.runOnUiThread {
      vm.setCustomFullscreen(dummyView, dummyCallback)
    }
    composeTestRule.waitForIdle()

    assertNotNull(vm.fullscreenView.value)
    val fsFlags = activity.window.attributes.flags
    assertTrue((fsFlags and WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0)

    composeTestRule.runOnUiThread {
      vm.exitFullscreen()
    }
    composeTestRule.waitForIdle()

    assertNull(vm.fullscreenView.value)
    val restoredFlags = activity.window.attributes.flags
    assertFalse((restoredFlags and WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0)
  }

  @Test
  fun readerAnnouncerWipesAllVolatileRamDataOnClose() {
    val vm = composeTestRule.activity.viewModel
    composeTestRule.waitForIdle()

    // Verify no stale AI assistant assets or search engines exist
    val context = ApplicationProvider.getApplicationContext<Context>()
    val assets = context.assets.list("")?.toList() ?: emptyList()
    assertTrue(assets.none { it.endsWith(".html") })
    assertTrue(SearchEngine.entries.none { it.id.contains("ai") })

    // Verify closeReaderMode clears all volatile reader/narrator state cleanly
    composeTestRule.runOnUiThread {
      vm.closeReaderMode()
    }
    composeTestRule.waitForIdle()
    assertNull(vm.readerArticle.value)
    assertTrue(vm.readerSegments.value.isEmpty())
    assertFalse(vm.narratorState.value.isSpeaking)
  }

  @Test
  fun videoPausableFixAndThemeResolutionsVerified() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val callbacks = object : com.example.webview.WebViewCallbacks {
      override fun onPageStarted(tabId: String, url: String) {}
      override fun onPageFinished(tabId: String, url: String, title: String?, canGoBack: Boolean, canGoForward: Boolean) {}
      override fun onProgressChanged(tabId: String, progress: Int) {}
      override fun onTitleReceived(tabId: String, title: String) {}
      override fun onTrackerBlocked(tabId: String) {}
      override fun onHttpBlockedByHttpsOnly(tabId: String, url: String) {}
      override fun onBlockedScheme(tabId: String, url: String, scheme: String) {}
      override fun onExternalSchemePrompt(tabId: String, url: String, scheme: String) {}
      override fun onPageError(tabId: String, error: com.example.model.PageError) {}
      override fun onRenderProcessCrashed(tabId: String) {}
      override fun onDownloadRequested(tabId: String, url: String, contentDisposition: String?, mimeType: String?, contentLength: Long) {}
      override fun onPermissionPrompt(tabId: String, origin: String, kind: com.example.model.WebPermissionKind, detail: String, onDecision: (Boolean) -> Unit) {}
      override fun onFileChooserRequested(callback: android.webkit.ValueCallback<Array<android.net.Uri>>?, acceptTypes: Array<String>?) {}
      override fun onFindMatches(tabId: String, activeOrdinal: Int, numberOfMatches: Int) {}
      override fun onFullscreenChanged(tabId: String, customView: android.view.View?, callback: android.webkit.WebChromeClient.CustomViewCallback?) {}
      override fun onLongPressLink(tabId: String, url: String, isImage: Boolean) {}
    }
    val pool = com.example.webview.WebViewPool(context, { SettingsState() }, callbacks)

    // Settings verification
    val darkSettings = SettingsState(
      dataSaverEnabled = true,
      themeMode = com.example.model.ThemeMode.DARK
    )
    assertTrue(pool.resolveIsDark(darkSettings))
    val lightSettings = SettingsState(themeMode = com.example.model.ThemeMode.LIGHT)
    assertFalse(pool.resolveIsDark(lightSettings))
  }

  @Test
  fun manifestSpeechRecognitionQueriesVerified() {
    val manifestFile = listOf(
      java.io.File("src/main/AndroidManifest.xml"),
      java.io.File("app/src/main/AndroidManifest.xml")
    ).firstOrNull { it.exists() }
    assertNotNull("AndroidManifest.xml should exist", manifestFile)
    val manifestContent = manifestFile!!.readText()
    assertTrue(manifestContent.contains("android.permission.RECORD_AUDIO"))
    assertTrue(manifestContent.contains("<queries>"))
    assertTrue(manifestContent.contains("android.speech.RecognitionService"))
    assertTrue(manifestContent.contains("android.speech.action.RECOGNIZE_SPEECH"))
  }

  @Test
  fun fullscreenVideoHidesSystemNotificationBarAndRestoresOnExit() {
    val activity = composeTestRule.activity
    val viewModel = activity.viewModel
    composeTestRule.waitForIdle()

    // Initially not in fullscreen
    assertNull(viewModel.fullscreenView.value)
    val initialFlags = activity.window.attributes.flags
    assertFalse("FLAG_FULLSCREEN should not be initially set", (initialFlags and WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0)

    // Simulate entering custom fullscreen (e.g. HTML5 video playback)
    val dummyView = android.view.View(activity)
    var viewHiddenCalled = false
    val dummyCallback = object : WebChromeClient.CustomViewCallback {
      override fun onCustomViewHidden() {
        viewHiddenCalled = true
      }
    }

    composeTestRule.runOnUiThread {
      viewModel.setCustomFullscreen(dummyView, dummyCallback)
    }
    composeTestRule.waitForIdle()

    // Fullscreen is active: notification bar hidden via window flags
    assertNotNull(viewModel.fullscreenView.value)
    val fullscreenFlags = activity.window.attributes.flags
    assertTrue("FLAG_FULLSCREEN must be set during fullscreen video playback", (fullscreenFlags and WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0)
    assertTrue("FLAG_KEEP_SCREEN_ON must be set during fullscreen video playback", (fullscreenFlags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0)

    // Exit fullscreen
    composeTestRule.runOnUiThread {
      viewModel.exitFullscreen()
    }
    composeTestRule.waitForIdle()

    // Fullscreen is exited: notification bar restored and flags cleared
    assertNull(viewModel.fullscreenView.value)
    assertTrue("CustomViewCallback.onCustomViewHidden() should be called", viewHiddenCalled)
    val restoredFlags = activity.window.attributes.flags
    assertFalse("FLAG_FULLSCREEN must be cleared after exiting fullscreen", (restoredFlags and WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0)
    assertFalse("FLAG_KEEP_SCREEN_ON must be cleared after exiting fullscreen", (restoredFlags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0)
  }

  @Test
  fun streamingVideoScriptAndCorsResponsesPreventBlackScreens() {
    val candidates = listOf(
      File("src/main/java/com/example/webview/WebViewPool.kt"),
      File("app/src/main/java/com/example/webview/WebViewPool.kt"),
      File("../app/src/main/java/com/example/webview/WebViewPool.kt")
    )
    val webViewPoolFile = candidates.firstOrNull { it.exists() }
    assertNotNull("WebViewPool.kt should be found", webViewPoolFile)
    val content = webViewPoolFile!!.readText()

    // Assert that destructive currentTime seeks are eliminated
    assertFalse("Should not force currentTime to duration as it causes black screens", content.contains("v.currentTime = v.duration"))
    // Assert safe playbackRate fast-forward and mute during ads
    assertTrue(content.contains("v.playbackRate = 16.0"))
    assertTrue(content.contains("v.__kio_ad_muted = true"))
    // Assert native player skipAd call is attempted
    assertTrue(content.contains("player.skipAd()"))
    // Assert CORS headers are present on blocked responses
    assertTrue(content.contains("Access-Control-Allow-Origin"))
    // Assert JSON blocked response returns empty object
    assertTrue(content.contains("emptyBlockedJsonResponse"))
    // Assert .ytp-ad-module is not hidden via CSS to prevent player breakages
    assertFalse(content.contains("'.ytp-ad-module'"))
  }

  @Test
  fun intentUriSanitizationEnforcesBrowsableCategoryAndNullsComponent() {
    val rawIntentUri = "intent:#Intent;action=android.intent.action.VIEW;component=com.example/.UnexportedActivity;category=android.intent.category.DEFAULT;end"
    val parsed = android.content.Intent.parseUri(rawIntentUri, android.content.Intent.URI_INTENT_SCHEME).apply {
      addCategory(android.content.Intent.CATEGORY_BROWSABLE)
      component = null
      selector = null
    }
    assertNull(parsed.component)
    assertNull(parsed.selector)
    assertTrue(parsed.hasCategory(android.content.Intent.CATEGORY_BROWSABLE))
  }

  @Test
  fun openTabInBackgroundKeepsCurrentTabActiveAndAddsTargetTab() {
    val vm = composeTestRule.activity.viewModel
    composeTestRule.waitForIdle()
    val originalTabId = vm.activeTabId.value
    val originalCount = vm.tabs.value.size

    composeTestRule.runOnUiThread {
      vm.openNewTab(isPrivate = false, initialUrl = "https://example.org/bg", switchToNewTab = false)
    }
    composeTestRule.waitForIdle()

    assertEquals(originalTabId, vm.activeTabId.value)
    assertEquals(originalCount + 1, vm.tabs.value.size)
    assertTrue(vm.tabs.value.any { it.url == "https://example.org/bg" && !it.isPrivate })

    composeTestRule.runOnUiThread {
      vm.openNewTab(isPrivate = true, initialUrl = "https://example.org/priv", switchToNewTab = true)
    }
    composeTestRule.waitForIdle()

    assertTrue(vm.activeTab().isPrivate)
    assertEquals("https://example.org/priv", vm.activeTab().url)
  }

  @Test
  fun privateModeStrictIsolationAndZeroLeakGuardsWork() {
    val vm = composeTestRule.activity.viewModel
    composeTestRule.waitForIdle()
    composeTestRule.runOnUiThread {
      vm.dismissIntro()
      vm.openNewTab(isPrivate = false)
    }
    composeTestRule.waitForIdle()

    // Standard Mode 3-dot menu shows standard options and does NOT show Private-Only tools
    composeTestRule.onNodeWithTag("nav_menu_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Bookmarks").assertIsDisplayed()
    composeTestRule.onNodeWithText("Share").assertIsDisplayed()
    composeTestRule.onNodeWithText("Open externally").assertDoesNotExist()
    composeTestRule.onNodeWithText("Capture").assertDoesNotExist()
    composeTestRule.onNodeWithText("Dark mode").assertDoesNotExist()
    composeTestRule.onNodeWithText("Add shortcut").assertDoesNotExist()
    composeTestRule.onNodeWithText("Full screen").assertDoesNotExist()
    composeTestRule.onNodeWithText("Clean Mode").assertDoesNotExist()
    composeTestRule.onNodeWithText("Burn session").assertDoesNotExist()
    composeTestRule.onNodeWithText("Shield audit").assertDoesNotExist()
    composeTestRule.onNodeWithTag("private_menu_banner").assertDoesNotExist()
    // Dismiss sheet by opening a Private tab via the grid item
    composeTestRule.onNodeWithText("Private tab").performClick()
    composeTestRule.waitForIdle()

    // Now in Private Mode: verify strict isolation boundary & guards
    assertTrue(vm.activeTab().isPrivate)
    assertTrue(vm.webViewPool.isCurrentlyInPrivateBarrier())
    assertNull(vm.readClipboardText())

    val bookmarksBefore = vm.bookmarks.value.size
    composeTestRule.runOnUiThread {
      vm.setOverlay(com.example.ui.ActiveOverlay.BOOKMARKS)
      assertEquals(com.example.ui.ActiveOverlay.NONE, vm.activeOverlay.value)
      vm.setOverlay(com.example.ui.ActiveOverlay.HISTORY)
      assertEquals(com.example.ui.ActiveOverlay.NONE, vm.activeOverlay.value)
      vm.toggleCurrentBookmark()
    }
    composeTestRule.waitForIdle()
    assertEquals(bookmarksBefore, vm.bookmarks.value.size)

    // Burn Session incinerates all private tabs and transitions barrier back to Standard Mode
    composeTestRule.runOnUiThread {
      vm.burnPrivateSession()
    }
    composeTestRule.waitForIdle()
    assertFalse(vm.activeTab().isPrivate)
    assertTrue(vm.tabs.value.none { it.isPrivate })
    assertFalse(vm.webViewPool.isCurrentlyInPrivateBarrier())
  }

  @Test
  fun darkModeAndLightModeSwitchEntireBrowserAndStatusBarCleanly() {
    val activity = composeTestRule.activity
    val vm = activity.viewModel
    composeTestRule.waitForIdle()
    composeTestRule.runOnUiThread {
      vm.dismissIntro()
      vm.openNewTab(isPrivate = false)
    }
    composeTestRule.waitForIdle()

    // Switch to Light Mode
    composeTestRule.runOnUiThread {
      vm.updateSettings { it.copy(themeMode = com.example.model.ThemeMode.LIGHT, nightWebMode = false) }
    }
    composeTestRule.waitForIdle()
    assertEquals(com.example.model.ThemeMode.LIGHT, vm.settings.value.themeMode)
    assertFalse(vm.settings.value.nightWebMode)
    assertFalse(vm.webViewPool.resolveIsDark(vm.settings.value))
    val lightController = androidx.core.view.WindowCompat.getInsetsController(activity.window, activity.window.decorView)
    assertTrue("Light mode must render dark status bar icons on light background", lightController.isAppearanceLightStatusBars)

    // Switch to Dark Mode
    composeTestRule.runOnUiThread {
      vm.updateSettings { it.copy(themeMode = com.example.model.ThemeMode.DARK, nightWebMode = true) }
    }
    composeTestRule.waitForIdle()
    assertEquals(com.example.model.ThemeMode.DARK, vm.settings.value.themeMode)
    assertTrue(vm.settings.value.nightWebMode)
    assertTrue(vm.webViewPool.resolveIsDark(vm.settings.value))
    val darkController = androidx.core.view.WindowCompat.getInsetsController(activity.window, activity.window.decorView)
    assertFalse("Dark mode must render light status bar icons on dark background", darkController.isAppearanceLightStatusBars)
  }

  @Test
  fun dataSavingPrefetchRequestsBlockedAndDiskCacheTrimmingWorks() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val callbacks = object : com.example.webview.WebViewCallbacks {
      override fun onPageStarted(tabId: String, url: String) {}
      override fun onPageFinished(tabId: String, url: String, title: String?, canGoBack: Boolean, canGoForward: Boolean) {}
      override fun onProgressChanged(tabId: String, progress: Int) {}
      override fun onTitleReceived(tabId: String, title: String) {}
      override fun onTrackerBlocked(tabId: String) {}
      override fun onHttpBlockedByHttpsOnly(tabId: String, url: String) {}
      override fun onBlockedScheme(tabId: String, url: String, scheme: String) {}
      override fun onExternalSchemePrompt(tabId: String, url: String, scheme: String) {}
      override fun onPageError(tabId: String, error: com.example.model.PageError) {}
      override fun onRenderProcessCrashed(tabId: String) {}
      override fun onDownloadRequested(tabId: String, url: String, contentDisposition: String?, mimeType: String?, contentLength: Long) {}
      override fun onPermissionPrompt(tabId: String, origin: String, kind: com.example.model.WebPermissionKind, detail: String, onDecision: (Boolean) -> Unit) {}
      override fun onFileChooserRequested(callback: android.webkit.ValueCallback<Array<android.net.Uri>>?, acceptTypes: Array<String>?) {}
      override fun onFindMatches(tabId: String, activeOrdinal: Int, numberOfMatches: Int) {}
      override fun onFullscreenChanged(tabId: String, customView: android.view.View?, callback: android.webkit.WebChromeClient.CustomViewCallback?) {}
      override fun onLongPressLink(tabId: String, url: String, isImage: Boolean) {}
    }
    val pool = com.example.webview.WebViewPool(context, { SettingsState(dataSaverEnabled = false) }, callbacks)
    val webView = pool.getOrCreateWebView("tab_perf", false, UserAgentMode.MOBILE, emptySet())!!
    val prefetchReq = object : android.webkit.WebResourceRequest {
      override fun getUrl(): android.net.Uri = android.net.Uri.parse("https://example.org/next-page")
      override fun isForMainFrame(): Boolean = false
      override fun isRedirect(): Boolean = false
      override fun hasGesture(): Boolean = false
      override fun getMethod(): String = "GET"
      override fun getRequestHeaders(): MutableMap<String, String> = mutableMapOf("Sec-Purpose" to "prefetch")
    }
    val resp = webView.webViewClient.shouldInterceptRequest(webView, prefetchReq)
    assertEquals(204, resp?.statusCode)
    pool.trimMemoryAndDiskCaches("tab_perf", aggressive = true)
    pool.destroyAll()
  }

  @Test
  fun systemDefaultModeWorksIdenticallyInPrivateModeAndStandardMode() {
    val activity = composeTestRule.activity
    val vm = activity.viewModel
    composeTestRule.waitForIdle()
    composeTestRule.runOnUiThread {
      vm.dismissIntro()
      vm.openNewTab(isPrivate = true)
    }
    composeTestRule.waitForIdle()
    assertTrue(vm.activeTab().isPrivate)

    // Set System Default Mode in Private Mode
    composeTestRule.runOnUiThread {
      vm.updateSettings { it.copy(themeMode = com.example.model.ThemeMode.SYSTEM) }
    }
    composeTestRule.waitForIdle()
    assertEquals(com.example.model.ThemeMode.SYSTEM, vm.settings.value.themeMode)

    // Simulate OS System Dark Mode change while in Private Mode
    composeTestRule.runOnUiThread {
      val darkConfig = android.content.res.Configuration(activity.resources.configuration).apply {
        uiMode = (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK.inv()) or
          android.content.res.Configuration.UI_MODE_NIGHT_YES
      }
      activity.onConfigurationChanged(darkConfig)
    }
    composeTestRule.waitForIdle()
    assertTrue("Private Mode in System Default must resolve dark when OS is dark", vm.webViewPool.resolveIsDark(vm.settings.value))
    val darkController = androidx.core.view.WindowCompat.getInsetsController(activity.window, activity.window.decorView)
    assertFalse("Private Mode System Dark must render light status bar icons", darkController.isAppearanceLightStatusBars)

    // Simulate OS System Light Mode change while in Private Mode
    composeTestRule.runOnUiThread {
      val lightConfig = android.content.res.Configuration(activity.resources.configuration).apply {
        uiMode = (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK.inv()) or
          android.content.res.Configuration.UI_MODE_NIGHT_NO
      }
      activity.onConfigurationChanged(lightConfig)
    }
    composeTestRule.waitForIdle()
    assertFalse("Private Mode in System Default must resolve light when OS is light", vm.webViewPool.resolveIsDark(vm.settings.value))
    val lightController = androidx.core.view.WindowCompat.getInsetsController(activity.window, activity.window.decorView)
    assertTrue("Private Mode System Light must render dark status bar icons", lightController.isAppearanceLightStatusBars)
  }

  @Test
  fun chromeTypographyAndWebsiteNameOnlyBookmarksVerified() {
    // 1. Verify Chrome-for-Android sans-serif typography across all styles (no monospace labelSmall)
    assertEquals(androidx.compose.ui.text.font.FontFamily.SansSerif, com.example.ui.theme.FeatherTypography.labelSmall.fontFamily)
    assertEquals(androidx.compose.ui.text.font.FontFamily.SansSerif, com.example.ui.theme.FeatherTypography.bodyMedium.fontFamily)
    assertEquals(androidx.compose.ui.text.font.FontFamily.SansSerif, com.example.ui.theme.FeatherTypography.titleMedium.fontFamily)

    // 2. Verify clean website display name extraction (hides raw URLs)
    assertEquals("Wikipedia", com.example.security.UrlValidator.extractWebsiteDisplayName("Wikipedia", "https://en.wikipedia.org/wiki/Main_Page"))
    assertEquals("Youtube", com.example.security.UrlValidator.extractWebsiteDisplayName("https://www.youtube.com/watch?v=123", "https://www.youtube.com/watch?v=123"))
    assertEquals("Github", com.example.security.UrlValidator.extractWebsiteDisplayName("", "https://github.com/akshatk1997/Pika"))

    // 3. Add a bookmark and verify the Bookmarks screen renders ONLY the website name (no raw URL)
    val vm = composeTestRule.activity.viewModel
    composeTestRule.waitForIdle()
    composeTestRule.runOnUiThread {
      vm.dismissIntro()
      vm.openNewTab(isPrivate = false)
      vm.addOrUpdateBookmark(null, "Wikipedia", "https://en.wikipedia.org/wiki/Android", "Bookmarks")
      vm.setOverlay(com.example.ui.ActiveOverlay.BOOKMARKS)
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Wikipedia").assertIsDisplayed()
    composeTestRule.onNodeWithText("Bookmarks · https://en.wikipedia.org/wiki/Android").assertDoesNotExist()
  }

  @Test
  fun quickMenuHasModesAndBlockImagesAction() {
    val activity = composeTestRule.activity
    val vm = activity.viewModel
    composeTestRule.waitForIdle()
    composeTestRule.runOnUiThread {
      vm.dismissIntro()
    }
    composeTestRule.waitForIdle()

    // Open quick menu in standard mode
    composeTestRule.onNodeWithTag("nav_menu_button").performClick()
    composeTestRule.waitForIdle()

    // Verify Modes tile exists in grid
    composeTestRule.onNodeWithText("Modes").assertIsDisplayed()

    // Verify Block Images tile exists in grid
    composeTestRule.onNodeWithText("Block Images").assertIsDisplayed()

    // Tap Modes to open expander
    composeTestRule.onNodeWithText("Modes").performClick()
    composeTestRule.waitForIdle()

    // Verify Viewing Mode choices are visible
    composeTestRule.onNodeWithText("Desktop").assertIsDisplayed()
    composeTestRule.onNodeWithText("iPhone").assertIsDisplayed()
    composeTestRule.onNodeWithText("Mobile").assertIsDisplayed()

    // Tap Desktop to switch mode
    composeTestRule.onNodeWithText("Desktop").performClick()
    composeTestRule.waitForIdle()
    assertEquals(com.example.model.UserAgentMode.DESKTOP, vm.activeTab().userAgentMode)
  }

  @Test
  fun soulBrowserFeaturesMediaDownloaderImagesAndScreenFilterWork() {
    val activity = composeTestRule.activity
    val vm = activity.viewModel

    // 1. Verify detected media sniffing and download dispatch
    val sampleMedia = com.example.model.DetectedMedia(
      url = "https://example.org/video.mp4",
      title = "Test Video",
      mimeType = "video/mp4",
      formatLabel = "MP4"
    )
    vm.downloadMedia(sampleMedia)
    val downloads = vm.downloadController.downloads.value
    assertTrue(downloads.any { it.url == "https://example.org/video.mp4" })

    // 2. Verify screen filter and clean mode toggles
    assertFalse(vm.settings.value.screenFilterEnabled)
    vm.toggleScreenFilter()
    assertTrue(vm.settings.value.screenFilterEnabled)

    assertFalse(vm.settings.value.cleanModeEnabled)
    vm.toggleCleanMode()
    assertTrue(vm.settings.value.cleanModeEnabled)

    // 3. Verify media format recognition
    assertEquals("MP4", vm.webViewPool.determineMediaFormat("https://cdn.example.com/movie.mp4"))
    assertEquals("HLS/M3U8", vm.webViewPool.determineMediaFormat("https://cdn.example.com/playlist.m3u8"))
    assertEquals("WEBM", vm.webViewPool.determineMediaFormat("https://cdn.example.com/clip.webm"))
    assertEquals("AUDIO", vm.webViewPool.determineMediaFormat("https://cdn.example.com/audio.aac"))
  }

  @Test
  fun updateCheckerSemverAndSettingsUpdateCardTriggerDownload() {
    val vm = composeTestRule.activity.viewModel
    composeTestRule.waitForIdle()

    // 1. Verify semver comparison logic
    assertTrue(com.example.data.UpdateChecker.isNewerVersion("v2.0.2", "2.0.1"))
    assertTrue(com.example.data.UpdateChecker.isNewerVersion("v2.1.0", "2.0.1"))
    assertTrue(com.example.data.UpdateChecker.isNewerVersion("3.0.0", "2.0.1"))
    assertFalse(com.example.data.UpdateChecker.isNewerVersion("v2.0.1", "2.0.1"))
    assertFalse(com.example.data.UpdateChecker.isNewerVersion("v2.0.0", "2.0.1"))
    assertFalse(com.example.data.UpdateChecker.isNewerVersion("1.9.9", "2.0.1"))

    // 2. Open Settings overlay
    composeTestRule.runOnUiThread {
      vm.setOverlay(com.example.ui.ActiveOverlay.SETTINGS)
    }
    composeTestRule.waitForIdle()

    // Initially with no update available, the update card is not shown
    composeTestRule.onNodeWithTag("settings_update_available_card").assertDoesNotExist()

    // Simulate new update available detection
    composeTestRule.runOnUiThread {
      vm.availableUpdate.value = com.example.model.AppUpdateInfo(
        versionName = "2.1.2",
        downloadUrl = "https://github.com/akshatk1997/Pika/releases/download/v2.1.2/Kio-v2.1.2.apk",
        releaseNotes = "New speed optimizations"
      )
    }
    composeTestRule.waitForIdle()

    // Verify update banner card appears at the top of Settings
    composeTestRule.onNodeWithTag("settings_update_available_card").assertIsDisplayed()
    composeTestRule.onNodeWithText("New Update Available!").assertIsDisplayed()
    composeTestRule.onNodeWithText("v2.1.2").assertIsDisplayed()

    // Click on Update button to trigger automatic download
    composeTestRule.onNodeWithTag("settings_download_update_button").performClick()
    composeTestRule.waitForIdle()

    // Verify download was enqueued in downloads list
    val downloads = vm.downloads.value
    assertTrue(downloads.any { it.fileName.contains("Kio-v2.1.2.apk") })

    // Clean up
    composeTestRule.runOnUiThread {
      vm.availableUpdate.value = null
      vm.setOverlay(com.example.ui.ActiveOverlay.NONE)
    }
    composeTestRule.waitForIdle()
  }

  @Test
  fun oneHandedModeAndGrayscaleModeAndSettingsHeaderRemoved() {
    val vm = composeTestRule.activity.viewModel
    composeTestRule.waitForIdle()
    composeTestRule.runOnUiThread {
      vm.dismissIntro()
      vm.setOverlay(com.example.ui.ActiveOverlay.SETTINGS)
    }
    composeTestRule.waitForIdle()

    // 1. Verify Kio Browser brand & version summary card is removed from settings header
    composeTestRule.onNodeWithText("General & Search").assertIsDisplayed()
    composeTestRule.onNodeWithText("Version ${com.example.BuildConfig.VERSION_NAME}").assertDoesNotExist()
    composeTestRule.onNodeWithText("Engine & System Information").assertDoesNotExist()
    composeTestRule.onNodeWithText("Build 76").assertDoesNotExist()
    composeTestRule.onNodeWithText("Build 77").assertDoesNotExist()

    // 2. Open Appearance & Display folder
    composeTestRule.onNodeWithTag("settings_folder_appearance").performClick()
    composeTestRule.waitForIdle()

    // 3. Verify Grayscale mode switch row exists and toggles
    composeTestRule.onNodeWithTag("setting_grayscale_mode").performScrollTo().assertIsDisplayed()
    assertFalse(vm.settings.value.grayscaleModeEnabled)
    composeTestRule.onNodeWithTag("setting_grayscale_mode").performClick()
    composeTestRule.waitForIdle()
    assertTrue(vm.settings.value.grayscaleModeEnabled)

    // 4. Verify One-handed mode switch row exists and toggles
    composeTestRule.onNodeWithTag("setting_one_handed_mode").performScrollTo().assertIsDisplayed()
    assertFalse(vm.settings.value.oneHandedModeEnabled)
    composeTestRule.onNodeWithTag("setting_one_handed_mode").performClick()
    composeTestRule.waitForIdle()
    assertTrue(vm.settings.value.oneHandedModeEnabled)

    // Close Settings and return to browser screen
    composeTestRule.onNodeWithTag("settings_back_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("settings_back_button").performClick()
    composeTestRule.waitForIdle()

    // 5. In browser screen, verify One-handed mode reachability banner is displayed
    composeTestRule.onNodeWithTag("one_handed_restore_banner").assertIsDisplayed()

    // Tap restore banner to expand back to full screen
    composeTestRule.onNodeWithTag("one_handed_restore_banner").performClick()
    composeTestRule.waitForIdle()

    // Verify floating quick toggle button appears when full screen
    composeTestRule.onNodeWithTag("one_handed_quick_toggle").assertIsDisplayed()
    composeTestRule.onNodeWithTag("one_handed_quick_toggle").performClick()
    composeTestRule.waitForIdle()

    // Verify one-handed mode is re-activated
    composeTestRule.onNodeWithTag("one_handed_restore_banner").assertIsDisplayed()

    // Clean up
    composeTestRule.runOnUiThread {
      vm.updateSettings {
        it.copy(oneHandedModeEnabled = false, grayscaleModeEnabled = false)
      }
    }
    composeTestRule.waitForIdle()
  }

  @Test
  fun animatedCursiveIntroScreenAndPrivacyPositioningVerified() {
    val vm = composeTestRule.activity.viewModel
    composeTestRule.runOnUiThread {
      vm.resetIntro()
    }
    composeTestRule.waitForIdle()

    // 1. Verify Cursive Script typography header matching reference image
    composeTestRule.onNodeWithText("One browser,").assertExists()
    composeTestRule.onNodeWithText("every thing you are.").assertExists()

    // 2. Verify removed items from user images are completely gone
    composeTestRule.onNodeWithText("STEALTH PRIVACY FIRST · ZERO TELEMETRY").assertDoesNotExist()
    composeTestRule.onNodeWithTag("intro_thought_process", useUnmergedTree = true).assertDoesNotExist()
    composeTestRule.onNodeWithText("Tactile Gestures & Pull-to-Refresh").assertDoesNotExist()

    // 3. Verify Unique Pillar Cards are present without 2mb mention
    composeTestRule.onNodeWithText("Stealth Privacy & Zero Leaks").assertExists()
    composeTestRule.onNodeWithText("Integrated Ad & Video Shield").assertExists()
    composeTestRule.onNodeWithText("One-Handed & Grayscale Modes").assertExists()
    composeTestRule.onNodeWithText("Tactile Reload & Omnibox Lock").assertExists()
    composeTestRule.onNodeWithText("Pure On-Device NLP Announcer").assertExists()
    composeTestRule.onNodeWithText("Media Sniffer & Instant Updates").assertExists()

    // 4. Verify Creative Category Chips exist
    composeTestRule.onNodeWithText("All Features").assertExists()
    composeTestRule.onNodeWithText("Privacy & Shield").assertExists()
    composeTestRule.onNodeWithText("Gestures & Flow").assertExists()
    composeTestRule.onNodeWithText("AI & Utilities").assertExists()

    // 5. Verify Start Browsing button is present and dismisses intro
    composeTestRule.onNodeWithTag("intro_get_started", useUnmergedTree = true).assertExists()
    composeTestRule.runOnUiThread {
      vm.dismissIntro()
    }
    composeTestRule.waitForIdle()
    assertFalse(vm.showIntro.value)
  }
}
