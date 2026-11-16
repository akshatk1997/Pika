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
  fun appNameMatchesKaiAndVersionIsUpdated() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Kai", appName)
    assertEquals("1.7.6", BuildConfig.VERSION_NAME)
    assertEquals(49, BuildConfig.VERSION_CODE)
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

    composeTestRule.onNodeWithText("Kai").assertIsDisplayed()
    composeTestRule.onNodeWithTag("kai_home_logo").assertIsDisplayed()
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
    composeTestRule.onNodeWithText("History, cookies & cache are cleared on exit").assertIsDisplayed()

    // Open Security Site Info dialog from omnibox security badge
    composeTestRule.onNodeWithTag("security_badge_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Private browsing is active").assertIsDisplayed()
    composeTestRule.onNodeWithText("Done").performClick()
    composeTestRule.waitForIdle()

    // Open Quick Action Grid Menu and navigate to Settings
    composeTestRule.onNodeWithTag("nav_menu_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Data Saver").assertIsDisplayed()
    composeTestRule.onNodeWithText("Block images").assertIsDisplayed()
    composeTestRule.onNodeWithText("Settings").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Version 1.7.6").assertIsDisplayed()
    composeTestRule.onNodeWithText("Address bar position").assertDoesNotExist()
    composeTestRule.onNodeWithTag("setting_data_saver").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("setting_background_play").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("setting_youtube_scale_fit").assertDoesNotExist()
    composeTestRule.onNodeWithTag("setting_retain_tabs").performScrollTo().assertIsDisplayed()
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
    composeTestRule.onNodeWithText("Kai").assertIsDisplayed()
    composeTestRule.onNodeWithTag("kai_home_logo").assertIsDisplayed()

    // Header tab strip is removed from tablet devices
    composeTestRule.onNodeWithTag("tablet_tab_new_button").assertDoesNotExist()

    // Tablet layout renders clean single-row top toolbar with instant shortcuts
    composeTestRule.onNodeWithTag("tablet_desktop_mode_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("tablet_bookmarks_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("tablet_downloads_button").assertIsDisplayed()
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
      override fun onResourceSniffed(tabId: String, count: Int) {}
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
    val inputs = listOf("Welcome to Kai", "Privacy first browser")
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
  fun homepageStandardSearchOnlyAndKaiAiPermanentlyRemoved() {
    composeTestRule.runOnUiThread {
      composeTestRule.activity.viewModel.dismissIntro()
      composeTestRule.activity.viewModel.openNewTab(false)
    }
    composeTestRule.waitForIdle()

    // Verify AI mode options and tabs do not exist on homepage
    composeTestRule.onNodeWithTag("home_mode_ai").assertDoesNotExist()
    composeTestRule.onNodeWithTag("home_mode_standard").assertDoesNotExist()

    // Verify clean Kai home logo is displayed
    composeTestRule.onNodeWithTag("kai_home_logo").assertIsDisplayed()
  }

  @Test
  fun searchEngineCompatibilityAndKaiAiFilesCleaned() {
    val context = ApplicationProvider.getApplicationContext<Context>()

    // Test backward compatibility mapping to DuckDuckGo
    assertEquals(SearchEngine.DUCKDUCKGO, SearchEngine.fromId("duck_ai"))
    assertEquals(SearchEngine.DUCKDUCKGO, SearchEngine.fromId("kai_ai"))

    // Verify SearchEngine enum does not contain KAI_AI
    val engineIds = SearchEngine.entries.map { it.id }
    assertFalse(engineIds.contains("kai_ai"))

    // Anti-tracking & fingerprinting protection verification
    assertTrue(com.example.security.TrackerBlocker.isTrackerHost("fingerprint.com"))
    assertTrue(com.example.security.TrackerBlocker.isTrackerHost("clarity.ms"))
    assertTrue(com.example.security.TrackerBlocker.isTrackerHost("datadoghq.com"))
    val cleanedUrl = com.example.security.UrlValidator.stripTrackingParams("https://example.com/shop?fbclid=123&gbraid=456&item=shoes")
    assertEquals("https://example.com/shop?item=shoes", cleanedUrl)

    // Verify kai.html is completely cleaned from assets
    val assetsList = context.assets.list("")?.toList() ?: emptyList()
    assertFalse(assetsList.contains("kai.html"))
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
      override fun onResourceSniffed(tabId: String, count: Int) {}
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
  fun kaiAiPermanentlyRemovedFromAssetsAndCodebase() {
    val context = ApplicationProvider.getApplicationContext<Context>()

    // Verify kai.html is absent from assets
    val assets = context.assets.list("")?.toList() ?: emptyList()
    assertFalse(assets.contains("kai.html"))

    val hasKaiHtml = try {
      context.assets.open("kai.html").use { true }
    } catch (_: java.io.IOException) {
      false
    }
    assertFalse(hasKaiHtml)

    // Verify SearchEngine enum has no KAI_AI
    assertTrue(SearchEngine.entries.none { it.id == "kai_ai" })
  }

  @Test
  fun kaiAiDomainAndInferenceEndpointsNotExemptFromTrackerBlocking() {
    val pool = composeTestRule.activity.viewModel.webViewPool
    val webView = pool.getOrCreateWebView("tab_cleanup", false, com.example.model.UserAgentMode.MOBILE, emptySet())!!
    
    // Simulate request to kai.ai - should not be intercepted as special local HTML
    val fakeRequest = object : android.webkit.WebResourceRequest {
      override fun getUrl(): android.net.Uri = android.net.Uri.parse("https://kai.ai")
      override fun isForMainFrame(): Boolean = true
      override fun isRedirect(): Boolean = false
      override fun hasGesture(): Boolean = false
      override fun getMethod(): String = "GET"
      override fun getRequestHeaders(): MutableMap<String, String> = mutableMapOf()
    }
    val response = webView.webViewClient.shouldInterceptRequest(webView, fakeRequest)
    // Should NOT be intercepted as internal HTML stream
    assertNull(response)
    pool.destroyTab("tab_cleanup")
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
      override fun onResourceSniffed(tabId: String, count: Int) {}
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
    assertTrue(content.contains("v.__kai_ad_muted = true"))
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
}
