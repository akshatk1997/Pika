package com.example.ui.screens

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.model.SecurityState
import com.example.model.SettingsState
import com.example.model.TabInfo
import com.example.model.UserAgentMode
import com.example.model.WebPermissionKind
import com.example.security.UrlValidator
import com.example.ui.ActiveOverlay
import com.example.ui.BrowserViewModel
import com.example.ui.components.FeatherIcons
import com.example.ui.components.FindInPageBar
import com.example.ui.components.HttpWarningOverlay
import com.example.ui.components.NewTabHomeView
import com.example.ui.components.PageErrorOverlay
import com.example.ui.components.PageSourceOverlay
import com.example.ui.components.ReaderModeOverlay
import com.example.ui.theme.FeatherMintDark
import com.example.ui.theme.FeatherPrivateAccent
import com.example.ui.theme.FeatherSecureGreen
import com.example.ui.theme.FeatherWarningAmber
import com.example.ui.theme.LocalReduceMotion

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@SuppressLint("ClickableViewAccessibility")
@Composable
fun BrowserScreen(
  viewModel: BrowserViewModel,
  onExportBookmarks: () -> Unit,
  onImportBookmarks: () -> Unit,
  onSavePdf: () -> Unit,
  onPinHomeShortcut: (String, String) -> Unit
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
  val pageSource by viewModel.pageSource.collectAsStateWithLifecycle()
  val isBrowserFullscreen by viewModel.isBrowserFullscreen.collectAsStateWithLifecycle()
  val pendingDownload by viewModel.pendingDownloadConsent.collectAsStateWithLifecycle()
  val externalLinkPrompt by viewModel.externalLinkPrompt.collectAsStateWithLifecycle()
  val permissionPrompt by viewModel.permissionPrompt.collectAsStateWithLifecycle()
  val linkContextTarget by viewModel.linkContextTarget.collectAsStateWithLifecycle()
  val fullscreenView by viewModel.fullscreenView.collectAsStateWithLifecycle()
  val statusToast by viewModel.statusToast.collectAsStateWithLifecycle()
  val showIntro by viewModel.showIntro.collectAsStateWithLifecycle()

  var showSiteInfoDialog by remember { mutableStateOf(false) }
  var showQuickSheet by remember { mutableStateOf(false) }
  var isNavBarVisible by remember { mutableStateOf(true) }

  val activeTab = tabs.firstOrNull { it.id == activeTabId } ?: tabs.first()
  val snackbarHostState = remember { SnackbarHostState() }
  val focusManager = LocalFocusManager.current

  LaunchedEffect(activeTab.id, activeTab.url, activeTab.isLoading) {
    if (activeTab.isLoading || activeTab.url.isBlank() || activeTab.url == "about:blank") {
      isNavBarVisible = true
    }
  }

  LaunchedEffect(statusToast) {
    statusToast?.let { msg ->
      snackbarHostState.showSnackbar(msg)
      viewModel.consumeToast()
    }
  }

  BackHandler(
    enabled = showIntro ||
      fullscreenView != null ||
      isBrowserFullscreen ||
      pageSource != null ||
      readerArticle != null ||
      findState.isVisible ||
      activeOverlay != ActiveOverlay.NONE ||
      activeTab.httpWarning != null ||
      activeTab.errorState != null ||
      activeTab.canGoBack
  ) {
    if (showIntro) {
      viewModel.dismissIntro()
    } else if (fullscreenView != null) {
      viewModel.exitFullscreen()
    } else {
      viewModel.goBack()
    }
  }

  Box(modifier = Modifier.fillMaxSize()) {
    val activeFullscreen = fullscreenView
    val activePageSource = pageSource
    val activeReaderArticle = readerArticle
    val hasFullOverlay = showIntro ||
      activeFullscreen != null ||
      activePageSource != null ||
      activeReaderArticle != null ||
      activeOverlay != ActiveOverlay.NONE

    // Always keep the main browser Scaffold + live WebView mounted underneath overlays
    // so opening/updating Settings or Tabs never detaches or resets active web pages.
    BoxWithConstraints(
      modifier = Modifier
        .fillMaxSize()
        .let { if (hasFullOverlay) it.clearAndSetSemantics {} else it }
    ) {
      val isWideWidth = maxWidth >= 600.dp
      val showNavRail = isWideWidth && maxHeight >= 460.dp

      val isImeVisible = WindowInsets.isImeVisible

      Scaffold(
        contentWindowInsets = WindowInsets.statusBars
      ) { innerPadding ->
        Row(
          modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .navigationBarsPadding()
            .imePadding()
        ) {
          if (showNavRail && !isBrowserFullscreen) {
            NavigationRail(
              containerColor = MaterialTheme.colorScheme.surface,
              modifier = Modifier.fillMaxHeight().verticalScroll(rememberScrollState())
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
                onClick = { viewModel.setOverlay(ActiveOverlay.RESOURCES) },
                enabled = activeTab.url.isNotBlank(),
                icon = { Icon(FeatherIcons.MediaSniff, contentDescription = null) },
                label = {
                  Text(
                    if (activeTab.sniffedResourcesCount > 0) "${activeTab.sniffedResourcesCount}" else stringResource(R.string.action_resources),
                    maxLines = 1
                  )
                }
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
            AnimatedVisibility(
              visible = !isBrowserFullscreen && (isNavBarVisible || isImeVisible || activeTab.url.isBlank() || activeTab.url == "about:blank" || activeTab.isLoading),
              enter = expandVertically() + fadeIn(),
              exit = shrinkVertically() + fadeOut()
            ) {
              BrowserToolbar(
                activeTab = activeTab,
                tabCount = tabs.size,
                settings = settings,
                compactSingleRow = isWideWidth,
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
                onSwipeTab = { delta -> viewModel.switchAdjacentTab(delta) },
                onOpenQuickMenu = { showQuickSheet = true }
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
                    bookmarks = bookmarks,
                    onSubmitQuery = { viewModel.navigateActiveTab(it) },
                    onOpenBookmark = { viewModel.navigateActiveTab(it) }
                  )
                }
                else -> {
                  val density = LocalDensity.current
                  val slopPx = remember(density) { with(density) { 18.dp.toPx() } }
                  val triggerPx = remember(density) { with(density) { 120.dp.toPx() } }
                  var pullProgress by remember { mutableFloatStateOf(0f) }
                  val pullStartX = remember { mutableFloatStateOf(0f) }
                  val pullStartY = remember { mutableFloatStateOf(0f) }
                  val webContainerVisibility = if (hasFullOverlay) View.INVISIBLE else View.VISIBLE

                  Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                      factory = { ctx -> FrameLayout(ctx) },
                      update = { container ->
                        if (container.visibility != webContainerVisibility) {
                          container.visibility = webContainerVisibility
                        }
                        val targetWebView = viewModel.getOrCreateActiveWebView()
                        if (targetWebView == null || !viewModel.webViewPool.isWebViewAlive(targetWebView)) {
                          for (i in 0 until container.childCount) {
                            val child = container.getChildAt(i) as? android.webkit.WebView
                            if (child != null) {
                              child.setOnTouchListener(null)
                              viewModel.webViewPool.detachFromHost(child)
                            }
                          }
                          container.removeAllViews()
                          return@AndroidView
                        }
                        viewModel.webViewPool.attachToHost(targetWebView, container.context)
                        if (container.childCount != 1 || container.getChildAt(0) !== targetWebView) {
                          for (i in 0 until container.childCount) {
                            val child = container.getChildAt(i) as? android.webkit.WebView
                            if (child != null && child !== targetWebView) {
                              child.setOnTouchListener(null)
                              child.setOnScrollChangeListener(null)
                              viewModel.webViewPool.detachFromHost(child)
                            }
                          }
                          container.removeAllViews()
                          (targetWebView.parent as? ViewGroup)?.removeView(targetWebView)
                          try {
                            container.addView(
                              targetWebView,
                              FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                              )
                            )
                          } catch (_: Throwable) {
                          }
                        }
                        targetWebView.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                          if (isImeVisible) {
                            if (!isNavBarVisible) isNavBarVisible = true
                            return@setOnScrollChangeListener
                          }
                          val dy = scrollY - oldScrollY
                          if (scrollY <= 24) {
                            if (!isNavBarVisible) isNavBarVisible = true
                          } else if (dy > 14) {
                            if (isNavBarVisible) isNavBarVisible = false
                          } else if (dy < -14) {
                            if (!isNavBarVisible) isNavBarVisible = true
                          }
                        }
                        targetWebView.setOnTouchListener { v, event ->
                          if (v.scrollY == 0) {
                            when (event.actionMasked) {
                              MotionEvent.ACTION_DOWN -> {
                                pullStartX.floatValue = event.x
                                pullStartY.floatValue = event.y
                                pullProgress = 0f
                                if (!isNavBarVisible) isNavBarVisible = true
                              }
                              MotionEvent.ACTION_MOVE -> {
                                val dy = event.y - pullStartY.floatValue
                                val dx = kotlin.math.abs(event.x - pullStartX.floatValue)
                                if (dy > slopPx && dy > dx * 1.4f && pullStartY.floatValue > 0f) {
                                  pullProgress = ((dy - slopPx) / triggerPx).coerceIn(0f, 1f)
                                } else {
                                  pullProgress = 0f
                                }
                              }
                              MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                if (pullProgress >= 0.95f) {
                                  viewModel.reloadOrStop()
                                }
                                pullProgress = 0f
                                pullStartX.floatValue = 0f
                                pullStartY.floatValue = 0f
                              }
                            }
                          } else {
                            pullProgress = 0f
                          }
                          false
                        }
                      },
                      onRelease = { container ->
                        for (i in 0 until container.childCount) {
                          val child = container.getChildAt(i) as? android.webkit.WebView
                          if (child != null) {
                            child.setOnTouchListener(null)
                            child.setOnScrollChangeListener(null)
                            viewModel.webViewPool.detachFromHost(child)
                          }
                        }
                        container.removeAllViews()
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

              if (isBrowserFullscreen) {
                Surface(
                  shape = RoundedCornerShape(24.dp),
                  color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                  border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                  shadowElevation = 6.dp,
                  modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(14.dp)
                    .clickable { viewModel.exitBrowserFullscreen() }
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Icon(Icons.Filled.Close, contentDescription = "Exit fullscreen", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Exit fullscreen", style = MaterialTheme.typography.labelMedium)
                  }
                }
              }
            }
          }
        }
      }
    }

    if (hasFullOverlay) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
          ) {}
      ) {
        when {
          showIntro -> {
            IntroScreen(
              onGetStarted = { viewModel.dismissIntro() }
            )
          }
          activeFullscreen != null -> {
            val (customView, _) = activeFullscreen
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
              AndroidView(
                factory = { ctx -> FrameLayout(ctx) },
                update = { container ->
                  if (container.childCount != 1 || container.getChildAt(0) !== customView) {
                    container.removeAllViews()
                    try {
                      (customView.parent as? ViewGroup)?.removeView(customView)
                      container.addView(
                        customView,
                        FrameLayout.LayoutParams(
                          ViewGroup.LayoutParams.MATCH_PARENT,
                          ViewGroup.LayoutParams.MATCH_PARENT
                        )
                      )
                    } catch (_: Throwable) {
                    }
                  }
                },
                onRelease = { container ->
                  container.removeAllViews()
                },
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
          }
          activePageSource != null -> {
            val (url, html) = activePageSource
            PageSourceOverlay(
              pageUrl = url,
              htmlSource = html,
              onCopy = { viewModel.copyTextToClipboard(it, "Source copied") },
              onClose = { viewModel.closePageSource() }
            )
          }
          activeReaderArticle != null -> {
            ReaderModeOverlay(
              article = activeReaderArticle,
              fontSizeSp = settings.readerFontSizeSp,
              darkReading = settings.readerDarkMode,
              onChangeFontSize = { sz -> viewModel.updateSettings { it.copy(readerFontSizeSp = sz) } },
              onToggleDark = { dark -> viewModel.updateSettings { it.copy(readerDarkMode = dark) } },
              onClose = { viewModel.closeReaderMode() }
            )
          }
          activeOverlay == ActiveOverlay.TABS -> {
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
          }
          activeOverlay == ActiveOverlay.BOOKMARKS -> {
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
          }
          activeOverlay == ActiveOverlay.HISTORY -> {
            HistoryScreen(
              history = history,
              historyEnabled = settings.historyEnabled,
              onSearch = { viewModel.refreshHistory(it) },
              onOpenUrl = { viewModel.navigateActiveTab(it) },
              onDeleteEntry = { viewModel.deleteHistoryEntry(it) },
              onClearAll = { viewModel.clearAllHistory() },
              onClose = { viewModel.setOverlay(ActiveOverlay.NONE) }
            )
          }
          activeOverlay == ActiveOverlay.DOWNLOADS -> {
            DownloadsScreen(
              downloads = downloads,
              onCancel = { viewModel.downloadController.cancelDownload(it) },
              onRetry = { viewModel.downloadController.retryDownload(it) },
              onOpen = {
                if (!viewModel.downloadController.openDownloadedFile(it)) {
                  viewModel.showToast("No app found to open this file")
                }
              },
              onReveal = { viewModel.downloadController.revealDownloadedLocation(it) },
              onClearAll = { viewModel.downloadController.clearAllDownloads() },
              onClose = { viewModel.setOverlay(ActiveOverlay.NONE) }
            )
          }
          activeOverlay == ActiveOverlay.RESOURCES -> {
            ResourcesScreen(
              resources = viewModel.getActiveSniffedResources(),
              onDownloadResource = { res ->
                viewModel.setOverlay(ActiveOverlay.NONE)
                viewModel.downloadUrlWithConsent(res.url)
              },
              onCopyUrl = { viewModel.copyTextToClipboard(it, "Link copied") },
              onClose = { viewModel.setOverlay(ActiveOverlay.NONE) }
            )
          }
          activeOverlay == ActiveOverlay.SETTINGS -> {
            val shareableUrl = if (!activeTab.isPrivate &&
              UrlValidator.isSafeWebUrl(activeTab.url) &&
              activeTab.url != "about:blank"
            ) {
              activeTab.url
            } else {
              ""
            }
            SettingsScreen(
              settings = settings,
              currentUrl = shareableUrl,
              sitePermissions = sitePermissions,
              onUpdateSettings = { viewModel.updateSettings(it) },
              onAddCustomBlockedHost = { host -> viewModel.addCustomBlockedHost(host) },
              onRemoveCustomBlockedHost = { host -> viewModel.removeCustomBlockedHost(host) },
              onRemovePermissionRule = { viewModel.removeSitePermissionRule(it) },
              onClearBrowsingData = { opts -> viewModel.clearSelectedBrowsingData(opts) },
              onExportBookmarks = onExportBookmarks,
              onImportBookmarks = onImportBookmarks,
              onShowMessage = { viewModel.showToast(it) },
              onClose = { viewModel.setOverlay(ActiveOverlay.NONE) }
            )
          }
        }
      }
    }

    SnackbarHost(
      hostState = snackbarHostState,
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .navigationBarsPadding()
        .imePadding()
        .padding(12.dp)
    )
  }

  // Via / X Browser Quick Action Grid Sheet
  if (showQuickSheet) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val hasUrl = activeTab.url.isNotBlank()
    val isBookmarked = viewModel.isCurrentPageBookmarked()
    ModalBottomSheet(
      onDismissRequest = { showQuickSheet = false },
      sheetState = sheetState,
      containerColor = MaterialTheme.colorScheme.surface
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp)
          .padding(bottom = 20.dp)
      ) {
        // Quick Modes Row
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          FilterChip(
            selected = activeTab.userAgentMode == UserAgentMode.DESKTOP,
            onClick = {
              showQuickSheet = false
              viewModel.toggleDesktopMode()
            },
            leadingIcon = { Icon(FeatherIcons.DesktopPc, contentDescription = null, modifier = Modifier.size(16.dp)) },
            label = { Text("Desktop") }
          )
          FilterChip(
            selected = activeTab.userAgentMode == UserAgentMode.IPHONE,
            onClick = {
              showQuickSheet = false
              val next = if (activeTab.userAgentMode == UserAgentMode.IPHONE) UserAgentMode.MOBILE else UserAgentMode.IPHONE
              viewModel.setUserAgentMode(next)
            },
            label = { Text("iPhone") }
          )
          FilterChip(
            selected = settings.nightWebMode,
            onClick = { viewModel.updateSettings { it.copy(nightWebMode = !it.nightWebMode) } },
            leadingIcon = { Icon(FeatherIcons.NightMoon, contentDescription = null, modifier = Modifier.size(16.dp)) },
            label = { Text("Night mode") }
          )
          FilterChip(
            selected = settings.noImageMode,
            onClick = { viewModel.updateSettings { it.copy(noImageMode = !it.noImageMode) } },
            leadingIcon = { Icon(FeatherIcons.NoImage, contentDescription = null, modifier = Modifier.size(16.dp)) },
            label = { Text("Block images") }
          )
          FilterChip(
            selected = isBrowserFullscreen,
            onClick = {
              showQuickSheet = false
              viewModel.toggleBrowserFullscreen()
            },
            leadingIcon = { Icon(FeatherIcons.Fullscreen, contentDescription = null, modifier = Modifier.size(16.dp)) },
            label = { Text("Fullscreen") }
          )
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
        Spacer(modifier = Modifier.height(12.dp))

        // 4-Column Crisp Grid Actions
        val gridActions = listOf(
          QuickActionItem(Icons.Filled.Add, stringResource(R.string.action_new_tab), true) {
            showQuickSheet = false
            viewModel.openNewTab(isPrivate = false)
          },
          QuickActionItem(FeatherIcons.PrivateMask, stringResource(R.string.action_new_private_tab), true) {
            showQuickSheet = false
            viewModel.openNewTab(isPrivate = true)
          },
          QuickActionItem(
            if (isBookmarked) FeatherIcons.Bookmark else FeatherIcons.BookmarkOutline,
            if (isBookmarked) stringResource(R.string.action_remove_bookmark) else stringResource(R.string.action_add_bookmark),
            hasUrl
          ) {
            showQuickSheet = false
            viewModel.toggleCurrentBookmark()
          },
          QuickActionItem(Icons.Filled.Search, stringResource(R.string.action_find_in_page), hasUrl) {
            showQuickSheet = false
            viewModel.openFindInPage()
          },
          QuickActionItem(FeatherIcons.Bookmark, stringResource(R.string.action_bookmarks), true) {
            showQuickSheet = false
            viewModel.setOverlay(ActiveOverlay.BOOKMARKS)
          },
          QuickActionItem(FeatherIcons.History, stringResource(R.string.action_history), true) {
            showQuickSheet = false
            viewModel.setOverlay(ActiveOverlay.HISTORY)
          },
          QuickActionItem(FeatherIcons.Download, stringResource(R.string.action_downloads), true) {
            showQuickSheet = false
            viewModel.setOverlay(ActiveOverlay.DOWNLOADS)
          },
          QuickActionItem(
            FeatherIcons.MediaSniff,
            if (activeTab.sniffedResourcesCount > 0) "${stringResource(R.string.action_resources)} (${activeTab.sniffedResourcesCount})"
            else stringResource(R.string.action_resources),
            hasUrl
          ) {
            showQuickSheet = false
            viewModel.setOverlay(ActiveOverlay.RESOURCES)
          },
          QuickActionItem(FeatherIcons.Reader, stringResource(R.string.action_reader_mode), hasUrl) {
            showQuickSheet = false
            viewModel.toggleReaderMode()
          },
          QuickActionItem(FeatherIcons.PrintPdf, stringResource(R.string.action_save_pdf), hasUrl) {
            showQuickSheet = false
            onSavePdf()
          },
          QuickActionItem(FeatherIcons.CodeSource, stringResource(R.string.action_view_source), hasUrl) {
            showQuickSheet = false
            viewModel.openPageSource()
          },
          QuickActionItem(FeatherIcons.Translate, stringResource(R.string.action_translate), hasUrl) {
            showQuickSheet = false
            viewModel.translateActivePage()
          },
          QuickActionItem(Icons.Filled.Share, stringResource(R.string.action_share), hasUrl) {
            showQuickSheet = false
            viewModel.shareActiveUrl()
          },
          QuickActionItem(FeatherIcons.OpenInNew, stringResource(R.string.action_open_external), hasUrl) {
            showQuickSheet = false
            viewModel.openInSystemBrowser()
          },
          QuickActionItem(Icons.Filled.Star, stringResource(R.string.action_pin_home), hasUrl) {
            showQuickSheet = false
            onPinHomeShortcut(activeTab.url, activeTab.title)
          },
          QuickActionItem(Icons.Filled.Settings, stringResource(R.string.action_settings), true) {
            showQuickSheet = false
            viewModel.setOverlay(ActiveOverlay.SETTINGS)
          }
        )

        gridActions.chunked(4).forEach { rowItems ->
          Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            rowItems.forEach { item ->
              Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (item.enabled) 0.55f else 0.2f),
                modifier = Modifier
                  .weight(1f)
                  .clip(RoundedCornerShape(14.dp))
                  .clickable(enabled = item.enabled, onClick = item.onClick)
              ) {
                Column(
                  horizontalAlignment = Alignment.CenterHorizontally,
                  modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp)
                ) {
                  Icon(
                    imageVector = item.icon,
                    contentDescription = item.label,
                    tint = if (item.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                    modifier = Modifier.size(20.dp)
                  )
                  Spacer(modifier = Modifier.height(5.dp))
                  Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (item.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                  )
                }
              }
            }
          }
        }
      }
    }
  }

  // Site Info & Connection Security Dialog
  if (showSiteInfoDialog) {
    val host = UrlValidator.extractHost(activeTab.url).ifEmpty { "Start Page" }
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
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = when (activeTab.securityState) {
              SecurityState.SECURE_HTTPS -> "Connection is secure"
              SecurityState.PRIVATE -> "Private browsing is active"
              SecurityState.INSECURE_HTTP -> "Connection is not secure"
              SecurityState.HOME -> "Start page"
              SecurityState.ERROR -> "This page could not be verified"
            },
            style = MaterialTheme.typography.bodyMedium
          )
          if (activeTab.url.isNotBlank()) {
            Text(
              text = "${activeTab.trackersBlockedCount} ads & trackers blocked",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.primary
            )
          }
          HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
          Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("JavaScript", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(
              checked = settings.javascriptEnabled,
              onCheckedChange = { enabled -> viewModel.updateSettings { it.copy(javascriptEnabled = enabled) } }
            )
          }
          Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Block ads & trackers", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(
              checked = settings.trackerBlockingEnabled,
              onCheckedChange = { enabled -> viewModel.updateSettings { it.copy(trackerBlockingEnabled = enabled) } }
            )
          }
          Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Dark mode for sites", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(
              checked = settings.nightWebMode,
              onCheckedChange = { enabled -> viewModel.updateSettings { it.copy(nightWebMode = enabled) } }
            )
          }
          Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Block images", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(
              checked = settings.noImageMode,
              onCheckedChange = { enabled -> viewModel.updateSettings { it.copy(noImageMode = enabled) } }
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
      title = { Text(if (target.isImage) "Image Options" else "Link Options") },
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
          ContextActionRow("Copy URL") {
            viewModel.dismissLinkContext()
            viewModel.copyTextToClipboard(target.url)
          }
          ContextActionRow("Download") {
            viewModel.dismissLinkContext()
            viewModel.downloadUrlWithConsent(target.url)
          }
          ContextActionRow("Share") {
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

  // Consent-First Download Dialog (with customizable filename)
  pendingDownload?.let { req ->
    var customName by remember(req.id) { mutableStateOf(req.fileName) }
    AlertDialog(
      onDismissRequest = { viewModel.downloadController.dismissPendingConsent() },
      title = { Text(stringResource(R.string.download_consent_title)) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(stringResource(R.string.download_consent_body, req.fileName, req.host))
          OutlinedTextField(
            value = customName,
            onValueChange = { customName = it },
            label = { Text("Filename") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
          )
          if (!req.isHttps) {
            Text(
              text = "Unencrypted HTTP download",
              style = MaterialTheme.typography.labelSmall,
              color = FeatherWarningAmber
            )
          }
        }
      },
      confirmButton = {
        TextButton(
          onClick = { viewModel.downloadController.approvePendingDownload(customName) },
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
      title = { Text("Open external app?") },
      text = {
        Text("Open '${ext.scheme}:' link?\n\n${ext.targetSummary}")
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
      title = { Text("Permission request") },
      text = {
        val permissionLabel = when (perm.kind) {
          WebPermissionKind.LOCATION -> "your location"
          WebPermissionKind.CAMERA_MIC -> "your camera and microphone"
          WebPermissionKind.PROTECTED_MEDIA -> "protected media playback"
          WebPermissionKind.POPUP -> "to open a popup"
          WebPermissionKind.FULLSCREEN -> "fullscreen view"
          WebPermissionKind.CLIPBOARD -> "clipboard access"
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("${perm.origin} requests $permissionLabel.")
          if (perm.detail.isNotBlank()) {
            Text(perm.detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          if (!activeTab.isPrivate) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.clickable { rememberSite = !rememberSite }
            ) {
              Checkbox(checked = rememberSite, onCheckedChange = { rememberSite = it })
              Text("Remember for this site", style = MaterialTheme.typography.bodyMedium)
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

private data class QuickActionItem(
  val icon: ImageVector,
  val label: String,
  val enabled: Boolean,
  val onClick: () -> Unit
)

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

@Composable
private fun BrowserToolbar(
  activeTab: TabInfo,
  tabCount: Int,
  settings: SettingsState,
  compactSingleRow: Boolean,
  readClipboard: () -> String?,
  onNavigate: (String) -> Unit,
  onBack: () -> Unit,
  onForward: () -> Unit,
  onReloadOrStop: () -> Unit,
  onCopyUrl: () -> Unit,
  onOpenSiteInfo: () -> Unit,
  onOpenTabs: () -> Unit,
  onSwipeTab: (Int) -> Unit,
  onOpenQuickMenu: () -> Unit,
  modifier: Modifier = Modifier
) {
  var omniboxValue by remember(activeTab.id, activeTab.url) {
    mutableStateOf(TextFieldValue(activeTab.url, TextRange(0, activeTab.url.length)))
  }
  var isFocused by remember { mutableStateOf(false) }
  var clipboardCandidate by remember { mutableStateOf<String?>(null) }
  val focusManager = LocalFocusManager.current
  val isHomePage = activeTab.url.isBlank()

  val (secIcon, secTint, secDesc) = when (activeTab.securityState) {
    SecurityState.PRIVATE -> Triple(FeatherIcons.PrivateMask, FeatherPrivateAccent, stringResource(R.string.cd_security_private))
    SecurityState.SECURE_HTTPS -> Triple(Icons.Filled.Lock, FeatherSecureGreen, stringResource(R.string.cd_security_https))
    SecurityState.INSECURE_HTTP, SecurityState.ERROR -> Triple(Icons.Filled.Warning, FeatherWarningAmber, stringResource(R.string.cd_security_http))
    SecurityState.HOME -> Triple(Icons.Filled.Search, MaterialTheme.colorScheme.onSurfaceVariant, stringResource(R.string.cd_security_home))
  }

  val displayHost = remember(activeTab.url) {
    UrlValidator.extractHost(activeTab.url).removePrefix("www.")
  }

  val omniboxPill: @Composable (Modifier) -> Unit = { pillModifier ->
    var swipeAccum by remember { mutableFloatStateOf(0f) }
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
      modifier = pillModifier
        .height(46.dp)
        .pointerInput(tabCount) {
          detectHorizontalDragGestures(
            onDragStart = { swipeAccum = 0f },
            onDragEnd = {
              if (swipeAccum > 90f) onSwipeTab(-1)
              else if (swipeAccum < -90f) onSwipeTab(1)
              swipeAccum = 0f
            },
            onHorizontalDrag = { _, dragAmount ->
              swipeAccum += dragAmount
            }
          )
        }
    ) {
      Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
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
            val modeTag = when (activeTab.userAgentMode) {
              UserAgentMode.DESKTOP -> " · PC"
              UserAgentMode.IPHONE -> " · iOS"
              UserAgentMode.MOBILE -> ""
            }
            Text(
              text = "$displayHost$modeTag",
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
        if (settings.noImageMode) {
          Icon(
            FeatherIcons.NoImage,
            contentDescription = "No-Image mode active",
            tint = FeatherWarningAmber,
            modifier = Modifier.padding(end = 6.dp).size(15.dp)
          )
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
              Icon(FeatherIcons.Shield, contentDescription = "Blocked", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
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
  }

  Surface(
    tonalElevation = 3.dp,
    shadowElevation = 4.dp,
    color = MaterialTheme.colorScheme.surface,
    modifier = modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      if (compactSingleRow) {
        // Tablet, foldable & desktop-window single-row top toolbar
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          if (isHomePage) {
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = secTint.copy(alpha = 0.14f),
              modifier = Modifier
                .size(34.dp)
                .clickable { onOpenSiteInfo() }
                .testTag("security_badge_button")
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = secIcon,
                  contentDescription = secDesc,
                  tint = secTint,
                  modifier = Modifier.size(18.dp)
                )
              }
            }
            Spacer(modifier = Modifier.width(4.dp))
          }
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
            enabled = activeTab.url.isNotBlank() || activeTab.errorState != null,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("nav_reload_button")
          ) {
            Icon(
              imageVector = if (activeTab.isLoading) Icons.Filled.Close else Icons.Filled.Refresh,
              contentDescription = if (activeTab.isLoading) stringResource(R.string.cd_stop) else stringResource(R.string.cd_reload)
            )
          }
          if (!isHomePage) {
            Spacer(modifier = Modifier.width(6.dp))
            omniboxPill(Modifier.weight(1f))
            Spacer(modifier = Modifier.width(6.dp))
          } else {
            Spacer(modifier = Modifier.weight(1f))
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
          IconButton(
            onClick = onOpenQuickMenu,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("nav_menu_button")
          ) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.cd_menu))
          }
        }
      } else {
        if (isHomePage) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Surface(
                shape = RoundedCornerShape(10.dp),
                color = secTint.copy(alpha = 0.14f),
                modifier = Modifier
                  .size(34.dp)
                  .clickable { onOpenSiteInfo() }
                  .testTag("security_badge_button")
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(
                    imageVector = secIcon,
                    contentDescription = secDesc,
                    tint = secTint,
                    modifier = Modifier.size(18.dp)
                  )
                }
              }
              Spacer(modifier = Modifier.width(4.dp))
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
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
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

              IconButton(
                onClick = onOpenQuickMenu,
                modifier = Modifier.minimumInteractiveComponentSize().testTag("nav_menu_button")
              ) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.cd_menu))
              }
            }
          }
        } else {
          Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) {
            omniboxPill(Modifier.fillMaxWidth())

            Row(
              modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
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
                enabled = activeTab.url.isNotBlank() || activeTab.errorState != null,
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

              IconButton(
                onClick = onOpenQuickMenu,
                modifier = Modifier.minimumInteractiveComponentSize().testTag("nav_menu_button")
              ) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.cd_menu))
              }
            }
          }
        }
      }

      if (!isHomePage && isFocused && !clipboardCandidate.isNullOrBlank() && clipboardCandidate != activeTab.url) {
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

      HorizontalDivider(
        thickness = 0.8.dp,
        color = if (activeTab.isPrivate) {
          FeatherPrivateAccent.copy(alpha = 0.45f)
        } else {
          MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
        }
      )
    }
  }
}
