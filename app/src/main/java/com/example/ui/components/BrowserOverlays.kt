package com.example.ui.components

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import kotlinx.coroutines.delay
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import com.example.model.DetectedMedia
import com.example.model.ScreenFilterType
import com.example.model.BookmarkItem
import com.example.model.HttpWarningState
import com.example.model.PageError
import com.example.model.PageErrorType
import com.example.model.ReaderArticle
import com.example.reader.NarrationSegment
import com.example.reader.NarratorState
import com.example.security.UrlValidator
import com.example.ui.FindState
import com.example.ui.theme.BatMidnight
import com.example.ui.theme.BatSignalGold
import com.example.ui.theme.FeatherDangerRed
import com.example.ui.theme.FeatherMintDark
import com.example.ui.theme.FeatherPrivateAccent
import com.example.ui.theme.FeatherPrivateAccentLight
import com.example.ui.theme.FeatherSecureGreen
import com.example.ui.theme.FeatherWarningAmber
import java.net.URLEncoder

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewTabHomeView(
  isPrivate: Boolean,
  isDarkTheme: Boolean = true,
  searchEngineName: String,
  bookmarks: List<BookmarkItem>,
  onSubmitQuery: (String) -> Unit,
  onOpenBookmark: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  var homeSearch by remember { mutableStateOf("") }

  // Adaptive Pure OLED Dark & Crisp Light Homepage Palette (KioLogo remains 100% untouched)
  val pageBg = when {
    isDarkTheme -> Color(0xFF000000)
    isPrivate -> Color(0xFFF6F3FF)
    else -> Color(0xFFF8FAFC)
  }
  val cardSurface = when {
    isDarkTheme -> Color(0xFF0B0C10)
    else -> Color(0xFFFFFFFF)
  }
  val searchSurface = when {
    isDarkTheme && isPrivate -> Color(0xFF140D22)
    isDarkTheme -> Color(0xFF0E1016)
    else -> Color(0xFFFFFFFF)
  }
  val hairlineBorder = when {
    isDarkTheme -> Color(0xFF1E222D)
    isPrivate -> Color(0xFFDDD6FE)
    else -> Color(0xFFE2E8F0)
  }
  val titleColor = if (isDarkTheme) Color(0xFFF8FAFC) else Color(0xFF0F172A)
  val mutedColor = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF475569)
  val primaryAccent = when {
    isPrivate && isDarkTheme -> FeatherPrivateAccent
    isPrivate -> FeatherPrivateAccentLight
    isDarkTheme -> Color(0xFF38BDF8)
    else -> Color(0xFF0284C7)
  }
  val auraAccent = if (isPrivate) primaryAccent else BatSignalGold
  val secondaryAccent = if (isPrivate) Color(0xFF6366F1) else FeatherMintDark

  BoxWithConstraints(
    modifier = modifier
      .fillMaxSize()
      .background(pageBg)
      .drawBehind {
        val center = Offset(size.width * 0.5f, size.height * 0.18f)
        val glowAlpha = if (isDarkTheme) 0.09f else 0.10f
        drawCircle(
          brush = Brush.radialGradient(
            colors = listOf(
              auraAccent.copy(alpha = glowAlpha),
              secondaryAccent.copy(alpha = 0.03f),
              Color.Transparent
            ),
            center = center,
            radius = size.minDimension * 0.68f
          ),
          radius = size.minDimension * 0.68f,
          center = center
        )
        drawCircle(
          color = auraAccent.copy(alpha = if (isDarkTheme) 0.06f else 0.09f),
          radius = size.minDimension * 0.34f,
          center = center,
          style = Stroke(width = 0.8.dp.toPx())
        )
        drawCircle(
          color = primaryAccent.copy(alpha = if (isDarkTheme) 0.04f else 0.07f),
          radius = size.minDimension * 0.52f,
          center = center,
          style = Stroke(width = 0.8.dp.toPx())
        )
      }
      .padding(horizontal = 20.dp, vertical = 14.dp),
    contentAlignment = Alignment.TopCenter
  ) {
    val isCompactHeight = maxHeight < 560.dp
    Column(
      modifier = Modifier
        .widthIn(max = 760.dp)
        .fillMaxSize()
        .verticalScroll(rememberScrollState()),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Spacer(modifier = Modifier.height(if (isCompactHeight) 4.dp else 18.dp))

      // Hero Emblem (Preserved Kio Homepage Emblem: Concentric Circle in Circle in Dot)
      val crestSize = if (isCompactHeight) 56.dp else 78.dp
      KioLogo(
        modifier = Modifier.testTag("kio_home_logo"),
        size = crestSize,
        isPrivate = isPrivate,
        animated = true,
        contentDescription = stringResource(R.string.app_name)
      )

      Spacer(modifier = Modifier.height(14.dp))

      Text(
        text = if (isPrivate) stringResource(R.string.tabs_private) else stringResource(R.string.app_name),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.SemiBold,
        color = titleColor
      )

      if (isPrivate) {
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
          shape = RoundedCornerShape(50),
          color = if (isDarkTheme) Color(0xFF100A1A) else Color(0xFFEDE9FE),
          border = BorderStroke(0.8.dp, primaryAccent.copy(alpha = 0.55f))
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(primaryAccent)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = stringResource(R.string.tabs_private_banner),
              style = MaterialTheme.typography.labelMedium,
              color = if (isDarkTheme) Color(0xFFE2E8F0) else Color(0xFF4C1D95)
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(18.dp))

      // Clean Search Box
      Surface(
        shape = RoundedCornerShape(28.dp),
        color = searchSurface,
        tonalElevation = 0.dp,
        shadowElevation = if (isDarkTheme) 0.dp else 2.dp,
        border = BorderStroke(1.dp, primaryAccent.copy(alpha = if (isDarkTheme) 0.32f else 0.42f)),
        modifier = Modifier.fillMaxWidth().height(54.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = primaryAccent,
            modifier = Modifier.size(20.dp)
          )
          Spacer(modifier = Modifier.width(12.dp))
          Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (homeSearch.isEmpty()) {
              Text(
                text = stringResource(R.string.home_search_hint, searchEngineName),
                style = MaterialTheme.typography.bodyLarge,
                color = mutedColor
              )
            }
            BasicTextField(
              value = homeSearch,
              onValueChange = { homeSearch = it },
              singleLine = true,
              textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = titleColor
              ),
              cursorBrush = SolidColor(primaryAccent),
              keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
              keyboardActions = KeyboardActions(onGo = {
                if (homeSearch.isNotBlank()) {
                  onSubmitQuery(homeSearch)
                }
              }),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("home_search_input")
            )
          }
          if (homeSearch.isNotBlank()) {
            IconButton(
              onClick = {
                if (homeSearch.isNotBlank()) {
                  onSubmitQuery(homeSearch)
                }
              },
              modifier = Modifier.minimumInteractiveComponentSize().testTag("home_search_go")
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Go",
                tint = primaryAccent,
                modifier = Modifier.size(20.dp)
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      if (isPrivate) {
        // Private Mode: Bookmarks are strictly excluded. Show isolated Private Vault protection card.
        Surface(
          shape = RoundedCornerShape(18.dp),
          color = cardSurface,
          border = BorderStroke(0.9.dp, primaryAccent.copy(alpha = 0.35f)),
          modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).testTag("private_vault_card")
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Surface(
                shape = RoundedCornerShape(10.dp),
                color = primaryAccent.copy(alpha = 0.14f),
                modifier = Modifier.size(34.dp)
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(
                    FeatherIcons.Shield,
                    contentDescription = null,
                    tint = primaryAccent,
                    modifier = Modifier.size(18.dp)
                  )
                }
              }
              Spacer(modifier = Modifier.width(10.dp))
              Column {
                Text(
                  text = "Private Browsing Shield",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.SemiBold,
                  color = titleColor
                )
                Text(
                  text = "Completely isolated from Standard Mode",
                  style = MaterialTheme.typography.labelSmall,
                  color = mutedColor
                )
              }
            }
            Text(
              text = "• Zero cookies, storage, bookmarks, or history shared across modes.\n" +
                "• Ephemeral RAM-only storage & anti-fingerprinting shield active.\n" +
                "• External apps, shortcuts, sharing, extensions & screenshots blocked.",
              style = MaterialTheme.typography.bodySmall,
              color = mutedColor,
              lineHeight = 19.sp
            )
          }
        }
      } else {
        // Standard Mode Only: Bookmarks Section
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(
            FeatherIcons.Bookmark,
            contentDescription = null,
            tint = primaryAccent,
            modifier = Modifier.size(16.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = stringResource(R.string.home_bookmarks_section).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = mutedColor
          )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (bookmarks.isEmpty()) {
          Surface(
            shape = RoundedCornerShape(18.dp),
            color = cardSurface,
            border = BorderStroke(0.8.dp, hairlineBorder),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier.padding(18.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Surface(
                shape = RoundedCornerShape(12.dp),
                color = primaryAccent.copy(alpha = 0.12f),
                modifier = Modifier.size(40.dp)
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(
                    FeatherIcons.BookmarkOutline,
                    contentDescription = null,
                    tint = primaryAccent,
                    modifier = Modifier.size(20.dp)
                  )
                }
              }
              Spacer(modifier = Modifier.width(14.dp))
              Text(
                text = stringResource(R.string.home_no_bookmarks),
                style = MaterialTheme.typography.bodyMedium,
                color = mutedColor
              )
            }
          }
        } else {
          val bookmarkRows = bookmarks.take(8).chunked(2)
          Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            bookmarkRows.forEach { rowItems ->
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                rowItems.forEach { bm ->
                  val host = UrlValidator.extractHost(bm.url)
                  val siteName = UrlValidator.extractWebsiteDisplayName(bm.title, bm.url)
                  val initial = siteName.firstOrNull()?.uppercase() ?: host.firstOrNull()?.uppercase() ?: "B"
                  Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = cardSurface,
                    tonalElevation = 0.dp,
                    border = BorderStroke(0.8.dp, hairlineBorder),
                    modifier = Modifier
                      .weight(1f)
                      .clip(RoundedCornerShape(16.dp))
                      .clickable { onOpenBookmark(bm.url) }
                      .testTag("home_bookmark_${bm.id}")
                  ) {
                    Row(
                      modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      SiteFavicon(
                        url = bm.url,
                        fallbackInitial = initial,
                        accentColor = primaryAccent,
                        size = 34.dp,
                        cornerRadius = 10.dp
                      )
                      Spacer(modifier = Modifier.width(10.dp))
                      Text(
                        text = siteName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = titleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                      )
                    }
                  }
                }
                if (rowItems.size == 1) {
                  Spacer(modifier = Modifier.weight(1f))
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun HttpWarningOverlay(
  warning: HttpWarningState,
  onGoBackSafely: () -> Unit,
  onProceedAnyway: () -> Unit,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .padding(24.dp),
    contentAlignment = Alignment.Center
  ) {
    Card(
      shape = RoundedCornerShape(22.dp),
      border = BorderStroke(1.dp, FeatherWarningAmber.copy(alpha = 0.45f)),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth()
    ) {
      Column(modifier = Modifier.padding(24.dp)) {
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = FeatherWarningAmber.copy(alpha = 0.14f),
          modifier = Modifier.size(48.dp)
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = Icons.Filled.Warning,
              contentDescription = null,
              tint = FeatherWarningAmber,
              modifier = Modifier.size(26.dp)
            )
          }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
          text = stringResource(R.string.http_warning_title),
          style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = stringResource(R.string.http_warning_body, warning.host),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(22.dp))
        Button(
          onClick = onGoBackSafely,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("http_go_back_button")
        ) {
          Text(stringResource(R.string.http_go_back))
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
          onClick = onProceedAnyway,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("http_proceed_button")
        ) {
          Text(stringResource(R.string.http_proceed_anyway))
        }
      }
    }
  }
}

@Composable
fun PageErrorOverlay(
  error: PageError,
  onRetry: () -> Unit,
  onGoHome: () -> Unit,
  modifier: Modifier = Modifier
) {
  var showDetails by remember { mutableStateOf(false) }

  val title = when (error.type) {
    PageErrorType.OFFLINE -> stringResource(R.string.error_offline_title)
    PageErrorType.DNS_FAILURE -> stringResource(R.string.error_dns_title)
    PageErrorType.CERTIFICATE_ERROR -> stringResource(R.string.error_ssl_title)
    PageErrorType.BLOCKED -> stringResource(R.string.error_blocked_title)
    PageErrorType.GENERIC -> "Unable to load page"
  }
  val body = when (error.type) {
    PageErrorType.OFFLINE -> stringResource(R.string.error_offline_body)
    PageErrorType.DNS_FAILURE -> stringResource(R.string.error_dns_body, error.host)
    PageErrorType.CERTIFICATE_ERROR -> stringResource(R.string.error_ssl_body, error.host)
    PageErrorType.BLOCKED -> stringResource(R.string.error_blocked_body, error.reason)
    PageErrorType.GENERIC -> "${error.host}: ${error.reason}"
  }
  val accent = if (error.type == PageErrorType.CERTIFICATE_ERROR || error.type == PageErrorType.BLOCKED) {
    FeatherDangerRed
  } else {
    FeatherWarningAmber
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .padding(24.dp),
    contentAlignment = Alignment.Center
  ) {
    Card(
      shape = RoundedCornerShape(22.dp),
      border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth()
    ) {
      Column(
        modifier = Modifier
          .padding(24.dp)
          .verticalScroll(rememberScrollState())
      ) {
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = accent.copy(alpha = 0.14f),
          modifier = Modifier.size(48.dp)
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = Icons.Filled.Warning,
              contentDescription = null,
              tint = accent,
              modifier = Modifier.size(26.dp)
            )
          }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(text = title, style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = body,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (error.type == PageErrorType.CERTIFICATE_ERROR) {
          Spacer(modifier = Modifier.height(10.dp))
          TextButton(onClick = { showDetails = !showDetails }) {
            Text(if (showDetails) "Hide certificate details" else "Show certificate details")
          }
          if (showDetails) {
            Surface(
              color = MaterialTheme.colorScheme.surfaceVariant,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              Text(
                text = "Reason: ${error.reason}\nURL: ${error.failingUrl}\n${error.certificateDetail ?: "No valid certificate chain"}",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(12.dp)
              )
            }
          }
        }
        Spacer(modifier = Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
          if (error.type != PageErrorType.CERTIFICATE_ERROR && error.type != PageErrorType.BLOCKED) {
            Button(
              onClick = onRetry,
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.weight(1f).minimumInteractiveComponentSize()
            ) {
              Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text(stringResource(R.string.error_retry))
            }
          }
          OutlinedButton(
            onClick = onGoHome,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.weight(1f).minimumInteractiveComponentSize()
          ) {
            Text(stringResource(R.string.http_go_back))
          }
        }
      }
    }
  }
}

@Composable
fun FindInPageBar(
  findState: FindState,
  onQueryChange: (String) -> Unit,
  onPrev: () -> Unit,
  onNext: () -> Unit,
  onClose: () -> Unit
) {
  BackHandler(enabled = findState.isVisible) { onClose() }
  Surface(
    tonalElevation = 4.dp,
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      OutlinedTextField(
        value = findState.query,
        onValueChange = onQueryChange,
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        placeholder = { Text(stringResource(R.string.find_hint)) },
        modifier = Modifier
          .weight(1f)
          .testTag("find_in_page_input")
      )
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = "${findState.activeIndex}/${findState.totalMatches}",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      IconButton(
        onClick = onPrev,
        modifier = Modifier.minimumInteractiveComponentSize().testTag("find_prev_button")
      ) {
        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Previous match")
      }
      IconButton(
        onClick = onNext,
        modifier = Modifier.minimumInteractiveComponentSize().testTag("find_next_button")
      ) {
        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Next match")
      }
      IconButton(
        onClick = onClose,
        modifier = Modifier.minimumInteractiveComponentSize().testTag("find_close_button")
      ) {
        Icon(Icons.Filled.Close, contentDescription = "Close find in page")
      }
    }
  }
}

@Composable
fun ReaderModeOverlay(
  article: ReaderArticle,
  segments: List<NarrationSegment>,
  narratorState: NarratorState,
  fontSizeSp: Int,
  darkReading: Boolean,
  onTogglePlayPause: () -> Unit,
  onPrevSegment: () -> Unit,
  onNextSegment: () -> Unit,
  onSelectSegment: (Int) -> Unit,
  onChangeSpeechRate: (Float) -> Unit,
  onChangeFontSize: (Int) -> Unit,
  onToggleDark: (Boolean) -> Unit,
  onClose: () -> Unit
) {
  BackHandler { onClose() }
  val bgColor = if (darkReading) Color(0xFF000000) else Color(0xFFFDFBF6)
  val surfaceColor = if (darkReading) Color(0xFF0B0E14) else Color(0xFFF3EFE6)
  val textColor = if (darkReading) Color(0xFFE2E8F0) else Color(0xFF1E293B)
  val subColor = if (darkReading) Color(0xFF94A3B8) else Color(0xFF64748B)
  val highlightBg = if (darkReading) Color(0xFF0E293B) else Color(0xFFDBEAFE)
  val highlightBorder = Color(0xFF38BDF8)

  val listState = rememberLazyListState()
  val speedSteps = remember { listOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f) }

  LaunchedEffect(narratorState.currentIndex, narratorState.isSpeaking) {
    if (narratorState.isSpeaking && segments.isNotEmpty()) {
      val target = (narratorState.currentIndex + 1).coerceIn(0, segments.size)
      try {
        listState.animateScrollToItem(target)
      } catch (_: Throwable) {
      }
    }
  }

  Surface(color = bgColor, modifier = Modifier.fillMaxSize().testTag("reader_announcer_overlay")) {
    Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
      Surface(
        color = surfaceColor,
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(FeatherIcons.Announcer, contentDescription = null, tint = highlightBorder)
          Spacer(modifier = Modifier.width(8.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Content Announcer & Reader",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.SemiBold,
              color = textColor,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Text(
              text = "Pure On-Device NLP · Zero Data Saved or Copied",
              style = MaterialTheme.typography.labelSmall,
              color = FeatherSecureGreen,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
          TextButton(
            onClick = { onChangeFontSize((fontSizeSp - 2).coerceAtLeast(14)) },
            modifier = Modifier.minimumInteractiveComponentSize()
          ) { Text("A-") }
          TextButton(
            onClick = { onChangeFontSize((fontSizeSp + 2).coerceAtMost(28)) },
            modifier = Modifier.minimumInteractiveComponentSize()
          ) { Text("A+") }
          Switch(
            checked = darkReading,
            onCheckedChange = onToggleDark,
            modifier = Modifier.padding(horizontal = 4.dp)
          )
          IconButton(
            onClick = onClose,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("reader_close_button")
          ) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.reader_close), tint = textColor)
          }
        }
      }

      // Strictly non-selectable volatile RAM article display (never copies or saves text)
      LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.widthIn(max = 700.dp).align(Alignment.CenterHorizontally).weight(1f).fillMaxWidth()
      ) {
        item {
          Text(
            text = article.siteHost.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = highlightBorder
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = article.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = textColor
          )
          Spacer(modifier = Modifier.height(8.dp))
          Box(
            modifier = Modifier
              .width(48.dp)
              .height(2.dp)
              .background(highlightBorder.copy(alpha = 0.6f))
          )
          Spacer(modifier = Modifier.height(6.dp))
        }

        if (segments.isEmpty()) {
          items(article.paragraphs) { paragraph ->
            Text(
              text = paragraph,
              fontSize = fontSizeSp.sp,
              lineHeight = (fontSizeSp * 1.6f).sp,
              color = textColor
            )
          }
        } else {
          itemsIndexed(segments, key = { _, seg -> seg.index }) { idx, seg ->
            val isCurrent = idx == narratorState.currentIndex && (narratorState.isSpeaking || narratorState.isPaused)
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = if (isCurrent) highlightBg else Color.Transparent,
              border = if (isCurrent) BorderStroke(1.dp, highlightBorder.copy(alpha = 0.55f)) else null,
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onSelectSegment(idx) }
            ) {
              Text(
                text = seg.displayText,
                fontSize = if (seg.isHeading) (fontSizeSp + 2).sp else fontSizeSp.sp,
                fontWeight = if (seg.isHeading || isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                lineHeight = (fontSizeSp * 1.6f).sp,
                color = if (isCurrent) (if (darkReading) Color.White else Color(0xFF0F172A)) else textColor,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
              )
            }
          }
        }
      }

      // Bottom Pure NLP Narration Controller Bar
      Surface(
        color = surfaceColor,
        border = BorderStroke(0.8.dp, highlightBorder.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
          val total = narratorState.totalSegments.coerceAtLeast(1)
          val progress = ((narratorState.currentIndex + 1).toFloat() / total.toFloat()).coerceIn(0f, 1f)
          LinearProgressIndicator(
            progress = { progress },
            color = highlightBorder,
            trackColor = subColor.copy(alpha = 0.25f),
            modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(4.dp))
          )
          Spacer(modifier = Modifier.height(8.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            // Speed cycling pill
            val nextSpeed = remember(narratorState.speechRate) {
              val idx = speedSteps.indexOfFirst { kotlin.math.abs(it - narratorState.speechRate) < 0.08f }
              speedSteps[(idx + 1) % speedSteps.size]
            }
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = highlightBorder.copy(alpha = 0.14f),
              border = BorderStroke(0.8.dp, highlightBorder.copy(alpha = 0.45f)),
              modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable { onChangeSpeechRate(nextSpeed) }
                .testTag("announcer_speed_button")
            ) {
              Text(
                text = "${narratorState.speechRate}x",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = highlightBorder,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
              )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              IconButton(
                onClick = onPrevSegment,
                modifier = Modifier.minimumInteractiveComponentSize().testTag("announcer_prev_button")
              ) {
                Icon(FeatherIcons.SkipPrev, contentDescription = "Previous sentence", tint = textColor)
              }
              Surface(
                shape = CircleShape,
                color = highlightBorder,
                modifier = Modifier
                  .size(46.dp)
                  .clip(CircleShape)
                  .clickable { onTogglePlayPause() }
                  .testTag("announcer_play_pause_button")
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(
                    imageVector = if (narratorState.isSpeaking && !narratorState.isPaused) FeatherIcons.Pause else FeatherIcons.Play,
                    contentDescription = if (narratorState.isSpeaking && !narratorState.isPaused) "Pause narration" else "Play narration",
                    tint = Color.Black,
                    modifier = Modifier.size(22.dp)
                  )
                }
              }
              IconButton(
                onClick = onNextSegment,
                modifier = Modifier.minimumInteractiveComponentSize().testTag("announcer_next_button")
              ) {
                Icon(FeatherIcons.SkipNext, contentDescription = "Next sentence", tint = textColor)
              }
            }

            Text(
              text = "${(narratorState.currentIndex + 1).coerceAtMost(total)} / $total",
              style = MaterialTheme.typography.labelMedium,
              color = subColor
            )
          }
          narratorState.statusNote?.let { note ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = note,
              style = MaterialTheme.typography.labelSmall,
              color = subColor,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }
      }
    }
  }
}

@Composable
fun PageSourceOverlay(
  pageUrl: String,
  htmlSource: String,
  onCopy: (String) -> Unit,
  onClose: () -> Unit
) {
  BackHandler { onClose() }
  Surface(
    color = MaterialTheme.colorScheme.background,
    modifier = Modifier.fillMaxSize()
  ) {
    Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
      Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(FeatherIcons.CodeSource, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
          Spacer(modifier = Modifier.width(8.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.action_view_source), style = MaterialTheme.typography.titleMedium)
            Text(
              UrlValidator.extractHost(pageUrl),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
          IconButton(
            onClick = { onCopy(htmlSource) },
            modifier = Modifier.minimumInteractiveComponentSize()
          ) {
            Icon(FeatherIcons.Copy, contentDescription = "Copy source")
          }
          IconButton(
            onClick = onClose,
            modifier = Modifier.minimumInteractiveComponentSize()
          ) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.reader_close))
          }
        }
      }
      Text(
        text = htmlSource,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(14.dp)
      )
    }
  }
}

@Composable
fun MediaDownloaderDialog(
  mediaList: List<DetectedMedia>,
  onDownload: (DetectedMedia) -> Unit,
  onCopyUrl: (String) -> Unit,
  onPlayExternal: (DetectedMedia) -> Unit,
  onClose: () -> Unit
) {
  AlertDialog(
    onDismissRequest = onClose,
    title = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(FeatherIcons.Video, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("Media Downloader (${mediaList.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
      }
    },
    text = {
      if (mediaList.isEmpty()) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Text(
            "No media streams detected yet on this page.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            "Play video or audio on the webpage to automatically sniff downloadable streams.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
          )
        }
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          items(mediaList) { item ->
            Card(
              shape = RoundedCornerShape(12.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(modifier = Modifier.padding(10.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                  ) {
                    Text(
                      text = item.formatLabel,
                      style = MaterialTheme.typography.labelSmall,
                      fontWeight = FontWeight.Bold,
                      color = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                  }
                  Text(
                    text = UrlValidator.extractHost(item.url),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = item.title.ifEmpty { item.url },
                  style = MaterialTheme.typography.bodySmall,
                  fontWeight = FontWeight.Medium,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.End,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  TextButton(
                    onClick = { onCopyUrl(item.url) },
                    contentPadding = PaddingValues(horizontal = 8.dp)
                  ) {
                    Text("Copy", style = MaterialTheme.typography.labelMedium)
                  }
                  TextButton(
                    onClick = { onPlayExternal(item) },
                    contentPadding = PaddingValues(horizontal = 8.dp)
                  ) {
                    Text("Play", style = MaterialTheme.typography.labelMedium)
                  }
                  Button(
                    onClick = { onDownload(item) },
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.height(34.dp)
                  ) {
                    Text("Download", style = MaterialTheme.typography.labelMedium)
                  }
                }
              }
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onClose) {
        Text("Done")
      }
    }
  )
}

@Composable
fun ImageSnifferDialog(
  images: List<String>,
  onDownloadImages: (List<String>) -> Unit,
  onClose: () -> Unit
) {
  var selectedUrls by remember(images) { mutableStateOf(images.toSet()) }

  AlertDialog(
    onDismissRequest = onClose,
    title = {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(FeatherIcons.ImageBox, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("Save Images (${images.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        if (images.isNotEmpty()) {
          val allSelected = selectedUrls.size == images.size
          TextButton(
            onClick = {
              selectedUrls = if (allSelected) emptySet() else images.toSet()
            },
            contentPadding = PaddingValues(horizontal = 6.dp)
          ) {
            Text(if (allSelected) "Deselect" else "Select All", style = MaterialTheme.typography.labelSmall)
          }
        }
      }
    },
    text = {
      if (images.isEmpty()) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Text(
            "No images detected on this page.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          itemsIndexed(images) { index, url ->
            val isChecked = selectedUrls.contains(url)
            Card(
              shape = RoundedCornerShape(10.dp),
              colors = CardDefaults.cardColors(
                containerColor = if (isChecked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
              ),
              modifier = Modifier
                .fillMaxWidth()
                .clickable {
                  selectedUrls = if (isChecked) selectedUrls - url else selectedUrls + url
                }
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Checkbox(
                  checked = isChecked,
                  onCheckedChange = { checked ->
                    selectedUrls = if (checked) selectedUrls + url else selectedUrls - url
                  }
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "Image #${index + 1} · ${UrlValidator.extractHost(url)}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                  )
                  Text(
                    text = url,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }
              }
            }
          }
        }
      }
    },
    confirmButton = {
      if (images.isNotEmpty()) {
        Button(
          onClick = {
            onDownloadImages(selectedUrls.toList())
            onClose()
          },
          enabled = selectedUrls.isNotEmpty()
        ) {
          Text("Save (${selectedUrls.size})")
        }
      } else {
        TextButton(onClick = onClose) {
          Text("Close")
        }
      }
    },
    dismissButton = {
      if (images.isNotEmpty()) {
        TextButton(onClick = onClose) {
          Text("Cancel")
        }
      }
    }
  )
}

@Composable
fun ScreenFilterDialog(
  enabled: Boolean,
  selectedType: ScreenFilterType,
  opacity: Float,
  onToggleEnabled: () -> Unit,
  onSelectType: (ScreenFilterType) -> Unit,
  onOpacityChange: (Float) -> Unit,
  onClose: () -> Unit
) {
  AlertDialog(
    onDismissRequest = onClose,
    title = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(FeatherIcons.EyeFilter, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("Screen Filter (Night & Eye Care)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
      }
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Enable Filter", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text("Dims screen below system minimum and reduces blue-light strain", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          Switch(checked = enabled, onCheckedChange = { onToggleEnabled() })
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

        Text("Filter Mode", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

        ScreenFilterType.entries.forEach { type ->
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable { onSelectType(type) }
              .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            RadioButton(
              selected = selectedType == type,
              onClick = { onSelectType(type) }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
              shape = CircleShape,
              color = Color(type.colorHex),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
              modifier = Modifier.size(16.dp)
            ) {}
            Spacer(modifier = Modifier.width(8.dp))
            Text(type.label, style = MaterialTheme.typography.bodyMedium)
          }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

        Column(modifier = Modifier.fillMaxWidth()) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text("Filter Intensity", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text("${(opacity * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
          }
          Slider(
            value = opacity,
            onValueChange = onOpacityChange,
            valueRange = 0.10f..0.80f,
            steps = 14,
            modifier = Modifier.fillMaxWidth()
          )
        }
      }
    },
    confirmButton = {
      Button(onClick = onClose) {
        Text("Done")
      }
    }
  )
}
