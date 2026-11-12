package com.example.ui.screens

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material3.InputChip
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
import com.example.BuildConfig
import com.example.R
import com.example.model.ClearDataOptions
import com.example.model.SearchEngine
import com.example.model.SettingsState
import com.example.model.SitePermissionRule
import com.example.model.ThemeMode
import com.example.model.ToolbarPosition
import com.example.security.UrlValidator
import com.example.ui.components.FeatherIcons
import com.example.ui.theme.FeatherSecureGreen
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
  settings: SettingsState,
  sitePermissions: List<SitePermissionRule>,
  onUpdateSettings: ((SettingsState) -> SettingsState) -> Unit,
  onAddCustomBlockedHost: (String) -> Boolean,
  onRemoveCustomBlockedHost: (String) -> Unit,
  onRemovePermissionRule: (SitePermissionRule) -> Unit,
  onClearBrowsingData: (ClearDataOptions) -> Unit,
  onExportBookmarks: () -> Unit,
  onImportBookmarks: () -> Unit,
  onClose: () -> Unit
) {
  BackHandler { onClose() }
  var homepageDraft by remember(settings.homepageUrl) { mutableStateOf(settings.homepageUrl) }
  var customSearchDraft by remember(settings.customSearchTemplate) { mutableStateOf(settings.customSearchTemplate) }
  var customBlockDraft by remember { mutableStateOf("") }
  var showClearDialog by remember { mutableStateOf(false) }

  Scaffold(
    contentWindowInsets = WindowInsets.safeDrawing,
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
      verticalArrangement = Arrangement.spacedBy(12.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier.fillMaxSize().padding(padding)
    ) {
      item {
        Card(
          shape = RoundedCornerShape(18.dp),
          border = BorderStroke(0.9.dp, FeatherSecureGreen.copy(alpha = 0.35f)),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = FeatherSecureGreen.copy(alpha = 0.14f),
              modifier = Modifier.size(40.dp)
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  FeatherIcons.Shield,
                  contentDescription = null,
                  tint = FeatherSecureGreen,
                  modifier = Modifier.size(20.dp)
                )
              }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Bat Privacy Core",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
              )
              Text(
                text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · Encrypted · Zero telemetry",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }
      }

      item {
        SettingsSectionCard("Search & Homepage", Modifier.widthIn(max = 720.dp)) {
          Text("Search engine", style = MaterialTheme.typography.titleMedium)
          Spacer(modifier = Modifier.height(4.dp))
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
              label = { Text("HTTPS template (%s)") },
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
            ) { Text("Save template") }
          }
          Spacer(modifier = Modifier.height(8.dp))
          OutlinedTextField(
            value = homepageDraft,
            onValueChange = { homepageDraft = it },
            label = { Text("Homepage (blank = Start Page)") },
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
        SettingsSectionCard("Layout & Reading", Modifier.widthIn(max = 720.dp)) {
          Text("Theme", style = MaterialTheme.typography.titleMedium)
          Spacer(modifier = Modifier.height(6.dp))
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            ThemeMode.entries.forEach { mode ->
              FilterChip(
                selected = settings.themeMode == mode,
                onClick = { onUpdateSettings { it.copy(themeMode = mode) } },
                label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }
              )
            }
          }
          Spacer(modifier = Modifier.height(10.dp))
          Text("Address bar position", style = MaterialTheme.typography.titleMedium)
          Spacer(modifier = Modifier.height(6.dp))
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            ToolbarPosition.entries.forEach { pos ->
              FilterChip(
                selected = settings.toolbarPosition == pos,
                onClick = { onUpdateSettings { it.copy(toolbarPosition = pos) } },
                label = { Text(pos.label) }
              )
            }
          }
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            SettingSwitchRow(
              title = "Dynamic Color",
              subtitle = "Match wallpaper palette",
              checked = settings.dynamicColor,
              tag = "setting_dynamic_color",
              onCheckedChange = { v -> onUpdateSettings { it.copy(dynamicColor = v) } }
            )
          }
          HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
          SettingSwitchRow(
            title = "Night Web Mode",
            subtitle = "Darken light web pages",
            checked = settings.nightWebMode,
            tag = "setting_night_web",
            onCheckedChange = { v -> onUpdateSettings { it.copy(nightWebMode = v) } }
          )
          HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
          SettingSwitchRow(
            title = "No-Image Mode",
            subtitle = "Block web images to save data",
            checked = settings.noImageMode,
            tag = "setting_no_image",
            onCheckedChange = { v -> onUpdateSettings { it.copy(noImageMode = v) } }
          )
          Spacer(modifier = Modifier.height(10.dp))
          Text("Text zoom: ${settings.textZoomPercent}%", style = MaterialTheme.typography.titleMedium)
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

      item {
        SettingsSectionCard("Privacy & Ad Shield", Modifier.widthIn(max = 720.dp)) {
          SettingSwitchRow(
            title = "HTTPS-Only Mode",
            subtitle = "Upgrade HTTP and warn on unencrypted sites",
            checked = settings.httpsOnlyEnabled,
            tag = "setting_https_only",
            onCheckedChange = { v -> onUpdateSettings { it.copy(httpsOnlyEnabled = v) } }
          )
          HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
          SettingSwitchRow(
            title = "Block 3rd-Party Cookies",
            subtitle = "Stop cross-site tracking cookies",
            checked = settings.blockThirdPartyCookies,
            tag = "setting_block_3p_cookies",
            onCheckedChange = { v -> onUpdateSettings { it.copy(blockThirdPartyCookies = v) } }
          )
          HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
          SettingSwitchRow(
            title = "Ad & Tracker Blocker",
            subtitle = "Block ads, popups & analytics hosts",
            checked = settings.trackerBlockingEnabled,
            tag = "setting_tracker_blocker",
            onCheckedChange = { v -> onUpdateSettings { it.copy(trackerBlockingEnabled = v) } }
          )
          HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
          SettingSwitchRow(
            title = "Save History",
            subtitle = "Keep local history in standard tabs",
            checked = settings.historyEnabled,
            tag = "setting_history_enabled",
            onCheckedChange = { v -> onUpdateSettings { it.copy(historyEnabled = v) } }
          )
          HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
          SettingSwitchRow(
            title = "Do Not Track / GPC",
            subtitle = stringResource(R.string.settings_dnt_note),
            checked = settings.doNotTrackEnabled,
            tag = "setting_dnt",
            onCheckedChange = { v -> onUpdateSettings { it.copy(doNotTrackEnabled = v) } }
          )
          HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
          SettingSwitchRow(
            title = "JavaScript",
            subtitle = "Disable for strict site sandboxing",
            checked = settings.javascriptEnabled,
            tag = "setting_js",
            onCheckedChange = { v -> onUpdateSettings { it.copy(javascriptEnabled = v) } }
          )
          HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
          Text("Custom block rules", style = MaterialTheme.typography.titleMedium)
          Spacer(modifier = Modifier.height(6.dp))
          Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
              value = customBlockDraft,
              onValueChange = { customBlockDraft = it },
              placeholder = { Text("ads.example.com") },
              singleLine = true,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.weight(1f).testTag("custom_block_input")
            )
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
              onClick = {
                if (onAddCustomBlockedHost(customBlockDraft)) {
                  customBlockDraft = ""
                }
              },
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.minimumInteractiveComponentSize().testTag("custom_block_add")
            ) {
              Icon(Icons.Filled.Add, contentDescription = "Add host rule")
            }
          }
          if (settings.customBlockedHosts.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              settings.customBlockedHosts.forEach { host ->
                InputChip(
                  selected = false,
                  onClick = { onRemoveCustomBlockedHost(host) },
                  label = { Text(host) },
                  trailingIcon = {
                    Icon(Icons.Filled.Close, contentDescription = "Remove $host", modifier = Modifier.size(16.dp))
                  }
                )
              }
            }
          }
        }
      }

      if (sitePermissions.isNotEmpty()) {
        item {
          SettingsSectionCard("Site Permissions", Modifier.widthIn(max = 720.dp)) {
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
                  Icon(Icons.Filled.Delete, contentDescription = "Revoke ${rule.origin}")
                }
              }
            }
          }
        }
      }

      item {
        SettingsSectionCard("Data & Bookmarks", Modifier.widthIn(max = 720.dp)) {
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
        Text(
          text = stringResource(R.string.settings_engine_note),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(vertical = 6.dp)
        )
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
          ClearOptionRow("Cookies & site data", clearCookies) { clearCookies = it }
          ClearOptionRow("Cache", clearCache) { clearCache = it }
          ClearOptionRow("Site permissions", clearPerms) { clearPerms = it }
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
    shape = RoundedCornerShape(18.dp),
    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    modifier = modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary
      )
      Spacer(modifier = Modifier.height(8.dp))
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
