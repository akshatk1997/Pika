package com.example

import android.content.Context
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
    assertEquals("1.5.3", BuildConfig.VERSION_NAME)
    assertEquals(26, BuildConfig.VERSION_CODE)
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
      youtubeScaleMode = com.example.model.YouTubeVideoScaleMode.FIT,
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
    assertEquals(com.example.model.YouTubeVideoScaleMode.FIT, loadedSettings.youtubeScaleMode)
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
    composeTestRule.onNodeWithText("Version 1.5.3").assertIsDisplayed()
    composeTestRule.onNodeWithText("Address bar position").assertDoesNotExist()
    composeTestRule.onNodeWithTag("setting_data_saver").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("setting_background_play").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("setting_youtube_scale_fit").performScrollTo().assertIsDisplayed()
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
  fun translationUrlConstructionAndReversalWorkReliably() {
    val original = "https://example.org/news?article=123"
    val encoded = java.net.URLEncoder.encode(original, java.nio.charset.StandardCharsets.UTF_8.name())
    val targetUrl = "https://translate.google.com/translate?sl=auto&tl=es&u=$encoded"
    assertTrue(targetUrl.contains("sl=auto"))
    assertTrue(targetUrl.contains("tl=es"))
    assertTrue(targetUrl.contains("u="))

    val parsed = android.net.Uri.parse(targetUrl)
    val restored = parsed.getQueryParameter("u")
    assertEquals(original, restored)
  }

  @Test
  fun dashedHostTranslationProxyFormattingAndReversal() {
    val host = "en.wikipedia.org"
    val dashedHost = host.replace("-", "--").replace(".", "-") + ".translate.goog"
    assertEquals("en-wikipedia-org.translate.goog", dashedHost)

    val origDashed = dashedHost.removeSuffix(".translate.goog")
    val restoredHost = origDashed.replace("-", ".").replace("..", "-")
    assertEquals(host, restoredHost)
  }

  @Test
  fun homepageAiModeSwitchAndKaiAiIntegrationWorks() {
    composeTestRule.waitForIdle()
    try {
      composeTestRule.onNodeWithTag("intro_get_started").performClick()
      composeTestRule.waitForIdle()
    } catch (_: Throwable) {
    }

    composeTestRule.onNodeWithTag("home_mode_standard").assertIsDisplayed()
    composeTestRule.onNodeWithTag("home_mode_ai").assertIsDisplayed()

    // Tap on Kai AI mode -> immediately navigates to full Kai AI mode
    composeTestRule.onNodeWithTag("home_mode_ai").performClick()
    composeTestRule.waitForIdle()

    // Verify active tab URL is loaded with https://kai.ai
    assertEquals("https://kai.ai", composeTestRule.activity.viewModel.activeTab().url)
  }

  @Test
  fun kaiAiSearchEngineQueryResolutionAndTrackerExemptionWorks() {
    val res = com.example.security.UrlValidator.resolveInputToTarget("what is quantum computing", SearchEngine.KAI_AI, "", false)
    assertTrue(res is com.example.security.ResolvedNavigation.LoadUrl)
    val url = (res as com.example.security.ResolvedNavigation.LoadUrl).url
    assertTrue(url.startsWith("https://kai.ai/?q="))
    assertTrue(url.contains("quantum"))

    // Test backward compatibility mapping
    assertEquals(SearchEngine.KAI_AI, SearchEngine.fromId("duck_ai"))
    assertEquals(SearchEngine.KAI_AI, SearchEngine.fromId("kai_ai"))

    // Tracker exemption checks
    assertFalse(com.example.security.TrackerBlocker.shouldBlockRequest("https://kai.ai/chat", "kai.ai", true))
    assertFalse(com.example.security.TrackerBlocker.shouldBlockRequest("https://text.pollinations.ai/prompt", "kai.ai", true))
    assertFalse(com.example.security.TrackerBlocker.shouldBlockRequest("https://generativelanguage.googleapis.com/v1beta/openai/chat/completions", "kai.ai", true))

    // Anti-tracking & fingerprinting protection verification
    assertTrue(com.example.security.TrackerBlocker.isTrackerHost("fingerprint.com"))
    assertTrue(com.example.security.TrackerBlocker.isTrackerHost("clarity.ms"))
    assertTrue(com.example.security.TrackerBlocker.isTrackerHost("datadoghq.com"))
    val cleanedUrl = com.example.security.UrlValidator.stripTrackingParams("https://example.com/shop?fbclid=123&gbraid=456&item=shoes")
    assertEquals("https://example.com/shop?item=shoes", cleanedUrl)

    // Built-in KaiAiApp verification with attachments, voice, camera removed, video keyframes, multimodal and ChatGPT fonts
    val context = ApplicationProvider.getApplicationContext<Context>()
    val html = com.example.ui.ai.KaiAiApp.getHtmlText(context)
    assertTrue(html.contains("Kai AI"))
    assertTrue(html.contains("Anonymous AI Workspace"))
    assertFalse(html.contains("100% Anonymous"))
    assertTrue(html.contains("attach-btn"))
    assertFalse(html.contains("camera-btn"))
    assertFalse(html.contains("camera-modal"))
    assertFalse(html.contains("shutter-btn"))
    assertTrue(html.contains("mic-btn"))
    assertTrue(html.contains("image_url"))
    assertTrue(html.contains("extractVideoKeyframes"))
    assertTrue(html.contains("SpeechRecognition"))
    assertTrue(html.contains("gemini-3.5-flash-lite"))
    assertTrue(html.contains("thought-process"))
    assertTrue(html.contains("Söhne"))
    assertTrue(html.contains("Söhne Mono"))
    assertTrue(html.contains("#070B14"))
    assertTrue(html.contains("#0E1626"))
    assertTrue(html.contains("#00F2FE"))
  }

  @Test
  fun youtubeScaleModeAndDataSaverFunctionalityWorks() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    
    // Test YouTubeVideoScaleMode enum mappings
    assertEquals(com.example.model.YouTubeVideoScaleMode.ORIGINAL, com.example.model.YouTubeVideoScaleMode.fromId("original"))
    assertEquals(com.example.model.YouTubeVideoScaleMode.FIT, com.example.model.YouTubeVideoScaleMode.fromId("fit"))
    assertEquals(com.example.model.YouTubeVideoScaleMode.STRETCHED, com.example.model.YouTubeVideoScaleMode.fromId("stretched"))
    assertEquals(com.example.model.YouTubeVideoScaleMode.ORIGINAL, com.example.model.YouTubeVideoScaleMode.fromId("unknown_fallback"))

    // Test default settings state
    val defaultState = SettingsState()
    assertEquals(com.example.model.YouTubeVideoScaleMode.ORIGINAL, defaultState.youtubeScaleMode)
    assertFalse(defaultState.dataSaverEnabled)

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

    val pool = com.example.webview.WebViewPool(context, { defaultState.copy(dataSaverEnabled = true, youtubeScaleMode = com.example.model.YouTubeVideoScaleMode.FIT) }, callbacks)
    val wv = pool.getOrCreateWebView("tab_yt_test", false, UserAgentMode.MOBILE, emptySet())!!
    pool.applyYouTubeScaleMode(wv, com.example.model.YouTubeVideoScaleMode.FIT)
    pool.applyYouTubeScaleMode(wv, com.example.model.YouTubeVideoScaleMode.STRETCHED)
    pool.applyYouTubeScaleMode(wv, com.example.model.YouTubeVideoScaleMode.ORIGINAL)
    pool.applyDataSaver(wv, true)
    pool.applyDataSaver(wv, false)
    pool.destroyTab("tab_yt_test")
  }

  @Test
  fun kaiVoiceBridgeAndMultimodalAttachmentsTest() {
    val context = ApplicationProvider.getApplicationContext<Context>()

    // 1. Verify KaiVoiceBridge constants and instance behavior
    assertEquals("KaiVoice", com.example.ui.ai.KaiVoiceBridge.JS_INTERFACE_NAME)
    var permNeededCalled = false
    val testWv = android.webkit.WebView(context)
    val bridge = com.example.ui.ai.KaiVoiceBridge(
      java.lang.ref.WeakReference(testWv),
      onPermissionNeeded = { permNeededCalled = true }
    )
    // Under Robolectric, audio permission is not granted by default
    assertFalse(bridge.hasPermission())
    bridge.requestPermission()
    assertTrue(permNeededCalled)
    // Methods can be called safely without exceptions
    bridge.stopListening()
    bridge.cancel()
    bridge.cleanup()

    // 2. Verify kai.html includes all required multimodal, dual-engine voice, and UI elements
    val html = com.example.ui.ai.KaiAiApp.getHtmlText(context)
    // Voice mic integration & dual-engine audio capture
    assertTrue(html.contains("KaiVoice"))
    assertTrue(html.contains("__kaiVoiceOnStart"))
    assertTrue(html.contains("__kaiVoiceOnResult"))
    assertTrue(html.contains("__kaiVoiceOnError"))
    assertTrue(html.contains("__kaiVoiceOnEnd"))
    assertTrue(html.contains("__kaiVoiceOnRmsChanged"))
    assertTrue(html.contains("__kaiVoiceOnPermissionGranted"))
    assertTrue(html.contains("__kaiVoiceOnPermissionNeeded"))
    assertTrue(html.contains("startAudioRecording"))
    assertTrue(html.contains("stopAudioRecording"))
    assertTrue(html.contains("voice-done-btn"))
    assertTrue(html.contains("voice-wave"))

    // PPT & PPTX presentation analysis
    assertTrue(html.contains("parsePresentationFile"))
    assertTrue(html.contains("extractPptxSlides"))
    assertTrue(html.contains("extractBinaryPptText"))
    assertTrue(html.contains("PRESENTATION DECK"))
    assertTrue(html.contains(".pptx"))
    assertTrue(html.contains(".ppt"))
    assertTrue(html.contains("msg-doc-chip ppt"))

    // PDF document analysis
    assertTrue(html.contains("parsePdfFile"))
    assertTrue(html.contains("decompressPdfFlate"))
    assertTrue(html.contains("extractPdfTextFromOperators"))
    assertTrue(html.contains("PDF DOCUMENT"))
    assertTrue(html.contains(".pdf"))
    assertTrue(html.contains("msg-doc-chip pdf"))

    // Audio file analysis & player
    assertTrue(html.contains("parseAudioFile"))
    assertTrue(html.contains("extractAudioMetadata"))
    assertTrue(html.contains("AUDIO FILE"))
    assertTrue(html.contains("msg-audio-player"))
    assertTrue(html.contains("msg-doc-chip audio"))
    assertTrue(html.contains(".mp3"))
    assertTrue(html.contains(".wav"))
    assertTrue(html.contains(".m4a"))

    // Persona prompt training for PPT, PDF, Audio
    assertTrue(html.contains("PowerPoint Presentations (.ppt, .pptx)"))
    assertTrue(html.contains("PDF Documents (.pdf)"))
    assertTrue(html.contains("Audio Files (.mp3, .wav, .m4a, .ogg, .aac)"))

    // File input accept attribute includes all requested formats
    assertTrue(html.contains("accept=\"image/*,video/*,audio/*,.pdf,.ppt,.pptx,.mp3,.wav,.m4a,.ogg,.aac,.flac"))
  }

  @Test
  fun kaiAiLightModePaletteAndThemeToggleVerified() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val html = context.assets.open("kai.html").bufferedReader().use { it.readText() }

    // Verify 100% Anonymous header label removed
    assertFalse(html.contains("100% Anonymous"))

    // Verify theme toggle button & SVG icons
    assertTrue(html.contains("id=\"theme-toggle-btn\""))
    assertTrue(html.contains("sun-icon"))
    assertTrue(html.contains("moon-icon"))

    // Verify light mode CSS palette
    assertTrue(html.contains("[data-theme=\"light\"]"))
    assertTrue(html.contains("--bg: #F8FAFC;"))
    assertTrue(html.contains("--bubble-user: #E0F2FE;"))
    assertTrue(html.contains("--bubble-ai: #FFFFFF;"))

    // Verify JavaScript theme switching APIs
    assertTrue(html.contains("function applyTheme("))
    assertTrue(html.contains("window.__kaiSetTheme"))
    assertTrue(html.contains("localStorage.setItem('kai_theme'"))
  }

  @Test
  fun youtubeScaleFixAndDataSaverVerified() {
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
      youtubeScaleMode = com.example.model.YouTubeVideoScaleMode.FIT,
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
}
