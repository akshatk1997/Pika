package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import com.example.model.BookmarkItem
import com.example.model.DownloadRecord
import com.example.model.DownloadStatus
import com.example.model.HistoryItem
import com.example.ui.components.FeatherIcons
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(
  bookmarks: List<BookmarkItem>,
  onSearch: (String) -> Unit,
  onOpenUrl: (String) -> Unit,
  onSaveBookmark: (Long?, String, String, String) -> Unit,
  onDeleteBookmark: (Long) -> Unit,
  onExportBookmarks: () -> Unit,
  onImportBookmarks: () -> Unit,
  onClose: () -> Unit
) {
  BackHandler { onClose() }
  var query by remember { mutableStateOf("") }
  var editingItem by remember { mutableStateOf<BookmarkItem?>(null) }
  var showAddDialog by remember { mutableStateOf(false) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.action_bookmarks)) },
        navigationIcon = {
          IconButton(onClick = onClose, modifier = Modifier.minimumInteractiveComponentSize()) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
          }
        },
        actions = {
          TextButton(onClick = onImportBookmarks, modifier = Modifier.testTag("bookmarks_import_button")) {
            Text(stringResource(R.string.bookmarks_import))
          }
          TextButton(onClick = onExportBookmarks, modifier = Modifier.testTag("bookmarks_export_button")) {
            Text(stringResource(R.string.bookmarks_export))
          }
          IconButton(
            onClick = { showAddDialog = true },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("bookmarks_add_button")
          ) {
            Icon(Icons.Filled.Add, contentDescription = "Add bookmark")
          }
        }
      )
    }
  ) { padding ->
    Column(
      modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Column(modifier = Modifier.widthIn(max = 720.dp).fillMaxSize()) {
        OutlinedTextField(
          value = query,
          onValueChange = {
            query = it
            onSearch(it)
          },
          singleLine = true,
          shape = RoundedCornerShape(24.dp),
          leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
          placeholder = { Text(stringResource(R.string.bookmarks_search_hint)) },
          modifier = Modifier.fillMaxWidth().testTag("bookmarks_search_input")
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (bookmarks.isEmpty()) {
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.home_no_bookmarks), color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        } else {
          LazyColumn(
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            items(bookmarks, key = { it.id }) { bm ->
              Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(14.dp))
                  .clickable {
                    onOpenUrl(bm.url)
                    onClose()
                  }
              ) {
                Row(
                  modifier = Modifier.padding(12.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(
                    FeatherIcons.Bookmark,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                  )
                  Spacer(modifier = Modifier.width(12.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text(bm.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                      text = "${bm.folder} · ${bm.url}",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                  }
                  IconButton(
                    onClick = { editingItem = bm },
                    modifier = Modifier.minimumInteractiveComponentSize()
                  ) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit bookmark ${bm.title}")
                  }
                  IconButton(
                    onClick = { onDeleteBookmark(bm.id) },
                    modifier = Modifier.minimumInteractiveComponentSize()
                  ) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete bookmark ${bm.title}")
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  val targetEdit = editingItem
  if (showAddDialog || targetEdit != null) {
    var titleInput by remember(targetEdit) { mutableStateOf(targetEdit?.title ?: "") }
    var urlInput by remember(targetEdit) { mutableStateOf(targetEdit?.url ?: "https://") }
    var folderInput by remember(targetEdit) { mutableStateOf(targetEdit?.folder ?: "Bookmarks") }

    AlertDialog(
      onDismissRequest = {
        showAddDialog = false
        editingItem = null
      },
      title = { Text(if (targetEdit == null) "Add Bookmark" else "Edit Bookmark") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = titleInput,
            onValueChange = { titleInput = it },
            label = { Text("Title") },
            singleLine = true
          )
          OutlinedTextField(
            value = urlInput,
            onValueChange = { urlInput = it },
            label = { Text("HTTPS URL") },
            singleLine = true
          )
          OutlinedTextField(
            value = folderInput,
            onValueChange = { folderInput = it },
            label = { Text("Folder") },
            singleLine = true
          )
        }
      },
      confirmButton = {
        TextButton(onClick = {
          onSaveBookmark(targetEdit?.id, titleInput, urlInput, folderInput)
          showAddDialog = false
          editingItem = null
        }) { Text("Save") }
      },
      dismissButton = {
        TextButton(onClick = {
          showAddDialog = false
          editingItem = null
        }) { Text("Cancel") }
      }
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
  history: List<HistoryItem>,
  historyEnabled: Boolean,
  onSearch: (String) -> Unit,
  onOpenUrl: (String) -> Unit,
  onDeleteEntry: (Long) -> Unit,
  onClearAll: () -> Unit,
  onClose: () -> Unit
) {
  BackHandler { onClose() }
  var query by remember { mutableStateOf("") }
  val dateFmt = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.action_history)) },
        navigationIcon = {
          IconButton(onClick = onClose, modifier = Modifier.minimumInteractiveComponentSize()) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
          }
        },
        actions = {
          if (history.isNotEmpty()) {
            TextButton(onClick = onClearAll, modifier = Modifier.testTag("history_clear_all_button")) {
              Text(stringResource(R.string.history_clear_all))
            }
          }
        }
      )
    }
  ) { padding ->
    Column(
      modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Column(modifier = Modifier.widthIn(max = 720.dp).fillMaxSize()) {
        if (!historyEnabled) {
          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
          ) {
            Text(
              text = stringResource(R.string.history_disabled_notice),
              style = MaterialTheme.typography.bodyMedium,
              modifier = Modifier.padding(12.dp)
            )
          }
        }
        OutlinedTextField(
          value = query,
          onValueChange = {
            query = it
            onSearch(it)
          },
          singleLine = true,
          shape = RoundedCornerShape(24.dp),
          leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
          placeholder = { Text(stringResource(R.string.history_search_hint)) },
          modifier = Modifier.fillMaxWidth().testTag("history_search_input")
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (history.isEmpty()) {
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No browsing history", color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        } else {
          LazyColumn(
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            items(history, key = { it.id }) { item ->
              Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(14.dp))
                  .clickable {
                    onOpenUrl(item.url)
                    onClose()
                  }
              ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                  Icon(FeatherIcons.History, contentDescription = null, modifier = Modifier.size(20.dp))
                  Spacer(modifier = Modifier.width(12.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                      text = "${dateFmt.format(Date(item.visitedAt))} · ${item.url}",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                  }
                  IconButton(
                    onClick = { onDeleteEntry(item.id) },
                    modifier = Modifier.minimumInteractiveComponentSize()
                  ) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete history item")
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
  downloads: List<DownloadRecord>,
  onCancel: (String) -> Unit,
  onOpen: (DownloadRecord) -> Unit,
  onReveal: (DownloadRecord) -> Unit,
  onClearAll: () -> Unit,
  onClose: () -> Unit
) {
  BackHandler { onClose() }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.action_downloads)) },
        navigationIcon = {
          IconButton(onClick = onClose, modifier = Modifier.minimumInteractiveComponentSize()) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
          }
        },
        actions = {
          if (downloads.isNotEmpty()) {
            TextButton(onClick = onClearAll) { Text(stringResource(R.string.history_clear_all)) }
          }
        }
      )
    }
  ) { padding ->
    Column(
      modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Column(modifier = Modifier.widthIn(max = 720.dp).fillMaxSize()) {
        if (downloads.isEmpty()) {
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.downloads_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        } else {
          LazyColumn(
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            items(downloads, key = { it.id }) { dl ->
              Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
              ) {
                Column(modifier = Modifier.padding(14.dp)) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(FeatherIcons.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                      Text(dl.fileName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                      Text(
                        text = "${dl.status.name} · ${formatBytes(dl.downloadedBytes)}" +
                          if (dl.totalBytes > 0) " / ${formatBytes(dl.totalBytes)}" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                      )
                    }
                    if (dl.status == DownloadStatus.DOWNLOADING) {
                      IconButton(
                        onClick = { onCancel(dl.id) },
                        modifier = Modifier.minimumInteractiveComponentSize()
                      ) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.download_cancel))
                      }
                    }
                  }
                  if (dl.status == DownloadStatus.DOWNLOADING) {
                    Spacer(modifier = Modifier.height(8.dp))
                    if (dl.totalBytes > 0) {
                      LinearProgressIndicator(
                        progress = { (dl.downloadedBytes.toFloat() / dl.totalBytes.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                      )
                    } else {
                      LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                  }
                  if (dl.status == DownloadStatus.COMPLETED) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                      OutlinedButton(
                        onClick = { onOpen(dl) },
                        modifier = Modifier.minimumInteractiveComponentSize()
                      ) { Text(stringResource(R.string.download_open)) }
                      OutlinedButton(
                        onClick = { onReveal(dl) },
                        modifier = Modifier.minimumInteractiveComponentSize()
                      ) { Text(stringResource(R.string.download_reveal)) }
                    }
                  }
                  if (!dl.errorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(dl.errorMessage, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

private fun formatBytes(bytes: Long): String {
  if (bytes < 1024) return "$bytes B"
  val kb = bytes / 1024.0
  if (kb < 1024) return String.format("%.1f KB", kb)
  val mb = kb / 1024.0
  return String.format("%.2f MB", mb)
}
