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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
  settings: SettingsState,
  currentUrl: String = "",
  sitePermissions: List<SitePermissionRule>,
  onUpdateSettings: ((SettingsState) -> SettingsState) -> Unit,
  onAddCustomBlockedHost: (String) -> Boolean,
  onRemoveCustomBlockedHost: (String) -> Unit,
  onRemovePermissionRule: (SitePermissionRule) -> Unit,
  onClearBrowsingData: (ClearDataOptions) -> Unit,
  onExportBookmarks: () -> Unit,
  onImportBookmarks: () -> Unit,
  onShowMessage: (String) -> Unit,
  onClose: () -> Unit
) {
  BackHandler { onClose() }
  var homepageDraft by remember(settings.homepageUrl) { mutableStateOf(settings.homepageUrl) }
  var homepageError by remember { mutableStateOf(false) }
  var customSearchDraft by remember(settings.customSearchTemplate) { mutableStateOf(settings.customSearchTemplate) }
  var customBlockDraft by remember { mutableStateOf("") }
  var customBlockError by remember { mutableStateOf(false) }
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
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
              )
              Text(
                text = "Version ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }
      }

      item {
        SettingsSectionCard("General", Modifier.widthIn(max = 720.dp)) {
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
            val saveCustomSearch = {
              if (UrlValidator.isValidCustomSearchTemplate(customSearchDraft)) {
                onUpdateSettings { it.copy(customSearchTemplate = customSearchDraft.trim()) }
                onShowMessage("Search URL saved")
              } else {
                onShowMessage("Enter an https:// URL containing %s")
              }
            }
            OutlinedTextField(
              value = customSearchDraft,
              onValueChange = { customSearchDraft = it },
              label = { Text("Search URL (%s)") },
              singleLine = true,
              isError = !UrlValidator.isValidCustomSearchTemplate(customSearchDraft),
              keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
              keyboardActions = KeyboardActions(onDone = { saveCustomSearch() }),
              modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedButton(onClick = saveCustomSearch) { Text("Save") }
          }
          Spacer(modifier = Modifier.height(8.dp))
          val commitHomepage = { raw: String ->
            val cleaned = UrlValidator.sanitizeControlChars(raw)
            val normalized = if (cleaned.isNotEmpty() && !cleaned.contains("://") && !cleaned.contains(' ') && cleaned.contains('.')) {
              "https://$cleaned"
            } else {
              cleaned
            }
            if (normalized.isEmpty() || (UrlValidator.isSafeWebUrl(normalized) && normalized != "about:blank")) {
              homepageDraft = normalized
              homepageError = false
              onUpdateSettings { it.copy(homepageUrl = normalized) }
              onShowMessage(if (normalized.isEmpty()) "Homepage set to Start page" else "Homepage saved")
            } else {
              homepageError = true
              onShowMessage("Enter a valid URL")
            }
          }
          OutlinedTextField(
            value = homepageDraft,
            onValueChange = {
              homepageDraft = it
              homepageError = false
            },
            label = { Text("Homepage") },
            placeholder = { Text("Start page") },
            isError = homepageError,
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { commitHomepage(homepageDraft) }),
            modifier = Modifier.fillMaxWidth().testTag("setting_homepage_input")
          )
          Spacer(modifier = Modifier.height(8.dp))
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            OutlinedButton(
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.testTag("setting_homepage_save"),
              onClick = { commitHomepage(homepageDraft) }
            ) { Text("Save") }
            if (currentUrl.isNotBlank() && UrlValidator.isSafeWebUrl(currentUrl) && currentUrl != "about:blank") {
              OutlinedButton(
                shape = RoundedCornerShape(12.dp),
                onClick = { commitHomepage(currentUrl) }
              ) { Text("Use current") }
            }
            if (homepageDraft.isNotBlank() || settings.homepageUrl.isNotBlank()) {
              TextButton(
                onClick = { commitHomepage("") }
              ) { Text("Reset") }
            }
          }
          HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
          SettingSwitchRow(
            title = "Retain open web pages",
            subtitle = "Keep open tabs and pages across restarts",
            checked = settings.retainOpenTabs,
            tag = "setting_retain_tabs",
            onCheckedChange = { v -> onUpdateSettings { it.copy(retainOpenTabs = v) } }
          )
        }
      }

      item {
        SettingsSectionCard("Appearance", Modifier.widthIn(max = 720.dp)) {
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
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            SettingSwitchRow(
              title = "Dynamic theme",
              subtitle = "Use wallpaper colors",
              checked = settings.dynamicColor,
              tag = "setting_dynamic_color",
              onCheckedChange = { v -> onUpdateSettings { it.copy(dynamicColor = v) } }
            )
          }
          HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
          SettingSwitchRow(
            title = "Dark mode for sites",
            subtitle = "Invert light web pages",
            checked = settings.nightWebMode,
            tag = "setting_night_web",
            onCheckedChange = { v -> onUpdateSettings { it.copy(nightWebMode = v) } }
          )
          HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
          SettingSwitchRow(
            title = "Block images",
            subtitle = "Save data on slow connections",
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
        SettingsSectionCard("Privacy & Security", Modifier.widthIn(max = 720.dp)) {
          SettingSwitchRow(
            title = "HTTPS-Only mode",
            subtitle = "Warn before unencrypted connections",
            checked = settings.httpsOnlyEnabled,
            tag = "setting_https_only",
            onCheckedChange = { v -> onUpdateSettings { it.copy(httpsOnlyEnabled = v) } }
          )
          HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
          SettingSwitchRow(
            title = "Block third-party cookies",
            subtitle = "Limit cross-site tracking",
            checked = settings.blockThirdPartyCookies,
            tag = "setting_block_3p_cookies",
            onCheckedChange = { v -> onUpdateSettings { it.copy(blockThirdPartyCookies = v) } }
          )
          HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
          SettingSwitchRow(
            title = "Block ads & trackers",
            subtitle = "Filter known ad and analytics domains",
            checked = settings.trackerBlockingEnabled,
            tag = "setting_tracker_blocker",
            onCheckedChange = { v -> onUpdateSettings { it.copy(trackerBlockingEnabled = v) } }
          )
          HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
          SettingSwitchRow(
            title = "Save history",
            subtitle = "Keep visited pages on device",
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
            subtitle = "Allow sites to run scripts",
            checked = settings.javascriptEnabled,
            tag = "setting_js",
            onCheckedChange = { v -> onUpdateSettings { it.copy(javascriptEnabled = v) } }
          )
          HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
          Text("Blocked domains", style = MaterialTheme.typography.titleMedium)
          Spacer(modifier = Modifier.height(6.dp))
          Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
              value = customBlockDraft,
              onValueChange = {
                customBlockDraft = it
                customBlockError = false
              },
              placeholder = { Text("ads.example.com") },
              isError = customBlockError,
              singleLine = true,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.weight(1f).testTag("custom_block_input")
            )
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
              onClick = {
                if (onAddCustomBlockedHost(customBlockDraft)) {
                  customBlockDraft = ""
                  customBlockError = false
                } else {
                  customBlockError = true
                  onShowMessage("Enter a valid domain")
                }
              },
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.minimumInteractiveComponentSize().testTag("custom_block_add")
            ) {
              Icon(Icons.Filled.Add, contentDescription = "Add blocked domain")
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
          SettingsSectionCard("Site permissions", Modifier.widthIn(max = 720.dp)) {
            sitePermissions.forEach { rule ->
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(rule.origin, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                  Text(
                    "${rule.kind.name.lowercase().replace('_', ' ')}: ${if (rule.allowed) "Allowed" else "Blocked"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
                IconButton(
                  onClick = { onRemovePermissionRule(rule) },
                  modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                  Icon(Icons.Filled.Delete, contentDescription = "Remove ${rule.origin}")
                }
              }
            }
          }
        }
      }

      item {
        SettingsSectionCard("Data", Modifier.widthIn(max = 720.dp)) {
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
