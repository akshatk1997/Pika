package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.FeatherPrivateAccent
import com.example.ui.theme.LocalReduceMotion
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Aesthetic modern Kai emblem: concentric outer circle, inner circle, and luminous center dot.
 * Features kinetic orbital rotation, harmonic breathing pulse, radiant gradients, and luxury finish.
 */
@Composable
fun KaiLogo(
  modifier: Modifier = Modifier,
  size: Dp = 80.dp,
  isPrivate: Boolean = false,
  animated: Boolean = true,
  contentDescription: String? = "Kai Logo"
) {
  val reduceMotion = LocalReduceMotion.current
  val runAnimation = animated && !reduceMotion

  val infiniteTransition = rememberInfiniteTransition(label = "KaiLogoAnimation")

  val rawOuterRotation = infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 360f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 8000, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "outerOrbitRotation"
  )

  val rawInnerRotation = infiniteTransition.animateFloat(
    initialValue = 360f,
    targetValue = 0f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 11000, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "innerOrbitRotation"
  )

  val rawPulseScale = infiniteTransition.animateFloat(
    initialValue = 0.94f,
    targetValue = 1.08f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 2600, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "corePulseScale"
  )

  val rawGlowAlpha = infiniteTransition.animateFloat(
    initialValue = 0.35f,
    targetValue = 0.85f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 2600, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "glowPulseAlpha"
  )

  val outerRotation = if (runAnimation) rawOuterRotation.value else 0f
  val innerRotation = if (runAnimation) rawInnerRotation.value else 45f
  val pulseScale = if (runAnimation) rawPulseScale.value else 1f
  val glowAlpha = if (runAnimation) rawGlowAlpha.value else 0.5f

  // Modern luxury palette
  val baseContainerColor = if (isPrivate) Color(0xFF130924) else Color(0xFF070B14)
  val borderColor = if (isPrivate) FeatherPrivateAccent.copy(alpha = 0.45f) else Color(0xFF38BDF8).copy(alpha = 0.35f)

  val outerGradientColors = if (isPrivate) {
    listOf(
      Color(0xFFC084FC), // Lavender Neon
      Color(0xFFE879F9), // Fuchsia
      Color(0xFF7C3AED), // Royal Purple
      Color(0xFFFFFFFF), // Highlight
      Color(0xFFC084FC)
    )
  } else {
    listOf(
      Color(0xFF00F2FE), // Electric Cyan
      Color(0xFF38BDF8), // Sky Azure
      Color(0xFF6366F1), // Royal Indigo
      Color(0xFFFFFFFF), // Highlight
      Color(0xFF00F2FE)
    )
  }

  val innerArcColor = if (isPrivate) Color(0xFFE879F9) else Color(0xFF38BDF8)
  val coreCenterColor = Color.White
  val coreRimColor = if (isPrivate) Color(0xFFC084FC) else Color(0xFF00F2FE)
  val auraGlowColor = if (isPrivate) Color(0xFFA855F7) else Color(0xFF00E5FF)

  Box(
    modifier = modifier
      .size(size)
      .shadow(elevation = 10.dp, shape = CircleShape, ambientColor = auraGlowColor, spotColor = auraGlowColor)
      .clip(CircleShape)
      .background(baseContainerColor)
      .border(1.2.dp, borderColor, CircleShape)
      .then(
        if (contentDescription != null) {
          Modifier.semantics { this.contentDescription = contentDescription }
        } else {
          Modifier
        }
      ),
    contentAlignment = Alignment.Center
  ) {
    Canvas(modifier = Modifier.fillMaxSize().padding(size * 0.08f)) {
      val w = this.size.width
      val h = this.size.height
      val centerOffset = Offset(w / 2f, h / 2f)
      val minDim = min(w, h)

      val outerRadius = minDim * 0.44f
      val innerRadius = minDim * 0.26f
      val coreRadius = minDim * 0.10f
      val strokeOuter = minDim * 0.038f
      val strokeInner = minDim * 0.032f

      // 1. Ambient Background Glow
      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(
            auraGlowColor.copy(alpha = 0.22f * glowAlpha),
            auraGlowColor.copy(alpha = 0.06f * glowAlpha),
            Color.Transparent
          ),
          center = centerOffset,
          radius = minDim * 0.52f
        ),
        radius = minDim * 0.52f,
        center = centerOffset
      )

      // 2. Outer Circle: Sweep Gradient Ring with orbital rotation
      rotate(degrees = outerRotation, pivot = centerOffset) {
        val sweepBrush = Brush.sweepGradient(
          colors = outerGradientColors,
          center = centerOffset
        )
        drawCircle(
          brush = sweepBrush,
          radius = outerRadius,
          center = centerOffset,
          style = Stroke(width = strokeOuter, cap = StrokeCap.Round)
        )

        // Orbital Satellite Pip at 0 degrees
        val satAngleRad = Math.toRadians(0.0)
        val satX = centerOffset.x + (outerRadius * cos(satAngleRad)).toFloat()
        val satY = centerOffset.y + (outerRadius * sin(satAngleRad)).toFloat()
        drawCircle(
          color = Color.White.copy(alpha = 0.8f),
          radius = strokeOuter * 0.95f,
          center = Offset(satX, satY)
        )
        drawCircle(
          color = coreRimColor,
          radius = strokeOuter * 0.55f,
          center = Offset(satX, satY)
        )
      }

      // 3. Inner Circle: Precision concentric arcs with counter-rotation & subtle pulse
      rotate(degrees = innerRotation, pivot = centerOffset) {
        val arcRectTopLeft = Offset(centerOffset.x - innerRadius, centerOffset.y - innerRadius)
        val arcRectSize = Size(innerRadius * 2f, innerRadius * 2f)
        val innerSweepBrush = Brush.sweepGradient(
          colors = listOf(
            innerArcColor.copy(alpha = 0.35f),
            innerArcColor,
            Color.White.copy(alpha = 0.9f),
            innerArcColor.copy(alpha = 0.35f)
          ),
          center = centerOffset
        )

        // Symmetrical dual arcs with micro gaps for modernist horology precision look
        drawArc(
          brush = innerSweepBrush,
          startAngle = 18f,
          sweepAngle = 144f,
          useCenter = false,
          topLeft = arcRectTopLeft,
          size = arcRectSize,
          style = Stroke(width = strokeInner, cap = StrokeCap.Round)
        )
        drawArc(
          brush = innerSweepBrush,
          startAngle = 198f,
          sweepAngle = 144f,
          useCenter = false,
          topLeft = arcRectTopLeft,
          size = arcRectSize,
          style = Stroke(width = strokeInner, cap = StrokeCap.Round)
        )
      }

      // 4. Center Dot: Luminous Quantum Core with breathing glow halo
      scale(scale = pulseScale, pivot = centerOffset) {
        // Soft aura halo behind core dot
        drawCircle(
          brush = Brush.radialGradient(
            colors = listOf(
              coreRimColor.copy(alpha = 0.70f * glowAlpha),
              auraGlowColor.copy(alpha = 0.25f * glowAlpha),
              Color.Transparent
            ),
            center = centerOffset,
            radius = coreRadius * 2.3f
          ),
          radius = coreRadius * 2.3f,
          center = centerOffset
        )

        // Core solid dot with radial diamond highlight
        drawCircle(
          brush = Brush.radialGradient(
            colors = listOf(
              coreCenterColor,
              coreRimColor
            ),
            center = centerOffset - Offset(coreRadius * 0.2f, coreRadius * 0.2f),
            radius = coreRadius
          ),
          radius = coreRadius,
          center = centerOffset
        )
      }
    }
  }
}
