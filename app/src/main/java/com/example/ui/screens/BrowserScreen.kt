package com.example.ui.screens

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import com.example.ui.components.SiteFavicon
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.example.model.DetectedMedia
import com.example.model.SecurityState
import com.example.model.SettingsState
import com.example.model.TabInfo
import com.example.model.ThemeMode
import com.example.model.UserAgentMode
import com.example.model.WebPermissionKind
import com.example.security.UrlValidator
import com.example.ui.ActiveOverlay
import com.example.ui.BrowserViewModel
import com.example.ui.components.FeatherIcons
import com.example.ui.components.FindInPageBar
import com.example.ui.components.HttpWarningOverlay
import com.example.ui.components.ImageSnifferDialog
import com.example.ui.components.MediaDownloaderDialog
import com.example.ui.components.NewTabHomeView
import com.example.ui.components.PageErrorOverlay
import com.example.ui.components.PageSourceOverlay
import com.example.ui.components.ReaderModeOverlay
import com.example.ui.components.ScreenFilterDialog
import com.example.ui.theme.FeatherDangerRed
import com.example.ui.theme.FeatherMintDark
import com.example.ui.theme.FeatherPrivateAccent
import com.example.ui.theme.FeatherPrivateAccentLight
import com.example.ui.theme.FeatherSecureGreen
import com.example.ui.theme.FeatherWarningAmber
import com.example.ui.theme.LocalReduceMotion

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@SuppressLint("ClickableViewAccessibility")
@Composable
fun BrowserScreen(
  viewModel: BrowserViewModel,
  resolvedDarkTheme: Boolean? = null,
  systemDarkTheme: Boolean? = null,
  onExportBookmarks: () -> Unit,
  onImportBookmarks: () -> Unit,
  onSavePdf: () -> Unit,
  onPinHomeShortcut: (String, String) -> Unit,
  onCaptureFullPageScreenshot: () -> Unit = {},
  onPlayMediaExternal: (DetectedMedia) -> Unit = {}
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
  val readerSegments by viewModel.readerSegments.collectAsStateWithLifecycle()
  val narratorState by viewModel.narratorState.collectAsStateWithLifecycle()
  val pageSource by viewModel.pageSource.collectAsStateWithLifecycle()
  val pendingDownload by viewModel.pendingDownloadConsent.collectAsStateWithLifecycle()
  val externalLinkPrompt by viewModel.externalLinkPrompt.collectAsStateWithLifecycle()
  val permissionPrompt by viewModel.permissionPrompt.collectAsStateWithLifecycle()
  val linkContextTarget by viewModel.linkContextTarget.collectAsStateWithLifecycle()
  val fullscreenView by viewModel.fullscreenView.collectAsStateWithLifecycle()
  val statusToast by viewModel.statusToast.collectAsStateWithLifecycle()
  val showIntro by viewModel.showIntro.collectAsStateWithLifecycle()
  val phishingAssessment by viewModel.phishingAssessment.collectAsStateWithLifecycle()
  val detectedMediaMap by viewModel.detectedMediaMap.collectAsStateWithLifecycle()
  val extractedImages by viewModel.extractedImages.collectAsStateWithLifecycle()
  val availableUpdate by viewModel.availableUpdate.collectAsStateWithLifecycle()

  var showSiteInfoDialog by remember { mutableStateOf(false) }
  var showPrivateAuditDialog by remember { mutableStateOf(false) }
  var showQuickSheet by remember { mutableStateOf(false) }
  var isNavBarVisible by remember { mutableStateOf(true) }

  val systemDark = systemDarkTheme ?: isSystemInDarkTheme()
  val isDarkTheme = resolvedDarkTheme ?: when (settings.themeMode) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
  }

  val activeTab = tabs.firstOrNull { it.id == activeTabId } ?: tabs.first()
  val detectedMediaList = remember(detectedMediaMap, activeTab.id) {
    detectedMediaMap[activeTab.id].orEmpty()
  }
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

  var isOneHandedActive by remember { mutableStateOf(false) }

  LaunchedEffect(settings.oneHandedModeEnabled) {
    isOneHandedActive = settings.oneHandedModeEnabled
  }

  val isBookmarked = remember(bookmarks, activeTab.url, activeTab.isPrivate) {
    !activeTab.isPrivate && activeTab.url.isNotBlank() && activeTab.url != "about:blank" &&
      bookmarks.any { it.url.equals(activeTab.url, ignoreCase = true) }
  }

  BackHandler(
    enabled = (settings.oneHandedModeEnabled && isOneHandedActive) ||
      showIntro ||
      fullscreenView != null ||
      pageSource != null ||
      readerArticle != null ||
      findState.isVisible ||
      activeOverlay != ActiveOverlay.NONE ||
      activeTab.httpWarning != null ||
      activeTab.errorState != null ||
      activeTab.canGoBack
  ) {
    if (settings.oneHandedModeEnabled && isOneHandedActive) {
      isOneHandedActive = false
    } else if (showIntro) {
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
      val isImeVisible = WindowInsets.isImeVisible

      Scaffold(
        containerColor = when {
          isDarkTheme -> Color.Black
          activeTab.isPrivate -> Color(0xFFF6F3FF)
          else -> Color(0xFFF8FAFC)
        },
        contentWindowInsets = WindowInsets.statusBars
      ) { innerPadding ->
        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .navigationBarsPadding()
            .imePadding()
        ) {
          AnimatedVisibility(
            visible = isNavBarVisible || isImeVisible || activeTab.url.isBlank() || activeTab.url == "about:blank" || activeTab.isLoading,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
          ) {
            BrowserToolbar(
              activeTab = activeTab,
              tabCount = tabs.size,
              isDarkTheme = isDarkTheme,
              settings = settings,
              compactSingleRow = false,
              isBookmarked = isBookmarked,
              onToggleBookmark = { viewModel.toggleCurrentBookmark() },
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
              onOpenQuickMenu = { showQuickSheet = true },
              onToggleDesktopMode = { viewModel.toggleDesktopMode() },
              onOpenBookmarks = { viewModel.setOverlay(ActiveOverlay.BOOKMARKS) },
              onOpenDownloads = { viewModel.setOverlay(ActiveOverlay.DOWNLOADS) },
              onNewTab = { viewModel.openNewTab(isPrivate = activeTab.isPrivate) }
            )
          }

          if (activeTab.isLoading && activeTab.progress in 1..99) {
              val animatedProgress by animateFloatAsState(
                targetValue = (activeTab.progress / 100f).coerceIn(0.05f, 1f),
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                label = "page_progress"
              )
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .height(3.dp)
                  .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
              ) {
                Box(
                  modifier = Modifier
                    .fillMaxWidth(fraction = animatedProgress)
                    .fillMaxHeight()
                    .background(
                      Brush.horizontalGradient(
                        if (activeTab.isPrivate) {
                          listOf(FeatherPrivateAccent, Color(0xFFC084FC), FeatherPrivateAccent)
                        } else {
                          listOf(MaterialTheme.colorScheme.primary, FeatherMintDark)
                        }
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

            phishingAssessment?.let { scam ->
              Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp)
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = "⚠️ Suspected Scam / Phishing (${scam.score}/100)",
                      style = MaterialTheme.typography.labelMedium,
                      color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                      text = scam.reasons.firstOrNull() ?: "High risk of credential theft",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onErrorContainer
                    )
                  }
                  Row {
                    TextButton(onClick = { viewModel.goBack() }) {
                      Text("Leave", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { viewModel.dismissPhishingWarning(scam.url) }) {
                      Text("Dismiss", color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                  }
                }
              }
            }

            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
              val fullHeight = maxHeight
              val isOneHanded = settings.oneHandedModeEnabled && isOneHandedActive

              val animatedOffsetFraction by animateFloatAsState(
                targetValue = if (isOneHanded) 0.38f else 0f,
                animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
                label = "one_handed_offset"
              )

              Box(modifier = Modifier.fillMaxSize()) {
                if (isOneHanded || animatedOffsetFraction > 0.01f) {
                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(fullHeight * animatedOffsetFraction)
                    .background(
                      if (isDarkTheme) Color(0xFF070A12).copy(alpha = 0.92f)
                      else Color(0xFFE2E8F0).copy(alpha = 0.92f)
                    )
                    .clickable { isOneHandedActive = false }
                    .testTag("one_handed_backdrop"),
                  contentAlignment = Alignment.Center
                ) {
                  Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isDarkTheme) Color(0xFF1E293B) else Color.White,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                    shadowElevation = 2.dp,
                    modifier = Modifier
                      .clickable { isOneHandedActive = false }
                      .testTag("one_handed_restore_banner")
                  ) {
                    Row(
                      modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Icon(
                        imageVector = FeatherIcons.Fullscreen,
                        contentDescription = "Full Screen",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                      )
                      Spacer(modifier = Modifier.width(6.dp))
                      Text(
                        text = "One-Handed Mode · Tap to expand",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                      )
                    }
                  }
                }
              }

              val contentAlignment = when (settings.oneHandedHandPreference) {
                com.example.model.OneHandedPreference.LEFT -> Alignment.BottomStart
                com.example.model.OneHandedPreference.RIGHT -> Alignment.BottomEnd
                com.example.model.OneHandedPreference.CENTER -> Alignment.BottomCenter
              }
              val contentWidthFraction = if (isOneHanded && settings.oneHandedHandPreference != com.example.model.OneHandedPreference.CENTER) 0.93f else 1.0f

              Box(
                modifier = Modifier
                  .fillMaxWidth(contentWidthFraction)
                  .fillMaxHeight(1f - animatedOffsetFraction)
                  .align(contentAlignment)
                  .clip(if (isOneHanded) RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp) else RoundedCornerShape(0.dp))
              ) {
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
                activeTab.url.isBlank() && !activeTab.isLoading -> {
                  NewTabHomeView(
                    isPrivate = activeTab.isPrivate,
                    isDarkTheme = isDarkTheme,
                    searchEngineName = settings.searchEngine.label,
                    bookmarks = if (activeTab.isPrivate) emptyList() else bookmarks,
                    onSubmitQuery = { viewModel.navigateActiveTab(it) },
                    onOpenBookmark = { viewModel.navigateActiveTab(it) }
                  )
                }
                else -> {
                  val density = LocalDensity.current
                  val slopPx = remember(density) { with(density) { 20.dp.toPx() } }
                  val triggerPx = remember(density) { with(density) { 92.dp.toPx() } }
                  var pullProgress by remember { mutableFloatStateOf(0f) }
                  val pullStartX = remember { mutableFloatStateOf(0f) }
                  val pullStartY = remember { mutableFloatStateOf(0f) }
                  var isPressSlideEngaged by remember { mutableStateOf(false) }
                  val pullHaptic = LocalHapticFeedback.current
                  val webContainerVisibility = if (hasFullOverlay && !settings.backgroundPlayEnabled) View.INVISIBLE else View.VISIBLE

                  Box(modifier = Modifier.fillMaxSize()) {
                    key(activeTab.id) {
                      AndroidView(
                        factory = { ctx ->
                          FrameLayout(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                              ViewGroup.LayoutParams.MATCH_PARENT,
                              ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            val targetWebView = viewModel.getOrCreateWebView(activeTab.id)
                            if (targetWebView != null && viewModel.webViewPool.isWebViewAlive(targetWebView)) {
                              viewModel.webViewPool.attachToHost(targetWebView, ctx)
                              (targetWebView.parent as? ViewGroup)?.removeView(targetWebView)
                              addView(
                                targetWebView,
                                FrameLayout.LayoutParams(
                                  ViewGroup.LayoutParams.MATCH_PARENT,
                                  ViewGroup.LayoutParams.MATCH_PARENT
                                )
                              )
                              targetWebView.requestLayout()
                              if (settings.grayscaleModeEnabled) {
                                val cm = android.graphics.ColorMatrix().apply { setSaturation(0f) }
                                val grayPaint = android.graphics.Paint().apply {
                                  colorFilter = android.graphics.ColorMatrixColorFilter(cm)
                                }
                                targetWebView.setLayerType(View.LAYER_TYPE_HARDWARE, grayPaint)
                              }
                            }
                          }
                        },
                        update = { container ->
                          if (container.visibility != webContainerVisibility) {
                            container.visibility = webContainerVisibility
                          }
                          val targetWebView = viewModel.getOrCreateWebView(activeTab.id)
                          if (targetWebView == null || !viewModel.webViewPool.isWebViewAlive(targetWebView)) {
                            for (i in 0 until container.childCount) {
                              val child = container.getChildAt(i) as? android.webkit.WebView
                              if (child != null) {
                                child.setOnTouchListener(null)
                                child.setOnScrollChangeListener(null)
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
                          if (settings.grayscaleModeEnabled) {
                            val cm = android.graphics.ColorMatrix().apply { setSaturation(0f) }
                            val grayPaint = android.graphics.Paint().apply {
                              colorFilter = android.graphics.ColorMatrixColorFilter(cm)
                            }
                            targetWebView.setLayerType(View.LAYER_TYPE_HARDWARE, grayPaint)
                          } else {
                            targetWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
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
                            val isFullscreen = fullscreenView != null
                            val isAtTop = !isFullscreen && (v.scrollY <= 0 || !v.canScrollVertically(-1))
                            if (isAtTop) {
                              when (event.actionMasked) {
                                MotionEvent.ACTION_DOWN -> {
                                  pullStartX.floatValue = event.x
                                  pullStartY.floatValue = event.y
                                  pullProgress = 0f
                                  isPressSlideEngaged = false
                                  if (!isNavBarVisible) isNavBarVisible = true
                                }
                                MotionEvent.ACTION_MOVE -> {
                                  if (pullStartY.floatValue <= 0f) {
                                    pullStartX.floatValue = event.x
                                    pullStartY.floatValue = event.y
                                  }
                                  val pressDuration = event.eventTime - event.downTime
                                  val dy = event.y - pullStartY.floatValue
                                  val dx = kotlin.math.abs(event.x - pullStartX.floatValue)

                                  // Only activate reload when user deliberately presses (>= 150ms) and slides down vertically, not on rapid scrolling or video interaction
                                  if (!isPressSlideEngaged && pressDuration >= 150L && dy > slopPx * 1.2f && dy > dx * 1.8f) {
                                    isPressSlideEngaged = true
                                    pullHaptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                  }

                                  if (isPressSlideEngaged && dy > slopPx && dy > dx * 1.5f && pullStartY.floatValue > 0f) {
                                    val newProgress = ((dy - slopPx) / triggerPx).coerceIn(0f, 1f)
                                    if (newProgress >= 0.85f && pullProgress < 0.85f) {
                                      pullHaptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                    pullProgress = newProgress
                                  } else if (dy <= 0f) {
                                    pullProgress = 0f
                                    isPressSlideEngaged = false
                                  }
                                }
                                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                  val wasEngaged = isPressSlideEngaged
                                  val progress = pullProgress
                                  pullProgress = 0f
                                  isPressSlideEngaged = false
                                  pullStartX.floatValue = 0f
                                  pullStartY.floatValue = 0f
                                  if (wasEngaged && progress >= 0.85f) {
                                    pullHaptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.reloadOrStop()
                                    return@setOnTouchListener true
                                  }
                                }
                              }
                            } else {
                              if (pullProgress > 0f || isPressSlideEngaged) {
                                pullProgress = 0f
                                isPressSlideEngaged = false
                                pullStartX.floatValue = 0f
                                pullStartY.floatValue = 0f
                              }
                            }
                            isPressSlideEngaged
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
                    }
                    if (pullProgress > 0.06f) {
                      val pullRotation = pullProgress * 360f
                      Surface(
                        shape = CircleShape,
                        color = if (isDarkTheme) Color(0xFF1E2330) else Color.White,
                        shadowElevation = 6.dp,
                        border = BorderStroke(1.dp, if (isDarkTheme) Color(0xFF333C4E) else Color(0xFFE2E8F0)),
                        modifier = Modifier
                          .align(Alignment.TopCenter)
                          .offset(y = (pullProgress * 50).dp)
                          .size(40.dp)
                      ) {
                        Box(contentAlignment = Alignment.Center) {
                          Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.cd_reload),
                            tint = if (pullProgress >= 0.85f) {
                              if (activeTab.isPrivate) FeatherPrivateAccent else if (isDarkTheme) Color(0xFF38BDF8) else Color(0xFF0284C7)
                            } else {
                              if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B)
                            },
                            modifier = Modifier
                              .size(20.dp)
                              .rotate(pullRotation)
                          )
                        }
                      }
                    }
                  }
                }
              }

              if (settings.screenFilterEnabled) {
                Box(
                  modifier = Modifier
                    .fillMaxSize()
                    .background(Color(settings.screenFilterType.colorHex).copy(alpha = settings.screenFilterOpacity))
                )
              }
            }
          }

          val groupTabs = remember(tabs, activeTab.groupId) {
            val gid = activeTab.groupId
            if (gid == null) emptyList() else tabs.filter { it.groupId == gid }
          }
          if (groupTabs.size > 1) {
            Surface(
              color = if (isDarkTheme) Color(0xFF11131A) else Color(0xFFF1F5F9),
              tonalElevation = 2.dp,
              modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("tab_group_bottom_strip")
            ) {
              Row(
                modifier = Modifier
                  .fillMaxSize()
                  .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                IconButton(
                  onClick = { viewModel.openNewTabInGroup(isPrivate = activeTab.isPrivate, initialUrl = "", switchToNewTab = true) },
                  modifier = Modifier.size(34.dp)
                ) {
                  Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "New tab in group",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                  )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Row(
                  modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  groupTabs.forEach { gTab ->
                    val isCurrent = gTab.id == activeTab.id
                    Box(
                      modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(
                          if (isCurrent) MaterialTheme.colorScheme.primaryContainer
                          else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                        )
                        .border(
                          width = if (isCurrent) 2.dp else 0.8.dp,
                          color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                          shape = CircleShape
                        )
                        .clickable {
                          if (isCurrent) {
                            viewModel.closeTab(gTab.id)
                          } else {
                            viewModel.switchToTab(gTab.id)
                          }
                        },
                      contentAlignment = Alignment.Center
                    ) {
                      SiteFavicon(
                        url = gTab.url,
                        title = gTab.title,
                        size = 22.dp,
                        isPrivate = gTab.isPrivate,
                        isDarkTheme = isDarkTheme
                      )
                    }
                  }
                }
                IconButton(
                  onClick = { viewModel.setOverlay(ActiveOverlay.TABS) },
                  modifier = Modifier.size(34.dp)
                ) {
                  Icon(
                    imageVector = Icons.Filled.KeyboardArrowUp,
                    contentDescription = "View all tabs",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                  )
                }
              }
            }
          }

          if (settings.oneHandedModeEnabled && !isOneHandedActive) {
              Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shadowElevation = 4.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                modifier = Modifier
                  .align(if (settings.oneHandedHandPreference == com.example.model.OneHandedPreference.LEFT) Alignment.BottomStart else Alignment.BottomEnd)
                  .padding(14.dp)
                  .size(42.dp)
                  .clickable { isOneHandedActive = true }
                  .testTag("one_handed_quick_toggle")
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(
                    imageVector = FeatherIcons.OneHand,
                    contentDescription = "One-Handed Mode",
                    modifier = Modifier.size(20.dp)
                  )
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
            val currentView = androidx.compose.ui.platform.LocalView.current
            androidx.compose.runtime.DisposableEffect(customView) {
              val activity = currentView.context as? android.app.Activity
              val window = activity?.window
              if (window != null) {
                try {
                  val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                  insetsController.systemBarsBehavior =
                    androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                  insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                  window.addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
                  window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } catch (_: Throwable) {
                }
              }
              onDispose {
                if (window != null && viewModel.fullscreenView.value == null) {
                  try {
                    val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                    insetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                  } catch (_: Throwable) {
                  }
                }
              }
            }
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
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.65f),
                border = BorderStroke(0.6.dp, Color.White.copy(alpha = 0.35f)),
                contentColor = Color.White,
                shadowElevation = 4.dp,
                modifier = Modifier
                  .align(Alignment.TopEnd)
                  .padding(top = 16.dp, end = 16.dp)
                  .clickable { viewModel.exitFullscreen() }
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(
                    Icons.Filled.Close,
                    contentDescription = "Exit",
                    modifier = Modifier.size(13.dp),
                    tint = Color.White
                  )
                  Spacer(modifier = Modifier.width(3.dp))
                  Text(
                    "Exit",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                  )
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
              segments = readerSegments,
              narratorState = narratorState,
              fontSizeSp = settings.readerFontSizeSp,
              darkReading = settings.readerDarkMode,
              onTogglePlayPause = { viewModel.toggleNarratorPlayPause() },
              onPrevSegment = { viewModel.prevNarratorSegment() },
              onNextSegment = { viewModel.nextNarratorSegment() },
              onSelectSegment = { viewModel.selectNarratorSegment(it) },
              onChangeSpeechRate = { viewModel.setNarratorSpeechRate(it) },
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
              onReopenClosedTab = { url -> viewModel.reopenClosedTab(url) },
              onEndPrivateSession = { viewModel.endAllPrivateTabs() },
              onCloseSheet = { viewModel.setOverlay(ActiveOverlay.NONE) }
            )
          }
          activeOverlay == ActiveOverlay.BOOKMARKS && !activeTab.isPrivate -> {
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
          activeOverlay == ActiveOverlay.HISTORY && !activeTab.isPrivate -> {
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
              isPrivateMode = activeTab.isPrivate,
              systemDarkTheme = systemDark,
              sitePermissions = sitePermissions,
              systemWebViewInfo = viewModel.systemWebViewPackageInfo,
              availableUpdate = availableUpdate,
              onCheckForUpdates = { viewModel.checkForUpdates(silent = true) },
              onDownloadUpdate = { url, ver -> viewModel.downloadAppUpdate(url, ver) },
              onUpdateSettings = { viewModel.updateSettings(it) },
              onAddCustomBlockedHost = { host -> viewModel.addCustomBlockedHost(host) },
              onRemoveCustomBlockedHost = { host -> viewModel.removeCustomBlockedHost(host) },
              onRemovePermissionRule = { viewModel.removeSitePermissionRule(it) },
              onClearBrowsingData = { opts -> viewModel.clearSelectedBrowsingData(opts) },
              onExportBookmarks = onExportBookmarks,
              onImportBookmarks = onImportBookmarks,
              onCopyText = { text, msg -> viewModel.copyTextToClipboard(text, msg) },
              onShowMessage = { viewModel.showToast(it) },
              onClose = { viewModel.setOverlay(ActiveOverlay.NONE) }
            )
          }
          activeOverlay == ActiveOverlay.MEDIA_DOWNLOADER -> {
            MediaDownloaderDialog(
              mediaList = detectedMediaList,
              images = extractedImages,
              onDownload = { viewModel.downloadMedia(it) },
              onCopyUrl = { viewModel.copyTextToClipboard(it, "Media URL copied") },
              onPlayExternal = onPlayMediaExternal,
              onDownloadImages = { viewModel.downloadImages(it) },
              onClose = { viewModel.setOverlay(ActiveOverlay.NONE) }
            )
          }
          activeOverlay == ActiveOverlay.IMAGE_SNIFFER -> {
            ImageSnifferDialog(
              images = extractedImages,
              onDownloadImages = { viewModel.downloadImages(it) },
              onClose = { viewModel.setOverlay(ActiveOverlay.NONE) }
            )
          }
          activeOverlay == ActiveOverlay.SCREEN_FILTER_SETTINGS -> {
            ScreenFilterDialog(
              enabled = settings.screenFilterEnabled,
              selectedType = settings.screenFilterType,
              opacity = settings.screenFilterOpacity,
              onToggleEnabled = { viewModel.toggleScreenFilter() },
              onSelectType = { viewModel.setScreenFilterType(it) },
              onOpacityChange = { viewModel.setScreenFilterOpacity(it) },
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

  // Quick Action Grid Sheet (strictly separated Standard vs Private Mode controls)
  if (showQuickSheet) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val hasUrl = activeTab.url.isNotBlank()
    val isPrivate = activeTab.isPrivate
    val isBookmarked = !isPrivate && viewModel.isCurrentPageBookmarked()
    ModalBottomSheet(
      onDismissRequest = { showQuickSheet = false },
      sheetState = sheetState,
      containerColor = MaterialTheme.colorScheme.surface
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 16.dp)
          .padding(bottom = 20.dp)
      ) {
        if (isPrivate) {
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = (if (isDarkTheme) FeatherPrivateAccent else FeatherPrivateAccentLight).copy(alpha = 0.14f),
            border = BorderStroke(0.8.dp, (if (isDarkTheme) FeatherPrivateAccent else FeatherPrivateAccentLight).copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).testTag("private_menu_banner")
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                FeatherIcons.PrivateMask,
                contentDescription = null,
                tint = if (isDarkTheme) FeatherPrivateAccent else FeatherPrivateAccentLight,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "Private Browsing",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (isDarkTheme) FeatherPrivateAccent else FeatherPrivateAccentLight
              )
            }
          }
        }

        var showModesExpanded by remember { mutableStateOf(false) }

        // Show Modes expander if toggled
        AnimatedVisibility(
          visible = showModesExpanded,
          enter = fadeIn() + expandVertically(),
          exit = fadeOut() + shrinkVertically()
        ) {
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
            modifier = Modifier
              .fillMaxWidth()
              .padding(bottom = 12.dp)
          ) {
            Column(modifier = Modifier.padding(12.dp)) {
              Text(
                text = "Viewing Mode",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
              )
              Spacer(modifier = Modifier.height(8.dp))
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                listOf(
                  Triple("Desktop", FeatherIcons.DesktopPc, UserAgentMode.DESKTOP),
                  Triple("iPhone", FeatherIcons.SunLight, UserAgentMode.IPHONE),
                  Triple("Mobile", FeatherIcons.NightMoon, UserAgentMode.MOBILE)
                ).forEach { (label, icon, mode) ->
                  FilterChip(
                    selected = activeTab.userAgentMode == mode,
                    onClick = {
                      viewModel.setUserAgentMode(mode)
                      showModesExpanded = false
                      showQuickSheet = false
                    },
                    leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text(label, maxLines = 1) },
                    modifier = Modifier.weight(1f)
                  )
                }
              }
            }
          }
        }

        // 4-Column Crisp Grid Actions: Private Mode excludes Bookmarks/History/Share/External
        // and provides exclusive Private-Mode-Only Safety Tools instead.
        val gridActions = if (isPrivate) {
          listOf(
            QuickActionItem(FeatherIcons.PrivateMask, stringResource(R.string.action_new_private_tab), true) {
              showQuickSheet = false
              viewModel.openNewTab(isPrivate = true)
            },
            QuickActionItem(
              FeatherIcons.BurnFlame,
              stringResource(R.string.action_burn_session),
              true,
              accentTint = FeatherDangerRed
            ) {
              showQuickSheet = false
              viewModel.burnPrivateSession()
            },
            QuickActionItem(
              Icons.Filled.Refresh,
              stringResource(R.string.action_new_identity),
              true,
              accentTint = if (isDarkTheme) FeatherPrivateAccent else FeatherPrivateAccentLight
            ) {
              showQuickSheet = false
              viewModel.rotatePrivateIdentity()
            },
            QuickActionItem(
              FeatherIcons.Shield,
              stringResource(R.string.action_shield_audit),
              true,
              accentTint = FeatherSecureGreen
            ) {
              showQuickSheet = false
              showPrivateAuditDialog = true
            },
            QuickActionItem(
              FeatherIcons.BroomWipe,
              stringResource(R.string.action_wipe_clipboard),
              true
            ) {
              showQuickSheet = false
              viewModel.wipeClipboard()
            },
            QuickActionItem(
              FeatherIcons.NoImage,
              stringResource(R.string.action_purge_ram),
              true
            ) {
              showQuickSheet = false
              viewModel.purgePrivateRamOnly()
            },
            QuickActionItem(
              FeatherIcons.Announcer,
              stringResource(R.string.action_announcer),
              hasUrl
            ) {
              showQuickSheet = false
              viewModel.toggleReaderMode()
            },
            QuickActionItem(Icons.Filled.Search, stringResource(R.string.action_find_in_page), hasUrl) {
              showQuickSheet = false
              viewModel.openFindInPage()
            },
            QuickActionItem(FeatherIcons.Download, stringResource(R.string.action_downloads), true) {
              showQuickSheet = false
              viewModel.setOverlay(ActiveOverlay.DOWNLOADS)
            },
            QuickActionItem(FeatherIcons.Translate, stringResource(R.string.action_translate), hasUrl) {
              showQuickSheet = false
              viewModel.translateActivePage()
            },
            QuickActionItem(
              FeatherIcons.Video,
              if (detectedMediaList.isNotEmpty() || extractedImages.isNotEmpty()) "Media (${detectedMediaList.size + extractedImages.size})" else "Media",
              hasUrl,
              accentTint = if (detectedMediaList.isNotEmpty() || extractedImages.isNotEmpty()) MaterialTheme.colorScheme.primary else null
            ) {
              showQuickSheet = false
              viewModel.openUnifiedMedia()
            },
            QuickActionItem(
              FeatherIcons.EyeFilter,
              "Screen Filter",
              true,
              accentTint = if (settings.screenFilterEnabled) FeatherWarningAmber else null
            ) {
              showQuickSheet = false
              viewModel.setOverlay(ActiveOverlay.SCREEN_FILTER_SETTINGS)
            },
            QuickActionItem(Icons.Filled.Settings, stringResource(R.string.action_settings), true) {
              showQuickSheet = false
              viewModel.setOverlay(ActiveOverlay.SETTINGS)
            }
          )
        } else {
          listOf(
            QuickActionItem(FeatherIcons.PrivateMask, stringResource(R.string.action_new_private_tab), true) {
              showQuickSheet = false
              viewModel.openNewTab(isPrivate = true)
            },
            QuickActionItem(
              FeatherIcons.DesktopPc,
              "Modes",
              true,
              accentTint = if (activeTab.userAgentMode != UserAgentMode.MOBILE) MaterialTheme.colorScheme.primary else null
            ) {
              showModesExpanded = !showModesExpanded
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
              FeatherIcons.Video,
              if (detectedMediaList.isNotEmpty() || extractedImages.isNotEmpty()) "Media (${detectedMediaList.size + extractedImages.size})" else "Media",
              hasUrl,
              accentTint = if (detectedMediaList.isNotEmpty() || extractedImages.isNotEmpty()) MaterialTheme.colorScheme.primary else null
            ) {
              showQuickSheet = false
              viewModel.openUnifiedMedia()
            },
            QuickActionItem(
              FeatherIcons.EyeFilter,
              "Screen Filter",
              true,
              accentTint = if (settings.screenFilterEnabled) FeatherWarningAmber else null
            ) {
              showQuickSheet = false
              viewModel.setOverlay(ActiveOverlay.SCREEN_FILTER_SETTINGS)
            },
            QuickActionItem(
              FeatherIcons.Announcer,
              stringResource(R.string.action_announcer),
              hasUrl
            ) {
              showQuickSheet = false
              viewModel.toggleReaderMode()
            },
            QuickActionItem(
              FeatherIcons.NoImage,
              "Block Images",
              true,
              accentTint = if (settings.noImageMode) FeatherWarningAmber else null
            ) {
              val next = !settings.noImageMode
              viewModel.updateSettings { it.copy(noImageMode = next) }
              viewModel.showToast(if (next) "Images blocked" else "Images allowed")
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
            QuickActionItem(Icons.Filled.Settings, stringResource(R.string.action_settings), true) {
              showQuickSheet = false
              viewModel.setOverlay(ActiveOverlay.SETTINGS)
            }
          )
        }

        gridActions.chunked(4).forEach { rowItems ->
          Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            rowItems.forEach { item ->
              val iconColor = when {
                !item.enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                item.accentTint != null -> item.accentTint
                else -> MaterialTheme.colorScheme.onSurface
              }
              Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (item.enabled) 0.55f else 0.2f),
                modifier = Modifier
                  .weight(1f)
                  .height(78.dp)
                  .clip(RoundedCornerShape(14.dp))
                  .clickable(enabled = item.enabled, onClick = item.onClick)
              ) {
                Column(
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.Center,
                  modifier = Modifier.fillMaxSize().padding(vertical = 8.dp, horizontal = 6.dp)
                ) {
                  Icon(
                    imageVector = item.icon,
                    contentDescription = item.label,
                    tint = iconColor,
                    modifier = Modifier.size(21.dp)
                  )
                  Spacer(modifier = Modifier.height(6.dp))
                  Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (item.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                  )
                }
              }
            }
            repeat(4 - rowItems.size) {
              Spacer(modifier = Modifier.weight(1f))
            }
          }
        }
      }
    }
  }

  // Private-Mode-Only Security Shield Audit Dialog
  if (showPrivateAuditDialog && activeTab.isPrivate) {
    AlertDialog(
      onDismissRequest = { showPrivateAuditDialog = false },
      title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            FeatherIcons.Shield,
            contentDescription = null,
            tint = FeatherSecureGreen,
            modifier = Modifier.size(22.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text("Private Shield Audit")
        }
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            "Hermetic bidirectional isolation is active between Standard and Private Mode:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Text("• Cookie Vault Barrier: Active (0 cross-mode cookies)", style = MaterialTheme.typography.bodySmall)
          Text("• RAM-Only Storage Sandbox: Active (localStorage & sessionStorage isolated in RAM)", style = MaterialTheme.typography.bodySmall)
          Text("• Third-Party Cookies & IndexedDB: Strictly Blocked", style = MaterialTheme.typography.bodySmall)
          Text("• Bookmarks & History: Completely Disabled", style = MaterialTheme.typography.bodySmall)
          Text("• Share, Shortcuts & External Apps: Blocked (zero external leaks)", style = MaterialTheme.typography.bodySmall)
          Text("• Anti-Screenshot (FLAG_SECURE) & Fingerprint Noise: Active", style = MaterialTheme.typography.bodySmall)
          Text(
            "• Trackers & Ads Blocked in Tab: ${activeTab.trackersBlockedCount}",
            style = MaterialTheme.typography.labelMedium,
            color = FeatherSecureGreen,
            fontWeight = FontWeight.SemiBold
          )
        }
      },
      confirmButton = {
        TextButton(onClick = { showPrivateAuditDialog = false }) { Text("Done") }
      },
      dismissButton = {
        TextButton(
          onClick = {
            showPrivateAuditDialog = false
            viewModel.burnPrivateSession()
          }
        ) { Text("Burn Session", color = FeatherDangerRed) }
      }
    )
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
          val currentOrigin = UrlValidator.extractOrigin(activeTab.url)
          val currentSitePerms = sitePermissions.filter {
            it.origin.equals(currentOrigin, ignoreCase = true) || it.origin.equals(host, ignoreCase = true)
          }
          if (currentSitePerms.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            Text("Site Permissions", style = MaterialTheme.typography.labelMedium)
            currentSitePerms.forEach { perm ->
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
              ) {
                Text(
                  perm.kind.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() },
                  style = MaterialTheme.typography.bodySmall
                )
                Text(
                  text = if (perm.allowed) "Allowed" else "Blocked",
                  style = MaterialTheme.typography.labelSmall,
                  color = if (perm.allowed) FeatherSecureGreen else FeatherWarningAmber
                )
              }
            }
          }
          HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
          viewModel.systemWebViewPackageInfo?.let { wvInfo ->
            Text(
              "Engine: $wvInfo",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          if (viewModel.isSystemWebViewOutdated) {
            Text(
              "Notice: System WebView may be outdated. Consider updating via Google Play.",
              style = MaterialTheme.typography.labelSmall,
              color = FeatherWarningAmber
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
            viewModel.openNewTab(isPrivate = activeTab.isPrivate, initialUrl = target.url, switchToNewTab = true)
          }
          ContextActionRow("Open in new tab in group") {
            viewModel.dismissLinkContext()
            viewModel.openNewTabInGroup(isPrivate = activeTab.isPrivate, initialUrl = target.url, switchToNewTab = false)
          }
          ContextActionRow("Open in background tab") {
            viewModel.dismissLinkContext()
            viewModel.openNewTab(isPrivate = activeTab.isPrivate, initialUrl = target.url, switchToNewTab = false)
          }
          if (!activeTab.isPrivate) {
            ContextActionRow("Open in private tab") {
              viewModel.dismissLinkContext()
              viewModel.openNewTab(isPrivate = true, initialUrl = target.url, switchToNewTab = true)
            }
          }
          ContextActionRow("Copy URL") {
            viewModel.dismissLinkContext()
            viewModel.copyTextToClipboard(target.url)
          }
          ContextActionRow("Download") {
            viewModel.dismissLinkContext()
            viewModel.downloadUrlWithConsent(target.url)
          }
          if (!target.isImage && !target.imageUrl.isNullOrBlank() && target.imageUrl != target.url) {
            ContextActionRow("Download image") {
              viewModel.dismissLinkContext()
              viewModel.downloadUrlWithConsent(target.imageUrl)
            }
          }
          if (!activeTab.isPrivate) {
            ContextActionRow("Share") {
              viewModel.dismissLinkContext()
              viewModel.shareUrl(target.url)
            }
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
          if (req.isRiskyFileType) {
            Text(
              text = "Warning: This file type may contain executable code. Only download if you completely trust this source.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error
            )
          }
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
  val accentTint: Color? = null,
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
  isDarkTheme: Boolean = true,
  settings: SettingsState,
  compactSingleRow: Boolean = false,
  isBookmarked: Boolean = false,
  onToggleBookmark: () -> Unit = {},
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
  onToggleDesktopMode: () -> Unit = {},
  onOpenBookmarks: () -> Unit = {},
  onOpenDownloads: () -> Unit = {},
  onNewTab: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  var omniboxValue by remember(activeTab.id, activeTab.url) {
    mutableStateOf(TextFieldValue(activeTab.url, TextRange(0, activeTab.url.length)))
  }
  var isAddressBarExpanded by remember(activeTab.id, activeTab.url) { mutableStateOf(false) }
  var isFocused by remember { mutableStateOf(false) }
  var clipboardCandidate by remember { mutableStateOf<String?>(null) }
  val focusManager = LocalFocusManager.current
  val haptic = LocalHapticFeedback.current
  val isHomePage = activeTab.url.isBlank()
  val isPrivate = activeTab.isPrivate

  val privateAccent = if (isDarkTheme) FeatherPrivateAccent else FeatherPrivateAccentLight
  val headerBg = when {
    isDarkTheme -> Color.Black
    isPrivate -> Color(0xFFF6F3FF)
    else -> Color(0xFFFFFFFF)
  }
  val headerAccent = if (isPrivate) privateAccent else if (isDarkTheme) Color(0xFF38BDF8) else Color(0xFF0284C7)
  val headerTextPrimary = if (isDarkTheme) Color(0xFFF8FAFC) else Color(0xFF0F172A)
  val headerTextMuted = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF475569)
  val tabBadgeBg = when {
    isPrivate -> privateAccent.copy(alpha = 0.16f)
    isDarkTheme -> Color(0xFF141720)
    else -> Color(0xFFF1F5F9)
  }

  val (secIcon, secTint, secDesc) = when (activeTab.securityState) {
    SecurityState.PRIVATE -> Triple(FeatherIcons.PrivateMask, privateAccent, stringResource(R.string.cd_security_private))
    SecurityState.SECURE_HTTPS -> Triple(Icons.Filled.Lock, FeatherSecureGreen, stringResource(R.string.cd_security_https))
    SecurityState.INSECURE_HTTP, SecurityState.ERROR -> Triple(Icons.Filled.Warning, FeatherWarningAmber, stringResource(R.string.cd_security_http))
    SecurityState.HOME -> Triple(Icons.Filled.Search, headerTextMuted, stringResource(R.string.cd_security_home))
  }

  val displayHost = remember(activeTab.url) {
    UrlValidator.extractHost(activeTab.url).removePrefix("www.")
  }

  val omniboxPill: @Composable (Modifier) -> Unit = { pillModifier ->
    var swipeAccum by remember { mutableFloatStateOf(0f) }
    val pillBg = when {
      isDarkTheme && isPrivate -> Color(0xFF140D22)
      isDarkTheme -> Color(0xFF0E1016)
      isPrivate -> Color(0xFFEDE9FE)
      isFocused -> Color(0xFFFFFFFF)
      else -> Color(0xFFF1F5F9)
    }
    val pillBorder = when {
      isFocused -> headerAccent
      isPrivate -> privateAccent.copy(alpha = 0.72f)
      isDarkTheme -> Color(0xFF232736)
      else -> Color(0xFFCBD5E1)
    }
    Surface(
      shape = RoundedCornerShape(26.dp),
      color = pillBg,
      tonalElevation = 0.dp,
      shadowElevation = 0.dp,
      border = BorderStroke(
        width = if (isFocused || isPrivate) 1.2.dp else 0.9.dp,
        color = pillBorder
      ),
      modifier = pillModifier
        .height(48.dp)
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
          color = secTint.copy(alpha = 0.16f),
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
              color = if (isPrivate) (if (isDarkTheme) Color(0xFFF3E8FF) else Color(0xFF4C1D95)) else headerTextPrimary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          } else if (omniboxValue.text.isEmpty()) {
            Text(
              text = if (isPrivate) "Search or enter URL · Zero Tracking" else stringResource(R.string.omnibox_hint),
              style = MaterialTheme.typography.bodyMedium,
              color = if (isPrivate) privateAccent.copy(alpha = 0.85f) else headerTextMuted,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
          BasicTextField(
            value = if (!isFocused && displayHost.isNotEmpty()) TextFieldValue("") else omniboxValue,
            onValueChange = { omniboxValue = it },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
              color = headerTextPrimary
            ),
            cursorBrush = SolidColor(headerAccent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = {
              if (omniboxValue.text.isNotBlank()) {
                isAddressBarExpanded = false
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
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_clear_input), tint = headerTextMuted, modifier = Modifier.size(18.dp))
          }
        } else if (activeTab.url.isNotBlank()) {
          IconButton(
            onClick = {
              haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
              onCopyUrl()
            },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("address_copy_button")
          ) {
            Icon(FeatherIcons.Copy, contentDescription = stringResource(R.string.cd_copy_url), tint = headerTextMuted, modifier = Modifier.size(17.dp))
          }
        }
        if (!isPrivate && activeTab.url.isNotBlank() && activeTab.url != "about:blank") {
          IconButton(
            onClick = {
              haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
              onToggleBookmark()
            },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("address_bookmark_star_button")
          ) {
            Icon(
              imageVector = if (isBookmarked) Icons.Filled.Star else FeatherIcons.StarOutline,
              contentDescription = if (isBookmarked) "Remove bookmark" else "Add to bookmarks",
              tint = if (isBookmarked) Color(0xFFFFB300) else headerTextMuted,
              modifier = Modifier.size(19.dp)
            )
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
            color = headerAccent.copy(alpha = 0.18f),
            border = BorderStroke(0.6.dp, headerAccent.copy(alpha = 0.45f)),
            modifier = Modifier.clickable { onOpenSiteInfo() }
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                FeatherIcons.Shield,
                contentDescription = "Blocked",
                tint = headerAccent,
                modifier = Modifier.size(12.dp)
              )
              Spacer(modifier = Modifier.width(3.dp))
              Text(
                text = activeTab.trackersBlockedCount.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = headerAccent
              )
            }
          }
        }
      }
    }
  }

  Surface(
    tonalElevation = 0.dp,
    shadowElevation = 0.dp,
    color = headerBg,
    contentColor = headerTextPrimary,
    modifier = modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
      ) {
        Column(
          modifier = Modifier
            .widthIn(max = 760.dp)
            .fillMaxWidth()
        ) {
          if (isHomePage) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              IconButton(
                onClick = {
                  haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                  onBack()
                },
                enabled = activeTab.canGoBack || activeTab.httpWarning != null || activeTab.errorState != null,
                modifier = Modifier.minimumInteractiveComponentSize().testTag("nav_back_button")
              ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
              }
              IconButton(
                onClick = {
                  haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                  onForward()
                },
                enabled = activeTab.canGoForward,
                modifier = Modifier.minimumInteractiveComponentSize().testTag("nav_forward_button")
              ) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.cd_forward))
              }

              Surface(
                shape = RoundedCornerShape(12.dp),
                color = secTint.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, secTint.copy(alpha = 0.35f)),
                modifier = Modifier
                  .minimumInteractiveComponentSize()
                  .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onOpenSiteInfo()
                  }
                  .testTag("security_badge_button")
              ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                  Icon(
                    imageVector = secIcon,
                    contentDescription = secDesc,
                    tint = secTint,
                    modifier = Modifier.size(20.dp)
                  )
                }
              }

              Surface(
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(
                  1.5.dp,
                  if (isPrivate) privateAccent else headerTextPrimary.copy(alpha = 0.85f)
                ),
                color = tabBadgeBg,
                modifier = Modifier
                  .minimumInteractiveComponentSize()
                  .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onOpenTabs()
                  }
                  .testTag("nav_tabs_button")
              ) {
                Box(
                  contentAlignment = Alignment.Center,
                  modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp)
                ) {
                  Text(
                    text = tabCount.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isPrivate) privateAccent else headerTextPrimary
                  )
                }
              }

              IconButton(
                onClick = {
                  haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                  onOpenQuickMenu()
                },
                modifier = Modifier.minimumInteractiveComponentSize().testTag("nav_menu_button")
              ) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.cd_menu))
              }
            }
          } else {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) {
              AnimatedVisibility(
                visible = isAddressBarExpanded || isFocused,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
              ) {
                Column {
                  omniboxPill(Modifier.fillMaxWidth())
                  Spacer(modifier = Modifier.height(6.dp))
                }
              }

              Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                IconButton(
                  onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onBack()
                  },
                  enabled = activeTab.canGoBack || activeTab.httpWarning != null || activeTab.errorState != null,
                  modifier = Modifier.minimumInteractiveComponentSize().testTag("nav_back_button")
                ) {
                  Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                }
                IconButton(
                  onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onForward()
                  },
                  enabled = activeTab.canGoForward,
                  modifier = Modifier.minimumInteractiveComponentSize().testTag("nav_forward_button")
                ) {
                  Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.cd_forward))
                }

                Surface(
                  shape = RoundedCornerShape(12.dp),
                  color = if (isAddressBarExpanded) headerAccent.copy(alpha = 0.22f) else secTint.copy(alpha = 0.14f),
                  border = BorderStroke(
                    1.dp,
                    if (isAddressBarExpanded) headerAccent.copy(alpha = 0.6f) else secTint.copy(alpha = 0.35f)
                  ),
                  modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clickable {
                      haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                      isAddressBarExpanded = !isAddressBarExpanded
                    }
                    .testTag("nav_lock_button")
                ) {
                  Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Icon(
                      imageVector = secIcon,
                      contentDescription = if (isAddressBarExpanded) "Hide address bar" else "Show address bar: $secDesc",
                      tint = secTint,
                      modifier = Modifier.size(20.dp)
                    )
                  }
                }

                Surface(
                  shape = RoundedCornerShape(12.dp),
                  border = BorderStroke(
                    1.5.dp,
                    if (isPrivate) privateAccent else headerTextPrimary.copy(alpha = 0.85f)
                  ),
                  color = tabBadgeBg,
                  modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clickable {
                      haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                      onOpenTabs()
                    }
                    .testTag("nav_tabs_button")
                ) {
                  Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp)
                  ) {
                    Text(
                      text = tabCount.toString(),
                      style = MaterialTheme.typography.labelMedium,
                      fontWeight = FontWeight.Bold,
                      color = if (isPrivate) privateAccent else headerTextPrimary
                    )
                  }
                }

                IconButton(
                  onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onOpenQuickMenu()
                  },
                  modifier = Modifier.minimumInteractiveComponentSize().testTag("nav_menu_button")
                ) {
                  Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.cd_menu))
                }
              }
            }
          }
        }
      }

      if (!isPrivate && !isHomePage && isFocused && !clipboardCandidate.isNullOrBlank() && clipboardCandidate != activeTab.url) {
        Surface(
          color = if (isDarkTheme) Color(0xFF0E1A2B) else Color(0xFFE0F2FE),
          modifier = Modifier
            .fillMaxWidth()
            .clickable {
              val clip = clipboardCandidate ?: return@clickable
              isAddressBarExpanded = false
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
              tint = headerAccent,
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Paste & Go: ${UrlValidator.sanitizeDisplayText(clipboardCandidate, 48)}",
              style = MaterialTheme.typography.labelMedium,
              color = if (isDarkTheme) Color(0xFFE0F2FE) else Color(0xFF0C4A6E),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }
      }

      if (!isHomePage) {
        HorizontalDivider(
          thickness = 0.8.dp,
          color = when {
            activeTab.isPrivate -> privateAccent.copy(alpha = 0.45f)
            isDarkTheme -> Color(0xFF1A1E29)
            else -> Color(0xFFE2E8F0)
          }
        )
      }
    }
  }
}
