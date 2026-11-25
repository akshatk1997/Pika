package com.example.ui.components

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// Retro Space Cadet Palette
private val SpaceCadetBg = Color(0xFF070B19)
private val SpaceCadetCardBg = Color(0xFF0F172A)
private val SpaceCadetAccentCyan = Color(0xFF00F0FF)
private val SpaceCadetAccentYellow = Color(0xFFFFD700)
private val SpaceCadetAccentPink = Color(0xFFFF2A85)
private val SpaceCadetAccentPurple = Color(0xFFA855F7)
private val SpaceCadetGreen = Color(0xFF10B981)
private val RetroLcdGreen = Color(0xFF39FF14)

private data class Bumper(
  val id: Int,
  val cxRel: Float,
  val cyRel: Float,
  val radiusRel: Float,
  val color: Color,
  val points: Int
)

@Composable
fun PinballGameDialog(
  onDismiss: () -> Unit
) {
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(
      usePlatformDefaultWidth = false,
      dismissOnBackPress = true,
      dismissOnClickOutside = false
    )
  ) {
    Surface(
      modifier = Modifier
        .fillMaxSize()
        .background(Color.Black.copy(alpha = 0.85f))
        .testTag("pinball_game_dialog"),
      color = Color.Transparent
    ) {
      Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
      ) {
        PinballGameScreen(onClose = onDismiss)
      }
    }
  }
}

@Composable
fun PinballGameScreen(
  onClose: () -> Unit
) {
  val haptic = LocalHapticFeedback.current
  var soundEnabled by remember { mutableStateOf(true) }

  val toneGen = remember {
    try {
      ToneGenerator(AudioManager.STREAM_MUSIC, 50)
    } catch (_: Throwable) {
      null
    }
  }

  DisposableEffect(Unit) {
    onDispose {
      try {
        toneGen?.release()
      } catch (_: Throwable) {}
    }
  }

  fun playSound(type: Int) {
    if (soundEnabled && toneGen != null) {
      try {
        toneGen.startTone(type, 35)
      } catch (_: Throwable) {}
    }
  }

  var score by remember { mutableIntStateOf(0) }
  var highScore by remember { mutableIntStateOf(10000) }
  var ballsLeft by remember { mutableIntStateOf(3) }
  var gameOver by remember { mutableStateOf(false) }
  var gameStarted by remember { mutableStateOf(false) }

  // Game state coordinates normalized 0f..1f
  var ballX by remember { mutableFloatStateOf(0.92f) }
  var ballY by remember { mutableFloatStateOf(0.78f) }
  var vx by remember { mutableFloatStateOf(0f) }
  var vy by remember { mutableFloatStateOf(0f) }

  var leftFlipperActive by remember { mutableStateOf(false) }
  var rightFlipperActive by remember { mutableStateOf(false) }

  // Animation values for flipper visual rotation (radians)
  val leftFlipperAngle = remember { Animatable(0.42f) }
  val rightFlipperAngle = remember { Animatable(-0.42f) }

  LaunchedEffect(leftFlipperActive) {
    leftFlipperAngle.animateTo(
      targetValue = if (leftFlipperActive) -0.55f else 0.42f,
      animationSpec = tween(durationMillis = 40, easing = LinearEasing)
    )
  }

  LaunchedEffect(rightFlipperActive) {
    rightFlipperAngle.animateTo(
      targetValue = if (rightFlipperActive) 0.55f else -0.42f,
      animationSpec = tween(durationMillis = 40, easing = LinearEasing)
    )
  }

  fun launchBall() {
    if (gameOver) {
      score = 0
      ballsLeft = 3
      gameOver = false
    }
    // Launch upward through the right channel
    ballX = 0.92f
    ballY = 0.76f
    vx = -0.002f
    vy = -0.024f
    gameStarted = true
    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    playSound(ToneGenerator.TONE_PROP_BEEP2)
  }

  // 3 Space Cadet Bumper Targets
  val bumpers = remember {
    listOf(
      Bumper(1, 0.44f, 0.28f, 0.075f, SpaceCadetAccentCyan, 500),
      Bumper(2, 0.25f, 0.40f, 0.065f, SpaceCadetAccentPink, 350),
      Bumper(3, 0.63f, 0.40f, 0.065f, SpaceCadetAccentYellow, 350)
    )
  }

  var activeBumperHit by remember { mutableIntStateOf(0) }
  var bumperFlashTime by remember { mutableLongStateOf(0L) }

  // Physics simulation loop
  LaunchedEffect(gameStarted, gameOver) {
    if (!gameStarted || gameOver) return@LaunchedEffect

    var lastFrameNanos = 0L
    val gravity = 0.00045f

    while (gameStarted && !gameOver) {
      withFrameNanos { frameNanos ->
        if (lastFrameNanos == 0L) {
          lastFrameNanos = frameNanos
          return@withFrameNanos
        }
        val dt = ((frameNanos - lastFrameNanos) / 16_666_666f).coerceIn(0.5f, 2.0f)
        lastFrameNanos = frameNanos

        // Apply gravity
        vy += gravity * dt

        // Position update
        ballX += vx * dt
        ballY += vy * dt

        // Wall collisions
        val leftWall = 0.06f
        val rightWall = if (ballY < 0.25f) 0.94f else 0.88f // right launch lane partition
        val topWall = 0.06f

        if (ballX - 0.022f < leftWall) {
          ballX = leftWall + 0.022f
          vx = -vx * 0.78f
          haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        if (ballX + 0.022f > rightWall) {
          ballX = rightWall - 0.022f
          vx = -vx * 0.78f
          haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        if (ballY - 0.022f < topWall) {
          ballY = topWall + 0.022f
          vy = -vy * 0.78f
          vx += 0.002f // curved arch deflection
        }

        // Bumper collisions
        for (bumper in bumpers) {
          val dx = ballX - bumper.cxRel
          val dy = ballY - bumper.cyRel
          val dist = sqrt(dx * dx + dy * dy)
          val minDist = bumper.radiusRel + 0.024f
          if (dist < minDist && dist > 0.0001f) {
            val nx = dx / dist
            val ny = dy / dist
            val dot = vx * nx + vy * ny
            if (dot < 0f) {
              vx = (vx - 1.85f * dot * nx) + nx * 0.006f
              vy = (vy - 1.85f * dot * ny) + ny * 0.006f
              ballX = bumper.cxRel + nx * minDist
              ballY = bumper.cyRel + ny * minDist
              score += bumper.points
              if (score > highScore) highScore = score
              activeBumperHit = bumper.id
              bumperFlashTime = System.currentTimeMillis()
              haptic.performHapticFeedback(HapticFeedbackType.LongPress)
              playSound(ToneGenerator.TONE_PROP_PROMPT)
            }
          }
        }

        // Slingshots (angled rebound kickers above flippers)
        if (ballY in 0.60f..0.73f) {
          if (ballX in 0.10f..0.22f && vx < 0f) {
            vx = 0.012f
            vy = -0.014f
            score += 250
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            playSound(ToneGenerator.TONE_PROP_BEEP)
          } else if (ballX in 0.72f..0.84f && vx > 0f) {
            vx = -0.012f
            vy = -0.014f
            score += 250
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            playSound(ToneGenerator.TONE_PROP_BEEP)
          }
        }

        // Flipper zone collision (Y ~ 0.81f..0.89f)
        val flipperBaseY = 0.84f
        if (ballY in 0.80f..0.88f) {
          // Left flipper range (X: ~0.18f to ~0.44f)
          if (ballX in 0.18f..0.45f && vy > 0f) {
            ballY = 0.80f
            vy = if (leftFlipperActive) -0.021f else -0.010f
            vx = 0.006f + (ballX - 0.30f) * 0.04f
            score += 100
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            playSound(ToneGenerator.TONE_PROP_ACK)
          }
          // Right flipper range (X: ~0.45f to ~0.72f)
          else if (ballX in 0.45f..0.72f && vy > 0f) {
            ballY = 0.80f
            vy = if (rightFlipperActive) -0.021f else -0.010f
            vx = -0.006f + (ballX - 0.60f) * 0.04f
            score += 100
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            playSound(ToneGenerator.TONE_PROP_ACK)
          }
        }

        // Ball drain (falls below flippers)
        if (ballY > 0.95f) {
          ballsLeft--
          if (ballsLeft <= 0) {
            gameOver = true
            gameStarted = false
            playSound(ToneGenerator.TONE_PROP_NACK)
          } else {
            // Respawn in plunger lane
            ballX = 0.92f
            ballY = 0.76f
            vx = 0f
            vy = 0f
            gameStarted = false
          }
        }
      }
    }
  }

  // Rank determination
  val rank = when {
    score >= 50000 -> "ADMIRAL"
    score >= 25000 -> "COMMANDER"
    score >= 10000 -> "LIEUTENANT"
    score >= 5000 -> "ENSIGN"
    else -> "CADET"
  }

  Card(
    modifier = Modifier
      .fillMaxWidth(0.96f)
      .widthIn(max = 440.dp)
      .padding(vertical = 12.dp)
      .testTag("pinball_game_card"),
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = SpaceCadetCardBg),
    border = BorderStroke(1.2.dp, SpaceCadetAccentCyan.copy(alpha = 0.65f))
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(14.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      // Classic Windows Space Cadet Header
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = SpaceCadetAccentCyan.copy(alpha = 0.2f),
            border = BorderStroke(0.6.dp, SpaceCadetAccentCyan)
          ) {
            Text(
              text = "WIN 3D",
              style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
              fontWeight = FontWeight.Bold,
              color = SpaceCadetAccentCyan,
              modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
            )
          }
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Space Cadet Pinball",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
          )
        }

        IconButton(
          onClick = onClose,
          modifier = Modifier
            .size(32.dp)
            .testTag("pinball_close_button")
        ) {
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Close Pinball",
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(20.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Retro LCD Scoreboard Display
      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF04060C),
        border = BorderStroke(1.dp, RetroLcdGreen.copy(alpha = 0.4f))
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(
              text = "SCORE",
              style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
              color = RetroLcdGreen.copy(alpha = 0.7f),
              fontWeight = FontWeight.SemiBold
            )
            Text(
              text = "%06d".format(score),
              fontFamily = FontFamily.Monospace,
              fontSize = 20.sp,
              fontWeight = FontWeight.Bold,
              color = RetroLcdGreen
            )
          }

          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              text = "RANK",
              style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
              color = SpaceCadetAccentYellow.copy(alpha = 0.7f),
              fontWeight = FontWeight.SemiBold
            )
            Text(
              text = rank,
              fontFamily = FontFamily.Monospace,
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = SpaceCadetAccentYellow
            )
          }

          Column(horizontalAlignment = Alignment.End) {
            Text(
              text = "BALLS",
              style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
              color = SpaceCadetAccentCyan.copy(alpha = 0.7f),
              fontWeight = FontWeight.SemiBold
            )
            Text(
              text = "$ballsLeft / 3",
              fontFamily = FontFamily.Monospace,
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold,
              color = SpaceCadetAccentCyan
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Main Pinball Table Canvas
      BoxWithConstraints(
        modifier = Modifier
          .fillMaxWidth()
          .aspectRatio(0.72f)
          .clip(RoundedCornerShape(14.dp))
          .background(SpaceCadetBg)
          .pointerInput(Unit) {
            detectTapGestures(
              onPress = { offset ->
                val isLeft = offset.x < size.width * 0.5f
                if (isLeft) leftFlipperActive = true else rightFlipperActive = true
                tryAwaitRelease()
                if (isLeft) leftFlipperActive = false else rightFlipperActive = false
              }
            )
          }
      ) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()

        Canvas(modifier = Modifier.fillMaxSize()) {
          // 1. Starfield background & table lane markings
          val starOffsets = listOf(
            Offset(w * 0.2f, h * 0.15f),
            Offset(w * 0.7f, h * 0.12f),
            Offset(w * 0.35f, h * 0.22f),
            Offset(w * 0.55f, h * 0.32f),
            Offset(w * 0.15f, h * 0.50f),
            Offset(w * 0.8f, h * 0.45f),
            Offset(w * 0.4f, h * 0.58f),
            Offset(w * 0.65f, h * 0.62f)
          )
          starOffsets.forEach { pos ->
            drawCircle(Color.White.copy(alpha = 0.4f), radius = 1.8f, center = pos)
          }

          // Top Arch Deflector Path
          val archPath = Path().apply {
            moveTo(w * 0.06f, h * 0.25f)
            cubicTo(
              w * 0.06f, h * 0.06f,
              w * 0.94f, h * 0.06f,
              w * 0.94f, h * 0.25f
            )
          }
          drawPath(
            path = archPath,
            color = SpaceCadetAccentCyan.copy(alpha = 0.5f),
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
          )

          // Right Plunger Lane Partition Line
          drawLine(
            color = SpaceCadetAccentCyan.copy(alpha = 0.45f),
            start = Offset(w * 0.88f, h * 0.25f),
            end = Offset(w * 0.88f, h * 0.92f),
            strokeWidth = 2.dp.toPx()
          )

          // Angled Guide Rails above flippers
          drawLine(
            color = SpaceCadetAccentPurple.copy(alpha = 0.6f),
            start = Offset(w * 0.06f, h * 0.65f),
            end = Offset(w * 0.20f, h * 0.82f),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round
          )
          drawLine(
            color = SpaceCadetAccentPurple.copy(alpha = 0.6f),
            start = Offset(w * 0.88f, h * 0.65f),
            end = Offset(w * 0.74f, h * 0.82f),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round
          )

          // 2. Space Cadet Attack Bumpers
          bumpers.forEach { bumper ->
            val bx = bumper.cxRel * w
            val by = bumper.cyRel * h
            val br = bumper.radiusRel * w

            val isFlashing = activeBumperHit == bumper.id && (System.currentTimeMillis() - bumperFlashTime < 180)
            val ringColor = if (isFlashing) Color.White else bumper.color

            // Bumper Outer Glow
            drawCircle(
              brush = Brush.radialGradient(
                colors = listOf(ringColor.copy(alpha = 0.4f), Color.Transparent),
                center = Offset(bx, by),
                radius = br * 1.6f
              ),
              radius = br * 1.6f,
              center = Offset(bx, by)
            )

            // Bumper Body
            drawCircle(
              color = Color(0xFF1E293B),
              radius = br,
              center = Offset(bx, by)
            )
            drawCircle(
              color = ringColor,
              radius = br,
              center = Offset(bx, by),
              style = Stroke(width = 3.dp.toPx())
            )
            // Center Core
            drawCircle(
              color = ringColor,
              radius = br * 0.35f,
              center = Offset(bx, by)
            )
          }

          // 3. Dual Responsive Flippers
          // Left Flipper Pivot at (w * 0.20f, h * 0.83f)
          val leftPivot = Offset(w * 0.20f, h * 0.83f)
          val flipperLength = w * 0.21f
          val lAngle = leftFlipperAngle.value
          val leftTip = Offset(
            leftPivot.x + flipperLength * cos(lAngle),
            leftPivot.y + flipperLength * sin(lAngle)
          )
          drawLine(
            color = SpaceCadetAccentYellow,
            start = leftPivot,
            end = leftTip,
            strokeWidth = 6.dp.toPx(),
            cap = StrokeCap.Round
          )
          drawCircle(Color.White, radius = 5.dp.toPx(), center = leftPivot)

          // Right Flipper Pivot at (w * 0.74f, h * 0.83f)
          val rightPivot = Offset(w * 0.74f, h * 0.83f)
          val rAngle = Math.PI.toFloat() + rightFlipperAngle.value
          val rightTip = Offset(
            rightPivot.x + flipperLength * cos(rAngle),
            rightPivot.y + flipperLength * sin(rAngle)
          )
          drawLine(
            color = SpaceCadetAccentYellow,
            start = rightPivot,
            end = rightTip,
            strokeWidth = 6.dp.toPx(),
            cap = StrokeCap.Round
          )
          drawCircle(Color.White, radius = 5.dp.toPx(), center = rightPivot)

          // 4. Plunger Spring at bottom of right lane
          val springY = h * 0.88f
          drawLine(
            color = SpaceCadetGreen,
            start = Offset(w * 0.93f, springY),
            end = Offset(w * 0.93f, springY + 18.dp.toPx()),
            strokeWidth = 4.dp.toPx()
          )

          // 5. Metallic Chrome Pinball
          val px = ballX * w
          val py = ballY * h
          val ballRadius = 8.dp.toPx()

          // Shadow
          drawCircle(
            color = Color.Black.copy(alpha = 0.5f),
            radius = ballRadius * 1.15f,
            center = Offset(px + 2f, py + 2f)
          )
          // Chrome Gradient Ball
          drawCircle(
            brush = Brush.radialGradient(
              colors = listOf(Color.White, Color(0xFFCBD5E1), Color(0xFF475569)),
              center = Offset(px - 2.5f, py - 2.5f),
              radius = ballRadius
            ),
            radius = ballRadius,
            center = Offset(px, py)
          )
        }

        // Game Over Banner Overlay
        if (gameOver) {
          Surface(
            modifier = Modifier
              .fillMaxSize()
              .background(Color.Black.copy(alpha = 0.75f)),
            color = Color.Transparent
          ) {
            Column(
              modifier = Modifier.fillMaxSize(),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center
            ) {
              Text(
                text = "MISSION ACCOMPLISHED",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = SpaceCadetAccentCyan
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "FINAL SCORE: %d".format(score),
                fontFamily = FontFamily.Monospace,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = RetroLcdGreen
              )
              Spacer(modifier = Modifier.height(14.dp))
              Button(
                onClick = { launchBall() },
                colors = ButtonDefaults.buttonColors(containerColor = SpaceCadetAccentCyan),
                shape = RoundedCornerShape(12.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.Refresh,
                  contentDescription = null,
                  tint = Color.Black,
                  modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("PLAY AGAIN", color = Color.Black, fontWeight = FontWeight.Bold)
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Ergonomic Thumb Action Controls
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        // Left Flipper Button
        Button(
          onClick = {},
          modifier = Modifier
            .weight(1f)
            .height(50.dp)
            .pointerInput(Unit) {
              detectTapGestures(
                onPress = {
                  leftFlipperActive = true
                  haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                  playSound(ToneGenerator.TONE_PROP_BEEP)
                  tryAwaitRelease()
                  leftFlipperActive = false
                }
              )
            }
            .testTag("pinball_left_flipper"),
          shape = RoundedCornerShape(12.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = if (leftFlipperActive) SpaceCadetAccentYellow else Color(0xFF1E293B)
          ),
          border = BorderStroke(1.dp, SpaceCadetAccentYellow.copy(alpha = 0.5f))
        ) {
          Text(
            text = "◄ LEFT",
            fontWeight = FontWeight.Bold,
            color = if (leftFlipperActive) Color.Black else Color.White
          )
        }

        // Launch Plunger Button
        Button(
          onClick = { launchBall() },
          modifier = Modifier
            .weight(1.1f)
            .height(50.dp)
            .testTag("pinball_launch_button"),
          shape = RoundedCornerShape(12.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = SpaceCadetGreen
          )
        ) {
          Text(
            text = if (gameStarted) "NUDGE 🚀" else "LAUNCH 🚀",
            fontWeight = FontWeight.Bold,
            color = Color.Black
          )
        }

        // Right Flipper Button
        Button(
          onClick = {},
          modifier = Modifier
            .weight(1f)
            .height(50.dp)
            .pointerInput(Unit) {
              detectTapGestures(
                onPress = {
                  rightFlipperActive = true
                  haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                  playSound(ToneGenerator.TONE_PROP_BEEP)
                  tryAwaitRelease()
                  rightFlipperActive = false
                }
              )
            }
            .testTag("pinball_right_flipper"),
          shape = RoundedCornerShape(12.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = if (rightFlipperActive) SpaceCadetAccentYellow else Color(0xFF1E293B)
          ),
          border = BorderStroke(1.dp, SpaceCadetAccentYellow.copy(alpha = 0.5f))
        ) {
          Text(
            text = "RIGHT ►",
            fontWeight = FontWeight.Bold,
            color = if (rightFlipperActive) Color.Black else Color.White
          )
        }
      }
    }
  }
}
