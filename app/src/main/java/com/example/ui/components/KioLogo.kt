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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalReduceMotion
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Precomputed deterministic stardust particle structure for 60/120Hz zero-allocation drawing.
 */
private data class StardustParticle(
  val nx: Float,
  val ny: Float,
  val sizeRatio: Float,
  val baseAlpha: Float,
  val twinkleSpeed: Float,
  val isCore: Boolean
)

/**
 * Procedural stardust pointillism matrix mirroring cosmic gravitational ripple waves and central star orb.
 */
private val STARDUST_PARTICLES: List<StardustParticle> by lazy {
  val list = ArrayList<StardustParticle>(250)
  val rand = java.util.Random(20261126L)

  // 1. Central Luminous Star Orb (Dense spherical stardust cluster)
  for (i in 0 until 60) {
    val r = sqrt(rand.nextFloat()) * 0.132f
    val theta = rand.nextFloat() * (2f * Math.PI.toFloat())
    val nx = r * cos(theta)
    val ny = r * sin(theta)
    val sizeRatio = 0.005f + rand.nextFloat() * 0.009f
    val baseAlpha = 0.45f + rand.nextFloat() * 0.55f
    val speed = 1.0f + rand.nextFloat() * 2.2f
    list.add(StardustParticle(nx, ny, sizeRatio, baseAlpha, speed, isCore = true))
  }

  // 2. Concentric Symmetrical Gravitational Wave Ripple Crests (Horizontal acoustic arcs)
  val rippleRadii = floatArrayOf(0.21f, 0.29f, 0.37f, 0.45f)
  val counts = intArrayOf(28, 36, 44, 52)

  for (tier in rippleRadii.indices) {
    val baseR = rippleRadii[tier]
    val count = counts[tier]
    for (i in 0 until count) {
      val isRight = rand.nextBoolean()
      val maxAngleDeg = 60f
      val angleDeg = (rand.nextFloat() * 2f - 1f) * maxAngleDeg
      val angleRad = Math.toRadians(angleDeg.toDouble()).toFloat()
      val arcCenterAngle = if (isRight) 0f else Math.PI.toFloat()
      val theta = arcCenterAngle + (if (isRight) angleRad else -angleRad)

      val radialJitter = (rand.nextFloat() - 0.5f) * 0.022f
      val r = baseR + radialJitter
      val nx = r * cos(theta)
      val ny = r * sin(theta)

      // Crest brightness: particles closer to horizontal equator (y = 0) are naturally denser and brighter
      val crestWeight = cos(angleRad).coerceIn(0.25f, 1f)
      val baseAlpha = (0.30f + rand.nextFloat() * 0.70f) * crestWeight
      val sizeRatio = 0.004f + rand.nextFloat() * 0.007f
      val speed = 0.8f + rand.nextFloat() * 1.8f
      list.add(StardustParticle(nx, ny, sizeRatio, baseAlpha, speed, isCore = false))
    }
  }

  // 3. Ambient Starfield Dust (Subtle cosmic void motes)
  for (i in 0 until 35) {
    val nx = (rand.nextFloat() - 0.5f) * 0.94f
    val ny = (rand.nextFloat() - 0.5f) * 0.94f
    val dist = sqrt(nx * nx + ny * ny)
    if (dist in 0.14f..0.48f) {
      val sizeRatio = 0.003f + rand.nextFloat() * 0.005f
      val baseAlpha = 0.12f + rand.nextFloat() * 0.40f
      val speed = 0.5f + rand.nextFloat() * 1.4f
      list.add(StardustParticle(nx, ny, sizeRatio, baseAlpha, speed, isCore = false))
    }
  }

  list
}

/**
 * Modern Kio emblem inspired by cosmic gravitational ripples and stardust pointillism:
 * features a central radiant star orb with expanding concentric crescent stardust wavefronts,
 * monochromatic OLED deep space black palette, diamond silver-white particles, and silk-smooth kinetic pulse.
 */
@Composable
fun KioLogo(
  modifier: Modifier = Modifier,
  size: Dp = 80.dp,
  isPrivate: Boolean = false,
  animated: Boolean = true,
  contentDescription: String? = "Kio Logo"
) {
  val reduceMotion = LocalReduceMotion.current
  val runAnimation = animated && !reduceMotion

  val infiniteTransition = rememberInfiniteTransition(label = "KioCosmicLogoAnimation")

  val rawCorePulse = infiniteTransition.animateFloat(
    initialValue = 0.94f,
    targetValue = 1.06f,
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

  val rawTwinkle = infiniteTransition.animateFloat(
    initialValue = 0.5f,
    targetValue = 1.0f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 2000, easing = LinearEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "stardustTwinkle"
  )

  val rawWavePhase = infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 3400, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "waveResonancePhase"
  )

  val pulseScale = if (runAnimation) rawCorePulse.value else 1f
  val glowAlpha = if (runAnimation) rawGlowAlpha.value else 0.6f
  val twinkle = if (runAnimation) rawTwinkle.value else 0.8f
  val wavePhase = if (runAnimation) rawWavePhase.value else 0.5f

  // Materialistic Deep Space Obsidian Palette with tactile depth
  val baseContainerBrush = if (isPrivate) {
    Brush.radialGradient(
      colors = listOf(Color(0xFF180F2A), Color(0xFF0D0618), Color(0xFF000000)),
      radius = Float.POSITIVE_INFINITY
    )
  } else {
    Brush.radialGradient(
      colors = listOf(Color(0xFF161A26), Color(0xFF0A0C13), Color(0xFF000000)),
      radius = Float.POSITIVE_INFINITY
    )
  }
  val borderBrush = Brush.linearGradient(
    colors = if (isPrivate) {
      listOf(Color(0xFFF3E8FF).copy(alpha = 0.45f), Color(0xFF7E22CE).copy(alpha = 0.18f), Color.White.copy(alpha = 0.06f))
    } else {
      listOf(Color.White.copy(alpha = 0.42f), Color(0xFF94A3B8).copy(alpha = 0.16f), Color.White.copy(alpha = 0.08f))
    },
    start = Offset.Zero,
    end = Offset.Infinite
  )
  val shadowGlowColor = if (isPrivate) Color(0xFFE9D5FF).copy(alpha = 0.35f) else Color(0xFFFFFFFF).copy(alpha = 0.30f)

  val particlePrimaryColor = Color(0xFFFFFFFF)
  val particleSecondaryColor = if (isPrivate) Color(0xFFF3E8FF) else Color(0xFFF1F5F9)
  val starCoreColor = Color(0xFFFFFFFF)
  val starCoreMidColor = if (isPrivate) Color(0xFFE9D5FF) else Color(0xFFE2E8F0)
  val starCoreShadowColor = if (isPrivate) Color(0xFF7E22CE) else Color(0xFF64748B)

  Box(
    modifier = modifier
      .size(size)
      .shadow(
        elevation = 14.dp,
        shape = CircleShape,
        ambientColor = shadowGlowColor,
        spotColor = shadowGlowColor
      )
      .clip(CircleShape)
      .background(baseContainerBrush)
      .border(1.2.dp, borderBrush, CircleShape)
      .then(
        if (contentDescription != null) {
          Modifier.semantics { this.contentDescription = contentDescription }
        } else {
          Modifier
        }
      ),
    contentAlignment = Alignment.Center
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val w = this.size.width
      val h = this.size.height
      val centerOffset = Offset(w / 2f, h / 2f)
      val minDim = min(w, h)

      val coreRadius = minDim * 0.138f
      val rippleRadii = floatArrayOf(
        minDim * 0.21f,
        minDim * 0.29f,
        minDim * 0.37f,
        minDim * 0.45f
      )

      // 1. Ambient Cosmic Bloom Halo (Refined Material Glow)
      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(
            particlePrimaryColor.copy(alpha = 0.32f * glowAlpha),
            particleSecondaryColor.copy(alpha = 0.10f * glowAlpha),
            Color.Transparent
          ),
          center = centerOffset,
          radius = minDim * 0.52f
        ),
        radius = minDim * 0.52f,
        center = centerOffset
      )

      // 2. Concentric Symmetrical Gravitational Crescent Wavefront Arcs (Dual-Layer Material Sheen)
      val strokeWidths = floatArrayOf(
        minDim * 0.032f,
        minDim * 0.026f,
        minDim * 0.020f,
        minDim * 0.016f
      )

      for (i in rippleRadii.indices) {
        val r = rippleRadii[i] + (wavePhase * 0.008f * minDim * (i + 1))
        val stroke = strokeWidths[i]
        val arcRectTopLeft = Offset(centerOffset.x - r, centerOffset.y - r)
        val arcRectSize = Size(r * 2f, r * 2f)

        // Diffusion shadow under arc for tactile elevation
        val diffusionBrush = Brush.linearGradient(
          colors = listOf(
            Color.Transparent,
            particlePrimaryColor.copy(alpha = 0.20f * glowAlpha),
            Color.Transparent
          ),
          start = Offset(centerOffset.x, centerOffset.y - r * 0.866f),
          end = Offset(centerOffset.x, centerOffset.y + r * 0.866f)
        )
        drawArc(
          brush = diffusionBrush,
          startAngle = 120f,
          sweepAngle = 120f,
          useCenter = false,
          topLeft = arcRectTopLeft,
          size = arcRectSize,
          style = Stroke(width = stroke * 1.6f, cap = StrokeCap.Round)
        )
        drawArc(
          brush = diffusionBrush,
          startAngle = -60f,
          sweepAngle = 120f,
          useCenter = false,
          topLeft = arcRectTopLeft,
          size = arcRectSize,
          style = Stroke(width = stroke * 1.6f, cap = StrokeCap.Round)
        )

        // Equator-focused gradient brush: brightest at horizontal center, fading towards tips
        val arcBrush = Brush.linearGradient(
          colors = listOf(
            Color.Transparent,
            particlePrimaryColor.copy(alpha = (0.55f + (3 - i) * 0.15f) * glowAlpha),
            Color.Transparent
          ),
          start = Offset(centerOffset.x, centerOffset.y - r * 0.866f),
          end = Offset(centerOffset.x, centerOffset.y + r * 0.866f)
        )

        // Left crescent wave
        drawArc(
          brush = arcBrush,
          startAngle = 120f,
          sweepAngle = 120f,
          useCenter = false,
          topLeft = arcRectTopLeft,
          size = arcRectSize,
          style = Stroke(width = stroke, cap = StrokeCap.Round)
        )

        // Right crescent wave
        drawArc(
          brush = arcBrush,
          startAngle = -60f,
          sweepAngle = 120f,
          useCenter = false,
          topLeft = arcRectTopLeft,
          size = arcRectSize,
          style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
      }

      // 3. Stardust Pointillism Matrix (Dense particles radiating along ripples and core)
      val particles = STARDUST_PARTICLES
      for (p in particles) {
        val (px, py) = if (p.isCore) {
          centerOffset.x + p.nx * minDim * pulseScale to centerOffset.y + p.ny * minDim * pulseScale
        } else {
          centerOffset.x + p.nx * minDim to centerOffset.y + p.ny * minDim
        }

        val dynamicTwinkle = (0.65f + 0.35f * sin((twinkle * p.twinkleSpeed * Math.PI).toFloat())).coerceIn(0.2f, 1f)
        val particleAlpha = (p.baseAlpha * dynamicTwinkle).coerceIn(0.08f, 1.0f)
        val particleRadius = p.sizeRatio * minDim * (if (p.isCore) pulseScale else 1f)

        drawCircle(
          color = (if (p.isCore) particlePrimaryColor else particleSecondaryColor).copy(alpha = particleAlpha),
          radius = particleRadius,
          center = Offset(px, py)
        )
      }

      // 4. Central Radiant Star Orb (Sculpted 3D Material Celestial Sphere)
      scale(scale = pulseScale, pivot = centerOffset) {
        // Outer core radial glow
        drawCircle(
          brush = Brush.radialGradient(
            colors = listOf(
              starCoreMidColor.copy(alpha = 0.60f * glowAlpha),
              particlePrimaryColor.copy(alpha = 0.22f * glowAlpha),
              Color.Transparent
            ),
            center = centerOffset,
            radius = coreRadius * 2.0f
          ),
          radius = coreRadius * 2.0f,
          center = centerOffset
        )

        // 3D Material spherical gradient with specular highlight from top-left
        drawCircle(
          brush = Brush.radialGradient(
            colors = listOf(
              starCoreColor,
              starCoreMidColor,
              starCoreShadowColor.copy(alpha = 0.90f)
            ),
            center = centerOffset - Offset(coreRadius * 0.32f, coreRadius * 0.32f),
            radius = coreRadius * 1.15f
          ),
          radius = coreRadius,
          center = centerOffset
        )

        // Specular glint hotspot
        drawCircle(
          color = Color.White.copy(alpha = 0.75f * glowAlpha),
          radius = coreRadius * 0.28f,
          center = centerOffset - Offset(coreRadius * 0.30f, coreRadius * 0.30f)
        )
      }
    }
  }
}
