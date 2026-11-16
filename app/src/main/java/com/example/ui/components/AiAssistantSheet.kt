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
import androidx.compose.material3.ScrollableTabRow
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
import com.example.ai.FactItem
import com.example.ai.GlossaryTerm
import com.example.ai.PageActionItemsResult
import com.example.ai.PageAnswerResult
import com.example.ai.PageFactsResult
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
  facts: PageFactsResult? = null,
  glossary: List<GlossaryTerm> = emptyList(),
  ttsState: BrowserViewModel.TtsUiState? = null,
  chatQas: List<PageAnswerResult>,
  isLoading: Boolean,
  onModeSelected: (SummaryMode) -> Unit,
  onAskQuestion: (String) -> Unit,
  onReadAloud: () -> Unit = {},
  onStopReadAloud: () -> Unit = {},
  onSetSpeechRate: (Float) -> Unit = {},
  onCopyText: (String) -> Unit = {},
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
      verticalArrangement = Arrangement.spacedBy(12.dp)
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
              .size(38.dp)
              .clip(RoundedCornerShape(12.dp))
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
          Spacer(modifier = Modifier.width(12.dp))
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
                text = "100% On-Device · Private · Zero Cloud Leak",
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

      // Quick Reading Overview Banner
      Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = "⏱️ ${summary?.readingTimeMinutes ?: insights?.estimatedReadTimeMinutes ?: 1} min read",
              style = MaterialTheme.typography.labelSmall
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
              text = "📝 ${insights?.wordCount ?: 0} words",
              style = MaterialTheme.typography.labelSmall
            )
          }
          Text(
            text = insights?.readingLevel?.substringBefore(" ") ?: "Standard",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
          )
        }
      }

      // Modern Navigation Tabs
      ScrollableTabRow(
        selectedTabIndex = selectedTab,
        edgePadding = 0.dp,
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        divider = {},
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(14.dp))
      ) {
        val tabTitles = listOf("Summary", "Actions", "Facts", "Glossary", "Insights", "Ask")
        tabTitles.forEachIndexed { index, title ->
          Tab(
            selected = selectedTab == index,
            onClick = { selectedTab = index },
            text = {
              Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
              )
            }
          )
        }
      }

      // TAB 0: SUMMARY & READ ALOUD
      if (selectedTab == 0) {
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
                text = selectedMode.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
              )
              if (summary != null) {
                IconButton(
                  onClick = { onCopyText(summary.summaryText) },
                  modifier = Modifier.size(28.dp)
                ) {
                  Icon(FeatherIcons.Copy, contentDescription = "Copy Summary", modifier = Modifier.size(16.dp))
                }
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
          Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = FeatherIcons.Play,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                  Text(
                    text = "Read Aloud (TTS)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                  )
                  Text(
                    text = if (ttsState?.isPlaying == true) "Playing on-device audio" else "Listen to summary",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }

              Row(verticalAlignment = Alignment.CenterVertically) {
                if (ttsState?.isPlaying == true) {
                  Button(
                    onClick = onStopReadAloud,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                  ) {
                    Text("Stop", style = MaterialTheme.typography.labelSmall)
                  }
                } else {
                  Button(
                    onClick = onReadAloud,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                  ) {
                    Text("Play", style = MaterialTheme.typography.labelSmall)
                  }
                }
              }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text("Speed:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              val currentRate = ttsState?.speechRate ?: 1.0f
              listOf(0.75f, 1.0f, 1.25f, 1.5f).forEach { rate ->
                FilterChip(
                  selected = kotlin.math.abs(currentRate - rate) < 0.05f,
                  onClick = { onSetSpeechRate(rate) },
                  label = { Text("${rate}x", style = MaterialTheme.typography.labelSmall) }
                )
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
            text = "📊 Key Findings & Takeaways",
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

      // TAB 2: FACTS & KEY CLAIMS (NEW)
      if (selectedTab == 2) {
        val factItems = facts?.facts ?: emptyList()
        val timeline = facts?.dates ?: emptyList()
        val citations = facts?.citations ?: emptyList()

        Text(
          text = "📊 Hard Numbers & Statistical Claims",
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary
        )

        if (factItems.isEmpty()) {
          Text(
            text = "No statistical numbers detected on this page.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        } else {
          factItems.forEach { fact ->
            Card(
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(12.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            ) {
              Column(modifier = Modifier.padding(10.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                  ) {
                    Text(
                      text = fact.value,
                      style = MaterialTheme.typography.labelSmall,
                      fontWeight = FontWeight.Bold,
                      color = MaterialTheme.colorScheme.onPrimaryContainer,
                      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                  }
                  IconButton(
                    onClick = { onCopyText(fact.context) },
                    modifier = Modifier.size(24.dp)
                  ) {
                    Icon(FeatherIcons.Copy, contentDescription = "Copy", modifier = Modifier.size(14.dp))
                  }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = fact.context, style = MaterialTheme.typography.bodySmall)
              }
            }
          }
        }

        if (timeline.isNotEmpty()) {
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = "🗓️ Timeline & Dates",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
          )
          timeline.forEach { dt ->
            Card(
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(10.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            ) {
              Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
                Text("📅", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.width(8.dp))
                Text(dt, style = MaterialTheme.typography.bodySmall)
              }
            }
          }
        }

        if (citations.isNotEmpty()) {
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = "📜 Research Citations",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
          )
          citations.forEach { cite ->
            Card(
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(10.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            ) {
              Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
                Text("🔗", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.width(8.dp))
                Text(cite, style = MaterialTheme.typography.bodySmall)
              }
            }
          }
        }
      }

      // TAB 3: KEY TERMS & GLOSSARY (NEW)
      if (selectedTab == 3) {
        Text(
          text = "📖 Technical Terms & Jargon Explainer",
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary
        )

        if (glossary.isEmpty()) {
          Text(
            text = "No specialized acronyms or terminology detected on this page.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        } else {
          glossary.forEach { item ->
            Card(
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(12.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            ) {
              Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Surface(
                  shape = RoundedCornerShape(6.dp),
                  color = MaterialTheme.colorScheme.primaryContainer
                ) {
                  Text(
                    text = item.term,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                  )
                }
                Text(
                  text = item.definition,
                  style = MaterialTheme.typography.bodySmall,
                  fontWeight = FontWeight.Medium
                )
                if (item.context.isNotBlank()) {
                  Text(
                    text = "Context: \"${item.context}\"",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
            }
          }
        }
      }

      // TAB 4: PAGE INSIGHTS & TONE
      if (selectedTab == 4) {
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
            Text("⚖️ Tone & Sentiment Analysis", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
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

      // TAB 5: GROUNDED ASK THIS PAGE
      if (selectedTab == 5) {
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
          listOf("Who is mentioned?", "What is the main finding?", "Summarize conclusions").forEach { prompt ->
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
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = "Q: ${qa.question}",
                  style = MaterialTheme.typography.bodySmall,
                  fontWeight = FontWeight.SemiBold,
                  color = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.weight(1f)
                )
                IconButton(
                  onClick = { onCopyText(qa.answer) },
                  modifier = Modifier.size(24.dp)
                ) {
                  Icon(FeatherIcons.Copy, contentDescription = "Copy", modifier = Modifier.size(13.dp))
                }
              }
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
