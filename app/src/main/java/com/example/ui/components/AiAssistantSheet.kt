package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.PageActionItemsResult
import com.example.ai.PageAnswerResult
import com.example.ai.PageInsightsResult
import com.example.ai.PageSummaryResult
import com.example.ai.SummaryMode
import com.example.ui.BrowserViewModel
import com.example.ui.theme.FeatherMintDark
import com.example.ui.theme.FeatherSecureGreen
import com.example.ui.theme.KaiAiCyan
import com.example.ui.theme.KaiAiIndigo
import com.example.ui.theme.KaiAiPurple

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AiAssistantSheet(
  summary: PageSummaryResult?,
  actionItems: PageActionItemsResult? = null,
  insights: PageInsightsResult? = null,
  ttsState: BrowserViewModel.TtsUiState? = null,
  chatQas: List<PageAnswerResult>,
  isLoading: Boolean,
  onModeSelected: (SummaryMode) -> Unit,
  onAskQuestion: (String) -> Unit,
  onReadAloud: () -> Unit = {},
  onStopReadAloud: () -> Unit = {},
  onSetSpeechRate: (Float) -> Unit = {},
  onDismiss: () -> Unit
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var questionText by remember { mutableStateOf("") }
  var selectedMode by remember { mutableStateOf(SummaryMode.EXPLAIN_SIMPLY) }
  var selectedTab by remember { mutableIntStateOf(0) }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp)
        .navigationBarsPadding()
        .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      // Modern Glass Header
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(36.dp)
              .clip(RoundedCornerShape(10.dp))
              .background(
                Brush.linearGradient(listOf(KaiAiPurple, KaiAiIndigo, KaiAiCyan))
              ),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = FeatherIcons.Sparkle,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(20.dp)
            )
          }
          Spacer(modifier = Modifier.width(10.dp))
          Column {
            Text(
              text = "Kai AI Intelligence",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(6.dp)
                  .clip(CircleShape)
                  .background(FeatherSecureGreen)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "On-Device · Private · Zero Cloud Leak",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }
        IconButton(onClick = onDismiss) {
          Icon(Icons.Filled.Close, contentDescription = "Close")
        }
      }

      // Modern Navigation Tabs
      TabRow(
        selectedTabIndex = selectedTab,
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
      ) {
        Tab(
          selected = selectedTab == 0,
          onClick = { selectedTab = 0 },
          text = { Text("Summary", style = MaterialTheme.typography.labelMedium) }
        )
        Tab(
          selected = selectedTab == 1,
          onClick = { selectedTab = 1 },
          text = { Text("Actions", style = MaterialTheme.typography.labelMedium) }
        )
        Tab(
          selected = selectedTab == 2,
          onClick = { selectedTab = 2 },
          text = { Text("Insights", style = MaterialTheme.typography.labelMedium) }
        )
        Tab(
          selected = selectedTab == 3,
          onClick = { selectedTab = 3 },
          text = { Text("Ask", style = MaterialTheme.typography.labelMedium) }
        )
      }

      // TAB 0: SUMMARY & READ ALOUD
      if (selectedTab == 0) {
        // Summary Mode Chips
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          SummaryMode.entries.forEach { mode ->
            FilterChip(
              selected = selectedMode == mode,
              onClick = {
                selectedMode = mode
                onModeSelected(mode)
              },
              label = { Text(mode.label, style = MaterialTheme.typography.labelSmall) }
            )
          }
        }

        // Summary Content Card
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(16.dp),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
          )
        ) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
              modifier = Modifier.fillMaxWidth()
            ) {
              Text(
                text = "Key Takeaways",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
              )
              if (summary != null) {
                Text(
                  text = "⚡ ${summary.readingTimeMinutes} min read",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }

            if (isLoading && summary == null) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 12.dp)
              ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Analyzing page content with local model...", style = MaterialTheme.typography.bodySmall)
              }
            } else {
              Text(
                text = summary?.summaryText ?: "Open any web article to generate a summary.",
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 22.sp
              )
            }
          }
        }

        // Modern Text-To-Speech (Read Aloud) Bar
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(14.dp),
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
          )
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Column {
                Text(
                  text = "Read Aloud",
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.Bold
                )
                Text(
                  text = if (ttsState?.isPlaying == true) "Playing via on-device audio" else "Listen to summary",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
              if (ttsState?.isPlaying == true) {
                Button(
                  onClick = onStopReadAloud,
                  colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                  Text("Stop", style = MaterialTheme.typography.labelSmall)
                }
              } else {
                Button(
                  onClick = onReadAloud,
                  colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                  Text("Play", style = MaterialTheme.typography.labelSmall)
                }
              }
            }
          }
        }
      }

      // TAB 1: ACTION ITEMS & TAKEAWAYS
      if (selectedTab == 1) {
        val items = actionItems?.actionItems ?: listOf("Analyze a page to identify actionable steps.")
        val takeaways = actionItems?.keyTakeaways ?: emptyList()
        val entities = actionItems?.keyEntities ?: emptyList()

        Text(
          text = "🎯 Actionable Next Steps",
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary
        )

        items.forEach { step ->
          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            )
          ) {
            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
              Text("✓", fontWeight = FontWeight.Bold, color = FeatherSecureGreen)
              Spacer(modifier = Modifier.width(8.dp))
              Text(step, style = MaterialTheme.typography.bodySmall)
            }
          }
        }

        if (takeaways.isNotEmpty()) {
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = "📊 Key Findings & Metrics",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
          )
          takeaways.forEach { takeaway ->
            Card(
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(12.dp),
              colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
              )
            ) {
              Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
                Text("•", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(takeaway, style = MaterialTheme.typography.bodySmall)
              }
            }
          }
        }

        if (entities.isNotEmpty()) {
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = "🏷️ Key Topics & Entities",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
          )
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            entities.forEach { entity ->
              Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
              ) {
                Text(
                  text = entity,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                  style = MaterialTheme.typography.labelSmall
                )
              }
            }
          }
        }
      }

      // TAB 2: PAGE INSIGHTS & TONE
      if (selectedTab == 2) {
        val pageScore = insights?.readingEaseScore ?: 70
        val readingLevel = insights?.readingLevel ?: "Standard"
        val tone = insights?.toneLabel ?: "Objective & Factual"
        val wordCount = insights?.wordCount ?: 0

        // Readability Card
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(14.dp),
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
          )
        ) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
              modifier = Modifier.fillMaxWidth()
            ) {
              Text("📖 Readability Score", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
              Text("$pageScore / 100", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            LinearProgressIndicator(
              progress = { (pageScore / 100f).coerceIn(0f, 1f) },
              modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
              color = if (pageScore >= 60) FeatherSecureGreen else FeatherMintDark
            )
            Row(
              horizontalArrangement = Arrangement.SpaceBetween,
              modifier = Modifier.fillMaxWidth()
            ) {
              Text("Level: $readingLevel", style = MaterialTheme.typography.labelSmall)
              Text("Words: $wordCount", style = MaterialTheme.typography.labelSmall)
            }
          }
        }

        // Tone Card
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(14.dp),
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
          )
        ) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("⚖️ Tone & Bias Analysis", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = if (tone.contains("Sensational")) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
            ) {
              Text(
                text = tone,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
              )
            }
            Text(
              text = "Heuristically verified using on-device linguistic sentiment scoring.",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }

      // TAB 3: GROUNDED ASK THIS PAGE
      if (selectedTab == 3) {
        OutlinedTextField(
          value = questionText,
          onValueChange = { questionText = it },
          placeholder = { Text("Ask a question about this page...") },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          trailingIcon = {
            IconButton(
              onClick = {
                if (questionText.isNotBlank()) {
                  onAskQuestion(questionText)
                  questionText = ""
                }
              }
            ) {
              Icon(Icons.Filled.Search, contentDescription = "Ask")
            }
          },
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
          keyboardActions = KeyboardActions(
            onSearch = {
              if (questionText.isNotBlank()) {
                onAskQuestion(questionText)
                questionText = ""
              }
            }
          ),
          modifier = Modifier.fillMaxWidth()
        )

        // Suggestion Sparks
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          listOf("Who wrote this?", "What is the main finding?", "Summarize conclusions").forEach { prompt ->
            Surface(
              shape = RoundedCornerShape(20.dp),
              border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
              color = MaterialTheme.colorScheme.surface,
              modifier = Modifier.clickable { onAskQuestion(prompt) }
            ) {
              Text(
                text = prompt,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelSmall
              )
            }
          }
        }

        // Conversation Q&A history
        chatQas.reversed().forEach { qa ->
          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
          ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(
                text = "Q: ${qa.question}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
              )
              Text(
                text = qa.answer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))
    }
  }
}
