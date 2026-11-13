package com.example.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BookmarkImportExport
import com.example.data.LocalBrowserDatabase
import com.example.data.SettingsRepository
import com.example.download.DownloadController
import com.example.media.MediaPlaybackService
import com.example.model.BookmarkItem
import com.example.model.ClearDataOptions
import com.example.model.ClosedTabRecord
import com.example.model.DownloadRecord
import com.example.model.ExternalLinkPrompt
import com.example.model.HistoryItem
import com.example.model.HttpWarningState
import com.example.model.LinkContextTarget
import com.example.model.PageError
import com.example.model.PageErrorType
import com.example.model.PendingDownloadConsent
import com.example.model.ReaderArticle
import com.example.model.SecurityState
import com.example.model.SettingsState
import com.example.model.SitePermissionRule
import com.example.model.SniffedResource
import com.example.model.TabInfo
import com.example.model.UserAgentMode
import com.example.model.WebPermissionKind
import com.example.model.WebPermissionPrompt
import com.example.security.ResolvedNavigation
import com.example.security.TrackerBlocker
import com.example.security.UrlValidator
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
  RESOURCES,
  SETTINGS
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

  private val _readerArticle = MutableStateFlow<ReaderArticle?>(null)
  val readerArticle: StateFlow<ReaderArticle?> = _readerArticle.asStateFlow()

  private val _pageSource = MutableStateFlow<Pair<String, String>?>(null)
  val pageSource: StateFlow<Pair<String, String>?> = _pageSource.asStateFlow()

  private val _isBrowserFullscreen = MutableStateFlow(false)
  val isBrowserFullscreen: StateFlow<Boolean> = _isBrowserFullscreen.asStateFlow()

  private val _showIntro = MutableStateFlow(!settingsRepo.hasSeenIntro())
  val showIntro: StateFlow<Boolean> = _showIntro.asStateFlow()

  fun dismissIntro() {
    _showIntro.value = false
    settingsRepo.setIntroSeen(true)
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

  private val approvedHttpHosts: MutableSet<String> = ConcurrentHashMap.newKeySet()
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

  private val webCallbacks = object : WebViewCallbacks {
    override fun onPageStarted(tabId: String, url: String) {
      runOnMain {
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
            sniffedResourcesCount = 0,
            faviconInitial = UrlValidator.extractHost(url).firstOrNull()?.uppercase() ?: ""
          )
        }
        if (tabById(tabId)?.isPrivate != true) {
          persistOpenTabs()
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

    override fun onResourceSniffed(tabId: String, count: Int) {
      runOnMain {
        updateTab(tabId) { it.copy(sniffedResourcesCount = count) }
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
  }

  val webViewPool = WebViewPool(
    context = appContext,
    settingsProvider = { _settings.value },
    callbacks = webCallbacks
  )

  init {
    refreshBookmarks()
    refreshHistory()

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

  fun getOrCreateActiveWebView(): WebView? {
    val tab = activeTab()
    val safeFallback = if (tab.errorState == null && tab.httpWarning == null) tab.url else ""
    return webViewPool.getOrCreateWebView(
      tabId = tab.id,
      isPrivate = tab.isPrivate,
      userAgentMode = tab.userAgentMode,
      approvedHttpHosts = approvedHttpHosts,
      fallbackUrl = safeFallback
    )
  }

  fun getActiveSniffedResources(): List<SniffedResource> =
    webViewPool.getSniffedResources(activeTab().id)

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
        _readerArticle.value = null
        _pageSource.value = null
        closeFindInPage()
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
            httpWarning = null,
            sniffedResourcesCount = 0
          )
        }
        webViewPool.getOrCreateWebView(tab.id, tab.isPrivate, tab.userAgentMode, approvedHttpHosts)
        webViewPool.loadUrl(tab.id, res.url)
        persistOpenTabs()
      }
    }
  }

  fun setUserAgentMode(mode: UserAgentMode) {
    val tab = activeTab()
    updateTab(tab.id) { it.copy(userAgentMode = mode) }
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

  fun openNewTab(isPrivate: Boolean = false, initialUrl: String = "") {
    val newTab = TabInfo(
      id = UUID.randomUUID().toString(),
      title = if (isPrivate) "Private Tab" else "New Tab",
      isPrivate = isPrivate,
      securityState = if (isPrivate) SecurityState.PRIVATE else SecurityState.HOME
    )
    _tabs.update { it + newTab }
    _activeTabId.value = newTab.id
    _activeOverlay.value = ActiveOverlay.NONE
    _readerArticle.value = null
    _pageSource.value = null
    closeFindInPage()

    val target = initialUrl.ifBlank { if (!isPrivate) _settings.value.homepageUrl else "" }
    if (target.isNotBlank() && UrlValidator.isSafeWebUrl(target)) {
      navigateActiveTab(target)
    } else {
      persistOpenTabs()
    }
  }

  fun switchToTab(tabId: String) {
    val targetTab = _tabs.value.firstOrNull { it.id == tabId } ?: return
    _activeTabId.value = tabId
    updateTab(tabId) { it.copy(lastActiveAt = System.currentTimeMillis()) }
    _activeOverlay.value = ActiveOverlay.NONE
    _readerArticle.value = null
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
    val closing = tabById(tabId) ?: return
    if (!closing.isPrivate && closing.url.isNotBlank()) {
      _closedTabs.update { (listOf(ClosedTabRecord(closing.title, closing.url)) + it).take(15) }
    }
    webViewPool.destroyTab(tabId)
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
      _readerArticle.value = null
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
        wv.goBack()
        return true
      }
    } catch (_: Throwable) {
    }
    return false
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
      cm.setPrimaryClip(ClipData.newPlainText("Text", text))
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

    // 1. If currently on a translated page, restore original
    if (url.contains(".translate.goog") || url.contains("translate.google.com")) {
      restoreOriginalPage(url)
      return
    }

    val targetLang = Locale.getDefault().language.ifEmpty { "en" }
    val targetLangName = Locale.getDefault().displayLanguage.ifBlank { targetLang }

    // 2. Try fast in-page DOM translation first via googtrans cookie & TranslateElement
    val webView = webViewPool.getLiveWebView(tab.id)
    if (webView != null && _settings.value.javascriptEnabled) {
      showToast("Translating page to $targetLangName...")
      val inPageJs = """
        (function() {
          try {
            var targetLang = '$targetLang';
            document.cookie = 'googtrans=/auto/' + targetLang + '; path=/;';
            try {
              var hostParts = location.hostname.split('.');
              if (hostParts.length >= 2) {
                document.cookie = 'googtrans=/auto/' + targetLang + '; path=/; domain=.' + hostParts.slice(-2).join('.');
              }
            } catch(e) {}

            if (window.google && window.google.translate) {
              var combo = document.querySelector('.goog-te-combo');
              if (combo) {
                combo.value = targetLang;
                combo.dispatchEvent(new Event('change'));
                return;
              }
            }

            window.googleTranslateElementInit = function() {
              try {
                new google.translate.TranslateElement({
                  pageLanguage: 'auto',
                  includedLanguages: targetLang,
                  autoDisplay: false
                }, 'shiz_trans_elem');
              } catch(e) {}
            };

            var elem = document.getElementById('shiz_trans_elem');
            if (!elem) {
              elem = document.createElement('div');
              elem.id = 'shiz_trans_elem';
              elem.style.display = 'none';
              document.body.appendChild(elem);
            }

            var script = document.createElement('script');
            script.src = 'https://translate.google.com/translate_a/element.js?cb=googleTranslateElementInit';
            script.onerror = function() {
              window.__shiz_trans_failed = true;
            };
            document.body.appendChild(script);

            setTimeout(function() {
              if (!window.google || !window.google.translate) {
                window.__shiz_trans_failed = true;
              }
            }, 1800);
          } catch(e) {
            window.__shiz_trans_failed = true;
          }
        })();
      """.trimIndent()

      webView.evaluateJavascript(inPageJs) { _ ->
        mainHandler.postDelayed({
          val checkJs = "window.__shiz_trans_failed === true"
          webView.evaluateJavascript(checkJs) { failedResult ->
            if (failedResult == "true") {
              navigateToTranslateProxy(url, targetLang)
            }
          }
        }, 1900)
      }
    } else {
      navigateToTranslateProxy(url, targetLang)
    }
  }

  private fun navigateToTranslateProxy(url: String, targetLang: String) {
    try {
      val uri = Uri.parse(url)
      val host = uri.host ?: ""
      val scheme = uri.scheme ?: "https"
      if (host.isNotEmpty()) {
        val dashedHost = host.replace("-", "--").replace(".", "-") + ".translate.goog"
        val pathAndQuery = buildString {
          append((uri.path ?: "").ifEmpty { "/" })
          val existingQuery = uri.query
          append("?_x_tr_sl=auto&_x_tr_tl=").append(targetLang).append("&_x_tr_hl=").append(targetLang)
          if (!existingQuery.isNullOrBlank()) {
            append("&").append(existingQuery)
          }
        }
        val targetUrl = "$scheme://$dashedHost$pathAndQuery"
        navigateActiveTab(targetUrl)
        return
      }
    } catch (_: Throwable) {
    }
    val encoded = URLEncoder.encode(url, StandardCharsets.UTF_8.name())
    navigateActiveTab("https://translate.google.com/translate?sl=auto&tl=$targetLang&u=$encoded")
  }

  private fun restoreOriginalPage(currentUrl: String) {
    showToast("Restoring original page...")
    try {
      val uri = Uri.parse(currentUrl)
      val queryU = uri.getQueryParameter("u")
      if (!queryU.isNullOrBlank() && UrlValidator.isSafeWebUrl(queryU)) {
        navigateActiveTab(queryU)
        return
      }
      val host = uri.host ?: ""
      if (host.endsWith(".translate.goog")) {
        val origDashedHost = host.removeSuffix(".translate.goog")
        val restoredHost = origDashedHost.replace("-", ".").replace("..", "-")
        val scheme = uri.scheme ?: "https"
        val path = uri.path ?: "/"
        val cleanQuery = uri.queryParameterNames
          ?.filterNot { it.startsWith("_x_tr_") }
          ?.joinToString("&") { "$it=${uri.getQueryParameter(it)}" }
        val finalUrl = buildString {
          append(scheme).append("://").append(restoredHost).append(path)
          if (!cleanQuery.isNullOrBlank()) append("?").append(cleanQuery)
        }
        navigateActiveTab(finalUrl)
        return
      }
    } catch (_: Throwable) {
    }
    val webView = webViewPool.getLiveWebView(activeTab().id)
    webView?.evaluateJavascript(
      "(function() { document.cookie = 'googtrans=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;'; location.reload(); })()",
      null
    )
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
            media[i].__shiz_user_paused = false;
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
            media[i].__shiz_user_paused = true;
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
            media[i].__shiz_user_paused = true;
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
          return JSON.stringify({
            playing: playing,
            count: media.length + (player ? 1 : 0),
            title: docTitle
          });
        } catch(e) {
          return JSON.stringify({ playing: false, count: 0, title: '' });
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

          if (isPlaying || (forceStart && mediaCount > 0)) {
            val pageTitle = docTitle.ifBlank { tab.title }.ifBlank { "Web Stream" }
            val host = UrlValidator.extractHost(tab.url).ifEmpty { "Playing" }
            MediaPlaybackService.start(
              context = appContext,
              title = pageTitle,
              domain = host,
              isPlaying = isPlaying
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

  // Reader Mode & Page Source
  fun toggleReaderMode() {
    if (_readerArticle.value != null) {
      _readerArticle.value = null
      return
    }
    val tab = activeTab()
    if (tab.url.isBlank()) return
    webViewPool.extractReaderArticle(tab.id, tab.url) { article ->
      if (article == null) {
        showToast("No readable text found")
      } else {
        _readerArticle.value = article
      }
    }
  }

  fun closeReaderMode() {
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
      val intent = Intent(Intent.ACTION_VIEW, Uri.parse(prompt.url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      appContext.startActivity(intent)
    } catch (_: Throwable) {
      showToast("No app for ${prompt.scheme}:")
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
    MediaPlaybackService.mediaControlListener = null
    MediaPlaybackService.stop(appContext)
    webViewPool.destroyAll()
  }
}
