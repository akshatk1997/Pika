package com.example.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.view.View
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BookmarkImportExport
import com.example.data.LocalBrowserDatabase
import com.example.data.SettingsRepository
import com.example.data.UpdateChecker
import com.example.download.DownloadController
import com.example.media.MediaPlaybackService
import com.example.model.AppUpdateInfo
import com.example.model.BookmarkItem
import com.example.model.ClearDataOptions
import com.example.model.ClosedTabRecord
import com.example.model.DetectedMedia
import com.example.model.DownloadRecord
import com.example.model.ExternalLinkPrompt
import com.example.model.HistoryItem
import com.example.model.HttpWarningState
import com.example.model.LinkContextTarget
import com.example.model.PageError
import com.example.model.PageErrorType
import com.example.model.PendingDownloadConsent
import com.example.model.ReaderArticle
import com.example.model.ScreenFilterType
import com.example.model.SecurityState
import com.example.model.SettingsState
import com.example.model.SitePermissionRule
import com.example.model.TabInfo
import com.example.model.ThemeMode
import com.example.model.UserAgentMode
import com.example.model.WebPermissionKind
import com.example.model.WebPermissionPrompt
import com.example.reader.NarrationSegment
import com.example.reader.NarratorState
import com.example.reader.NlpContentAnnouncer
import com.example.security.PhishingRiskAssessment
import com.example.security.PhishingRiskLevel
import com.example.security.PhishingScamDetector
import com.example.security.ResolvedNavigation
import com.example.security.TrackerBlocker
import com.example.security.UrlValidator
import com.example.service.PageTranslationService
import com.example.webview.WebViewCallbacks
import com.example.webview.WebViewPool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class ActiveOverlay {
  NONE,
  TABS,
  BOOKMARKS,
  HISTORY,
  DOWNLOADS,
  SETTINGS,
  MEDIA_DOWNLOADER,
  IMAGE_SNIFFER,
  SCREEN_FILTER_SETTINGS
}

data class FindState(
  val isVisible: Boolean = false,
  val query: String = "",
  val activeIndex: Int = 0,
  val totalMatches: Int = 0
)

class BrowserViewModel(application: Application) : AndroidViewModel(application) {

  private val appContext = application.applicationContext
  private val mainHandler = Handler(Looper.getMainLooper())

  private val db = LocalBrowserDatabase(appContext)
  private val settingsRepo = SettingsRepository(appContext)
  val downloadController = DownloadController(appContext, viewModelScope)

  private val _settings = MutableStateFlow(settingsRepo.loadSettings())
  val settings: StateFlow<SettingsState> = _settings.asStateFlow()

  private val _sitePermissions = MutableStateFlow(settingsRepo.loadSitePermissions())
  val sitePermissions: StateFlow<List<SitePermissionRule>> = _sitePermissions.asStateFlow()

  private val initialTab = TabInfo(id = UUID.randomUUID().toString(), title = "New Tab")
  private val _tabs = MutableStateFlow(listOf(initialTab))
  val tabs: StateFlow<List<TabInfo>> = _tabs.asStateFlow()

  private val _activeTabId = MutableStateFlow(initialTab.id)
  val activeTabId: StateFlow<String> = _activeTabId.asStateFlow()

  private val _closedTabs = MutableStateFlow<List<ClosedTabRecord>>(emptyList())
  val closedTabs: StateFlow<List<ClosedTabRecord>> = _closedTabs.asStateFlow()

  private val _bookmarks = MutableStateFlow<List<BookmarkItem>>(emptyList())
  val bookmarks: StateFlow<List<BookmarkItem>> = _bookmarks.asStateFlow()

  private val _history = MutableStateFlow<List<HistoryItem>>(emptyList())
  val history: StateFlow<List<HistoryItem>> = _history.asStateFlow()

  private val _activeOverlay = MutableStateFlow(ActiveOverlay.NONE)
  val activeOverlay: StateFlow<ActiveOverlay> = _activeOverlay.asStateFlow()

  private val _findState = MutableStateFlow(FindState())
  val findState: StateFlow<FindState> = _findState.asStateFlow()

  val availableUpdate = MutableStateFlow<AppUpdateInfo?>(null)

  fun checkForUpdates(silent: Boolean = true, onComplete: ((AppUpdateInfo?) -> Unit)? = null) {
    viewModelScope.launch {
      val info = UpdateChecker.checkUpdate(com.example.BuildConfig.VERSION_NAME)
      if (info != null || !silent) {
        availableUpdate.value = info
      }
      if (!silent) {
        if (info != null) {
          showToast("Update available: v${info.versionName}")
        } else {
          showToast("Kio is up to date (v${com.example.BuildConfig.VERSION_NAME})")
        }
      }
      onComplete?.invoke(info)
    }
  }

  fun downloadAppUpdate(downloadUrl: String, versionName: String) {
    val fileName = "Kio-v$versionName.apk"
    val mime = "application/vnd.android.package-archive"
    downloadController.enqueueDirectDownload(
      url = downloadUrl,
      fileName = fileName,
      mimeType = mime,
      isPrivateTab = false
    )
    showToast("Downloading update v$versionName...")

    try {
      val dm = appContext.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as? android.app.DownloadManager
      if (dm != null) {
        val req = android.app.DownloadManager.Request(android.net.Uri.parse(downloadUrl)).apply {
          setTitle("Kio Browser Update v$versionName")
          setDescription("Downloading Kio v$versionName APK update...")
          setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
          setMimeType(mime)
          setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, fileName)
          setAllowedOverMetered(true)
          setAllowedOverRoaming(true)
        }
        dm.enqueue(req)
      }
    } catch (_: Throwable) {
    }
  }

  private val _readerArticle = MutableStateFlow<ReaderArticle?>(null)
  val readerArticle: StateFlow<ReaderArticle?> = _readerArticle.asStateFlow()

  private val _readerSegments = MutableStateFlow<List<NarrationSegment>>(emptyList())
  val readerSegments: StateFlow<List<NarrationSegment>> = _readerSegments.asStateFlow()

  private val _narratorState = MutableStateFlow(NarratorState())
  val narratorState: StateFlow<NarratorState> = _narratorState.asStateFlow()

  private val contentAnnouncer = NlpContentAnnouncer(appContext) { state ->
    _narratorState.value = state
  }

  private val _pageSource = MutableStateFlow<Pair<String, String>?>(null)
  val pageSource: StateFlow<Pair<String, String>?> = _pageSource.asStateFlow()

  private val _detectedMediaMap = MutableStateFlow<Map<String, List<DetectedMedia>>>(emptyMap())
  val detectedMediaMap: StateFlow<Map<String, List<DetectedMedia>>> = _detectedMediaMap.asStateFlow()

  private val _extractedImages = MutableStateFlow<List<String>>(emptyList())
  val extractedImages: StateFlow<List<String>> = _extractedImages.asStateFlow()

  private val _isBrowserFullscreen = MutableStateFlow(false)
  val isBrowserFullscreen: StateFlow<Boolean> = _isBrowserFullscreen.asStateFlow()

  private val _showIntro = MutableStateFlow(!settingsRepo.hasSeenIntro())
  val showIntro: StateFlow<Boolean> = _showIntro.asStateFlow()

  fun dismissIntro() {
    _showIntro.value = false
    settingsRepo.setIntroSeen(true)
  }

  fun resetIntro() {
    _showIntro.value = true
    settingsRepo.setIntroSeen(false)
  }

  private val _externalLinkPrompt = MutableStateFlow<ExternalLinkPrompt?>(null)
  val externalLinkPrompt: StateFlow<ExternalLinkPrompt?> = _externalLinkPrompt.asStateFlow()

  private val _permissionPrompt = MutableStateFlow<WebPermissionPrompt?>(null)
  val permissionPrompt: StateFlow<WebPermissionPrompt?> = _permissionPrompt.asStateFlow()
  private var pendingPermissionCallback: ((Boolean) -> Unit)? = null

  private val _linkContextTarget = MutableStateFlow<LinkContextTarget?>(null)
  val linkContextTarget: StateFlow<LinkContextTarget?> = _linkContextTarget.asStateFlow()

  private val _fullscreenView = MutableStateFlow<Pair<View, WebChromeClient.CustomViewCallback?>?>(null)
  val fullscreenView: StateFlow<Pair<View, WebChromeClient.CustomViewCallback?>?> = _fullscreenView.asStateFlow()

  private val _statusToast = MutableStateFlow<String?>(null)
  val statusToast: StateFlow<String?> = _statusToast.asStateFlow()

  private var pendingFileChooserCallback: ValueCallback<Array<Uri>>? = null
  var onLaunchFileChooser: ((Array<String>?) -> Unit)? = null
  var onRequestMediaPermissions: (() -> Unit)? = null
  var onAudioPermissionRequested: (() -> Unit)? = null

  fun onAudioPermissionGranted() {
    webViewPool.notifyAudioPermissionGranted()
  }

  private val approvedHttpHosts: MutableSet<String> = ConcurrentHashMap.newKeySet()
  @Volatile private var clipboardWrittenInPrivate: Boolean = false
  private var bookmarkQuery = ""
  private var historyQuery = ""

  private inline fun runOnMain(crossinline block: () -> Unit) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
      block()
    } else {
      mainHandler.post { block() }
    }
  }

  val downloads: StateFlow<List<DownloadRecord>> = downloadController.downloads
  val pendingDownloadConsent: StateFlow<PendingDownloadConsent?> = downloadController.pendingConsent

  val systemWebViewPackageInfo: String? by lazy {
    try {
      val pkg = WebView.getCurrentWebViewPackage()
      pkg?.let {
        val name = it.packageName.substringAfterLast('.')
        "$name v${it.versionName}"
      }
    } catch (_: Throwable) {
      null
    }
  }

  val isSystemWebViewOutdated: Boolean by lazy {
    try {
      val pkg = WebView.getCurrentWebViewPackage() ?: return@lazy false
      val major = pkg.versionName?.substringBefore('.')?.toIntOrNull() ?: 0
      major in 1..85
    } catch (_: Throwable) {
      false
    }
  }

  // On-Device Phishing & Scam URL Detector
  private val _phishingAssessment = MutableStateFlow<PhishingRiskAssessment?>(null)
  val phishingAssessment: StateFlow<PhishingRiskAssessment?> = _phishingAssessment.asStateFlow()

  private val _dismissedPhishingUrls = ConcurrentHashMap.newKeySet<String>()

  fun dismissPhishingWarning(url: String) {
    _dismissedPhishingUrls.add(url)
    _phishingAssessment.value = null
  }

  fun onSystemTrimMemory(level: Int) {
    val aggressive = level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW
    webViewPool.trimMemoryAndDiskCaches(_activeTabId.value, aggressive = aggressive)
  }

  fun onSystemThemeChanged(isSystemDark: Boolean) {
    webViewPool.updateSystemDarkMode(isSystemDark)
    val current = _settings.value
    if (current.themeMode == ThemeMode.SYSTEM) {
      webViewPool.applySettingsToAll(current) { id ->
        val t = tabById(id)
        (t?.isPrivate == true) to (t?.userAgentMode ?: UserAgentMode.MOBILE)
      }
    }
  }

  // Pure On-Device NLP Content Announcer Controls (Zero Copy / Zero Persistence)
  fun toggleNarratorPlayPause() = contentAnnouncer.togglePlayPause()

  fun nextNarratorSegment() = contentAnnouncer.nextSegment()

  fun prevNarratorSegment() = contentAnnouncer.previousSegment()

  fun selectNarratorSegment(index: Int) = contentAnnouncer.speakFromIndex(index)

  fun setNarratorSpeechRate(rate: Float) = contentAnnouncer.setSpeechRate(rate)

  private val webCallbacks = object : WebViewCallbacks {
    override fun onPageStarted(tabId: String, url: String) {
      runOnMain {
        _detectedMediaMap.update { it - tabId }
        val sec = when {
          tabById(tabId)?.isPrivate == true -> SecurityState.PRIVATE
          UrlValidator.isHttpsUrl(url) -> SecurityState.SECURE_HTTPS
          UrlValidator.isHttpUrl(url) -> SecurityState.INSECURE_HTTP
          else -> SecurityState.HOME
        }
        updateTab(tabId) {
          it.copy(
            url = url,
            isLoading = true,
            progress = 15,
            securityState = sec,
            errorState = null,
            httpWarning = null,
            faviconInitial = UrlValidator.extractHost(url).firstOrNull()?.uppercase() ?: ""
          )
        }
        if (tabById(tabId)?.isPrivate != true) {
          persistOpenTabs()
        }
        if (tabId == _activeTabId.value) {
          if (url.isNotBlank() && !_dismissedPhishingUrls.contains(url)) {
            val assessment = PhishingScamDetector.analyzeUrl(url)
            _phishingAssessment.value = if (assessment.level != PhishingRiskLevel.LOW) assessment else null
          } else {
            _phishingAssessment.value = null
          }
        }
      }
    }

    override fun onPageFinished(
      tabId: String,
      url: String,
      title: String?,
      canGoBack: Boolean,
      canGoForward: Boolean
    ) {
      runOnMain {
        val safeTitle = UrlValidator.sanitizeDisplayText(title, 180)
          .ifEmpty { UrlValidator.extractHost(url).ifEmpty { "Page" } }
        val tab = tabById(tabId)
        val sec = when {
          tab?.errorState != null -> SecurityState.ERROR
          tab?.isPrivate == true -> SecurityState.PRIVATE
          UrlValidator.isHttpsUrl(url) -> SecurityState.SECURE_HTTPS
          UrlValidator.isHttpUrl(url) -> SecurityState.INSECURE_HTTP
          else -> SecurityState.HOME
        }
        updateTab(tabId) {
          it.copy(
            url = url,
            title = safeTitle,
            isLoading = false,
            progress = 100,
            canGoBack = canGoBack,
            canGoForward = canGoForward,
            securityState = sec
          )
        }
        if (tab != null && !tab.isPrivate && _settings.value.historyEnabled && tab.errorState == null) {
          viewModelScope.launch(Dispatchers.IO) {
            try {
              db.recordHistory(safeTitle, url)
              _history.value = db.getHistory(historyQuery)
            } catch (_: Throwable) {
            }
          }
        }
        persistOpenTabs()
        if (tabId == _activeTabId.value && url.isNotBlank() && !_dismissedPhishingUrls.contains(url)) {
          val assessment = PhishingScamDetector.analyzeUrl(url)
          _phishingAssessment.value = if (assessment.level != PhishingRiskLevel.LOW) assessment else null
        }
      }
    }

    override fun onProgressChanged(tabId: String, progress: Int) {
      runOnMain {
        updateTab(tabId) { tab ->
          if (tab.errorState != null || tab.httpWarning != null) {
            tab
          } else {
            tab.copy(progress = progress, isLoading = progress < 100 && tab.isLoading)
          }
        }
      }
    }

    override fun onTitleReceived(tabId: String, title: String) {
      runOnMain {
        updateTab(tabId) { it.copy(title = title) }
      }
    }

    override fun onTrackerBlocked(tabId: String) {
      runOnMain {
        updateTab(tabId) { it.copy(trackersBlockedCount = it.trackersBlockedCount + 1) }
      }
    }

    override fun onHttpBlockedByHttpsOnly(tabId: String, url: String) {
      runOnMain {
        val host = UrlValidator.extractHost(url)
        updateTab(tabId) {
          it.copy(
            isLoading = false,
            securityState = SecurityState.INSECURE_HTTP,
            httpWarning = HttpWarningState(originalUrl = url, host = host, upgradeAttemptFailed = false)
          )
        }
      }
    }

    override fun onBlockedScheme(tabId: String, url: String, scheme: String) {
      runOnMain {
        updateTab(tabId) {
          it.copy(
            isLoading = false,
            securityState = SecurityState.ERROR,
            errorState = PageError(
              type = PageErrorType.BLOCKED,
              failingUrl = UrlValidator.sanitizeDisplayText(url, 180),
              host = scheme,
              reason = "Unsupported link ($scheme:)"
            )
          )
        }
      }
    }

    override fun onExternalSchemePrompt(tabId: String, url: String, scheme: String) {
      runOnMain {
        // Hard isolation: Private tabs never hand off links to external apps
        if (tabById(tabId)?.isPrivate == true) return@runOnMain
        _externalLinkPrompt.value = ExternalLinkPrompt(
          url = url,
          scheme = scheme,
          targetSummary = UrlValidator.sanitizeDisplayText(url, 120)
        )
      }
    }

    override fun onPageError(tabId: String, error: PageError) {
      runOnMain {
        val tab = tabById(tabId)
        val upgradeOrigin = tab?.upgradedFromHttpUrl
        if (upgradeOrigin != null && error.failingUrl.startsWith("https://")) {
          val host = UrlValidator.extractHost(upgradeOrigin)
          updateTab(tabId) {
            it.copy(
              isLoading = false,
              upgradedFromHttpUrl = null,
              securityState = SecurityState.INSECURE_HTTP,
              httpWarning = HttpWarningState(originalUrl = upgradeOrigin, host = host, upgradeAttemptFailed = true)
            )
          }
          return@runOnMain
        }
        updateTab(tabId) {
          it.copy(
            isLoading = false,
            securityState = SecurityState.ERROR,
            errorState = error
          )
        }
      }
    }

    override fun onRenderProcessCrashed(tabId: String) {
      runOnMain {
        val tab = tabById(tabId)
        val url = tab?.url ?: ""
        updateTab(tabId) {
          it.copy(
            isLoading = false,
            securityState = SecurityState.ERROR,
            errorState = PageError(
              type = PageErrorType.GENERIC,
              failingUrl = url,
              host = UrlValidator.extractHost(url).ifEmpty { "Page" },
              reason = "This page stopped responding."
            )
          )
        }
      }
    }

    override fun onDownloadRequested(
      tabId: String,
      url: String,
      contentDisposition: String?,
      mimeType: String?,
      contentLength: Long
    ) {
      runOnMain {
        val isPriv = tabById(tabId)?.isPrivate == true
        downloadController.requestDownloadConsent(url, contentDisposition, mimeType, contentLength, isPriv)
      }
    }

    override fun onPermissionPrompt(
      tabId: String,
      origin: String,
      kind: WebPermissionKind,
      detail: String,
      onDecision: (Boolean) -> Unit
    ) {
      runOnMain {
        val isPriv = tabById(tabId)?.isPrivate == true
        if (!isPriv) {
          val existing = _sitePermissions.value.firstOrNull { it.origin == origin && it.kind == kind }
          if (existing != null) {
            onDecision(existing.allowed)
            return@runOnMain
          }
        }
        pendingPermissionCallback?.invoke(false)
        pendingPermissionCallback = onDecision
        _permissionPrompt.value = WebPermissionPrompt(
          id = UUID.randomUUID().toString(),
          origin = origin,
          kind = kind,
          detail = detail
        )
      }
    }

    override fun onFileChooserRequested(
      callback: ValueCallback<Array<Uri>>?,
      acceptTypes: Array<String>?
    ) {
      runOnMain {
        pendingFileChooserCallback?.onReceiveValue(null)
        pendingFileChooserCallback = callback
        val handler = onLaunchFileChooser
        if (handler == null) {
          pendingFileChooserCallback?.onReceiveValue(null)
          pendingFileChooserCallback = null
        } else {
          handler.invoke(acceptTypes)
        }
      }
    }

    override fun onFindMatches(tabId: String, activeOrdinal: Int, numberOfMatches: Int) {
      runOnMain {
        if (tabId == _activeTabId.value) {
          _findState.update {
            it.copy(
              activeIndex = if (numberOfMatches > 0) activeOrdinal + 1 else 0,
              totalMatches = numberOfMatches
            )
          }
        }
      }
    }

    override fun onFullscreenChanged(
      tabId: String,
      customView: View?,
      callback: WebChromeClient.CustomViewCallback?
    ) {
      runOnMain {
        if (customView == null) {
          _fullscreenView.value = null
        } else {
          _fullscreenView.value = customView to callback
        }
      }
    }

    override fun onLongPressLink(tabId: String, url: String, isImage: Boolean) {
      runOnMain {
        _linkContextTarget.value = LinkContextTarget(url = url, isImage = isImage)
      }
    }

    override fun onLongPressLinkWithImage(tabId: String, url: String, isImage: Boolean, imageUrl: String?) {
      runOnMain {
        _linkContextTarget.value = LinkContextTarget(url = url, isImage = isImage, imageUrl = imageUrl)
      }
    }

    override fun onMediaDetected(tabId: String, media: DetectedMedia) {
      runOnMain {
        _detectedMediaMap.update { map ->
          val current = map[tabId].orEmpty()
          if (current.none { it.url == media.url }) {
            map + (tabId to (current + media))
          } else map
        }
      }
    }

    override fun onAudioPermissionNeeded() {
      runOnMain {
        onAudioPermissionRequested?.invoke()
      }
    }

    override fun onNewTabRequested(parentTabId: String, url: String, isPrivate: Boolean) {
      runOnMain {
        openNewTab(isPrivate = isPrivate, initialUrl = url)
      }
    }

    override fun onNewTabCreated(newTabId: String, isPrivate: Boolean, initialUrl: String) {
      runOnMain {
        val target = initialUrl.trim()
        val newTab = TabInfo(
          id = newTabId,
          url = target,
          title = if (isPrivate) {
            if (target.isNotBlank()) UrlValidator.extractHost(target).ifEmpty { "Private Tab" } else "Private Tab"
          } else {
            if (target.isNotBlank()) UrlValidator.extractHost(target).ifEmpty { "New Tab" } else "New Tab"
          },
          isPrivate = isPrivate,
          securityState = when {
            isPrivate -> SecurityState.PRIVATE
            UrlValidator.isHttpsUrl(target) -> SecurityState.SECURE_HTTPS
            UrlValidator.isHttpUrl(target) -> SecurityState.INSECURE_HTTP
            else -> SecurityState.HOME
          },
          isLoading = true,
          progress = 15
        )
        _tabs.update { it + newTab }
        _activeTabId.value = newTabId
        _activeOverlay.value = ActiveOverlay.NONE
        closeReaderMode()
        _pageSource.value = null
        closeFindInPage()
        persistOpenTabs()
      }
    }

    override fun onCloseTabRequested(tabId: String) {
      runOnMain {
        closeTab(tabId)
      }
    }
  }

  val webViewPool = WebViewPool(
    context = appContext,
    settingsProvider = { _settings.value },
    callbacks = webCallbacks
  )

  init {
    cleanExtensionStorage(appContext)
    refreshBookmarks()
    refreshHistory()
    webViewPool.trimMemoryAndDiskCaches(_activeTabId.value, aggressive = false)

    MediaPlaybackService.mediaControlListener = { action ->
      runOnMain {
        handleMediaControlAction(action)
      }
    }

    viewModelScope.launch {
      while (true) {
        delay(2500)
        if (_settings.value.backgroundPlayEnabled) {
          val tab = activeTab()
          if (tab.url.isNotBlank() && tab.url != "about:blank") {
            checkAndSyncMediaPlayback(forceStart = false)
          }
        }
      }
    }

    val retained = if (_settings.value.retainOpenTabs) settingsRepo.loadRetainedTabs() else null
    if (retained != null) {
      val (restoredTabs, restoredActiveId) = retained
      restoredTabs.forEach { t ->
        if (!t.isPrivate && t.url.isNotBlank()) {
          webViewPool.registerStandardOriginForVault(t.url)
        }
      }
      _tabs.value = restoredTabs
      _activeTabId.value = restoredActiveId
      val current = activeTab()
      if (current.url.isNotBlank() && UrlValidator.isSafeWebUrl(current.url)) {
        loadRestoredTabSafely(current.id, current.url)
      }
    } else {
      val home = _settings.value.homepageUrl
      if (home.isNotBlank() && UrlValidator.isSafeWebUrl(home)) {
        navigateActiveTab(home)
      }
    }
  }

  private fun loadRestoredTabSafely(tabId: String, rawUrl: String) {
    val tab = tabById(tabId) ?: return
    val s = _settings.value
    val res = UrlValidator.resolveInputToTarget(rawUrl, s.searchEngine, s.customSearchTemplate, s.httpsOnlyEnabled)
    if (res is ResolvedNavigation.LoadUrl) {
      val host = UrlValidator.extractHost(res.url)
      val customSet = s.customBlockedHosts.toSet()
      if (customSet.isNotEmpty() && TrackerBlocker.isHostMatchingSet(host, customSet)) {
        return
      }
      val originalHttp = if (res.upgradedFromHttp && res.url.startsWith("https://")) {
        "http://" + res.url.removePrefix("https://")
      } else {
        null
      }
      updateTab(tab.id) {
        it.copy(
          url = res.url,
          upgradedFromHttpUrl = originalHttp,
          isLoading = true,
          progress = 10,
          errorState = null,
          httpWarning = null
        )
      }
      webViewPool.getOrCreateWebView(tab.id, tab.isPrivate, tab.userAgentMode, approvedHttpHosts)
      webViewPool.loadUrl(tab.id, res.url)
    }
  }

  private fun persistOpenTabs() {
    if (_settings.value.retainOpenTabs) {
      settingsRepo.saveRetainedTabs(_tabs.value, _activeTabId.value)
    }
  }

  fun flushRetainedTabs() {
    persistOpenTabs()
  }

  fun getOrCreateWebView(tabId: String): WebView? {
    val tab = tabById(tabId) ?: activeTab()
    val safeFallback = if (tab.errorState == null && tab.httpWarning == null) tab.url else ""
    return webViewPool.getOrCreateWebView(
      tabId = tab.id,
      isPrivate = tab.isPrivate,
      userAgentMode = tab.userAgentMode,
      approvedHttpHosts = approvedHttpHosts,
      fallbackUrl = safeFallback
    )
  }

  fun getOrCreateActiveWebView(): WebView? = getOrCreateWebView(activeTab().id)

  fun activeTab(): TabInfo {
    val currentId = _activeTabId.value
    return _tabs.value.firstOrNull { it.id == currentId } ?: _tabs.value.first()
  }

  private fun tabById(id: String): TabInfo? = _tabs.value.firstOrNull { it.id == id }

  private fun updateTab(tabId: String, transform: (TabInfo) -> TabInfo) {
    _tabs.update { list -> list.map { if (it.id == tabId) transform(it) else it } }
  }

  fun navigateActiveTab(rawInput: String) {
    val tab = activeTab()
    val s = _settings.value
    when (val res = UrlValidator.resolveInputToTarget(rawInput, s.searchEngine, s.customSearchTemplate, s.httpsOnlyEnabled)) {
      ResolvedNavigation.Empty -> return
      is ResolvedNavigation.BlockedScheme -> {
        updateTab(tab.id) {
          it.copy(
            securityState = SecurityState.ERROR,
            errorState = PageError(
              type = PageErrorType.BLOCKED,
              failingUrl = UrlValidator.sanitizeDisplayText(res.rawInput, 180),
              host = res.scheme,
              reason = "Unsupported link (${res.scheme}:)"
            )
          )
        }
      }
      is ResolvedNavigation.LoadUrl -> {
        val host = UrlValidator.extractHost(res.url)
        val customSet = s.customBlockedHosts.toSet()
        if (customSet.isNotEmpty() && TrackerBlocker.isHostMatchingSet(host, customSet)) {
          updateTab(tab.id) {
            it.copy(
              securityState = SecurityState.ERROR,
              errorState = PageError(
                type = PageErrorType.BLOCKED,
                failingUrl = res.url,
                host = host,
                reason = host
              )
            )
          }
          return
        }
        closeReaderMode()
        _pageSource.value = null
        closeFindInPage()
        val originalHttp = if (res.upgradedFromHttp && res.url.startsWith("https://")) {
          "http://" + res.url.removePrefix("https://")
        } else {
          null
        }
        val effectiveUa = tab.userAgentMode
        updateTab(tab.id) {
          it.copy(
            url = res.url,
            userAgentMode = effectiveUa,
            upgradedFromHttpUrl = originalHttp,
            isLoading = true,
            progress = 10,
            errorState = null,
            httpWarning = null
          )
        }
        webViewPool.getOrCreateWebView(tab.id, tab.isPrivate, effectiveUa, approvedHttpHosts)
        webViewPool.loadUrl(tab.id, res.url)
        persistOpenTabs()
      }
    }
  }

  fun setUserAgentMode(mode: UserAgentMode) {
    val tab = activeTab()
    val updatedUrl = if (mode == UserAgentMode.DESKTOP && tab.url.isNotBlank()) {
      UrlValidator.toDesktopUrl(tab.url)
    } else {
      tab.url
    }
    updateTab(tab.id) { it.copy(userAgentMode = mode, url = updatedUrl) }
    webViewPool.applyUserAgentMode(tab.id, tab.isPrivate, mode)
    persistOpenTabs()
    showToast("${mode.label} site")
  }

  fun toggleDesktopMode() {
    val next = if (activeTab().userAgentMode == UserAgentMode.DESKTOP) {
      UserAgentMode.MOBILE
    } else {
      UserAgentMode.DESKTOP
    }
    setUserAgentMode(next)
  }

  fun toggleBrowserFullscreen() {
    _isBrowserFullscreen.update { !it }
  }

  fun exitBrowserFullscreen() {
    _isBrowserFullscreen.value = false
  }

  fun switchAdjacentTab(delta: Int): Boolean {
    val list = _tabs.value
    if (list.size <= 1) return false
    val idx = list.indexOfFirst { it.id == _activeTabId.value }
    if (idx < 0) return false
    val targetIdx = (idx + delta).coerceIn(0, list.lastIndex)
    if (targetIdx == idx) return false
    switchToTab(list[targetIdx].id)
    return true
  }

  private fun applyModeBoundaryTransition(wasPrivate: Boolean, nowPrivate: Boolean) {
    webViewPool.syncActiveModeBoundary(nowPrivate)
    if (wasPrivate && !nowPrivate && clipboardWrittenInPrivate) {
      wipeClipboard(silent = true)
    }
  }

  fun openNewTab(
    isPrivate: Boolean = false,
    initialUrl: String = "",
    switchToNewTab: Boolean = true
  ) {
    val wasPrivate = activeTab().isPrivate
    val target = initialUrl.ifBlank { if (!isPrivate) _settings.value.homepageUrl else "" }
    val newTab = TabInfo(
      id = UUID.randomUUID().toString(),
      url = target,
      title = if (isPrivate) {
        if (target.isNotBlank()) UrlValidator.extractHost(target).ifEmpty { "Private Tab" } else "Private Tab"
      } else {
        if (target.isNotBlank()) UrlValidator.extractHost(target).ifEmpty { "New Tab" } else "New Tab"
      },
      isPrivate = isPrivate,
      securityState = when {
        isPrivate -> SecurityState.PRIVATE
        UrlValidator.isHttpsUrl(target) -> SecurityState.SECURE_HTTPS
        UrlValidator.isHttpUrl(target) -> SecurityState.INSECURE_HTTP
        else -> SecurityState.HOME
      },
      isLoading = target.isNotBlank(),
      progress = if (target.isNotBlank()) 10 else 0
    )
    _tabs.update { it + newTab }

    if (switchToNewTab) {
      _activeTabId.value = newTab.id
      applyModeBoundaryTransition(wasPrivate = wasPrivate, nowPrivate = isPrivate)
      _activeOverlay.value = ActiveOverlay.NONE
      closeReaderMode()
      _pageSource.value = null
      closeFindInPage()

      if (target.isNotBlank() && UrlValidator.isSafeWebUrl(target)) {
        navigateActiveTab(target)
      } else {
        persistOpenTabs()
      }
    } else {
      if (target.isNotBlank() && UrlValidator.isSafeWebUrl(target)) {
        loadRestoredTabSafely(newTab.id, target)
      }
      persistOpenTabs()
      showToast("Opened in background")
    }
  }

  fun switchToTab(tabId: String) {
    val wasPrivate = activeTab().isPrivate
    val targetTab = _tabs.value.firstOrNull { it.id == tabId } ?: return
    _activeTabId.value = tabId
    applyModeBoundaryTransition(wasPrivate = wasPrivate, nowPrivate = targetTab.isPrivate)
    updateTab(tabId) { it.copy(lastActiveAt = System.currentTimeMillis()) }
    _activeOverlay.value = ActiveOverlay.NONE
    closeReaderMode()
    _pageSource.value = null
    closeFindInPage()
    persistOpenTabs()
    if (targetTab.url.isNotBlank() &&
      targetTab.errorState == null &&
      targetTab.httpWarning == null &&
      webViewPool.getLiveWebView(tabId) == null &&
      !webViewPool.hasSavedState(tabId)
    ) {
      loadRestoredTabSafely(tabId, targetTab.url)
    }
  }

  fun closeTab(tabId: String) {
    val wasPrivate = activeTab().isPrivate
    if (_fullscreenView.value != null && _activeTabId.value == tabId) {
      exitFullscreen()
    }
    val closing = tabById(tabId) ?: return
    if (!closing.isPrivate && closing.url.isNotBlank()) {
      _closedTabs.update { (listOf(ClosedTabRecord(closing.title, closing.url)) + it).take(15) }
    }
    webViewPool.destroyTab(tabId, isPrivate = closing.isPrivate)
    val remaining = _tabs.value.filterNot { it.id == tabId }
    if (remaining.isEmpty()) {
      val fresh = TabInfo(id = UUID.randomUUID().toString(), title = "New Tab")
      _tabs.value = listOf(fresh)
      _activeTabId.value = fresh.id
    } else {
      _tabs.value = remaining
      if (_activeTabId.value == tabId) {
        _activeTabId.value = remaining.last().id
      }
    }
    if (closing.isPrivate && _tabs.value.none { it.isPrivate }) {
      endPrivateSessionCleanup(emptyList())
    }
    val nextActive = activeTab()
    applyModeBoundaryTransition(wasPrivate = wasPrivate, nowPrivate = nextActive.isPrivate)
    persistOpenTabs()
    if (nextActive.url.isNotBlank() &&
      nextActive.errorState == null &&
      nextActive.httpWarning == null &&
      webViewPool.getLiveWebView(nextActive.id) == null &&
      !webViewPool.hasSavedState(nextActive.id)
    ) {
      loadRestoredTabSafely(nextActive.id, nextActive.url)
    }
  }

  fun moveTab(tabId: String, delta: Int) {
    _tabs.update { list ->
      val idx = list.indexOfFirst { it.id == tabId }
      if (idx < 0) return@update list
      val target = (idx + delta).coerceIn(0, list.lastIndex)
      if (idx == target) return@update list
      val mutable = list.toMutableList()
      val item = mutable.removeAt(idx)
      mutable.add(target, item)
      mutable
    }
    persistOpenTabs()
  }

  fun restoreLastClosedTab() {
    val list = _closedTabs.value
    val latest = list.firstOrNull() ?: return
    _closedTabs.value = list.drop(1)
    openNewTab(isPrivate = false, initialUrl = latest.url)
  }

  fun reopenClosedTab(url: String) {
    if (url.isBlank()) return
    _closedTabs.update { list ->
      val idx = list.indexOfFirst { it.url == url }
      if (idx >= 0) list.toMutableList().apply { removeAt(idx) } else list
    }
    openNewTab(isPrivate = false, initialUrl = url)
  }

  fun endAllPrivateTabs() {
    val privateIds = _tabs.value.filter { it.isPrivate }.map { it.id }
    val remaining = _tabs.value.filterNot { it.isPrivate }
    if (remaining.isEmpty()) {
      val fresh = TabInfo(id = UUID.randomUUID().toString(), title = "New Tab")
      _tabs.value = listOf(fresh)
      _activeTabId.value = fresh.id
    } else {
      _tabs.value = remaining
      if (privateIds.contains(_activeTabId.value)) {
        _activeTabId.value = remaining.last().id
      }
    }
    endPrivateSessionCleanup(privateIds)
    persistOpenTabs()
    val nextActive = activeTab()
    if (nextActive.url.isNotBlank() &&
      nextActive.errorState == null &&
      nextActive.httpWarning == null &&
      webViewPool.getLiveWebView(nextActive.id) == null &&
      !webViewPool.hasSavedState(nextActive.id)
    ) {
      loadRestoredTabSafely(nextActive.id, nextActive.url)
    }
    showToast("Private tabs closed")
  }

  private fun endPrivateSessionCleanup(privateIds: List<String>) {
    webViewPool.wipePrivateSessionData(privateIds)
    downloadController.clearPrivateDownloads()
    if (clipboardWrittenInPrivate) {
      wipeClipboard(silent = true)
    }
  }

  // Private-Mode-Only Safety Actions
  fun burnPrivateSession() {
    val privateIds = _tabs.value.filter { it.isPrivate }.map { it.id }
    val remaining = _tabs.value.filterNot { it.isPrivate }
    if (remaining.isEmpty()) {
      val fresh = TabInfo(id = UUID.randomUUID().toString(), title = "New Tab")
      _tabs.value = listOf(fresh)
      _activeTabId.value = fresh.id
    } else {
      _tabs.value = remaining
      if (privateIds.contains(_activeTabId.value)) {
        _activeTabId.value = remaining.last().id
      }
    }
    closeReaderMode()
    _pageSource.value = null
    closeFindInPage()
    _activeOverlay.value = ActiveOverlay.NONE
    endPrivateSessionCleanup(privateIds)
    wipeClipboard(silent = true)
    webViewPool.syncActiveModeBoundary(false)
    persistOpenTabs()
    showToast("Private session cleared")
  }

  fun rotatePrivateIdentity() {
    val tab = activeTab()
    if (!tab.isPrivate) return
    val privateIds = _tabs.value.filter { it.isPrivate }.map { it.id }
    webViewPool.purgePrivateRamData(privateIds, reloadTabId = if (tab.url.isNotBlank()) tab.id else null)
    showToast("Identity reset")
  }

  fun purgePrivateRamOnly() {
    val tab = activeTab()
    if (!tab.isPrivate) return
    val privateIds = _tabs.value.filter { it.isPrivate }.map { it.id }
    webViewPool.purgePrivateRamData(privateIds, reloadTabId = null)
    showToast("Private storage cleared")
  }

  fun wipeClipboard(silent: Boolean = false) {
    clipboardWrittenInPrivate = false
    try {
      val cm = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        cm.clearPrimaryClip()
      } else {
        cm.setPrimaryClip(ClipData.newPlainText("", ""))
      }
      if (!silent) showToast("Clipboard wiped")
    } catch (_: Throwable) {
      if (!silent) showToast("Clipboard cleared")
    }
  }

  fun goBack(): Boolean {
    if (_isBrowserFullscreen.value) {
      _isBrowserFullscreen.value = false
      return true
    }
    if (_pageSource.value != null) {
      _pageSource.value = null
      return true
    }
    if (_readerArticle.value != null) {
      closeReaderMode()
      return true
    }
    if (_findState.value.isVisible) {
      closeFindInPage()
      return true
    }
    if (_activeOverlay.value != ActiveOverlay.NONE) {
      _activeOverlay.value = ActiveOverlay.NONE
      return true
    }
    val tab = activeTab()
    val wv = webViewPool.getLiveWebView(tab.id)
    if (tab.httpWarning != null || tab.errorState != null) {
      val liveUrl = try { wv?.url.orEmpty() } catch (_: Throwable) { "" }
      val canGoBackNow = try { wv?.canGoBack() == true } catch (_: Throwable) { false }
      val failedSameUrl = liveUrl == tab.errorState?.failingUrl ||
        liveUrl == tab.httpWarning?.originalUrl ||
        (tab.httpWarning?.upgradeAttemptFailed == true &&
          liveUrl.startsWith("https://") &&
          UrlValidator.extractHost(liveUrl) == tab.httpWarning.host)
      if (wv != null && failedSameUrl && canGoBackNow) {
        updateTab(tab.id) { it.copy(httpWarning = null, errorState = null) }
        try {
          wv.goBack()
        } catch (_: Throwable) {
        }
      } else if (wv != null &&
        liveUrl.isNotBlank() &&
        liveUrl != "about:blank" &&
        UrlValidator.isSafeWebUrl(liveUrl) &&
        !failedSameUrl
      ) {
        val rawTitle = try { wv.title } catch (_: Throwable) { null }
        val restoredTitle = UrlValidator.sanitizeDisplayText(rawTitle, 180)
          .ifEmpty { UrlValidator.extractHost(liveUrl).ifEmpty { "Page" } }
        val canFwdNow = try { wv.canGoForward() } catch (_: Throwable) { false }
        updateTab(tab.id) {
          it.copy(
            httpWarning = null,
            errorState = null,
            url = liveUrl,
            title = restoredTitle,
            canGoBack = canGoBackNow,
            canGoForward = canFwdNow,
            securityState = when {
              it.isPrivate -> SecurityState.PRIVATE
              UrlValidator.isHttpsUrl(liveUrl) -> SecurityState.SECURE_HTTPS
              else -> SecurityState.INSECURE_HTTP
            }
          )
        }
      } else {
        updateTab(tab.id) {
          it.copy(
            httpWarning = null,
            errorState = null,
            url = "",
            title = if (it.isPrivate) "Private Tab" else "New Tab",
            canGoBack = false,
            canGoForward = false,
            securityState = if (it.isPrivate) SecurityState.PRIVATE else SecurityState.HOME
          )
        }
      }
      return true
    }
    try {
      if (wv != null && wv.canGoBack()) {
        try {
          wv.settings.cacheMode = android.webkit.WebSettings.LOAD_CACHE_ELSE_NETWORK
        } catch (_: Throwable) {}
        wv.goBack()
        return true
      }
    } catch (_: Throwable) {
    }
    return false
  }

  fun activeTabDetectedMedia(): List<DetectedMedia> {
    return _detectedMediaMap.value[_activeTabId.value].orEmpty()
  }

  fun captureFullPage(context: Context) {
    val tab = activeTab()
    if (tab.url.isBlank() || tab.url == "about:blank") {
      showToast("Open a webpage first")
      return
    }
    showToast("Capturing full page...")
    webViewPool.captureFullPageScreenshot(context, tab.id, tab.title) { fileName, success ->
      if (success) {
        showToast("Full page saved to Pictures/Kio: $fileName")
      } else {
        showToast("Capture failed: ${fileName ?: "Unknown error"}")
      }
    }
  }

  fun openUnifiedMedia() {
    val tab = activeTab()
    if (tab.url.isNotBlank() && tab.url != "about:blank") {
      webViewPool.extractPageImages(tab.id) { images ->
        runOnMain {
          _extractedImages.value = images
        }
      }
    }
    setOverlay(ActiveOverlay.MEDIA_DOWNLOADER)
  }

  fun openImageSniffer() {
    openUnifiedMedia()
  }

  fun downloadMedia(media: DetectedMedia) {
    val ext = when (media.formatLabel) {
      "MP4" -> ".mp4"
      "WEBM" -> ".webm"
      "MP3" -> ".mp3"
      "AUDIO" -> ".m4a"
      else -> ".mp4"
    }
    val name = UrlValidator.sanitizeFilename("${media.title.ifEmpty { "video" }}_${System.currentTimeMillis()}$ext")
    downloadController.enqueueDirectDownload(media.url, name, media.mimeType, activeTab().isPrivate)
    showToast("Downloading ${media.formatLabel}...")
  }

  fun downloadImages(images: List<String>) {
    if (images.isEmpty()) {
      showToast("No images selected")
      return
    }
    val isPriv = activeTab().isPrivate
    images.forEachIndexed { i, url ->
      val safeName = "Image_${i + 1}_${System.currentTimeMillis()}.jpg"
      downloadController.enqueueDirectDownload(url, safeName, "image/jpeg", isPriv)
    }
    showToast("Downloading ${images.size} images to Pictures...")
  }

  fun toggleScreenFilter() {
    val next = !_settings.value.screenFilterEnabled
    updateSettings { it.copy(screenFilterEnabled = next) }
    showToast(if (next) "Screen Filter enabled" else "Screen Filter disabled")
  }

  fun setScreenFilterType(type: ScreenFilterType) {
    updateSettings { it.copy(screenFilterType = type) }
  }

  fun setScreenFilterOpacity(opacity: Float) {
    updateSettings { it.copy(screenFilterOpacity = opacity.coerceIn(0.1f, 0.8f)) }
  }

  fun toggleCleanMode() {
    val next = !_settings.value.cleanModeEnabled
    updateSettings { it.copy(cleanModeEnabled = next) }
    val wv = webViewPool.getLiveWebView(_activeTabId.value)
    if (wv != null && next) {
      webViewPool.applyCleanModeCss(wv)
    }
    showToast(if (next) "Clean Mode on (ad/banner clutter hidden)" else "Clean Mode off")
  }

  fun goForward() {
    val wv = webViewPool.getLiveWebView(_activeTabId.value)
    try {
      if (wv != null && wv.canGoForward()) {
        wv.goForward()
      }
    } catch (_: Throwable) {
    }
  }

  fun reloadOrStop() {
    val tab = activeTab()
    val wv = webViewPool.getLiveWebView(tab.id)
    if (tab.isLoading) {
      try {
        wv?.stopLoading()
      } catch (_: Throwable) {
      }
      updateTab(tab.id) { it.copy(isLoading = false) }
      return
    }
    val errUrl = tab.errorState?.failingUrl.orEmpty()
    if (tab.errorState != null && errUrl.isNotBlank()) {
      navigateActiveTab(errUrl)
      return
    }
    if (wv == null) {
      if (tab.url.isNotBlank()) {
        navigateActiveTab(tab.url)
      }
      return
    }
    if (tab.url.isNotBlank()) {
      updateTab(tab.id) { it.copy(errorState = null, httpWarning = null) }
      try {
        wv.reload()
      } catch (_: Throwable) {
      }
    }
  }

  fun proceedToHttpAnyway() {
    val tab = activeTab()
    val warning = tab.httpWarning ?: return
    approvedHttpHosts.add(warning.host)
    updateTab(tab.id) { it.copy(httpWarning = null, isLoading = true) }
    webViewPool.getOrCreateWebView(tab.id, tab.isPrivate, tab.userAgentMode, approvedHttpHosts)
    webViewPool.loadUrl(tab.id, warning.originalUrl)
  }

  fun readClipboardText(): String? {
    // Strict Private Mode isolation: never peek external/standard clipboard into Private Mode
    if (activeTab().isPrivate) return null
    return try {
      val cm = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
      val item = cm.primaryClip?.getItemAt(0) ?: return null
      UrlValidator.sanitizeControlChars(item.coerceToText(appContext).toString()).takeIf { it.isNotBlank() }
    } catch (_: Throwable) {
      null
    }
  }

  fun copyTextToClipboard(text: String, toastMessage: String = "Copied") {
    if (text.isBlank()) return
    try {
      val cm = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
      val clipData = ClipData.newPlainText("Text", text)
      if (activeTab().isPrivate) {
        clipboardWrittenInPrivate = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          try {
            clipData.description.extras = PersistableBundle().apply {
              putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
          } catch (_: Throwable) {
          }
        }
      }
      cm.setPrimaryClip(clipData)
      // Android 13+ automatically shows a system clipboard confirmation overlay
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        showToast(toastMessage)
      }
    } catch (_: Throwable) {
      showToast("Copy unavailable")
    }
  }

  fun copyActiveUrl() {
    copyTextToClipboard(activeTab().url, "URL copied")
  }

  fun openInSystemBrowser() {
    if (activeTab().isPrivate) {
      showToast("Unavailable in Private Mode")
      return
    }
    val url = activeTab().url
    if (!UrlValidator.isSafeWebUrl(url) || url == "about:blank") return
    try {
      val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      appContext.startActivity(intent)
    } catch (_: Throwable) {
      showToast("No external browser found")
    }
  }

  fun translateActivePage() {
    val tab = activeTab()
    val url = tab.url
    if (!UrlValidator.isSafeWebUrl(url) || url == "about:blank") {
      showToast("Open a page to translate")
      return
    }

    val webView = webViewPool.getLiveWebView(tab.id)
    if (webView == null || !_settings.value.javascriptEnabled) {
      showToast("JavaScript is required for page translation")
      return
    }

    val targetLangCode = Locale.getDefault().language.ifEmpty { "en" }
    val targetLangName = Locale.getDefault().displayLanguage.ifBlank { targetLangCode }

    val extractJs = """
      (function() {
        // 1. If currently on a translated page, restore original DOM text nodes immediately
        if (window.__kio_is_translated) {
          try {
            if (window.__kio_orig_text_nodes) {
              for (var i = 0; i < window.__kio_orig_text_nodes.length; i++) {
                var it = window.__kio_orig_text_nodes[i];
                if (it.node && it.text != null) it.node.nodeValue = it.text;
              }
            }
            window.__kio_is_translated = false;
            var banner = document.getElementById('kio-translate-banner');
            if (banner) banner.remove();
            return JSON.stringify({ action: 'restored' });
          } catch(e) {
            return JSON.stringify({ action: 'error_restore' });
          }
        }

        // 2. Collect visible, non-script/style DOM text nodes from the live page
        var walker = document.createTreeWalker(
          document.body || document.documentElement,
          NodeFilter.SHOW_TEXT,
          {
            acceptNode: function(node) {
              if (!node || !node.nodeValue || !node.nodeValue.trim()) return NodeFilter.FILTER_REJECT;
              var parent = node.parentElement;
              if (!parent) return NodeFilter.FILTER_REJECT;
              var tag = parent.tagName;
              if (tag === 'SCRIPT' || tag === 'STYLE' || tag === 'CODE' || 
                  tag === 'PRE' || tag === 'TEXTAREA' || tag === 'INPUT' || 
                  tag === 'NOSCRIPT' || tag === 'SVG' || tag === 'AUDIO' || 
                  tag === 'VIDEO' || tag === 'CANVAS' || tag === 'OBJECT' || 
                  tag === 'IFRAME') {
                return NodeFilter.FILTER_REJECT;
              }
              if (parent.closest && parent.closest('[translate="no"], .notranslate, #kio-translate-banner')) {
                return NodeFilter.FILTER_REJECT;
              }
              return NodeFilter.FILTER_ACCEPT;
            }
          }
        );

        var origNodes = [];
        var texts = [];
        var currentNode;
        while ((currentNode = walker.nextNode()) && texts.length < 300) {
          var val = currentNode.nodeValue.trim();
          if (val.length > 1) {
            origNodes.push({ node: currentNode, text: currentNode.nodeValue });
            texts.push(val);
          }
        }

        if (texts.length === 0) {
          return JSON.stringify({ action: 'no_content' });
        }

        window.__kio_orig_text_nodes = origNodes;

        // 3. Create non-intrusive floating status banner
        var oldBanner = document.getElementById('kio-translate-banner');
        if (oldBanner) oldBanner.remove();
        var banner = document.createElement('div');
        banner.id = 'kio-translate-banner';
        banner.style.cssText = 'position:fixed;bottom:20px;left:50%;transform:translateX(-50%);z-index:999999;background:rgba(15,23,42,0.94);color:#F1F5F9;font-family:system-ui,-apple-system,sans-serif;font-size:12px;padding:8px 18px;border-radius:24px;border:1px solid rgba(0,242,254,0.35);box-shadow:0 6px 20px rgba(0,0,0,0.4);display:flex;align-items:center;gap:10px;pointer-events:auto;backdrop-filter:blur(10px);';
        banner.innerHTML = '<span style="color:#00F2FE;">🌐</span> Translating page in real time...';
        document.body.appendChild(banner);

        return JSON.stringify({ action: 'translate', texts: texts });
      })();
    """.trimIndent()

    webView.evaluateJavascript(extractJs) { rawResult ->
      if (rawResult.isNullOrBlank() || rawResult == "null") return@evaluateJavascript
      try {
        val unescaped = JSONObject("{\"res\":$rawResult}").getString("res")
        val json = JSONObject(unescaped)
        val action = json.optString("action")
        when (action) {
          "restored" -> {
            showToast("Original page restored")
            return@evaluateJavascript
          }
          "no_content" -> {
            showToast("No translatable text found on this page")
            return@evaluateJavascript
          }
          "translate" -> {
            val textsArray = json.optJSONArray("texts") ?: return@evaluateJavascript
            val textList = mutableListOf<String>()
            for (i in 0 until textsArray.length()) {
              textList.add(textsArray.getString(i))
            }
            showToast("Translating page to $targetLangName...")

            viewModelScope.launch(Dispatchers.IO) {
              val batchSize = 35
              val chunks = textList.chunked(batchSize)
              var currentOffset = 0

              for (chunk in chunks) {
                val translatedChunk = PageTranslationService.translateBatch(chunk, targetLangCode, targetLangName)
                val chunkJsonArray = JSONArray()
                for (t in translatedChunk) {
                  chunkJsonArray.put(t)
                }

                withContext(Dispatchers.Main) {
                  val liveWv = webViewPool.getLiveWebView(tab.id)
                  val applyChunkJs = """
                    (function() {
                      var offset = $currentOffset;
                      var translations = $chunkJsonArray;
                      if (window.__kio_orig_text_nodes) {
                        for (var k = 0; k < translations.length; k++) {
                          var targetIndex = offset + k;
                          if (targetIndex < window.__kio_orig_text_nodes.length) {
                            var item = window.__kio_orig_text_nodes[targetIndex];
                            if (item && item.node && translations[k]) {
                              item.node.nodeValue = translations[k];
                            }
                          }
                        }
                      }
                    })();
                  """.trimIndent()
                  liveWv?.evaluateJavascript(applyChunkJs, null)
                }
                currentOffset += chunk.size
              }

              // Finalize floating status badge with 1-tap restore toggle
              withContext(Dispatchers.Main) {
                val liveWv = webViewPool.getLiveWebView(tab.id)
                val finalizeJs = """
                  (function() {
                    window.__kio_is_translated = true;
                    var banner = document.getElementById('kio-translate-banner');
                    if (banner) {
                      banner.innerHTML = '<span style="color:#00F2FE;">🌐</span> Translated to $targetLangName (Real-Time Translate) <button id="kio-restore-btn" style="background:rgba(255,255,255,0.18);border:none;color:#FFF;padding:3px 10px;border-radius:12px;cursor:pointer;margin-left:8px;font-size:11px;font-weight:600;">Restore</button>';
                      var btn = document.getElementById('kio-restore-btn');
                      if (btn) {
                        btn.onclick = function() {
                          if (window.__kio_orig_text_nodes) {
                            for (var m = 0; m < window.__kio_orig_text_nodes.length; m++) {
                              var it = window.__kio_orig_text_nodes[m];
                              if (it.node && it.text != null) it.node.nodeValue = it.text;
                            }
                          }
                          window.__kio_is_translated = false;
                          banner.remove();
                        };
                      }
                    }
                  })();
                """.trimIndent()
                liveWv?.evaluateJavascript(finalizeJs, null)
                showToast("Page translated to $targetLangName")
              }
            }
          }
        }
      } catch (_: Throwable) {
      }
    }
  }

  fun ensureMediaServiceRunningSynchronously() {
    val tab = activeTab()
    if (!_settings.value.backgroundPlayEnabled || tab.url.isBlank() || tab.url == "about:blank") return
    val host = UrlValidator.extractHost(tab.url).ifEmpty { "Playing" }
    val title = tab.title.ifBlank { "Web Stream" }
    MediaPlaybackService.start(
      context = appContext,
      title = title,
      domain = host,
      isPlaying = true
    )
  }

  fun handleMediaControlAction(action: String) {
    val tab = activeTab()
    val webView = webViewPool.getLiveWebView(tab.id) ?: return
    if (!_settings.value.javascriptEnabled) return
    val js = when (action) {
      MediaPlaybackService.ACTION_PLAY -> """
        (function() {
          var player = document.querySelector('#movie_player') || document.querySelector('.html5-video-player');
          if (player && typeof player.playVideo === 'function') {
            player.playVideo();
          }
          var media = document.querySelectorAll('video, audio');
          for (var i = 0; i < media.length; i++) {
            media[i].__kio_user_paused = false;
            if (media[i].paused) {
              media[i].play().catch(function() {});
            }
          }
        })();
      """.trimIndent()
      MediaPlaybackService.ACTION_PAUSE -> """
        (function() {
          var player = document.querySelector('#movie_player') || document.querySelector('.html5-video-player');
          if (player && typeof player.pauseVideo === 'function') {
            player.pauseVideo();
          }
          var media = document.querySelectorAll('video, audio');
          for (var i = 0; i < media.length; i++) {
            media[i].__kio_user_paused = true;
            if (!media[i].paused) {
              media[i].pause();
            }
          }
        })();
      """.trimIndent()
      MediaPlaybackService.ACTION_PREV -> """
        (function() {
          var player = document.querySelector('#movie_player') || document.querySelector('.html5-video-player');
          if (player && typeof player.seekTo === 'function' && typeof player.getCurrentTime === 'function') {
            player.seekTo(Math.max(0, player.getCurrentTime() - 10), true);
          } else {
            var media = document.querySelectorAll('video, audio');
            for (var i = 0; i < media.length; i++) {
              media[i].currentTime = Math.max(0, (media[i].currentTime || 0) - 10);
            }
          }
        })();
      """.trimIndent()
      MediaPlaybackService.ACTION_NEXT -> """
        (function() {
          var player = document.querySelector('#movie_player') || document.querySelector('.html5-video-player');
          if (player && typeof player.nextVideo === 'function') {
            player.nextVideo();
          } else if (player && typeof player.seekTo === 'function' && typeof player.getCurrentTime === 'function') {
            player.seekTo(player.getCurrentTime() + 10, true);
          } else {
            var nextBtn = document.querySelector('.ytp-next-button, [aria-label="Next"], [title="Next"]');
            if (nextBtn) {
              nextBtn.click();
            } else {
              var media = document.querySelectorAll('video, audio');
              for (var i = 0; i < media.length; i++) {
                media[i].currentTime = (media[i].currentTime || 0) + 10;
              }
            }
          }
        })();
      """.trimIndent()
      MediaPlaybackService.ACTION_STOP -> """
        (function() {
          var player = document.querySelector('#movie_player') || document.querySelector('.html5-video-player');
          if (player && typeof player.stopVideo === 'function') {
            player.stopVideo();
          } else if (player && typeof player.pauseVideo === 'function') {
            player.pauseVideo();
          }
          var media = document.querySelectorAll('video, audio');
          for (var i = 0; i < media.length; i++) {
            media[i].__kio_user_paused = true;
            media[i].pause();
          }
        })();
      """.trimIndent()
      else -> return
    }
    try {
      webView.evaluateJavascript(js, null)
    } catch (_: Throwable) {
    }
  }

  fun checkAndSyncMediaPlayback(forceStart: Boolean = false) {
    val tab = activeTab()
    val webView = webViewPool.getLiveWebView(tab.id)
    if (webView == null || !_settings.value.javascriptEnabled || !_settings.value.backgroundPlayEnabled) {
      if (MediaPlaybackService.isServiceRunning && !forceStart) {
        MediaPlaybackService.stop(appContext)
      }
      return
    }

    val probeJs = """
      (function() {
        try {
          var player = document.querySelector('#movie_player') || document.querySelector('.html5-video-player');
          var ytPlaying = false;
          if (player && typeof player.getPlayerState === 'function') {
            var state = player.getPlayerState();
            ytPlaying = (state === 1 || state === 3);
          }
          var media = document.querySelectorAll('video, audio');
          var playing = ytPlaying;
          for (var i = 0; i < media.length; i++) {
            var m = media[i];
            if (!m.paused && !m.ended && m.readyState > 1) {
              playing = true;
              break;
            }
          }
          var docTitle = '';
          var ytTitleEl = document.querySelector('h1.title, .slim-video-metadata-title, .video-details h1');
          if (ytTitleEl && ytTitleEl.innerText) {
            docTitle = ytTitleEl.innerText.trim();
          }
          if (!docTitle) {
            docTitle = document.title || '';
          }
          var artUrl = '';
          try {
            var ms = navigator.mediaSession && navigator.mediaSession.metadata;
            if (ms && ms.artwork && ms.artwork.length > 0) {
              artUrl = ms.artwork[ms.artwork.length - 1].src || ms.artwork[0].src || '';
            }
            if (!artUrl) {
              var v = document.querySelector('video[poster]');
              if (v) artUrl = v.getAttribute('poster') || '';
            }
            if (!artUrl) {
              var og = document.querySelector('meta[property="og:image"]');
              if (og) artUrl = og.getAttribute('content') || '';
            }
          } catch(e) {}
          return JSON.stringify({
            playing: playing,
            count: media.length + (player ? 1 : 0),
            title: docTitle,
            artwork: artUrl
          });
        } catch(e) {
          return JSON.stringify({ playing: false, count: 0, title: '', artwork: '' });
        }
      })()
    """.trimIndent()

    try {
      webView.evaluateJavascript(probeJs) { result ->
        if (result.isNullOrBlank() || result == "null") return@evaluateJavascript
        try {
          val unquoted = if (result.startsWith("\"") && result.endsWith("\"") && result.length >= 2) {
            JSONObject(JSONTokener(result).nextValue().toString())
          } else {
            JSONObject(result)
          }
          val isPlaying = unquoted.optBoolean("playing", false)
          val mediaCount = unquoted.optInt("count", 0)
          val docTitle = unquoted.optString("title", "")
          val artUrl = unquoted.optString("artwork", "")

          if (isPlaying || (forceStart && mediaCount > 0)) {
            val pageTitle = docTitle.ifBlank { tab.title }.ifBlank { "Web Stream" }
            val host = UrlValidator.extractHost(tab.url).ifEmpty { "Playing" }
            MediaPlaybackService.start(
              context = appContext,
              title = pageTitle,
              domain = host,
              isPlaying = isPlaying,
              artworkUrl = artUrl
            )
          } else if (mediaCount == 0 && MediaPlaybackService.isServiceRunning) {
            MediaPlaybackService.stop(appContext)
          }
        } catch (_: Throwable) {
        }
      }
    } catch (_: Throwable) {
    }
  }

  fun shareUrl(url: String) {
    if (activeTab().isPrivate) {
      showToast("Unavailable in Private Mode")
      return
    }
    if (!UrlValidator.isSafeWebUrl(url) || url == "about:blank") return
    try {
      val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, url)
      }
      val chooser = Intent.createChooser(intent, "Share").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      appContext.startActivity(chooser)
    } catch (_: Throwable) {
      showToast("Unable to share")
    }
  }

  fun shareActiveUrl() {
    shareUrl(activeTab().url)
  }

  fun dismissLinkContext() {
    _linkContextTarget.value = null
  }

  fun downloadUrlWithConsent(url: String) {
    downloadController.requestDownloadConsent(
      url = url,
      contentDisposition = null,
      mimeType = null,
      contentLength = -1L,
      isPrivateTab = activeTab().isPrivate
    )
  }

  // Bookmarks
  fun refreshBookmarks(query: String = bookmarkQuery) {
    bookmarkQuery = query
    viewModelScope.launch(Dispatchers.IO) {
      try {
        _bookmarks.value = db.getBookmarks(query)
      } catch (_: Throwable) {
      }
    }
  }

  fun isCurrentPageBookmarked(): Boolean {
    val url = activeTab().url
    return url.isNotBlank() && _bookmarks.value.any { it.url == url }
  }

  fun toggleCurrentBookmark() {
    val tab = activeTab()
    if (tab.isPrivate) {
      showToast("Bookmarks unavailable in Private Mode")
      return
    }
    if (!UrlValidator.isSafeWebUrl(tab.url) || tab.url == "about:blank") return
    viewModelScope.launch(Dispatchers.IO) {
      try {
        if (db.isBookmarked(tab.url)) {
          db.deleteBookmarkByUrl(tab.url)
          showToast("Bookmark removed")
        } else {
          db.upsertBookmark(tab.title, tab.url)
          showToast("Bookmark saved")
        }
        _bookmarks.value = db.getBookmarks(bookmarkQuery)
      } catch (_: Throwable) {
      }
    }
  }

  fun normalizeWebUrlInput(rawUrl: String): String {
    val cleaned = UrlValidator.sanitizeControlChars(rawUrl)
    if (cleaned.isEmpty()) return ""
    return if (!cleaned.contains("://") && !cleaned.contains(' ') && cleaned.contains('.')) {
      "https://$cleaned"
    } else {
      cleaned
    }
  }

  fun addOrUpdateBookmark(id: Long?, title: String, url: String, folder: String): Boolean {
    val normalized = normalizeWebUrlInput(url)
    if (!UrlValidator.isSafeWebUrl(normalized) || normalized == "about:blank") {
      showToast("Enter a valid URL")
      return false
    }
    viewModelScope.launch(Dispatchers.IO) {
      try {
        if (id == null || id <= 0L) {
          db.upsertBookmark(title, normalized, folder)
        } else {
          db.updateBookmark(id, title, normalized, folder)
        }
        _bookmarks.value = db.getBookmarks(bookmarkQuery)
      } catch (_: Throwable) {
      }
    }
    return true
  }

  fun deleteBookmark(id: Long) {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        db.deleteBookmarkById(id)
        _bookmarks.value = db.getBookmarks(bookmarkQuery)
      } catch (_: Throwable) {
      }
    }
  }

  fun exportBookmarksHtml(): String = BookmarkImportExport.exportToNetscapeHtml(_bookmarks.value)

  fun importBookmarksFromText(rawContent: String) {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        val parsed = BookmarkImportExport.parseImportedBookmarks(rawContent)
        parsed.forEach { db.upsertBookmark(it.title, it.url, it.folder) }
        _bookmarks.value = db.getBookmarks(bookmarkQuery)
        withContext(Dispatchers.Main) {
          showToast("Imported ${parsed.size} bookmarks")
        }
      } catch (_: Throwable) {
        withContext(Dispatchers.Main) {
          showToast("Import failed")
        }
      }
    }
  }

  // History
  fun refreshHistory(query: String = historyQuery) {
    historyQuery = query
    viewModelScope.launch(Dispatchers.IO) {
      try {
        _history.value = db.getHistory(query)
      } catch (_: Throwable) {
      }
    }
  }

  fun deleteHistoryEntry(id: Long) {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        db.deleteHistoryEntry(id)
        _history.value = db.getHistory(historyQuery)
      } catch (_: Throwable) {
      }
    }
  }

  fun clearAllHistory() {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        db.clearAllHistory()
        _history.value = emptyList()
        withContext(Dispatchers.Main) { showToast("History cleared") }
      } catch (_: Throwable) {
      }
    }
  }

  // Find in page
  fun openFindInPage() {
    _findState.value = FindState(isVisible = true)
  }

  fun updateFindQuery(query: String) {
    _findState.update { it.copy(query = query, activeIndex = 0, totalMatches = 0) }
    val wv = webViewPool.getLiveWebView(_activeTabId.value) ?: return
    try {
      if (query.isEmpty()) {
        wv.clearMatches()
      } else {
        wv.findAllAsync(query)
      }
    } catch (_: Throwable) {
    }
  }

  fun findNext(forward: Boolean) {
    try {
      webViewPool.getLiveWebView(_activeTabId.value)?.findNext(forward)
    } catch (_: Throwable) {
    }
  }

  fun closeFindInPage() {
    _findState.value = FindState(isVisible = false)
    try {
      webViewPool.getLiveWebView(_activeTabId.value)?.clearMatches()
    } catch (_: Throwable) {
    }
  }

  // Pure On-Device NLP Content Announcer / Reader Mode (Volatile RAM only — zero copy/save)
  fun toggleReaderMode() {
    if (_readerArticle.value != null) {
      closeReaderMode()
      return
    }
    val tab = activeTab()
    if (tab.url.isBlank()) {
      showToast("Open a web page to narrate")
      return
    }
    webViewPool.extractReaderArticle(tab.id, tab.url) { article ->
      if (article == null) {
        showToast("No readable text found")
      } else {
        val segments = contentAnnouncer.startArticle(article, autoSpeak = true)
        _readerSegments.value = segments
        _readerArticle.value = article
      }
    }
  }

  fun closeReaderMode() {
    contentAnnouncer.wipeAndStop()
    _readerSegments.value = emptyList()
    _readerArticle.value = null
  }

  fun openPageSource() {
    val tab = activeTab()
    if (tab.url.isBlank()) return
    webViewPool.extractPageSource(tab.id) { html ->
      if (html == null) {
        showToast("Source unavailable")
      } else {
        _pageSource.value = tab.url to html
      }
    }
  }

  fun closePageSource() {
    _pageSource.value = null
  }

  // Permissions & External links
  fun resolvePermissionPrompt(allowed: Boolean, rememberSite: Boolean) {
    val prompt = _permissionPrompt.value
    _permissionPrompt.value = null
    val cb = pendingPermissionCallback
    pendingPermissionCallback = null
    cb?.invoke(allowed)
    if (allowed && prompt?.kind == WebPermissionKind.CAMERA_MIC) {
      onRequestMediaPermissions?.invoke()
    }
    if (prompt != null && rememberSite && !activeTab().isPrivate) {
      _sitePermissions.update { current ->
        val filtered = current.filterNot { it.origin == prompt.origin && it.kind == prompt.kind }
        val updated = filtered + SitePermissionRule(prompt.origin, prompt.kind, allowed)
        settingsRepo.saveSitePermissions(updated)
        updated
      }
    }
  }

  fun removeSitePermissionRule(rule: SitePermissionRule) {
    _sitePermissions.update { current ->
      val updated = current.filterNot { it.origin == rule.origin && it.kind == rule.kind }
      settingsRepo.saveSitePermissions(updated)
      updated
    }
  }

  fun dismissExternalLinkPrompt() {
    _externalLinkPrompt.value = null
  }

  fun confirmExternalLinkPrompt() {
    val prompt = _externalLinkPrompt.value ?: return
    _externalLinkPrompt.value = null
    try {
      val intent = if (prompt.url.startsWith("intent:", ignoreCase = true)) {
        Intent.parseUri(prompt.url, Intent.URI_INTENT_SCHEME).apply {
          addCategory(Intent.CATEGORY_BROWSABLE)
          component = null
          selector = null
        }
      } else {
        Intent(Intent.ACTION_VIEW, Uri.parse(prompt.url)).apply {
          addCategory(Intent.CATEGORY_BROWSABLE)
        }
      }
      intent.flags = (intent.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION).inv()) or
        Intent.FLAG_ACTIVITY_NEW_TASK

      try {
        appContext.startActivity(intent)
      } catch (_: Exception) {
        val fallbackUrl = intent.getStringExtra("browser_fallback_url")
        if (!fallbackUrl.isNullOrBlank() && UrlValidator.isSafeWebUrl(fallbackUrl)) {
          openNewTab(isPrivate = activeTab().isPrivate, initialUrl = fallbackUrl)
        } else {
          showToast("No app available for ${prompt.scheme}:")
        }
      }
    } catch (_: Throwable) {
      showToast("Invalid link (${prompt.scheme}:)")
    }
  }

  fun deliverChosenFiles(uris: Array<Uri>?) {
    val cb = pendingFileChooserCallback
    pendingFileChooserCallback = null
    try {
      cb?.onReceiveValue(uris)
    } catch (_: Throwable) {
    }
  }

  internal fun setCustomFullscreen(view: View?, callback: WebChromeClient.CustomViewCallback?) {
    _fullscreenView.value = if (view != null) view to callback else null
  }

  fun exitFullscreen() {
    val pair = _fullscreenView.value ?: return
    _fullscreenView.value = null
    try {
      pair.second?.onCustomViewHidden()
    } catch (_: Throwable) {
    }
  }

  // Custom block rules
  fun addCustomBlockedHost(rawHost: String): Boolean {
    val normalized = TrackerBlocker.normalizeCustomHost(rawHost)
    if (normalized.isEmpty()) return false
    updateSettings { cur ->
      if (normalized in cur.customBlockedHosts) cur
      else cur.copy(customBlockedHosts = cur.customBlockedHosts + normalized)
    }
    return true
  }

  fun removeCustomBlockedHost(host: String) {
    updateSettings { cur ->
      cur.copy(customBlockedHosts = cur.customBlockedHosts.filterNot { it == host })
    }
  }

  // Settings
  fun updateSettings(transform: (SettingsState) -> SettingsState) {
    val updated = transform(_settings.value)
    _settings.value = updated
    settingsRepo.saveSettings(updated)
    if (updated.retainOpenTabs) {
      settingsRepo.saveRetainedTabs(_tabs.value, _activeTabId.value)
    } else {
      settingsRepo.clearRetainedTabs()
    }
    webViewPool.applySettingsToAll(updated) { id ->
      val t = tabById(id)
      (t?.isPrivate == true) to (t?.userAgentMode ?: UserAgentMode.MOBILE)
    }
  }

  fun clearSelectedBrowsingData(options: ClearDataOptions) {
    webViewPool.clearBrowsingData(options)
    if (options.clearCookies) {
      approvedHttpHosts.clear()
      downloadController.clearAllDownloads()
    }
    if (options.clearSitePermissions) {
      settingsRepo.clearAllSitePermissions()
      _sitePermissions.value = emptyList()
    }
    if (options.clearHistory) {
      settingsRepo.clearRetainedTabs()
      viewModelScope.launch(Dispatchers.IO) {
        try {
          db.clearAllHistory()
          _history.value = emptyList()
        } catch (_: Throwable) {
        }
      }
    }
    showToast("Browsing data cleared")
  }

  fun setOverlay(overlay: ActiveOverlay) {
    if (activeTab().isPrivate &&
      (overlay == ActiveOverlay.BOOKMARKS || overlay == ActiveOverlay.HISTORY)
    ) {
      showToast("Unavailable in Private Mode")
      return
    }
    _activeOverlay.value = overlay
    if (overlay == ActiveOverlay.BOOKMARKS) refreshBookmarks("")
    if (overlay == ActiveOverlay.HISTORY) refreshHistory("")
  }

  fun showToast(message: String) {
    _statusToast.value = message
  }

  fun consumeToast() {
    _statusToast.value = null
  }

  override fun onCleared() {
    super.onCleared()
    contentAnnouncer.shutdown()
    MediaPlaybackService.mediaControlListener = null
    MediaPlaybackService.stop(appContext)
    webViewPool.destroyAll()
  }

  companion object {
    fun cleanExtensionStorage(context: Context) {
      try {
        val extDir = context.getDir("chrome_extensions", Context.MODE_PRIVATE)
        if (extDir.exists()) {
          extDir.deleteRecursively()
        }
        val cacheExtDir = java.io.File(context.cacheDir, "extensions_download")
        if (cacheExtDir.exists()) {
          cacheExtDir.deleteRecursively()
        }
        val prefs = context.getSharedPreferences("chrome_extensions_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
      } catch (_: Throwable) {
      }
    }
  }
}
