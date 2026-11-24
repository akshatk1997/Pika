package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.components.FeatherIcons
import com.example.ui.components.KioLogo

@Composable
fun IntroScreen(
  onGetStarted: () -> Unit
) {
  BackHandler { onGetStarted() }

  val haptic = LocalHapticFeedback.current
  var isVisible by remember { mutableStateOf(false) }

  LaunchedEffect(Unit) {
    isVisible = true
  }

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
          .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
      ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Animated Entrance for Emblem & Typography
        AnimatedVisibility(
          visible = isVisible,
          enter = fadeIn(tween(450)) + slideInVertically(
            spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
          ) { -40 }
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Hero Emblem (Aesthetic Animated Kio Emblem)
            KioLogo(
              modifier = Modifier
                .size(96.dp)
                .testTag("kio_intro_logo"),
              isPrivate = false,
              animated = true,
              contentDescription = stringResource(R.string.app_name)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Privacy-Focused Tag Pill
            Surface(
              shape = RoundedCornerShape(20.dp),
              color = Color(0xFF10B981).copy(alpha = 0.12f),
              border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.45f)),
              modifier = Modifier.padding(bottom = 12.dp)
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(
                  imageVector = FeatherIcons.PrivateMask,
                  contentDescription = null,
                  tint = Color(0xFF10B981),
                  modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = "STEALTH PRIVACY FIRST · ZERO TELEMETRY",
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFF10B981),
                  letterSpacing = 0.8.sp
                )
              }
            }

            // Prominent Cursive Script Header matching reference typography:
            // "One browser,\nevery thing you are."
            Text(
              text = "One browser,",
              fontFamily = FontFamily.Cursive,
              fontSize = 38.sp,
              fontWeight = FontWeight.Normal,
              textAlign = TextAlign.Center,
              lineHeight = 44.sp,
              color = MaterialTheme.colorScheme.onBackground
            )
            Text(
              text = "every thing you are.",
              fontFamily = FontFamily.Cursive,
              fontSize = 38.sp,
              fontWeight = FontWeight.Normal,
              textAlign = TextAlign.Center,
              lineHeight = 44.sp,
              color = MaterialTheme.colorScheme.onBackground
            )
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Privacy Positioning Card
        AnimatedVisibility(
          visible = isVisible,
          enter = fadeIn(tween(350)) + slideInVertically(
            spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
          ) { 30 }
        ) {
          Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("intro_thought_process")
          ) {
            Text(
              text = "Architected for absolute privacy: zero telemetry, zero analytics, RAM-only private isolation, and lightning-fast speed.",
              style = MaterialTheme.typography.bodyMedium,
              fontStyle = FontStyle.Italic,
              fontWeight = FontWeight.Medium,
              color = MaterialTheme.colorScheme.onSurface,
              textAlign = TextAlign.Center,
              modifier = Modifier.padding(horizontal = 18.dp, vertical = 13.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Unique Privacy & Power Pillars (Staggered Animation)
        AnimatedVisibility(
          visible = isVisible,
          enter = fadeIn(tween(400)) + slideInVertically(
            spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
          ) { 40 }
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            IntroPillarCard(
              icon = FeatherIcons.PrivateMask,
              badgeText = "STEALTH VAULT",
              badgeColor = Color(0xFF10B981),
              title = "Stealth Privacy & Zero Leaks",
              subtitle = "Hardware-isolated private mode, strict cookie vault barrier, anti-fingerprinting noise, and zero telemetry."
            )

            IntroPillarCard(
              icon = FeatherIcons.Shield,
              badgeText = "AD SHIELD",
              badgeColor = Color(0xFF38BDF8),
              title = "Integrated Ad & Video Shield",
              subtitle = "Blocks trackers, malicious popups, banner ads, and streaming video interruptions automatically."
            )

            IntroPillarCard(
              icon = FeatherIcons.OneHand,
              badgeText = "ERGONOMICS",
              badgeColor = Color(0xFFA855F7),
              title = "One-Handed & Grayscale Modes",
              subtitle = "38% reachability viewport (Left/Right hand) and hardware-accelerated monochrome night reading."
            )

            IntroPillarCard(
              icon = FeatherIcons.Announcer,
              badgeText = "ON-DEVICE AI",
              badgeColor = Color(0xFFF59E0B),
              title = "Pure On-Device NLP Announcer",
              subtitle = "Clean article reader with text-to-speech voice narration running 100% in RAM with zero data stored."
            )

            IntroPillarCard(
              icon = FeatherIcons.DataSaver,
              badgeText = "120Hz FLUID",
              badgeColor = Color(0xFF06B6D4),
              title = "Tactile Gestures & Pull-to-Refresh",
              subtitle = "Slide down anywhere to reload, tap the security lock to toggle the omnibox, and fluid high-refresh page navigation."
            )
          }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Start Browsing Button with Breathing Glow Accent
        AnimatedVisibility(
          visible = isVisible,
          enter = fadeIn(tween(450)) + slideInVertically(
            spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
          ) { 50 }
        ) {
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
        }

        Spacer(modifier = Modifier.height(18.dp))
      }
    }
  }
}

@Composable
private fun IntroPillarCard(
  icon: ImageVector,
  badgeText: String,
  badgeColor: Color,
  title: String,
  subtitle: String
) {
  Card(
    shape = RoundedCornerShape(18.dp),
    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Surface(
        shape = RoundedCornerShape(14.dp),
        color = badgeColor.copy(alpha = 0.14f),
        border = BorderStroke(0.8.dp, badgeColor.copy(alpha = 0.4f)),
        modifier = Modifier.size(44.dp)
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = icon,
            contentDescription = null,
            tint = badgeColor,
            modifier = Modifier.size(22.dp)
          )
        }
      }
      Spacer(modifier = Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f, fill = false)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = badgeColor.copy(alpha = 0.15f)
          ) {
            Text(
              text = badgeText,
              style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
              fontWeight = FontWeight.Bold,
              color = badgeColor,
              modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
            )
          }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          lineHeight = 16.sp
        )
      }
    }
  }
}
