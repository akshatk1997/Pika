package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.components.FeatherIcons
import com.example.ui.components.KioLogo

private enum class IntroCategory(val label: String) {
  ALL("All Features"),
  PRIVACY("Privacy & Shield"),
  FLOW("Gestures & Flow"),
  POWER("AI & Utilities")
}

private data class FeaturePillar(
  val id: String,
  val category: IntroCategory,
  val icon: ImageVector,
  val badgeText: String,
  val badgeColor: Color,
  val title: String,
  val subtitle: String,
  val spotlightDetail: String
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IntroScreen(
  onGetStarted: () -> Unit
) {
  BackHandler { onGetStarted() }

  val haptic = LocalHapticFeedback.current
  val entranceProgress = remember { Animatable(0f) }

  val line1Full = "One browser,"
  val line2Full = "every thing you are."

  val line1Progress = remember { Animatable(0f) }
  val line2Progress = remember { Animatable(0f) }

  var selectedCategory by remember { mutableStateOf(IntroCategory.ALL) }
  var expandedPillarId by remember { mutableStateOf<String?>("stealth_privacy") }

  LaunchedEffect(Unit) {
    entranceProgress.animateTo(
      targetValue = 1f,
      animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
    )
    line1Progress.animateTo(
      targetValue = line1Full.length.toFloat(),
      animationSpec = tween(durationMillis = 350, easing = LinearEasing)
    )
    line2Progress.animateTo(
      targetValue = line2Full.length.toFloat(),
      animationSpec = tween(durationMillis = 480, easing = LinearEasing)
    )
  }

  val line1Chars = line1Progress.value.toInt().coerceIn(0, line1Full.length)
  val line2Chars = line2Progress.value.toInt().coerceIn(0, line2Full.length)

  val displayLine1 = line1Full.take(line1Chars)
  val displayLine2 = line2Full.take(line2Chars)

  // Infinite breathing glow for Start button
  val infiniteTransition = rememberInfiniteTransition(label = "intro_glow")
  val glowAlpha by infiniteTransition.animateFloat(
    initialValue = 0.35f,
    targetValue = 0.85f,
    animationSpec = infiniteRepeatable(
      animation = tween(1400, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "intro_glow_alpha"
  )

  val pillars = remember {
    listOf(
      FeaturePillar(
        id = "stealth_privacy",
        category = IntroCategory.PRIVACY,
        icon = FeatherIcons.PrivateMask,
        badgeText = "STEALTH VAULT",
        badgeColor = Color(0xFF10B981),
        title = "Stealth Privacy & Zero Leaks",
        subtitle = "Hardware-isolated private mode, strict cookie vault barrier, anti-fingerprinting noise, and zero telemetry.",
        spotlightDetail = "✦ RAM-only volatile tabs · Zero disk traces · Isolated cookie vault"
      ),
      FeaturePillar(
        id = "ad_shield",
        category = IntroCategory.PRIVACY,
        icon = FeatherIcons.Shield,
        badgeText = "AD SHIELD",
        badgeColor = Color(0xFF38BDF8),
        title = "Integrated Ad & Video Shield",
        subtitle = "Blocks trackers, malicious popups, banner ads, and streaming video interruptions automatically.",
        spotlightDetail = "✦ Real-time subresource filtering · Zero annoying popunders & redirects"
      ),
      FeaturePillar(
        id = "one_hand_grayscale",
        category = IntroCategory.FLOW,
        icon = FeatherIcons.OneHand,
        badgeText = "ERGONOMICS",
        badgeColor = Color(0xFFA855F7),
        title = "One-Handed & Grayscale Modes",
        subtitle = "38% reachability viewport (Left/Right hand) and hardware-accelerated monochrome night reading.",
        spotlightDetail = "✦ Single-thumb reachability zone · OLED true black & white reading matrix"
      ),
      FeaturePillar(
        id = "tactile_reload",
        category = IntroCategory.FLOW,
        icon = FeatherIcons.BurnFlame,
        badgeText = "TACTILE FLOW",
        badgeColor = Color(0xFFF43F5E),
        title = "Tactile Reload & Omnibox Lock",
        subtitle = "Intentional press-slide down gesture for tactile reloading, and lock-tap omnibox with live tracker count.",
        spotlightDetail = "✦ Deliberate press-slide gesture (no accidental reloads) · Lock omnibox toggle"
      ),
      FeaturePillar(
        id = "nlp_announcer",
        category = IntroCategory.POWER,
        icon = FeatherIcons.Announcer,
        badgeText = "ON-DEVICE AI",
        badgeColor = Color(0xFFF59E0B),
        title = "Pure On-Device NLP Announcer",
        subtitle = "Clean article reader with text-to-speech voice narration running 100% in RAM with zero data stored.",
        spotlightDetail = "✦ Volatile text reader · Text-to-speech engine · 100% on-device processing"
      ),
      FeaturePillar(
        id = "media_updates",
        category = IntroCategory.POWER,
        icon = FeatherIcons.Download,
        badgeText = "TURBO UTILITIES",
        badgeColor = Color(0xFF0EA5E9),
        title = "Media Sniffer & Instant Updates",
        subtitle = "Multi-stream video/audio detector, background download manager, and in-settings automated APK updates.",
        spotlightDetail = "✦ In-page multi-format media detector · Automated in-settings release updater"
      )
    )
  }

  Scaffold(
    contentWindowInsets = WindowInsets.safeDrawing,
    containerColor = MaterialTheme.colorScheme.background
  ) { padding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(horizontal = 20.dp, vertical = 12.dp),
      contentAlignment = Alignment.Center
    ) {
      Column(
        modifier = Modifier
          .widthIn(max = 520.dp)
          .fillMaxWidth()
          .graphicsLayer {
            alpha = entranceProgress.value
            translationY = (1f - entranceProgress.value) * 35f
          }
          .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
      ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Hero Emblem (Aesthetic Animated Kio Emblem)
        KioLogo(
          modifier = Modifier
            .size(92.dp)
            .testTag("kio_intro_logo"),
          isPrivate = false,
          animated = true,
          contentDescription = stringResource(R.string.app_name)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Prominent Cursive Script Header with Typewriter Typing Animation:
        // "One browser,\nevery thing you are."
        Text(
          text = displayLine1,
          fontFamily = FontFamily.Cursive,
          fontSize = 38.sp,
          fontWeight = FontWeight.Normal,
          textAlign = TextAlign.Center,
          lineHeight = 44.sp,
          color = MaterialTheme.colorScheme.onBackground
        )
        Box(
          modifier = Modifier.defaultMinSize(minHeight = 44.dp),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = displayLine2,
            fontFamily = FontFamily.Cursive,
            fontSize = 38.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
            lineHeight = 44.sp,
            color = MaterialTheme.colorScheme.onBackground
          )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
          text = "Every feature crafted for absolute privacy, tactile control, and lightning-fast speed.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
          modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Category Filter Chips for Creative Browsing
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
          verticalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("intro_category_chips")
        ) {
          IntroCategory.entries.forEach { cat ->
            val isSelected = selectedCategory == cat
            FilterChip(
              selected = isSelected,
              onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                selectedCategory = cat
              },
              label = {
                Text(
                  text = cat.label,
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
              },
              shape = RoundedCornerShape(12.dp),
              colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                selectedLabelColor = MaterialTheme.colorScheme.primary
              ),
              border = BorderStroke(
                width = if (isSelected) 1.2.dp else 0.8.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
              )
            )
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Comprehensive Feature Pillars
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          pillars.forEach { pillar ->
            val isVisible = selectedCategory == IntroCategory.ALL || selectedCategory == pillar.category
            AnimatedVisibility(
              visible = isVisible,
              enter = fadeIn() + expandVertically(),
              exit = fadeOut() + shrinkVertically()
            ) {
              val isExpanded = expandedPillarId == pillar.id
              IntroPillarCard(
                pillar = pillar,
                isExpanded = isExpanded,
                onClick = {
                  haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                  expandedPillarId = if (isExpanded) null else pillar.id
                }
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Start Browsing Button with Breathing Glow Accent
        Surface(
          shape = RoundedCornerShape(18.dp),
          border = BorderStroke(
            width = 1.5.dp,
            brush = Brush.horizontalGradient(
              colors = listOf(
                MaterialTheme.colorScheme.primary.copy(alpha = glowAlpha),
                Color(0xFF38BDF8).copy(alpha = glowAlpha),
                Color(0xFF10B981).copy(alpha = glowAlpha)
              )
            )
          ),
          color = Color.Transparent,
          modifier = Modifier.fillMaxWidth()
        ) {
          Button(
            onClick = {
              haptic.performHapticFeedback(HapticFeedbackType.LongPress)
              onGetStarted()
            },
            shape = RoundedCornerShape(17.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier
              .fillMaxWidth()
              .height(54.dp)
              .testTag("intro_get_started")
          ) {
            Text(
              text = "Start Browsing",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.width(10.dp))
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowForward,
              contentDescription = null,
              modifier = Modifier.size(19.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(18.dp))
      }
    }
  }
}

@Composable
private fun IntroPillarCard(
  pillar: FeaturePillar,
  isExpanded: Boolean,
  onClick: () -> Unit
) {
  val borderColor = if (isExpanded) {
    pillar.badgeColor.copy(alpha = 0.75f)
  } else {
    MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
  }

  Card(
    shape = RoundedCornerShape(18.dp),
    border = BorderStroke(if (isExpanded) 1.2.dp else 0.8.dp, borderColor),
    colors = CardDefaults.cardColors(
      containerColor = if (isExpanded) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
      } else {
        MaterialTheme.colorScheme.surface
      }
    ),
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(18.dp))
      .clickable(onClick = onClick)
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically
      ) {
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = pillar.badgeColor.copy(alpha = 0.14f),
          border = BorderStroke(0.8.dp, pillar.badgeColor.copy(alpha = 0.4f)),
          modifier = Modifier.size(44.dp)
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = pillar.icon,
              contentDescription = null,
              tint = pillar.badgeColor,
              modifier = Modifier.size(22.dp)
            )
          }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = pillar.title,
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = pillar.badgeColor.copy(alpha = 0.15f)
            ) {
              Text(
                text = pillar.badgeText,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                fontWeight = FontWeight.Bold,
                color = pillar.badgeColor,
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
              )
            }
          }
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = pillar.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 16.sp
          )
        }
      }

      // Dynamic Spotlight Micro-Chip on Expand
      AnimatedVisibility(
        visible = isExpanded,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
      ) {
        Column {
          Spacer(modifier = Modifier.height(8.dp))
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = pillar.badgeColor.copy(alpha = 0.12f),
            border = BorderStroke(0.6.dp, pillar.badgeColor.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Text(
              text = pillar.spotlightDetail,
              style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
              color = pillar.badgeColor,
              fontWeight = FontWeight.SemiBold,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
            )
          }
        }
      }
    }
  }
}
