package com.example.ui.screens

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.model.SecurityState
import com.example.model.TabInfo
import com.example.security.UrlValidator
import com.example.ui.ActiveOverlay
import com.example.ui.BrowserViewModel
import com.example.ui.components.FeatherIcons
import com.example.ui.components.FindInPageBar
import com.example.ui.components.HttpWarningOverlay
import com.example.ui.components.NewTabHomeView
import com.example.ui.components.PageErrorOverlay
import com.example.ui.components.ReaderModeOverlay
import com.example.ui.theme.FeatherMintDark
import com.example.ui.theme.FeatherPrivateAccent
import com.example.ui.theme.FeatherSecureGreen
import com.example.ui.theme.FeatherWarningAmber
import com.example.ui.theme.LocalReduceMotion

@SuppressLint("ClickableViewAccessibility")
@Composable
fun BrowserScreen(
  viewModel: BrowserViewModel,
  onExportBookmarks: () -> Unit,
  onImportBookmarks: () -> Unit
) {
  val tabs by viewModel.tabs.collectAsStateWithLifecycle()
  val activeTabId by viewModel.activeTabId.collectAsStateWithLifecycle()
  val closedTabs by viewModel.closedTabs.collectAsStateWithLifecycle()
  val settings by viewModel.settings.collectAsStateWithLifecycle()
  val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
  val history by viewModel.history.collectAsStateWithLifecycle()
  val downloads by viewModel.downloads.collectAsStateWithLifecycle()
  val sitePermissions by viewModel.sitePermissions.collectAsStateWithLifecycle()
  val activeOverlay by viewModel.activeOverlay.collectAsStateWithLifecycle()
  val findState by viewModel.findState.collectAsStateWithLifecycle()
  val readerArticle by viewModel.readerArticle.collectAsStateWithLifecycle()
  val pendingDownload by viewModel.pendingDownloadConsent.collectAsStateWithLifecycle()
  val externalLinkPrompt by viewModel.externalLinkPrompt.collectAsStateWithLifecycle()
  val permissionPrompt by viewModel.permissionPrompt.collectAsStateWithLifecycle()
  val linkContextTarget by viewModel.linkContextTarget.collectAsStateWithLifecycle()
  val fullscreenView by viewModel.fullscreenView.collectAsStateWithLifecycle()
  val statusToast by viewModel.statusToast.collectAsStateWithLifecycle()
  val sessionTrackersBlocked by viewModel.sessionTrackersBlocked.collectAsStateWithLifecycle()

  var showSiteInfoDialog by remember { mutableStateOf(false) }

  val activeTab = tabs.firstOrNull { it.id == activeTabId } ?: tabs.first()
  val snackbarHostState = remember { SnackbarHostState() }
  val focusManager = LocalFocusManager.current

  LaunchedEffect(statusToast) {
    statusToast?.let { msg ->
      snackbarHostState.showSnackbar(msg)
      viewModel.consumeToast()
    }
  }

  BackHandler(
    enabled = fullscreenView != null ||
      readerArticle != null ||
      findState.isVisible ||
      activeOverlay != ActiveOverlay.NONE ||
      activeTab.httpWarning != null ||
      activeTab.errorState != null ||
      activeTab.canGoBack
  ) {
    if (fullscreenView != null) {
      viewModel.exitFullscreen()
    } else {
      viewModel.goBack()
    }
  }

  fullscreenView?.let { (customView, _) ->
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
      AndroidView(
        factory = { customView },
        modifier = Modifier.fillMaxSize()
      )
      Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        modifier = Modifier
          .align(Alignment.TopEnd)
          .statusBarsPadding()
          .padding(16.dp)
          .clickable { viewModel.exitFullscreen() }
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(Icons.Filled.Close, contentDescription = "Exit fullscreen", modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Exit fullscreen", style = MaterialTheme.typography.labelMedium)
        }
      }
    }
    return
  }

  readerArticle?.let { article ->
    ReaderModeOverlay(
      article = article,
      fontSizeSp = settings.readerFontSizeSp,
      darkReading = settings.readerDarkMode,
      onChangeFontSize = { sz -> viewModel.updateSettings { it.copy(readerFontSizeSp = sz) } },
      onToggleDark = { dark -> viewModel.updateSettings { it.copy(readerDarkMode = dark) } },
      onClose = { viewModel.closeReaderMode() }
    )
    return
  }

  when (activeOverlay) {
    ActiveOverlay.TABS -> {
      TabsSheet(
        tabs = tabs,
        activeTabId = activeTabId,
        closedTabs = closedTabs,
        onSelectTab = { viewModel.switchToTab(it) },
        onCloseTab = { viewModel.closeTab(it) },
        onMoveTab = { id, delta -> viewModel.moveTab(id, delta) },
        onNewTab = { isPriv -> viewModel.openNewTab(isPrivate = isPriv) },
        onRestoreClosedTab = { viewModel.restoreLastClosedTab() },
        onEndPrivateSession = { viewModel.endAllPrivateTabs() },
        onCloseSheet = { viewModel.setOverlay(ActiveOverlay.NONE) }
      )
      return
    }
    ActiveOverlay.BOOKMARKS -> {
      BookmarksScreen(
        bookmarks = bookmarks,
        onSearch = { viewModel.refreshBookmarks(it) },
        onOpenUrl = { viewModel.navigateActiveTab(it) },
        onSaveBookmark = { id, title, url, folder -> viewModel.addOrUpdateBookmark(id, title, url, folder) },
        onDeleteBookmark = { viewModel.deleteBookmark(it) },
        onExportBookmarks = onExportBookmarks,
        onImportBookmarks = onImportBookmarks,
        onClose = { viewModel.setOverlay(ActiveOverlay.NONE) }
      )
      return
    }
    ActiveOverlay.HISTORY -> {
      HistoryScreen(
        history = history,
        historyEnabled = settings.historyEnabled,
        onSearch = { viewModel.refreshHistory(it) },
        onOpenUrl = { viewModel.navigateActiveTab(it) },
        onDeleteEntry = { viewModel.deleteHistoryEntry(it) },
        onClearAll = { viewModel.clearAllHistory() },
        onClose = { viewModel.setOverlay(ActiveOverlay.NONE) }
      )
      return
    }
    ActiveOverlay.DOWNLOADS -> {
      DownloadsScreen(
        downloads = downloads,
        onCancel = { viewModel.downloadController.cancelDownload(it) },
        onOpen = {
          if (!viewModel.downloadController.openDownloadedFile(it)) {
            viewModel.showToast("No app found to open this file")
          }
        },
        onReveal = { viewModel.downloadController.revealDownloadedLocation(it) },
        onClearAll = { viewModel.downloadController.clearAllDownloads() },
        onClose = { viewModel.setOverlay(ActiveOverlay.NONE) }
      )
      return
    }
    ActiveOverlay.SETTINGS -> {
      SettingsScreen(
        settings = settings,
        sitePermissions = sitePermissions,
        onUpdateSettings = { viewModel.updateSettings(it) },
        onRemovePermissionRule = { viewModel.removeSitePermissionRule(it) },
        onClearBrowsingData = { opts -> viewModel.clearSelectedBrowsingData(opts) },
        onExportBookmarks = onExportBookmarks,
        onImportBookmarks = onImportBookmarks,
        onClose = { viewModel.setOverlay(ActiveOverlay.NONE) }
      )
      return
    }
    ActiveOverlay.NONE -> Unit
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val isExpanded = maxWidth >= 600.dp

    Scaffold(
      contentWindowInsets = WindowInsets.safeDrawing,
      snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
      Row(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding)
          .imePadding()
      ) {
        if (isExpanded) {
          NavigationRail(
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxHeight()
          ) {
            NavigationRailItem(
              selected = false,
              onClick = { viewModel.setOverlay(ActiveOverlay.TABS) },
              icon = { Text(tabs.size.toString(), fontWeight = FontWeight.Bold) },
              label = { Text(stringResource(R.string.tabs_title)) }
            )
            NavigationRailItem(
              selected = false,
              onClick = { viewModel.setOverlay(ActiveOverlay.BOOKMARKS) },
              icon = { Icon(FeatherIcons.Bookmark, contentDescription = null) },
              label = { Text(stringResource(R.string.action_bookmarks)) }
            )
            NavigationRailItem(
              selected = false,
              onClick = { viewModel.setOverlay(ActiveOverlay.HISTORY) },
              icon = { Icon(FeatherIcons.History, contentDescription = null) },
              label = { Text(stringResource(R.string.action_history)) }
            )
            NavigationRailItem(
              selected = false,
              onClick = { viewModel.setOverlay(ActiveOverlay.DOWNLOADS) },
              icon = { Icon(FeatherIcons.Download, contentDescription = null) },
              label = { Text(stringResource(R.string.action_downloads)) }
            )
            NavigationRailItem(
              selected = false,
              onClick = { viewModel.setOverlay(ActiveOverlay.SETTINGS) },
              icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
              label = { Text(stringResource(R.string.action_settings)) }
            )
          }
        }

        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
          if (isExpanded) {
            BrowserToolbar(
              activeTab = activeTab,
              tabCount = tabs.size,
              isBookmarked = viewModel.isCurrentPageBookmarked(),
              readClipboard = { viewModel.readClipboardText() },
              onNavigate = {
                focusManager.clearFocus()
                viewModel.navigateActiveTab(it)
              },
              onBack = { viewModel.goBack() },
              onForward = { viewModel.goForward() },
              onReloadOrStop = { viewModel.reloadOrStop() },
              onCopyUrl = { viewModel.copyActiveUrl() },
              onOpenSiteInfo = { showSiteInfoDialog = true },
              onOpenTabs = { viewModel.setOverlay(ActiveOverlay.TABS) },
              onMenuAction = { action -> handleMenuAction(action, viewModel) }
            )
          }

          if (activeTab.isLoading && activeTab.progress in 1..99) {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .height(2.5.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
              Box(
                modifier = Modifier
                  .fillMaxWidth(fraction = (activeTab.progress / 100f).coerceIn(0.05f, 1f))
                  .fillMaxHeight()
                  .background(
                    Brush.horizontalGradient(
                      listOf(
                        if (activeTab.isPrivate) FeatherPrivateAccent else MaterialTheme.colorScheme.primary,
                        FeatherMintDark
                      )
                    )
                  )
              )
            }
          }

          val reduceMotion = LocalReduceMotion.current
          if (reduceMotion) {
            if (findState.isVisible) {
              FindInPageBar(
                findState = findState,
                onQueryChange = { viewModel.updateFindQuery(it) },
                onPrev = { viewModel.findNext(false) },
                onNext = { viewModel.findNext(true) },
                onClose = { viewModel.closeFindInPage() }
              )
            }
          } else {
            AnimatedVisibility(visible = findState.isVisible) {
              FindInPageBar(
                findState = findState,
                onQueryChange = { viewModel.updateFindQuery(it) },
                onPrev = { viewModel.findNext(false) },
                onNext = { viewModel.findNext(true) },
                onClose = { viewModel.closeFindInPage() }
              )
            }
          }

          Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
              activeTab.httpWarning != null -> {
                HttpWarningOverlay(
                  warning = activeTab.httpWarning,
                  onGoBackSafely = { viewModel.goBack() },
                  onProceedAnyway = { viewModel.proceedToHttpAnyway() }
                )
              }
              activeTab.errorState != null -> {
                PageErrorOverlay(
                  error = activeTab.errorState,
                  onRetry = {
                    val url = activeTab.errorState.failingUrl
                    if (url.isNotBlank()) viewModel.navigateActiveTab(url)
                  },
                  onGoHome = { viewModel.goBack() }
                )
              }
              activeTab.url.isBlank() -> {
                NewTabHomeView(
                  isPrivate = activeTab.isPrivate,
                  searchEngineName = settings.searchEngine.label,
                  trackersBlockedCount = sessionTrackersBlocked,
                  bookmarks = bookmarks,
                  onSubmitQuery = { viewModel.navigateActiveTab(it) },
                  onOpenBookmark = { viewModel.navigateActiveTab(it) }
                )
              }
              else -> {
                var pullProgress by remember { mutableFloatStateOf(0f) }
                val pullStartY = remember { mutableFloatStateOf(0f) }

                Box(modifier = Modifier.fillMaxSize()) {
                  AndroidView(
                    factory = { ctx -> FrameLayout(ctx) },
                    update = { container ->
                      val targetWebView = viewModel.getOrCreateActiveWebView()
                      if (container.childCount != 1 || container.getChildAt(0) !== targetWebView) {
                        container.removeAllViews()
                        (targetWebView.parent as? ViewGroup)?.removeView(targetWebView)
                        container.addView(
                          targetWebView,
                          FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                          )
                        )
                        targetWebView.setOnTouchListener { v, event ->
                          if (v.scrollY == 0) {
                            when (event.actionMasked) {
                              MotionEvent.ACTION_DOWN -> {
                                pullStartY.floatValue = event.y
                                pullProgress = 0f
                              }
                              MotionEvent.ACTION_MOVE -> {
                                val dy = event.y - pullStartY.floatValue
                                if (dy > 24f && pullStartY.floatValue > 0f) {
                                  pullProgress = (dy / 220f).coerceIn(0f, 1f)
                                } else {
                                  pullProgress = 0f
                                }
                              }
                              MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                if (pullProgress >= 0.95f) {
                                  viewModel.reloadOrStop()
                                }
                                pullProgress = 0f
                                pullStartY.floatValue = 0f
                              }
                            }
                          } else {
                            pullProgress = 0f
                          }
                          false
                        }
                      }
                    },
                    modifier = Modifier.fillMaxSize().testTag("webview_container")
                  )
                  if (pullProgress > 0.1f) {
                    LinearProgressIndicator(
                      progress = { pullProgress },
                      modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
                    )
                  }
                }
              }
            }
          }

          if (!isExpanded) {
            BrowserToolbar(
              activeTab = activeTab,
              tabCount = tabs.size,
              isBookmarked = viewModel.isCurrentPageBookmarked(),
              readClipboard = { viewModel.readClipboardText() },
              onNavigate = {
                focusManager.clearFocus()
                viewModel.navigateActiveTab(it)
              },
              onBack = { viewModel.goBack() },
              onForward = { viewModel.goForward() },
              onReloadOrStop = { viewModel.reloadOrStop() },
              onCopyUrl = { viewModel.copyActiveUrl() },
              onOpenSiteInfo = { showSiteInfoDialog = true },
              onOpenTabs = { viewModel.setOverlay(ActiveOverlay.TABS) },
              onMenuAction = { action -> handleMenuAction(action, viewModel) },
              modifier = Modifier.navigationBarsPadding()
            )
          }
        }
      }
    }
  }

  // Site Info & Connection Security Sheet Dialog
  if (showSiteInfoDialog) {
    val host = UrlValidator.extractHost(activeTab.url).ifEmpty { "Pika Start Page" }
    AlertDialog(
      onDismissRequest = { showSiteInfoDialog = false },
      title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = when (activeTab.securityState) {
              SecurityState.SECURE_HTTPS -> Icons.Filled.Lock
              SecurityState.PRIVATE -> FeatherIcons.PrivateMask
              SecurityState.HOME -> FeatherIcons.FeatherLogo
              else -> Icons.Filled.Warning
            },
            contentDescription = null,
            tint = when (activeTab.securityState) {
              SecurityState.SECURE_HTTPS -> FeatherSecureGreen
              SecurityState.PRIVATE -> FeatherPrivateAccent
              SecurityState.HOME -> MaterialTheme.colorScheme.primary
              else -> FeatherWarningAmber
            }
          )
          Spacer(modifier = Modifier.width(10.dp))
          Text(host, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text = when (activeTab.securityState) {
              SecurityState.SECURE_HTTPS -> "Connection is encrypted with verified TLS (HTTPS)."
              SecurityState.PRIVATE -> "Private tab: cookies, cache, history, and storage are isolated and wiped on session end."
              SecurityState.INSECURE_HTTP -> "Connection is unencrypted (HTTP). Avoid entering passwords or sensitive data."
              SecurityState.HOME -> "Local Pika start page. Zero network requests."
              SecurityState.ERROR -> "Connection blocked or failed security checks."
            },
            style = MaterialTheme.typography.bodyMedium
          )
          Text(
            text = "Trackers blocked on this tab: ${activeTab.trackersBlockedCount}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
          )
          HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
          ) {
            Text("JavaScript", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(
              checked = settings.javascriptEnabled,
              onCheckedChange = { enabled -> viewModel.updateSettings { it.copy(javascriptEnabled = enabled) } }
            )
          }
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
          ) {
            Text("Tracker blocker", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(
              checked = settings.trackerBlockingEnabled,
              onCheckedChange = { enabled -> viewModel.updateSettings { it.copy(trackerBlockingEnabled = enabled) } }
            )
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showSiteInfoDialog = false }) { Text("Done") }
      },
      dismissButton = {
        if (activeTab.url.isNotBlank()) {
          TextButton(
            onClick = {
              showSiteInfoDialog = false
              viewModel.copyActiveUrl()
            }
          ) { Text("Copy URL") }
        }
      }
    )
  }

  // Long-Press Link / Image Context Dialog
  linkContextTarget?.let { target ->
    val safeDisplay = UrlValidator.sanitizeDisplayText(target.url, 110)
    AlertDialog(
      onDismissRequest = { viewModel.dismissLinkContext() },
      title = { Text(if (target.isImage) "Image / Link Options" else "Link Options") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = safeDisplay,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = 8.dp)
          )
          ContextActionRow("Open in new tab") {
            viewModel.dismissLinkContext()
            viewModel.openNewTab(isPrivate = false, initialUrl = target.url)
          }
          ContextActionRow("Open in private tab") {
            viewModel.dismissLinkContext()
            viewModel.openNewTab(isPrivate = true, initialUrl = target.url)
          }
          ContextActionRow("Copy link address") {
            viewModel.dismissLinkContext()
            viewModel.copyTextToClipboard(target.url)
          }
          ContextActionRow("Download target") {
            viewModel.dismissLinkContext()
            viewModel.downloadUrlWithConsent(target.url)
          }
          ContextActionRow("Share link") {
            viewModel.dismissLinkContext()
            viewModel.shareUrl(target.url)
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { viewModel.dismissLinkContext() }) { Text("Close") }
      }
    )
  }

  pendingDownload?.let { req ->
    AlertDialog(
      onDismissRequest = { viewModel.downloadController.dismissPendingConsent() },
      title = { Text(stringResource(R.string.download_consent_title)) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(stringResource(R.string.download_consent_body, req.fileName, req.host))
          Text(
            text = "Type: ${req.mimeType} · ${if (req.isHttps) "HTTPS Verified" else "Unencrypted HTTP"}",
            style = MaterialTheme.typography.labelSmall,
            color = if (req.isHttps) FeatherSecureGreen else FeatherWarningAmber
          )
        }
      },
      confirmButton = {
        TextButton(
          onClick = { viewModel.downloadController.approvePendingDownload() },
          modifier = Modifier.testTag("download_confirm_button")
        ) { Text(stringResource(R.string.download_allow)) }
      },
      dismissButton = {
        TextButton(onClick = { viewModel.downloadController.dismissPendingConsent() }) {
          Text(stringResource(R.string.download_cancel))
        }
      }
    )
  }

  externalLinkPrompt?.let { ext ->
    AlertDialog(
      onDismissRequest = { viewModel.dismissExternalLinkPrompt() },
      title = { Text("Open external application?") },
      text = {
        Text("This page wants to open an external '${ext.scheme}:' link:\n\n${ext.targetSummary}")
      },
      confirmButton = {
        TextButton(onClick = { viewModel.confirmExternalLinkPrompt() }) { Text("Open") }
      },
      dismissButton = {
        TextButton(onClick = { viewModel.dismissExternalLinkPrompt() }) { Text("Cancel") }
      }
    )
  }

  permissionPrompt?.let { perm ->
    var rememberSite by remember(perm.id) { mutableStateOf(false) }
    AlertDialog(
      onDismissRequest = { viewModel.resolvePermissionPrompt(allowed = false, rememberSite = false) },
      title = { Text("Site permission request") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("${perm.origin} is requesting ${perm.kind.name.lowercase().replace('_', ' ')} permission.")
          if (perm.detail.isNotBlank()) {
            Text(perm.detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          if (!activeTab.isPrivate) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.clickable { rememberSite = !rememberSite }
            ) {
              Checkbox(checked = rememberSite, onCheckedChange = { rememberSite = it })
              Text("Remember decision for this site", style = MaterialTheme.typography.bodyMedium)
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { viewModel.resolvePermissionPrompt(allowed = true, rememberSite = rememberSite) }) {
          Text("Allow")
        }
      },
      dismissButton = {
        TextButton(onClick = { viewModel.resolvePermissionPrompt(allowed = false, rememberSite = rememberSite) }) {
          Text("Deny")
        }
      }
    )
  }
}

@Composable
private fun ContextActionRow(label: String, onClick: () -> Unit) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .minimumInteractiveComponentSize()
      .clickable(onClick = onClick)
      .padding(horizontal = 4.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(label, style = MaterialTheme.typography.bodyLarge)
  }
}

private enum class MenuAction {
  NEW_TAB,
  NEW_PRIVATE_TAB,
  TOGGLE_BOOKMARK,
  TOGGLE_DESKTOP_SITE,
  BOOKMARKS,
  HISTORY,
  DOWNLOADS,
  FIND_IN_PAGE,
  READER_MODE,
  OPEN_SYSTEM_BROWSER,
  SHARE,
  SETTINGS
}

private fun handleMenuAction(action: MenuAction, viewModel: BrowserViewModel) {
  when (action) {
    MenuAction.NEW_TAB -> viewModel.openNewTab(isPrivate = false)
    MenuAction.NEW_PRIVATE_TAB -> viewModel.openNewTab(isPrivate = true)
    MenuAction.TOGGLE_BOOKMARK -> viewModel.toggleCurrentBookmark()
    MenuAction.TOGGLE_DESKTOP_SITE -> viewModel.toggleDesktopMode()
    MenuAction.BOOKMARKS -> viewModel.setOverlay(ActiveOverlay.BOOKMARKS)
    MenuAction.HISTORY -> viewModel.setOverlay(ActiveOverlay.HISTORY)
    MenuAction.DOWNLOADS -> viewModel.setOverlay(ActiveOverlay.DOWNLOADS)
    MenuAction.FIND_IN_PAGE -> viewModel.openFindInPage()
    MenuAction.READER_MODE -> viewModel.toggleReaderMode()
    MenuAction.OPEN_SYSTEM_BROWSER -> viewModel.openInSystemBrowser()
    MenuAction.SHARE -> viewModel.shareActiveUrl()
    MenuAction.SETTINGS -> viewModel.setOverlay(ActiveOverlay.SETTINGS)
  }
}

@Composable
private fun BrowserToolbar(
  activeTab: TabInfo,
  tabCount: Int,
  isBookmarked: Boolean,
  readClipboard: () -> String?,
  onNavigate: (String) -> Unit,
  onBack: () -> Unit,
  onForward: () -> Unit,
  onReloadOrStop: () -> Unit,
  onCopyUrl: () -> Unit,
  onOpenSiteInfo: () -> Unit,
  onOpenTabs: () -> Unit,
  onMenuAction: (MenuAction) -> Unit,
  modifier: Modifier = Modifier
) {
  var omniboxValue by remember(activeTab.id, activeTab.url) {
    mutableStateOf(TextFieldValue(activeTab.url, TextRange(0, activeTab.url.length)))
  }
  var isFocused by remember { mutableStateOf(false) }
  var menuExpanded by remember { mutableStateOf(false) }
  var clipboardCandidate by remember { mutableStateOf<String?>(null) }
  val focusManager = LocalFocusManager.current

  val displayHost = remember(activeTab.url) {
    UrlValidator.extractHost(activeTab.url).removePrefix("www.")
  }

  Surface(
    tonalElevation = 4.dp,
    shadowElevation = 8.dp,
    color = MaterialTheme.colorScheme.surface,
    modifier = modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      HorizontalDivider(
        thickness = 0.8.dp,
        color = if (activeTab.isPrivate) {
          FeatherPrivateAccent.copy(alpha = 0.45f)
        } else {
          MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
        }
      )

      if (isFocused && !clipboardCandidate.isNullOrBlank() && clipboardCandidate != activeTab.url) {
        Surface(
          color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
          modifier = Modifier
            .fillMaxWidth()
            .clickable {
              val clip = clipboardCandidate ?: return@clickable
              focusManager.clearFocus()
              onNavigate(clip)
            }
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("paste_and_go_chip")
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              Icons.AutoMirrored.Filled.ArrowForward,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Paste & Go: ${UrlValidator.sanitizeDisplayText(clipboardCandidate, 48)}",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }
      }

      Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
        // Frosted Omnibox Pill
        Surface(
          shape = RoundedCornerShape(24.dp),
          color = MaterialTheme.colorScheme.surfaceVariant,
          border = BorderStroke(
            width = 0.9.dp,
            color = when {
              isFocused -> MaterialTheme.colorScheme.primary
              activeTab.isPrivate -> FeatherPrivateAccent.copy(alpha = 0.65f)
              else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
            }
          ),
          modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            val (secIcon, secTint, secDesc) = when (activeTab.securityState) {
              SecurityState.PRIVATE -> Triple(FeatherIcons.PrivateMask, FeatherPrivateAccent, stringResource(R.string.cd_security_private))
              SecurityState.SECURE_HTTPS -> Triple(Icons.Filled.Lock, FeatherSecureGreen, stringResource(R.string.cd_security_https))
              SecurityState.INSECURE_HTTP, SecurityState.ERROR -> Triple(Icons.Filled.Warning, FeatherWarningAmber, stringResource(R.string.cd_security_http))
              SecurityState.HOME -> Triple(Icons.Filled.Search, MaterialTheme.colorScheme.onSurfaceVariant, stringResource(R.string.cd_security_home))
            }
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = secTint.copy(alpha = 0.14f),
              modifier = Modifier
                .size(30.dp)
                .clickable { onOpenSiteInfo() }
                .testTag("security_badge_button")
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = secIcon,
                  contentDescription = secDesc,
                  tint = secTint,
                  modifier = Modifier.size(16.dp)
                )
              }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
              if (!isFocused && displayHost.isNotEmpty()) {
                Text(
                  text = if (activeTab.isDesktopMode) "$displayHost · Desktop" else displayHost,
                  style = MaterialTheme.typography.bodyMedium,
                  fontWeight = FontWeight.Medium,
                  color = MaterialTheme.colorScheme.onSurface,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              } else if (omniboxValue.text.isEmpty()) {
                Text(
                  text = stringResource(R.string.omnibox_hint),
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
              BasicTextField(
                value = if (!isFocused && displayHost.isNotEmpty()) TextFieldValue("") else omniboxValue,
                onValueChange = { omniboxValue = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                  color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = {
                  if (omniboxValue.text.isNotBlank()) {
                    focusManager.clearFocus()
                    onNavigate(omniboxValue.text)
                  }
                }),
                modifier = Modifier
                  .fillMaxWidth()
                  .onFocusChanged { focusState ->
                    isFocused = focusState.isFocused
                    if (focusState.isFocused) {
                      clipboardCandidate = readClipboard()
                      val currentUrl = activeTab.url
                      if (currentUrl.isNotEmpty()) {
                        omniboxValue = TextFieldValue(currentUrl, TextRange(0, currentUrl.length))
                      }
                    }
                  }
                  .testTag("address_bar_input")
              )
            }
            if (isFocused && omniboxValue.text.isNotEmpty()) {
              IconButton(
                onClick = { omniboxValue = TextFieldValue("") },
                modifier = Modifier.minimumInteractiveComponentSize().testTag("address_clear_button")
              ) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_clear_input), modifier = Modifier.size(18.dp))
              }
            } else if (activeTab.url.isNotBlank()) {
              IconButton(
                onClick = onCopyUrl,
                modifier = Modifier.minimumInteractiveComponentSize().testTag("address_copy_button")
              ) {
                Icon(FeatherIcons.Copy, contentDescription = stringResource(R.string.cd_copy_url), modifier = Modifier.size(17.dp))
              }
            }
            if (activeTab.trackersBlockedCount > 0) {
              Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                modifier = Modifier.clickable { onOpenSiteInfo() }
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(FeatherIcons.Shield, contentDescription = "Trackers blocked", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                  Spacer(modifier = Modifier.width(3.dp))
                  Text(
                    text = activeTab.trackersBlockedCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                  )
                }
              }
            }
          }
        }

        // Precision Bottom Action Bar
        Row(
          modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          IconButton(
            onClick = onBack,
            enabled = activeTab.canGoBack || activeTab.httpWarning != null || activeTab.errorState != null,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("nav_back_button")
          ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
          }
          IconButton(
            onClick = onForward,
            enabled = activeTab.canGoForward,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("nav_forward_button")
          ) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.cd_forward))
          }
          IconButton(
            onClick = onReloadOrStop,
            enabled = activeTab.url.isNotBlank(),
            modifier = Modifier.minimumInteractiveComponentSize().testTag("nav_reload_button")
          ) {
            Icon(
              imageVector = if (activeTab.isLoading) Icons.Filled.Close else Icons.Filled.Refresh,
              contentDescription = if (activeTab.isLoading) stringResource(R.string.cd_stop) else stringResource(R.string.cd_reload)
            )
          }

          Surface(
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .clickable { onOpenTabs() }
              .testTag("nav_tabs_button")
          ) {
            Box(
              contentAlignment = Alignment.Center,
              modifier = Modifier.padding(horizontal = 11.dp, vertical = 4.dp)
            ) {
              Text(
                text = tabCount.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
              )
            }
          }

          Box {
            IconButton(
              onClick = { menuExpanded = true },
              modifier = Modifier.minimumInteractiveComponentSize().testTag("nav_menu_button")
            ) {
              Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.cd_menu))
            }
            DropdownMenu(
              expanded = menuExpanded,
              onDismissRequest = { menuExpanded = false }
            ) {
              DropdownMenuItem(
                text = { Text(stringResource(R.string.action_new_tab)) },
                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                onClick = { menuExpanded = false; onMenuAction(MenuAction.NEW_TAB) }
              )
              DropdownMenuItem(
                text = { Text(stringResource(R.string.action_new_private_tab)) },
                leadingIcon = { Icon(FeatherIcons.PrivateMask, contentDescription = null) },
                onClick = { menuExpanded = false; onMenuAction(MenuAction.NEW_PRIVATE_TAB) }
              )
              if (activeTab.url.isNotBlank()) {
                DropdownMenuItem(
                  text = {
                    Text(if (isBookmarked) stringResource(R.string.action_remove_bookmark) else stringResource(R.string.action_add_bookmark))
                  },
                  leadingIcon = {
                    Icon(if (isBookmarked) FeatherIcons.Bookmark else FeatherIcons.BookmarkOutline, contentDescription = null)
                  },
                  onClick = { menuExpanded = false; onMenuAction(MenuAction.TOGGLE_BOOKMARK) }
                )
                DropdownMenuItem(
                  text = { Text("Desktop site") },
                  leadingIcon = {
                    if (activeTab.isDesktopMode) Icon(Icons.Filled.Check, contentDescription = null)
                  },
                  onClick = { menuExpanded = false; onMenuAction(MenuAction.TOGGLE_DESKTOP_SITE) }
                )
                DropdownMenuItem(
                  text = { Text(stringResource(R.string.action_find_in_page)) },
                  leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                  onClick = { menuExpanded = false; onMenuAction(MenuAction.FIND_IN_PAGE) }
                )
                DropdownMenuItem(
                  text = { Text(stringResource(R.string.action_reader_mode)) },
                  leadingIcon = { Icon(FeatherIcons.Reader, contentDescription = null) },
                  onClick = { menuExpanded = false; onMenuAction(MenuAction.READER_MODE) }
                )
                DropdownMenuItem(
                  text = { Text(stringResource(R.string.action_share)) },
                  leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                  onClick = { menuExpanded = false; onMenuAction(MenuAction.SHARE) }
                )
                DropdownMenuItem(
                  text = { Text(stringResource(R.string.action_open_external)) },
                  leadingIcon = { Icon(FeatherIcons.OpenInNew, contentDescription = null) },
                  onClick = { menuExpanded = false; onMenuAction(MenuAction.OPEN_SYSTEM_BROWSER) }
                )
              }
              HorizontalDivider()
              DropdownMenuItem(
                text = { Text(stringResource(R.string.action_bookmarks)) },
                leadingIcon = { Icon(FeatherIcons.Bookmark, contentDescription = null) },
                onClick = { menuExpanded = false; onMenuAction(MenuAction.BOOKMARKS) }
              )
              DropdownMenuItem(
                text = { Text(stringResource(R.string.action_history)) },
                leadingIcon = { Icon(FeatherIcons.History, contentDescription = null) },
                onClick = { menuExpanded = false; onMenuAction(MenuAction.HISTORY) }
              )
              DropdownMenuItem(
                text = { Text(stringResource(R.string.action_downloads)) },
                leadingIcon = { Icon(FeatherIcons.Download, contentDescription = null) },
                onClick = { menuExpanded = false; onMenuAction(MenuAction.DOWNLOADS) }
              )
              DropdownMenuItem(
                text = { Text(stringResource(R.string.action_settings)) },
                leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                onClick = { menuExpanded = false; onMenuAction(MenuAction.SETTINGS) }
              )
            }
          }
        }
      }
    }
  }
}
