package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
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
import com.example.model.BookmarkItem
import com.example.model.HttpWarningState
import com.example.model.PageError
import com.example.model.PageErrorType
import com.example.model.ReaderArticle
import com.example.security.UrlValidator
import com.example.ui.FindState
import com.example.ui.theme.BatMidnight
import com.example.ui.theme.BatSignalGold
import com.example.ui.theme.FeatherDangerRed
import com.example.ui.theme.FeatherMintDark
import com.example.ui.theme.FeatherPrivateAccent
import com.example.ui.theme.FeatherWarningAmber

@Composable
fun NewTabHomeView(
  isPrivate: Boolean,
  searchEngineName: String,
  bookmarks: List<BookmarkItem>,
  onSubmitQuery: (String) -> Unit,
  onOpenBookmark: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  var homeSearch by remember { mutableStateOf("") }
  val primaryAccent = if (isPrivate) FeatherPrivateAccent else MaterialTheme.colorScheme.primary
  val auraAccent = if (isPrivate) FeatherPrivateAccent else BatSignalGold
  val secondaryAccent = if (isPrivate) Color(0xFF6366F1) else FeatherMintDark
  val surfaceColor = MaterialTheme.colorScheme.surface
  val outlineColor = MaterialTheme.colorScheme.outline

  BoxWithConstraints(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .drawBehind {
        val center = Offset(size.width * 0.5f, size.height * 0.18f)
        drawCircle(
          brush = Brush.radialGradient(
            colors = listOf(
              auraAccent.copy(alpha = 0.15f),
              secondaryAccent.copy(alpha = 0.05f),
              Color.Transparent
            ),
            center = center,
            radius = size.minDimension * 0.72f
          ),
          radius = size.minDimension * 0.72f,
          center = center
        )
        drawCircle(
          color = auraAccent.copy(alpha = 0.08f),
          radius = size.minDimension * 0.34f,
          center = center,
          style = Stroke(width = 1.dp.toPx())
        )
        drawCircle(
          color = primaryAccent.copy(alpha = 0.05f),
          radius = size.minDimension * 0.52f,
          center = center,
          style = Stroke(width = 1.dp.toPx())
        )
      }
      .padding(horizontal = 20.dp, vertical = 14.dp),
    contentAlignment = Alignment.TopCenter
  ) {
    val isCompactHeight = maxHeight < 460.dp
    Column(
      modifier = Modifier.widthIn(max = 760.dp).fillMaxSize(),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Spacer(modifier = Modifier.height(if (isCompactHeight) 6.dp else 18.dp))

      // Hero Emblem (3D Bat Mascot Crest or Private Mask)
      val crestSize = if (isCompactHeight) 52.dp else 74.dp
      Surface(
        shape = RoundedCornerShape(if (isCompactHeight) 16.dp else 22.dp),
        color = if (isPrivate) primaryAccent.copy(alpha = 0.12f) else BatMidnight,
        border = BorderStroke(1.2.dp, auraAccent.copy(alpha = 0.55f)),
        shadowElevation = 6.dp,
        modifier = Modifier.size(crestSize)
      ) {
        Box(contentAlignment = Alignment.Center) {
          if (isPrivate) {
            Icon(
              imageVector = FeatherIcons.PrivateMask,
              contentDescription = stringResource(R.string.app_name),
              tint = primaryAccent,
              modifier = Modifier.size(if (isCompactHeight) 24.dp else 34.dp)
            )
          } else {
            Image(
              painter = painterResource(id = R.drawable.bat_icon_fg),
              contentDescription = stringResource(R.string.app_name),
              contentScale = ContentScale.Fit,
              modifier = Modifier.fillMaxSize().padding(3.dp)
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      Text(
        text = if (isPrivate) stringResource(R.string.tabs_private) else stringResource(R.string.app_name),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground
      )

      if (isPrivate) {
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
          shape = RoundedCornerShape(50),
          color = surfaceColor.copy(alpha = 0.85f),
          border = BorderStroke(0.8.dp, FeatherPrivateAccent.copy(alpha = 0.5f))
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(FeatherPrivateAccent)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = stringResource(R.string.tabs_private_banner),
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      // Home Search Box
      Surface(
        shape = RoundedCornerShape(28.dp),
        color = surfaceColor,
        tonalElevation = 4.dp,
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, primaryAccent.copy(alpha = 0.28f)),
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
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            BasicTextField(
              value = homeSearch,
              onValueChange = { homeSearch = it },
              singleLine = true,
              textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface
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
              onClick = { onSubmitQuery(homeSearch) },
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

      Spacer(modifier = Modifier.height(26.dp))

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
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      Spacer(modifier = Modifier.height(12.dp))

      if (bookmarks.isEmpty()) {
        Surface(
          shape = RoundedCornerShape(18.dp),
          color = surfaceColor.copy(alpha = 0.72f),
          border = BorderStroke(0.8.dp, outlineColor.copy(alpha = 0.6f)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = primaryAccent.copy(alpha = 0.1f),
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
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      } else {
        LazyVerticalGrid(
          columns = GridCells.Adaptive(minSize = 152.dp),
          contentPadding = PaddingValues(bottom = 16.dp),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.fillMaxSize()
        ) {
          items(bookmarks.take(12), key = { it.id }) { bm ->
            val host = UrlValidator.extractHost(bm.url)
            val initial = host.firstOrNull()?.uppercase() ?: "B"
            Surface(
              shape = RoundedCornerShape(16.dp),
              color = surfaceColor,
              tonalElevation = 1.dp,
              border = BorderStroke(0.8.dp, outlineColor.copy(alpha = 0.65f)),
              modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .clip(RoundedCornerShape(16.dp))
                .clickable { onOpenBookmark(bm.url) }
                .testTag("home_bookmark_${bm.id}")
            ) {
              Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Surface(
                  shape = RoundedCornerShape(11.dp),
                  color = primaryAccent.copy(alpha = 0.13f),
                  modifier = Modifier.size(36.dp)
                ) {
                  Box(contentAlignment = Alignment.Center) {
                    Text(
                      text = initial,
                      style = MaterialTheme.typography.titleMedium,
                      fontWeight = FontWeight.Bold,
                      color = primaryAccent
                    )
                  }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = bm.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                  Text(
                    text = host,
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
  fontSizeSp: Int,
  darkReading: Boolean,
  onChangeFontSize: (Int) -> Unit,
  onToggleDark: (Boolean) -> Unit,
  onClose: () -> Unit
) {
  BackHandler { onClose() }
  val bgColor = if (darkReading) Color(0xFF0B0E14) else Color(0xFFFDFBF6)
  val textColor = if (darkReading) Color(0xFFE2E8F0) else Color(0xFF1E293B)
  val subColor = if (darkReading) Color(0xFF94A3B8) else Color(0xFF64748B)

  Surface(color = bgColor, modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
      Surface(
        color = bgColor,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(FeatherIcons.Reader, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = stringResource(R.string.reader_title),
            style = MaterialTheme.typography.titleMedium,
            color = textColor,
            modifier = Modifier.weight(1f)
          )
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

      LazyColumn(
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.widthIn(max = 680.dp).align(Alignment.CenterHorizontally).fillMaxSize()
      ) {
        item {
          Text(
            text = article.siteHost.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
          )
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = article.title,
            style = MaterialTheme.typography.headlineMedium,
            color = textColor
          )
          Spacer(modifier = Modifier.height(10.dp))
          Box(
            modifier = Modifier
              .width(48.dp)
              .height(2.dp)
              .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
          )
          Spacer(modifier = Modifier.height(4.dp))
        }
        items(article.paragraphs) { paragraph ->
          Text(
            text = paragraph,
            fontSize = fontSizeSp.sp,
            lineHeight = (fontSizeSp * 1.6f).sp,
            color = textColor
          )
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
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(14.dp)
      )
    }
  }
}
