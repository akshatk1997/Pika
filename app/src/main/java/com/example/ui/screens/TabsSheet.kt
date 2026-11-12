package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.ClosedTabRecord
import com.example.model.TabInfo
import com.example.ui.components.FeatherIcons
import com.example.ui.theme.FeatherPrivateAccent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabsSheet(
  tabs: List<TabInfo>,
  activeTabId: String,
  closedTabs: List<ClosedTabRecord>,
  onSelectTab: (String) -> Unit,
  onCloseTab: (String) -> Unit,
  onMoveTab: (String, Int) -> Unit,
  onNewTab: (Boolean) -> Unit,
  onRestoreClosedTab: () -> Unit,
  onEndPrivateSession: () -> Unit,
  onCloseSheet: () -> Unit
) {
  BackHandler { onCloseSheet() }

  val activeIsPrivate = tabs.firstOrNull { it.id == activeTabId }?.isPrivate == true
  var showPrivate by remember(activeIsPrivate) { mutableStateOf(activeIsPrivate) }
  var searchQuery by remember { mutableStateOf("") }

  val filteredTabs = remember(tabs, showPrivate, searchQuery) {
    tabs.filter { it.isPrivate == showPrivate }.filter { tab ->
      if (searchQuery.isBlank()) true
      else tab.title.contains(searchQuery, ignoreCase = true) || tab.url.contains(searchQuery, ignoreCase = true)
    }
  }

  Scaffold(
    contentWindowInsets = WindowInsets.safeDrawing,
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.tabs_title)) },
        navigationIcon = {
          IconButton(
            onClick = onCloseSheet,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("tabs_back_button")
          ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
          }
        },
        actions = {
          if (closedTabs.isNotEmpty() && !showPrivate) {
            IconButton(
              onClick = onRestoreClosedTab,
              modifier = Modifier.minimumInteractiveComponentSize().testTag("tabs_restore_button")
            ) {
              Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.tabs_restore))
            }
          }
          IconButton(
            onClick = { onNewTab(showPrivate) },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("tabs_new_button")
          ) {
            Icon(
              Icons.Filled.Add,
              contentDescription = if (showPrivate) stringResource(R.string.action_new_private_tab) else stringResource(R.string.action_new_tab)
            )
          }
        }
      )
    }
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(horizontal = 16.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Column(modifier = Modifier.widthIn(max = 960.dp).fillMaxSize()) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          FilterChip(
            selected = !showPrivate,
            onClick = { showPrivate = false },
            label = { Text("${stringResource(R.string.tabs_normal)} (${tabs.count { !it.isPrivate }})") },
            modifier = Modifier.testTag("tabs_filter_normal")
          )
          FilterChip(
            selected = showPrivate,
            onClick = { showPrivate = true },
            leadingIcon = {
              Icon(FeatherIcons.PrivateMask, contentDescription = null, modifier = Modifier.size(16.dp))
            },
            label = { Text("${stringResource(R.string.tabs_private)} (${tabs.count { it.isPrivate }})") },
            modifier = Modifier.testTag("tabs_filter_private")
          )
          Spacer(modifier = Modifier.weight(1f))
          if (showPrivate && tabs.any { it.isPrivate }) {
            TextButton(
              onClick = onEndPrivateSession,
              modifier = Modifier.testTag("tabs_end_private")
            ) {
              Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text(stringResource(R.string.tabs_close_all_private))
            }
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
          value = searchQuery,
          onValueChange = { searchQuery = it },
          singleLine = true,
          shape = RoundedCornerShape(24.dp),
          leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
          placeholder = { Text(stringResource(R.string.tabs_search_hint)) },
          modifier = Modifier.fillMaxWidth().testTag("tabs_search_input")
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (filteredTabs.isEmpty()) {
          Box(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text(
                text = if (showPrivate) stringResource(R.string.tabs_empty_private) else "No matching tabs",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Spacer(modifier = Modifier.height(12.dp))
              Button(
                onClick = { onNewTab(showPrivate) },
                shape = RoundedCornerShape(14.dp)
              ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (showPrivate) stringResource(R.string.action_new_private_tab) else stringResource(R.string.action_new_tab))
              }
            }
          }
        } else {
          LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 280.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
          ) {
            itemsIndexed(filteredTabs, key = { _, t -> t.id }) { idx, tab ->
              val isSelected = tab.id == activeTabId
              val accent = if (tab.isPrivate) FeatherPrivateAccent else MaterialTheme.colorScheme.primary
              Card(
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(
                  width = if (isSelected) 1.8.dp else 0.8.dp,
                  color = if (isSelected) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                ),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(16.dp))
                  .clickable { onSelectTab(tab.id) }
                  .testTag("tab_card_${tab.id}")
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = accent.copy(alpha = 0.14f),
                    modifier = Modifier.size(38.dp)
                  ) {
                    Box(contentAlignment = Alignment.Center) {
                      if (tab.faviconInitial.isNotEmpty()) {
                        Text(
                          text = tab.faviconInitial,
                          fontWeight = FontWeight.Bold,
                          color = accent
                        )
                      } else {
                        Icon(
                          imageVector = if (tab.isPrivate) FeatherIcons.PrivateMask else FeatherIcons.FeatherLogo,
                          contentDescription = null,
                          tint = accent,
                          modifier = Modifier.size(18.dp)
                        )
                      }
                    }
                  }

                  Spacer(modifier = Modifier.width(12.dp))

                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = tab.title,
                      style = MaterialTheme.typography.titleMedium,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                    Text(
                      text = tab.url.ifBlank { "Local start page" },
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                  }

                  if (filteredTabs.size > 1) {
                    IconButton(
                      onClick = { onMoveTab(tab.id, -1) },
                      enabled = idx > 0,
                      modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                      Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move tab up")
                    }
                    IconButton(
                      onClick = { onMoveTab(tab.id, 1) },
                      enabled = idx < filteredTabs.lastIndex,
                      modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                      Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move tab down")
                    }
                  }

                  IconButton(
                    onClick = { onCloseTab(tab.id) },
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("close_tab_${tab.id}")
                  ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close tab ${tab.title}")
                  }
                }
              }
            }
          }
        }

        if (closedTabs.isNotEmpty() && !showPrivate) {
          OutlinedButton(
            onClick = onRestoreClosedTab,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).minimumInteractiveComponentSize()
          ) {
            Text("${stringResource(R.string.tabs_restore)}: ${closedTabs.first().title}", maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
        }
      }
    }
  }
}
