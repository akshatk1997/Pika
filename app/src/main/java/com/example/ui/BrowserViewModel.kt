package com.example.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import com.example.model.TabInfo
import com.example.model.WebPermissionKind
import com.example.model.WebPermissionPrompt
import com.example.security.ResolvedNavigation
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
import java.util.UUID

enum class ActiveOverlay {
  NONE,
  TABS,
  BOOKMARKS,
  HISTORY,
  DOWNLOADS,
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

  private val _sessionTrackersBlocked = MutableStateFlow(0)
  val sessionTrackersBlocked: StateFlow<Int> = _sessionTrackersBlocked.asStateFlow()

  private var pendingFileChooserCallback: ValueCallback<Array<Uri>>? = null
  var onLaunchFileChooser: ((Array<String>?) -> Unit)? = null

  private val approvedHttpHosts = HashSet<String>()

  val downloads: StateFlow<List<DownloadRecord>> = downloadController.downloads
  val pendingDownloadConsent: StateFlow<PendingDownloadConsent?> = downloadController.pendingConsent

  private val webCallbacks = object : WebViewCallbacks {
    override fun onPageStarted(tabId: String, url: String) {
      mainHandler.post {
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
      }
    }

    override fun onPageFinished(
      tabId: String,
      url: String,
      title: String?,
      canGoBack: Boolean,
      canGoForward: Boolean
    ) {
      mainHandler.post {
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
            db.recordHistory(safeTitle, url)
            refreshHistory()
          }
        }
      }
    }

    override fun onProgressChanged(tabId: String, progress: Int) {
      mainHandler.post {
        updateTab(tabId) { it.copy(progress = progress, isLoading = progress < 100) }
      }
    }

    override fun onTitleReceived(tabId: String, title: String) {
      mainHandler.post {
        updateTab(tabId) { it.copy(title = title) }
      }
    }

    override fun onTrackerBlocked(tabId: String) {
      mainHandler.post {
        _sessionTrackersBlocked.update { it + 1 }
        updateTab(tabId) { it.copy(trackersBlockedCount = it.trackersBlockedCount + 1) }
      }
    }

    override fun onHttpBlockedByHttpsOnly(tabId: String, url: String) {
      mainHandler.post {
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
      mainHandler.post {
        updateTab(tabId) {
          it.copy(
            isLoading = false,
            securityState = SecurityState.ERROR,
            errorState = PageError(
              type = PageErrorType.BLOCKED,
              failingUrl = UrlValidator.sanitizeDisplayText(url, 180),
              host = scheme,
              reason = "Blocked unsafe scheme '$scheme:'"
            )
          )
        }
      }
    }

    override fun onExternalSchemePrompt(tabId: String, url: String, scheme: String) {
      mainHandler.post {
        _externalLinkPrompt.value = ExternalLinkPrompt(
          url = url,
          scheme = scheme,
          targetSummary = UrlValidator.sanitizeDisplayText(url, 120)
        )
      }
    }

    override fun onPageError(tabId: String, error: PageError) {
      mainHandler.post {
        updateTab(tabId) {
          it.copy(
            isLoading = false,
            securityState = SecurityState.ERROR,
            errorState = error
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
      mainHandler.post {
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
      mainHandler.post {
        val isPriv = tabById(tabId)?.isPrivate == true
        if (!isPriv) {
          val existing = _sitePermissions.value.firstOrNull { it.origin == origin && it.kind == kind }
          if (existing != null) {
            onDecision(existing.allowed)
            return@post
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
      mainHandler.post {
        pendingFileChooserCallback?.onReceiveValue(null)
        pendingFileChooserCallback = callback
        onLaunchFileChooser?.invoke(acceptTypes)
      }
    }

    override fun onFindMatches(tabId: String, activeOrdinal: Int, numberOfMatches: Int) {
      mainHandler.post {
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
      mainHandler.post {
        if (customView == null) {
          _fullscreenView.value = null
        } else {
          _fullscreenView.value = customView to callback
        }
      }
    }

    override fun onLongPressLink(tabId: String, url: String, isImage: Boolean) {
      mainHandler.post {
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
    val home = _settings.value.homepageUrl
    if (home.isNotBlank() && UrlValidator.isSafeWebUrl(home)) {
      navigateActiveTab(home)
    }
  }

  fun getOrCreateActiveWebView(): WebView {
    val tab = activeTab()
    return webViewPool.getOrCreateWebView(
      tabId = tab.id,
      isPrivate = tab.isPrivate,
      isDesktopMode = tab.isDesktopMode,
      approvedHttpHosts = approvedHttpHosts
    )
  }

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
              reason = "Blocked unsafe URI scheme '${res.scheme}:'"
            )
          )
        }
      }
      is ResolvedNavigation.LoadUrl -> {
        _readerArticle.value = null
        closeFindInPage()
        updateTab(tab.id) {
          it.copy(
            url = res.url,
            isLoading = true,
            progress = 10,
            errorState = null,
            httpWarning = null
          )
        }
        webViewPool.getOrCreateWebView(tab.id, tab.isPrivate, tab.isDesktopMode, approvedHttpHosts)
        webViewPool.loadUrl(tab.id, res.url)
      }
    }
  }

  fun toggleDesktopMode() {
    val tab = activeTab()
    val nextDesktop = !tab.isDesktopMode
    updateTab(tab.id) { it.copy(isDesktopMode = nextDesktop) }
    webViewPool.applyDesktopMode(tab.id, tab.isPrivate, nextDesktop)
    showToast(if (nextDesktop) "Desktop site enabled" else "Mobile site enabled")
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
    closeFindInPage()

    val target = initialUrl.ifBlank { if (!isPrivate) _settings.value.homepageUrl else "" }
    if (target.isNotBlank() && UrlValidator.isSafeWebUrl(target)) {
      navigateActiveTab(target)
    }
  }

  fun switchToTab(tabId: String) {
    if (_tabs.value.any { it.id == tabId }) {
      _activeTabId.value = tabId
      updateTab(tabId) { it.copy(lastActiveAt = System.currentTimeMillis()) }
      _activeOverlay.value = ActiveOverlay.NONE
      _readerArticle.value = null
      closeFindInPage()
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
    showToast("Private session erased")
  }

  private fun endPrivateSessionCleanup(privateIds: List<String>) {
    webViewPool.wipePrivateSessionData(privateIds)
    downloadController.clearPrivateDownloads()
  }

  fun goBack(): Boolean {
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
    if (tab.httpWarning != null || tab.errorState != null) {
      updateTab(tab.id) {
        it.copy(
          httpWarning = null,
          errorState = null,
          url = "",
          title = if (it.isPrivate) "Private Tab" else "New Tab",
          securityState = if (it.isPrivate) SecurityState.PRIVATE else SecurityState.HOME
        )
      }
      return true
    }
    val wv = webViewPool.getLiveWebView(tab.id)
    if (wv != null && wv.canGoBack()) {
      wv.goBack()
      return true
    }
    return false
  }

  fun goForward() {
    val wv = webViewPool.getLiveWebView(_activeTabId.value)
    if (wv != null && wv.canGoForward()) {
      wv.goForward()
    }
  }

  fun reloadOrStop() {
    val tab = activeTab()
    val wv = webViewPool.getLiveWebView(tab.id) ?: return
    if (tab.isLoading) {
      wv.stopLoading()
      updateTab(tab.id) { it.copy(isLoading = false) }
    } else if (tab.url.isNotBlank()) {
      updateTab(tab.id) { it.copy(errorState = null, httpWarning = null) }
      wv.reload()
    }
  }

  fun proceedToHttpAnyway() {
    val tab = activeTab()
    val warning = tab.httpWarning ?: return
    approvedHttpHosts.add(warning.host)
    updateTab(tab.id) { it.copy(httpWarning = null, isLoading = true) }
    webViewPool.getOrCreateWebView(tab.id, tab.isPrivate, tab.isDesktopMode, approvedHttpHosts)
    webViewPool.loadUrl(tab.id, warning.originalUrl)
  }

  fun readClipboardText(): String? {
    return try {
      val cm = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
      val item = cm.primaryClip?.getItemAt(0) ?: return null
      UrlValidator.sanitizeControlChars(item.coerceToText(appContext).toString()).takeIf { it.isNotBlank() }
    } catch (_: Throwable) {
      null
    }
  }

  fun copyTextToClipboard(text: String, toastMessage: String = "URL copied") {
    if (text.isBlank()) return
    val cm = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("URL", text))
    showToast(toastMessage)
  }

  fun copyActiveUrl() {
    copyTextToClipboard(activeTab().url)
  }

  fun openInSystemBrowser() {
    val url = activeTab().url
    if (!UrlValidator.isSafeWebUrl(url)) return
    try {
      val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      appContext.startActivity(intent)
    } catch (_: Throwable) {
      showToast("No external browser available")
    }
  }

  fun shareUrl(url: String) {
    if (!UrlValidator.isSafeWebUrl(url)) return
    val intent = Intent(Intent.ACTION_SEND).apply {
      type = "text/plain"
      putExtra(Intent.EXTRA_TEXT, url)
    }
    val chooser = Intent.createChooser(intent, "Share link").apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    appContext.startActivity(chooser)
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
  fun refreshBookmarks(query: String = "") {
    viewModelScope.launch(Dispatchers.IO) {
      _bookmarks.value = db.getBookmarks(query)
    }
  }

  fun isCurrentPageBookmarked(): Boolean {
    val url = activeTab().url
    return url.isNotBlank() && _bookmarks.value.any { it.url == url }
  }

  fun toggleCurrentBookmark() {
    val tab = activeTab()
    if (!UrlValidator.isSafeWebUrl(tab.url)) return
    viewModelScope.launch(Dispatchers.IO) {
      if (db.isBookmarked(tab.url)) {
        db.deleteBookmarkByUrl(tab.url)
        showToast("Bookmark removed")
      } else {
        db.upsertBookmark(tab.title, tab.url)
        showToast("Bookmark saved locally")
      }
      _bookmarks.value = db.getBookmarks()
    }
  }

  fun addOrUpdateBookmark(id: Long?, title: String, url: String, folder: String) {
    viewModelScope.launch(Dispatchers.IO) {
      if (id == null || id <= 0L) {
        db.upsertBookmark(title, url, folder)
      } else {
        db.updateBookmark(id, title, url, folder)
      }
      _bookmarks.value = db.getBookmarks()
    }
  }

  fun deleteBookmark(id: Long) {
    viewModelScope.launch(Dispatchers.IO) {
      db.deleteBookmarkById(id)
      _bookmarks.value = db.getBookmarks()
    }
  }

  fun exportBookmarksHtml(): String = BookmarkImportExport.exportToNetscapeHtml(_bookmarks.value)

  fun importBookmarksFromText(rawContent: String) {
    viewModelScope.launch(Dispatchers.IO) {
      val parsed = BookmarkImportExport.parseImportedBookmarks(rawContent)
      parsed.forEach { db.upsertBookmark(it.title, it.url, it.folder) }
      _bookmarks.value = db.getBookmarks()
      withContext(Dispatchers.Main) {
        showToast("Imported ${parsed.size} bookmarks")
      }
    }
  }

  // History
  fun refreshHistory(query: String = "") {
    viewModelScope.launch(Dispatchers.IO) {
      _history.value = db.getHistory(query)
    }
  }

  fun deleteHistoryEntry(id: Long) {
    viewModelScope.launch(Dispatchers.IO) {
      db.deleteHistoryEntry(id)
      _history.value = db.getHistory()
    }
  }

  fun clearAllHistory() {
    viewModelScope.launch(Dispatchers.IO) {
      db.clearAllHistory()
      _history.value = emptyList()
      withContext(Dispatchers.Main) { showToast("History cleared") }
    }
  }

  // Find in page
  fun openFindInPage() {
    _findState.value = FindState(isVisible = true)
  }

  fun updateFindQuery(query: String) {
    _findState.update { it.copy(query = query, activeIndex = 0, totalMatches = 0) }
    val wv = webViewPool.getLiveWebView(_activeTabId.value) ?: return
    if (query.isEmpty()) {
      wv.clearMatches()
    } else {
      wv.findAllAsync(query)
    }
  }

  fun findNext(forward: Boolean) {
    webViewPool.getLiveWebView(_activeTabId.value)?.findNext(forward)
  }

  fun closeFindInPage() {
    _findState.value = FindState(isVisible = false)
    webViewPool.getLiveWebView(_activeTabId.value)?.clearMatches()
  }

  // Reader mode
  fun toggleReaderMode() {
    if (_readerArticle.value != null) {
      _readerArticle.value = null
      return
    }
    val tab = activeTab()
    if (tab.url.isBlank()) return
    webViewPool.extractReaderArticle(tab.id, tab.url) { article ->
      if (article == null) {
        showToast("No readable article text found")
      } else {
        _readerArticle.value = article
      }
    }
  }

  fun closeReaderMode() {
    _readerArticle.value = null
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
      showToast("No app found to handle ${prompt.scheme}:")
    }
  }

  fun deliverChosenFiles(uris: Array<Uri>?) {
    pendingFileChooserCallback?.onReceiveValue(uris)
    pendingFileChooserCallback = null
  }

  fun exitFullscreen() {
    val pair = _fullscreenView.value ?: return
    _fullscreenView.value = null
    pair.second?.onCustomViewHidden()
  }

  // Settings
  fun updateSettings(transform: (SettingsState) -> SettingsState) {
    val updated = transform(_settings.value)
    _settings.value = updated
    settingsRepo.saveSettings(updated)
    webViewPool.applySettingsToAll(updated) { id ->
      val t = tabById(id)
      (t?.isPrivate == true) to (t?.isDesktopMode == true)
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
      clearAllHistory()
    }
    showToast("Selected browsing data cleared")
  }

  fun setOverlay(overlay: ActiveOverlay) {
    _activeOverlay.value = overlay
    if (overlay == ActiveOverlay.BOOKMARKS) refreshBookmarks()
    if (overlay == ActiveOverlay.HISTORY) refreshHistory()
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
