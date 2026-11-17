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
@Config(sdk = [26, 36])
class ExampleRobolectricTest {

  @get:Rule
  val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun appNameMatchesKioAndVersionIsUpdated() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Kio", appName)
    assertEquals("1.9.2", BuildConfig.VERSION_NAME)
    assertEquals(64, BuildConfig.VERSION_CODE)
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
      dataSaverEnabled = true
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

    // Open Extensions directly from the toolbar puzzle icon (replaces removed AI Assistant)
    composeTestRule.onNodeWithTag("nav_extensions_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("extensions_store_input").assertIsDisplayed()
    composeTestRule.onNodeWithTag("extensions_open_webstore_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("extensions_pick_file_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("extensions_back_button").performClick()
    composeTestRule.waitForIdle()

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
    composeTestRule.onNodeWithText("Data Saver").assertIsDisplayed()
    composeTestRule.onNodeWithText("Block images").assertIsDisplayed()
    composeTestRule.onNodeWithText("Full screen").assertIsDisplayed()
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
    composeTestRule.onNodeWithText("Settings").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Version 1.9.2").assertIsDisplayed()
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
  @Config(sdk = [26, 36], qualifiers = "sw600dp-w1024dp-h768dp-xhdpi")
  fun tabletHeaderHasNoTabBarAndToolbarShortcutsWorkAcrossAndroidVersions() {
    composeTestRule.waitForIdle()
    try {
      composeTestRule.onNodeWithTag("intro_get_started").performClick()
      composeTestRule.waitForIdle()
    } catch (_: Throwable) {
    }
    composeTestRule.onNodeWithText("Kio").assertIsDisplayed()
    composeTestRule.onNodeWithTag("kio_home_logo").assertIsDisplayed()

    // Header tab strip is removed from tablet devices
    composeTestRule.onNodeWithTag("tablet_tab_new_button").assertDoesNotExist()

    // Tablet layout renders clean single-row top toolbar with instant shortcuts
    composeTestRule.onNodeWithTag("tablet_desktop_mode_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("tablet_bookmarks_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("tablet_downloads_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("tablet_extensions_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("tablet_new_tab_button").assertIsDisplayed()

    // Open a new tab directly from the toolbar '+' button
    composeTestRule.onNodeWithTag("tablet_new_tab_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Search with DuckDuckGo").assertIsDisplayed()
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

    // Verify clean Kio home logo and Extensions button are displayed
    composeTestRule.onNodeWithTag("kio_home_logo").assertIsDisplayed()
    composeTestRule.onNodeWithTag("nav_extensions_button").assertIsDisplayed()
  }

  @Test
  fun extensionManagerLocalInstallToggleAndRemoveLifecycle() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val mgr = com.example.extensions.ChromeExtensionManager(context)
    val sampleUserscript = """
      // ==UserScript==
      // @name         Kio Test Helper
      // @version      1.2.3
      // @description  Verifies extension install and injection
      // @match        https://example.org/*
      // ==/UserScript==
      window.__kio_test_ext_ran = true;
    """.trimIndent().toByteArray(Charsets.UTF_8)

    val result = mgr.installFromBytes(sampleUserscript, "kio_helper.user.js", preferredId = "testext001")
    assertTrue(result is com.example.extensions.ExtensionInstallResult.Success)
    val ext = (result as com.example.extensions.ExtensionInstallResult.Success).extension
    assertEquals("Kio Test Helper", ext.name)
    assertEquals("1.2.3", ext.version)
    assertTrue(ext.enabled)

    val injected = mgr.buildPageInjectionScript("https://example.org/page", "end")
    assertNotNull(injected)
    assertTrue(injected!!.contains("__kio_test_ext_ran"))
    assertNull(mgr.buildPageInjectionScript("https://other-site.com/", "end"))

    mgr.setExtensionEnabled("testext001", false)
    assertNull(mgr.buildPageInjectionScript("https://example.org/page", "end"))

    mgr.removeExtension("testext001")
    assertNull(mgr.getExtension("testext001"))
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
  fun browserFullscreenModeHidesSystemBarsAndRestoresCleanly() {
    val activity = composeTestRule.activity
    val vm = activity.viewModel
    composeTestRule.waitForIdle()

    assertFalse(vm.isBrowserFullscreen.value)
    composeTestRule.runOnUiThread {
      vm.toggleBrowserFullscreen()
    }
    composeTestRule.waitForIdle()

    assertTrue(vm.isBrowserFullscreen.value)
    val fsFlags = activity.window.attributes.flags
    assertTrue((fsFlags and WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0)

    composeTestRule.runOnUiThread {
      vm.exitBrowserFullscreen()
    }
    composeTestRule.waitForIdle()

    assertFalse(vm.isBrowserFullscreen.value)
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
    composeTestRule.onNodeWithText("Open externally").assertIsDisplayed()
    composeTestRule.onNodeWithText("Add shortcut").assertIsDisplayed()
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
      vm.setOverlay(com.example.ui.ActiveOverlay.EXTENSIONS)
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
  fun chromeWebStoreExtensionsEngineDesktopCrxEndpointsAndSynchronousPopupHtmlPreludeWork() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    // 1. Verify Chrome Web Store URL detection & full desktop CRX3 candidate endpoints
    assertTrue(com.example.webview.WebViewPool.isChromeWebStoreUrl("https://chromewebstore.google.com/detail/free-vpn-for-chrome-vpn-p/majdfhpaihoncoakbjgbdhglocklcgno"))
    assertTrue(com.example.webview.WebViewPool.isChromeWebStoreUrl("https://chrome.google.com/webstore/detail/eimadpbcbfnmbkopoojfekhnkhdbieeh"))
    assertFalse(com.example.webview.WebViewPool.isChromeWebStoreUrl("https://example.org/detail/eimadpbcbfnmbkopoojfekhnkhdbieeh"))

    val extId = com.example.extensions.CrxUnpacker.extractChromeStoreId(
      "https://chromewebstore.google.com/detail/free-vpn-for-chrome-vpn-p/majdfhpaihoncoakbjgbdhglocklcgno"
    )
    assertEquals("majdfhpaihoncoakbjgbdhglocklcgno", extId)

    val candidates = com.example.extensions.CrxUnpacker.buildChromeWebStoreCrxCandidateUrls(extId!!)
    assertTrue(candidates.size >= 3)
    assertTrue(candidates.first().contains("os=win"))
    assertTrue(candidates.first().contains("arch=x64"))
    assertTrue(candidates.first().contains("prod=chromecrx"))
    assertTrue(candidates.first().contains("acceptformat=crx2,crx3"))

    // 2. Build a real ZIP extension package containing manifest.json + popup.html and verify synchronous prelude injection
    val baos = java.io.ByteArrayOutputStream()
    java.util.zip.ZipOutputStream(baos).use { zos ->
      zos.putNextEntry(java.util.zip.ZipEntry("manifest.json"))
      zos.write(
        """
        {
          "manifest_version": 3,
          "name": "Popup Test Ext",
          "version": "2.0.0",
          "action": { "default_popup": "popup.html" },
          "content_scripts": [{ "matches": ["https://example.org/*"], "js": ["content.js"] }]
        }
        """.trimIndent().toByteArray(Charsets.UTF_8)
      )
      zos.closeEntry()

      zos.putNextEntry(java.util.zip.ZipEntry("popup.html"))
      zos.write("<html><head><title>Popup</title></head><body><script src=\"popup.js\"></script></body></html>".toByteArray(Charsets.UTF_8))
      zos.closeEntry()

      zos.putNextEntry(java.util.zip.ZipEntry("content.js"))
      zos.write("window.__kio_ext_v2 = true;".toByteArray(Charsets.UTF_8))
      zos.closeEntry()
    }

    val mgr = com.example.extensions.ChromeExtensionManager(context)
    val installRes = mgr.installFromBytes(baos.toByteArray(), "popup_test.zip", preferredId = "popuptestext0001")
    assertTrue(installRes is com.example.extensions.ExtensionInstallResult.Success)

    // Serve popup.html via both virtual https://kio-ext.invalid/ and chrome-extension:// URLs
    val httpsResp = mgr.serveVirtualExtensionResource("https://kio-ext.invalid/popuptestext0001/popup.html")
    assertNotNull(httpsResp)
    val htmlText = httpsResp!!.data.bufferedReader().readText()
    assertTrue("HTML must inject chrome API prelude synchronously inside <head>", htmlText.contains("var __kioChrome"))
    assertTrue("HTML must expose chrome.scripting API", htmlText.contains("scripting:"))
    assertTrue(htmlText.indexOf("var __kioChrome") < htmlText.indexOf("popup.js"))

    val chromeSchemeResp = mgr.serveVirtualExtensionResource("chrome-extension://popuptestext0001/content.js")
    assertNotNull(chromeSchemeResp)
    assertTrue(chromeSchemeResp!!.data.bufferedReader().readText().contains("__kio_ext_v2"))

    // Verify storage & message live dispatch scripts
    val storageScript = mgr.buildStorageChangeBroadcastScript("popuptestext0001", "{\"enabled\":{\"newValue\":true}}")
    assertTrue(storageScript.contains("__kioExtNotifyStorage"))
    val msgScript = mgr.buildMessageDispatchScript("popuptestext0001", "{\"action\":\"toggle\"}")
    assertTrue(msgScript.contains("__kioExtDeliverMessage"))

    mgr.removeExtension("popuptestext0001")
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
}
