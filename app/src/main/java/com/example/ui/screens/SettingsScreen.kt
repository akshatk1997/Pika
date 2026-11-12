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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.ClearDataOptions
import com.example.model.SearchEngine
import com.example.model.SettingsState
import com.example.model.SitePermissionRule
import com.example.model.ThemeMode
import com.example.security.UrlValidator
import com.example.ui.components.FeatherIcons
import com.example.ui.theme.FeatherSecureGreen
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
  settings: SettingsState,
  sitePermissions: List<SitePermissionRule>,
  onUpdateSettings: ((SettingsState) -> SettingsState) -> Unit,
  onRemovePermissionRule: (SitePermissionRule) -> Unit,
  onClearBrowsingData: (ClearDataOptions) -> Unit,
  onExportBookmarks: () -> Unit,
  onImportBookmarks: () -> Unit,
  onClose: () -> Unit
) {
  BackHandler { onClose() }
  var homepageDraft by remember(settings.homepageUrl) { mutableStateOf(settings.homepageUrl) }
  var customSearchDraft by remember(settings.customSearchTemplate) { mutableStateOf(settings.customSearchTemplate) }
  var showClearDialog by remember { mutableStateOf(false) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.settings_title)) },
        navigationIcon = {
          IconButton(
            onClick = onClose,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("settings_back_button")
          ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
          }
        }
      )
    }
  ) { padding ->
    LazyColumn(
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier.fillMaxSize().padding(padding)
    ) {
      item {
        Card(
          shape = RoundedCornerShape(20.dp),
          border = BorderStroke(1.dp, FeatherSecureGreen.copy(alpha = 0.35f)),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Surface(
              shape = RoundedCornerShape(14.dp),
              color = FeatherSecureGreen.copy(alpha = 0.14f),
              modifier = Modifier.size(44.dp)
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  FeatherIcons.Shield,
                  contentDescription = null,
                  tint = FeatherSecureGreen,
                  modifier = Modifier.size(22.dp)
                )
              }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Pika Hardened Shell",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
              )
              Text(
                text = "Keystore AES-256 encrypted settings · Zero telemetry · System WebView",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }
      }

      item {
        SettingsSectionCard("Search & Homepage", Modifier.widthIn(max = 720.dp)) {
          Text("Default search engine", style = MaterialTheme.typography.titleMedium)
          Spacer(modifier = Modifier.height(6.dp))
          SearchEngine.entries.forEach { engine ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .clickable { onUpdateSettings { it.copy(searchEngine = engine) } },
              verticalAlignment = Alignment.CenterVertically
            ) {
              RadioButton(
                selected = settings.searchEngine == engine,
                onClick = { onUpdateSettings { it.copy(searchEngine = engine) } }
              )
              Text(engine.label, style = MaterialTheme.typography.bodyLarge)
            }
          }
          if (settings.searchEngine == SearchEngine.CUSTOM) {
            OutlinedTextField(
              value = customSearchDraft,
              onValueChange = { customSearchDraft = it },
              label = { Text("HTTPS search template (use %s)") },
              singleLine = true,
              isError = !UrlValidator.isValidCustomSearchTemplate(customSearchDraft),
              modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedButton(
              onClick = {
                if (UrlValidator.isValidCustomSearchTemplate(customSearchDraft)) {
                  onUpdateSettings { it.copy(customSearchTemplate = customSearchDraft.trim()) }
                }
              }
            ) { Text("Save custom template") }
          }
          Spacer(modifier = Modifier.height(10.dp))
          OutlinedTextField(
            value = homepageDraft,
            onValueChange = { homepageDraft = it },
            label = { Text("Homepage URL (blank = local Start Page)") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
          )
          Spacer(modifier = Modifier.height(8.dp))
          OutlinedButton(
            shape = RoundedCornerShape(12.dp),
            onClick = {
              val cleaned = UrlValidator.sanitizeControlChars(homepageDraft)
              if (cleaned.isEmpty() || UrlValidator.isSafeWebUrl(cleaned)) {
                onUpdateSettings { it.copy(homepageUrl = cleaned) }
              }
            }
          ) { Text("Save homepage") }
        }
      }

      item {
        SettingsSectionCard("Privacy & Security (Default-On)", Modifier.widthIn(max = 720.dp)) {
          SettingSwitchRow(
            title = "HTTPS-Only Mode",
            subtitle = "Upgrade HTTP URLs to HTTPS and warn before unencrypted HTTP connections.",
            checked = settings.httpsOnlyEnabled,
            tag = "setting_https_only",
            onCheckedChange = { v -> onUpdateSettings { it.copy(httpsOnlyEnabled = v) } }
          )
          HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
          SettingSwitchRow(
            title = "Block Third-Party Cookies",
            subtitle = "Prevent cross-site tracking cookies in standard tabs.",
            checked = settings.blockThirdPartyCookies,
            tag = "setting_block_3p_cookies",
            onCheckedChange = { v -> onUpdateSettings { it.copy(blockThirdPartyCookies = v) } }
          )
          HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
          SettingSwitchRow(
            title = "Tracker & Ad Network Blocker",
            subtitle = "Block known third-party analytics and ad trackers at request level.",
            checked = settings.trackerBlockingEnabled,
            tag = "setting_tracker_blocker",
            onCheckedChange = { v -> onUpdateSettings { it.copy(trackerBlockingEnabled = v) } }
          )
          HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
          SettingSwitchRow(
            title = "Record Browsing History",
            subtitle = "Disable to stop saving visited pages locally even in standard tabs.",
            checked = settings.historyEnabled,
            tag = "setting_history_enabled",
            onCheckedChange = { v -> onUpdateSettings { it.copy(historyEnabled = v) } }
          )
          HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
          SettingSwitchRow(
            title = "Do Not Track / Global Privacy Control",
            subtitle = stringResource(R.string.settings_dnt_note),
            checked = settings.doNotTrackEnabled,
            tag = "setting_dnt",
            onCheckedChange = { v -> onUpdateSettings { it.copy(doNotTrackEnabled = v) } }
          )
          HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
          SettingSwitchRow(
            title = "JavaScript Execution",
            subtitle = "Disable for maximum attack-surface reduction on untrusted sites.",
            checked = settings.javascriptEnabled,
            tag = "setting_js",
            onCheckedChange = { v -> onUpdateSettings { it.copy(javascriptEnabled = v) } }
          )
        }
      }

      item {
        SettingsSectionCard("Appearance & Zoom", Modifier.widthIn(max = 720.dp)) {
          Text("Theme", style = MaterialTheme.typography.titleMedium)
          Spacer(modifier = Modifier.height(8.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { mode ->
              FilterChip(
                selected = settings.themeMode == mode,
                onClick = { onUpdateSettings { it.copy(themeMode = mode) } },
                label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }
              )
            }
          }
          Spacer(modifier = Modifier.height(12.dp))
          Text("Page text zoom: ${settings.textZoomPercent}%", style = MaterialTheme.typography.titleMedium)
          Slider(
            value = settings.textZoomPercent.toFloat(),
            onValueChange = { raw ->
              val snapped = ((raw / 5f).roundToInt() * 5).coerceIn(75, 200)
              onUpdateSettings { it.copy(textZoomPercent = snapped) }
            },
            valueRange = 75f..200f
          )
        }
      }

      if (sitePermissions.isNotEmpty()) {
        item {
          SettingsSectionCard("Saved Site Permissions", Modifier.widthIn(max = 720.dp)) {
            sitePermissions.forEach { rule ->
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(rule.origin, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                  Text(
                    "${rule.kind.name}: ${if (rule.allowed) "Allowed" else "Blocked"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
                IconButton(
                  onClick = { onRemovePermissionRule(rule) },
                  modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                  Icon(Icons.Filled.Delete, contentDescription = "Revoke permission for ${rule.origin}")
                }
              }
            }
          }
        }
      }

      item {
        SettingsSectionCard("Bookmarks & Browsing Data", Modifier.widthIn(max = 720.dp)) {
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
              onClick = onImportBookmarks,
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.weight(1f).minimumInteractiveComponentSize()
            ) { Text(stringResource(R.string.bookmarks_import)) }
            OutlinedButton(
              onClick = onExportBookmarks,
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.weight(1f).minimumInteractiveComponentSize()
            ) { Text(stringResource(R.string.bookmarks_export)) }
          }
          Spacer(modifier = Modifier.height(10.dp))
          Button(
            onClick = { showClearDialog = true },
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("settings_clear_data_button")
          ) {
            Icon(Icons.Filled.Delete, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.settings_clear_data))
          }
        }
      }

      item {
        SettingsSectionCard("Engine & Network Transparency", Modifier.widthIn(max = 720.dp)) {
          Text(
            text = stringResource(R.string.settings_engine_note),
            style = MaterialTheme.typography.bodyMedium
          )
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = "Permissions declared: INTERNET (page loading & user-approved downloads) and ACCESS_NETWORK_STATE (offline detection). Zero analytics, zero crash reporting, zero background services.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }
  }

  if (showClearDialog) {
    var clearHistory by remember { mutableStateOf(true) }
    var clearCookies by remember { mutableStateOf(true) }
    var clearCache by remember { mutableStateOf(true) }
    var clearPerms by remember { mutableStateOf(false) }

    AlertDialog(
      onDismissRequest = { showClearDialog = false },
      title = { Text(stringResource(R.string.settings_clear_data)) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          ClearOptionRow("Browsing history", clearHistory) { clearHistory = it }
          ClearOptionRow("Cookies & site storage", clearCookies) { clearCookies = it }
          ClearOptionRow("Cached files", clearCache) { clearCache = it }
          ClearOptionRow("Saved site permissions", clearPerms) { clearPerms = it }
        }
      },
      confirmButton = {
        TextButton(
          onClick = {
            showClearDialog = false
            onClearBrowsingData(
              ClearDataOptions(
                clearHistory = clearHistory,
                clearCookies = clearCookies,
                clearCache = clearCache,
                clearSitePermissions = clearPerms
              )
            )
          }
        ) { Text("Clear") }
      },
      dismissButton = {
        TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
      }
    )
  }
}

@Composable
private fun ClearOptionRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 2.dp)
  ) {
    Checkbox(checked = checked, onCheckedChange = onChange)
    Text(label, style = MaterialTheme.typography.bodyMedium)
  }
}

@Composable
private fun SettingsSectionCard(
  title: String,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit
) {
  Card(
    shape = RoundedCornerShape(20.dp),
    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    modifier = modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(18.dp)) {
      Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary
      )
      Spacer(modifier = Modifier.height(10.dp))
      content()
    }
  }
}

@Composable
private fun SettingSwitchRow(
  title: String,
  subtitle: String,
  checked: Boolean,
  tag: String,
  onCheckedChange: (Boolean) -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .minimumInteractiveComponentSize()
      .clickable { onCheckedChange(!checked) }
      .padding(vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
      Text(title, style = MaterialTheme.typography.titleMedium)
      Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      modifier = Modifier.testTag(tag)
    )
  }
}
