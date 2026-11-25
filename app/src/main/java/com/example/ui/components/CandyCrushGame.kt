package com.example.ui.components

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.abs
import kotlin.random.Random

// Candy Types
enum class CandyType(val color: Color, val label: String) {
  RED(Color(0xFFEF4444), "🍬"),
  YELLOW(Color(0xFFFBBF24), "🍋"),
  GREEN(Color(0xFF10B981), "🍏"),
  PURPLE(Color(0xFFA855F7), "🍇"),
  ORANGE(Color(0xFFF97316), "🍊"),
  BLUE(Color(0xFF06B6D4), "💎"),
  RAINBOW(Color(0xFFEC4899), "🌟")
}

private const val GRID_ROWS = 6
private const val GRID_COLS = 6
private const val INITIAL_MOVES = 22
private const val TARGET_SCORE_1_STAR = 3000
private const val TARGET_SCORE_2_STARS = 7500
private const val TARGET_SCORE_3_STARS = 15000

@Composable
fun CandyCrushDialog(
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
        .testTag("candy_crush_dialog"),
      color = Color.Transparent
    ) {
      Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
      ) {
        CandyCrushScreen(onClose = onDismiss)
      }
    }
  }
}

@Composable
fun CandyCrushScreen(
  onClose: () -> Unit
) {
  val haptic = LocalHapticFeedback.current
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

  fun playTone(type: Int) {
    if (toneGen != null) {
      try {
        toneGen.startTone(type, 40)
      } catch (_: Throwable) {}
    }
  }

  // 6x6 grid state
  val grid = remember {
    mutableStateListOf<CandyType>().apply {
      val baseTypes = listOf(
        CandyType.RED,
        CandyType.YELLOW,
        CandyType.GREEN,
        CandyType.PURPLE,
        CandyType.ORANGE,
        CandyType.BLUE
      )
      repeat(GRID_ROWS * GRID_COLS) {
        add(baseTypes[Random.nextInt(baseTypes.size)])
      }
    }
  }

  var score by remember { mutableIntStateOf(0) }
  var movesLeft by remember { mutableIntStateOf(INITIAL_MOVES) }
  var selectedIndex by remember { mutableStateOf<Int?>(null) }
  var gameOver by remember { mutableStateOf(false) }
  var gameWon by remember { mutableStateOf(false) }
  var comboCount by remember { mutableIntStateOf(0) }

  fun getIndex(r: Int, c: Int) = r * GRID_COLS + c

  // Match detection & clearance logic
  fun findMatches(): Set<Int> {
    val matched = mutableSetOf<Int>()

    // Horizontal check
    for (r in 0 until GRID_ROWS) {
      var c = 0
      while (c < GRID_COLS) {
        val type = grid[getIndex(r, c)]
        var matchLen = 1
        while (c + matchLen < GRID_COLS && grid[getIndex(r, c + matchLen)] == type) {
          matchLen++
        }
        if (matchLen >= 3) {
          for (i in 0 until matchLen) {
            matched.add(getIndex(r, c + i))
          }
        }
        c += matchLen
      }
    }

    // Vertical check
    for (c in 0 until GRID_COLS) {
      var r = 0
      while (r < GRID_ROWS) {
        val type = grid[getIndex(r, c)]
        var matchLen = 1
        while (r + matchLen < GRID_ROWS && grid[getIndex(r + matchLen, c)] == type) {
          matchLen++
        }
        if (matchLen >= 3) {
          for (i in 0 until matchLen) {
            matched.add(getIndex(r + i, c))
          }
        }
        r += matchLen
      }
    }

    return matched
  }

  // Initial purge of starting matches to ensure a fresh clean board
  LaunchedEffect(Unit) {
    var matches = findMatches()
    val baseTypes = listOf(
      CandyType.RED,
      CandyType.YELLOW,
      CandyType.GREEN,
      CandyType.PURPLE,
      CandyType.ORANGE,
      CandyType.BLUE
    )
    while (matches.isNotEmpty()) {
      for (idx in matches) {
        grid[idx] = baseTypes[Random.nextInt(baseTypes.size)]
      }
      matches = findMatches()
    }
  }

  // Handle candy collapse and refill
  fun collapseAndRefill(matched: Set<Int>) {
    val baseTypes = listOf(
      CandyType.RED,
      CandyType.YELLOW,
      CandyType.GREEN,
      CandyType.PURPLE,
      CandyType.ORANGE,
      CandyType.BLUE
    )

    // Column by column gravity
    for (c in 0 until GRID_COLS) {
      val columnCandies = mutableListOf<CandyType>()
      for (r in GRID_ROWS - 1 downTo 0) {
        val idx = getIndex(r, c)
        if (!matched.contains(idx)) {
          columnCandies.add(grid[idx])
        }
      }
      // Fill remaining at top
      while (columnCandies.size < GRID_ROWS) {
        val isRainbow = Random.nextFloat() < 0.05f
        columnCandies.add(if (isRainbow) CandyType.RAINBOW else baseTypes[Random.nextInt(baseTypes.size)])
      }
      // Re-populate column from bottom to top
      for (r in GRID_ROWS - 1 downTo 0) {
        val candy = columnCandies[GRID_ROWS - 1 - r]
        grid[getIndex(r, c)] = candy
      }
    }
  }

  // Perform Swap and Match
  fun handleCandyTap(index: Int) {
    if (gameOver) return

    val currentSelected = selectedIndex
    if (currentSelected == null) {
      selectedIndex = index
      haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
      playTone(ToneGenerator.TONE_PROP_BEEP)
      return
    }

    if (currentSelected == index) {
      selectedIndex = null
      return
    }

    // Check adjacency
    val r1 = currentSelected / GRID_COLS
    val c1 = currentSelected % GRID_COLS
    val r2 = index / GRID_COLS
    val c2 = index % GRID_COLS

    val isAdjacent = (abs(r1 - r2) == 1 && c1 == c2) || (abs(c1 - c2) == 1 && r1 == r2)

    if (!isAdjacent) {
      selectedIndex = index
      haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
      return
    }

    // Rainbow Bomb Special Action
    if (grid[currentSelected] == CandyType.RAINBOW || grid[index] == CandyType.RAINBOW) {
      val targetColorType = if (grid[currentSelected] == CandyType.RAINBOW) grid[index] else grid[currentSelected]
      val toClear = mutableSetOf<Int>()
      toClear.add(currentSelected)
      toClear.add(index)
      for (i in 0 until grid.size) {
        if (grid[i] == targetColorType) toClear.add(i)
      }
      score += toClear.size * 150
      collapseAndRefill(toClear)
      movesLeft--
      selectedIndex = null
      haptic.performHapticFeedback(HapticFeedbackType.LongPress)
      playTone(ToneGenerator.TONE_PROP_PROMPT)
      if (score >= TARGET_SCORE_1_STAR) gameWon = true
      if (movesLeft <= 0) gameOver = true
      return
    }

    // Standard swap
    val temp = grid[currentSelected]
    grid[currentSelected] = grid[index]
    grid[index] = temp

    val matches = findMatches()
    if (matches.isNotEmpty()) {
      // Valid move!
      val pts = matches.size * 80 + (if (matches.size > 3) (matches.size - 3) * 120 else 0)
      score += pts
      movesLeft--
      collapseAndRefill(matches)

      haptic.performHapticFeedback(HapticFeedbackType.LongPress)
      playTone(ToneGenerator.TONE_PROP_PROMPT)

      // Cascade check
      var cascadeMatches = findMatches()
      var cascadeMultiplier = 1
      while (cascadeMatches.isNotEmpty()) {
        cascadeMultiplier++
        score += cascadeMatches.size * 100 * cascadeMultiplier
        collapseAndRefill(cascadeMatches)
        cascadeMatches = findMatches()
      }

      comboCount = cascadeMultiplier
      if (score >= TARGET_SCORE_1_STAR) gameWon = true
      if (movesLeft <= 0) gameOver = true
      selectedIndex = null
    } else {
      // Revert swap (no match)
      val revert = grid[currentSelected]
      grid[currentSelected] = grid[index]
      grid[index] = revert
      selectedIndex = null
      haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
      playTone(ToneGenerator.TONE_PROP_NACK)
    }
  }

  fun restartGame() {
    val baseTypes = listOf(
      CandyType.RED,
      CandyType.YELLOW,
      CandyType.GREEN,
      CandyType.PURPLE,
      CandyType.ORANGE,
      CandyType.BLUE
    )
    for (i in 0 until grid.size) {
      grid[i] = baseTypes[Random.nextInt(baseTypes.size)]
    }
    var matches = findMatches()
    while (matches.isNotEmpty()) {
      for (idx in matches) {
        grid[idx] = baseTypes[Random.nextInt(baseTypes.size)]
      }
      matches = findMatches()
    }
    score = 0
    movesLeft = INITIAL_MOVES
    selectedIndex = null
    gameOver = false
    gameWon = false
    comboCount = 0
    playTone(ToneGenerator.TONE_PROP_BEEP2)
  }

  val starCount = when {
    score >= TARGET_SCORE_3_STARS -> 3
    score >= TARGET_SCORE_2_STARS -> 2
    score >= TARGET_SCORE_1_STAR -> 1
    else -> 0
  }

  // Infinite pulsing glow for selected candy
  val infiniteTransition = rememberInfiniteTransition(label = "candy_glow")
  val pulseScale by infiniteTransition.animateFloat(
    initialValue = 0.94f,
    targetValue = 1.08f,
    animationSpec = infiniteRepeatable(
      animation = tween(400, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "pulse_scale"
  )

  Card(
    modifier = Modifier
      .fillMaxWidth(0.96f)
      .widthIn(max = 440.dp)
      .padding(vertical = 12.dp)
      .testTag("candy_crush_card"),
    shape = RoundedCornerShape(22.dp),
    colors = CardDefaults.cardColors(containerColor = Color(0xFF140D22)),
    border = BorderStroke(1.5.dp, Color(0xFFEC4899).copy(alpha = 0.75f))
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(14.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      // Top Sweet Candy Bar Header
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFFEC4899).copy(alpha = 0.22f),
            border = BorderStroke(1.dp, Color(0xFFEC4899))
          ) {
            Text(
              text = "🍬 MATCH-3",
              style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
              fontWeight = FontWeight.Bold,
              color = Color(0xFFEC4899),
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
            )
          }
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Kio Candy Crush",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
          )
        }

        IconButton(
          onClick = onClose,
          modifier = Modifier
            .size(34.dp)
            .testTag("candy_close_button")
        ) {
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Close Candy Crush",
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(20.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Scoreboard & Moves Pill
      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF201335),
        border = BorderStroke(1.dp, Color(0xFFA855F7).copy(alpha = 0.45f))
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          // Score & Stars
          Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = "SCORE: ",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f),
                fontWeight = FontWeight.Bold
              )
              Text(
                text = "%d".format(score),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFBBF24),
                fontFamily = FontFamily.Monospace
              )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
              repeat(3) { starIndex ->
                Icon(
                  imageVector = Icons.Default.Star,
                  contentDescription = null,
                  tint = if (starIndex < starCount) Color(0xFFFBBF24) else Color.White.copy(alpha = 0.25f),
                  modifier = Modifier.size(16.dp)
                )
              }
            }
          }

          // Moves Left Pill
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (movesLeft <= 5) Color(0xFFEF4444).copy(alpha = 0.3f) else Color(0xFF10B981).copy(alpha = 0.25f),
            border = BorderStroke(1.dp, if (movesLeft <= 5) Color(0xFFEF4444) else Color(0xFF10B981))
          ) {
            Column(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Text(
                text = "MOVES",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = Color.White.copy(alpha = 0.75f),
                fontWeight = FontWeight.Bold
              )
              Text(
                text = "$movesLeft",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontFamily = FontFamily.Monospace
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      // 6x6 Candy Grid Table
      BoxWithConstraints(
        modifier = Modifier
          .fillMaxWidth()
          .aspectRatio(1f)
          .clip(RoundedCornerShape(16.dp))
          .background(Color(0xFF1B102C))
          .padding(8.dp)
          .testTag("candy_grid_container")
      ) {
        val totalWidth = maxWidth
        val cellSize = (totalWidth - 10.dp) / GRID_COLS

        Column(
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.SpaceBetween
        ) {
          for (r in 0 until GRID_ROWS) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              for (c in 0 until GRID_COLS) {
                val idx = getIndex(r, c)
                val candy = grid[idx]
                val isSelected = selectedIndex == idx

                Box(
                  modifier = Modifier
                    .size(cellSize)
                    .padding(2.5.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                      if (isSelected) Color(0xFFEC4899).copy(alpha = 0.35f) else Color(0xFF291A42)
                    )
                    .scale(if (isSelected) pulseScale else 1f)
                    .clickable(
                      interactionSource = remember { MutableInteractionSource() },
                      indication = null
                    ) {
                      handleCandyTap(idx)
                    }
                    .testTag("candy_cell_${r}_${c}"),
                  contentAlignment = Alignment.Center
                ) {
                  // Vibrant Candy Glyph & Graphic
                  Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                    val w = size.width
                    val h = size.height

                    // Radial Glossy Candy Shape
                    drawCircle(
                      brush = Brush.radialGradient(
                        colors = listOf(
                          Color.White.copy(alpha = 0.85f),
                          candy.color,
                          candy.color.copy(alpha = 0.7f)
                        ),
                        center = Offset(w * 0.35f, h * 0.35f),
                        radius = w * 0.45f
                      ),
                      radius = w * 0.42f,
                      center = center
                    )

                    // Outline Ring
                    drawCircle(
                      color = if (isSelected) Color.White else candy.color.copy(alpha = 0.5f),
                      radius = w * 0.42f,
                      center = center,
                      style = Stroke(width = if (isSelected) 2.5.dp.toPx() else 1.2.dp.toPx())
                    )
                  }

                  Text(
                    text = candy.label,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                  )
                }
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Bottom Controls & Action Bar
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Shuffle / Restart Button
        Button(
          onClick = { restartGame() },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E1C4E)),
          shape = RoundedCornerShape(12.dp),
          border = BorderStroke(1.dp, Color(0xFFA855F7).copy(alpha = 0.6f)),
          modifier = Modifier.testTag("candy_restart_button")
        ) {
          Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(16.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text("SHUFFLE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }

        // Combo Multiplier Banner
        if (comboCount > 1) {
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFFF59E0B).copy(alpha = 0.25f),
            border = BorderStroke(1.dp, Color(0xFFF59E0B))
          ) {
            Text(
              text = "COMBO x$comboCount! 🔥",
              color = Color(0xFFF59E0B),
              fontWeight = FontWeight.Bold,
              fontSize = 12.sp,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
          }
        }
      }

      // Game Over / Victory Modal
      if (gameOver) {
        Spacer(modifier = Modifier.height(12.dp))
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = Color(0xFF22113A),
          border = BorderStroke(1.5.dp, if (gameWon) Color(0xFF10B981) else Color(0xFFEF4444)),
          modifier = Modifier.fillMaxWidth().testTag("candy_game_over_card")
        ) {
          Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Text(
              text = if (gameWon) "🎉 SWEET VICTORY! 🎉" else "OUT OF MOVES! 🍬",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = if (gameWon) Color(0xFF10B981) else Color(0xFFEF4444)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "Final Score: %d (%d Stars)".format(score, starCount),
              color = Color.White,
              fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(10.dp))
            Button(
              onClick = { restartGame() },
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC4899)),
              shape = RoundedCornerShape(12.dp)
            ) {
              Text("PLAY AGAIN", color = Color.White, fontWeight = FontWeight.Bold)
            }
          }
        }
      }
    }
  }
}
