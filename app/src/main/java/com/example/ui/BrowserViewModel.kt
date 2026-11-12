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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
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
    val url = activeTab().url
    if (!UrlValidator.isSafeWebUrl(url) || url == "about:blank") return
    val encoded = URLEncoder.encode(url, StandardCharsets.UTF_8.name())
    navigateActiveTab("https://translate.google.com/translate?sl=auto&tl=en&u=$encoded")
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
    webViewPool.destroyAll()
  }
}
