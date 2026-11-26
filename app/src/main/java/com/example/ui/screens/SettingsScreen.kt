package com.example.ui.screens

import android.content.Intent
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.R
import com.example.model.AppUpdateInfo
import com.example.model.ClearDataOptions
import com.example.model.OneHandedPreference
import com.example.model.ScreenFilterType
import com.example.model.SearchEngine
import com.example.model.SettingsState
import com.example.model.SitePermissionRule
import com.example.model.ThemeMode
import com.example.security.UrlValidator
import com.example.ui.components.FeatherIcons
import com.example.ui.theme.FeatherMintDark
import com.example.ui.theme.FeatherPrivateAccent
import com.example.ui.theme.FeatherSecureGreen
import com.example.ui.theme.FeatherWarningAmber
import kotlin.math.roundToInt

private enum class SettingsFolder(val title: String) {
  GENERAL("General & Search"),
  APPEARANCE("Appearance & Display"),
  PRIVACY("Privacy & Security"),
  DATA_SAVER("Data Saver & Bandwidth"),
  MEDIA("Media & Playback"),
  TABS("Tabs & Session"),
  STORAGE("Data & Storage"),
  HELP_FEEDBACK("Help & Feedback"),
  ABOUT("About Kio")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
  settings: SettingsState,
  currentUrl: String = "",
  isPrivateMode: Boolean = false,
  systemDarkTheme: Boolean? = null,
  sitePermissions: List<SitePermissionRule>,
  systemWebViewInfo: String? = null,
  onUpdateSettings: ((SettingsState) -> SettingsState) -> Unit,
  onAddCustomBlockedHost: (String) -> Boolean,
  onRemoveCustomBlockedHost: (String) -> Unit,
  onRemovePermissionRule: (SitePermissionRule) -> Unit,
  onClearBrowsingData: (ClearDataOptions) -> Unit,
  onExportBookmarks: () -> Unit,
  onImportBookmarks: () -> Unit,
  onCopyText: (String, String) -> Unit = { _, _ -> },
  onShowMessage: (String) -> Unit,
  availableUpdate: AppUpdateInfo? = null,
  onCheckForUpdates: () -> Unit = {},
  onDownloadUpdate: (String, String) -> Unit = { _, _ -> },
  onClose: () -> Unit
) {
  var activeFolder by remember { mutableStateOf<SettingsFolder?>(null) }

  LaunchedEffect(Unit) {
    if (availableUpdate == null) {
      onCheckForUpdates()
    }
  }

  BackHandler {
    if (activeFolder != null) {
      activeFolder = null
    } else {
      onClose()
    }
  }

  var homepageDraft by remember(settings.homepageUrl) { mutableStateOf(settings.homepageUrl) }
  var homepageError by remember { mutableStateOf(false) }
  var customSearchDraft by remember(settings.customSearchTemplate) { mutableStateOf(settings.customSearchTemplate) }
  var customBlockDraft by remember { mutableStateOf("") }
  var customBlockError by remember { mutableStateOf(false) }
  var showClearDialog by remember { mutableStateOf(false) }
  var infoDialogContent by remember { mutableStateOf<Pair<String, String>?>(null) }

  val context = LocalContext.current
  val effectiveSystemDark = systemDarkTheme ?: isSystemInDarkTheme()
  val resolvedDark = when (settings.themeMode) {
    ThemeMode.SYSTEM -> effectiveSystemDark
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
  }

  Scaffold(
    contentWindowInsets = WindowInsets.safeDrawing,
    topBar = {
      TopAppBar(
        title = { Text(activeFolder?.title ?: stringResource(R.string.settings_title)) },
        navigationIcon = {
          IconButton(
            onClick = {
              if (activeFolder != null) {
                activeFolder = null
              } else {
                onClose()
              }
            },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("settings_back_button")
          ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
          }
        }
      )
    }
  ) { padding ->
    key(activeFolder) {
      val scrollState = rememberScrollState()
      Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
          .fillMaxSize()
          .padding(padding)
          .verticalScroll(scrollState)
          .padding(16.dp)
          .testTag("settings_list")
      ) {
      when (activeFolder) {
        null -> {
          // New Update Available Option / Banner Card at the Top of Settings
          if (availableUpdate != null) {
            Card(
              shape = RoundedCornerShape(18.dp),
              border = BorderStroke(1.2.dp, Color(0xFF38BDF8)),
              colors = CardDefaults.cardColors(
                containerColor = if (resolvedDark) Color(0xFF0C1929) else Color(0xFFE0F2FE)
              ),
              modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .clickable {
                  onDownloadUpdate(availableUpdate.downloadUrl, availableUpdate.versionName)
                }
                .testTag("settings_update_available_card")
            ) {
              Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Surface(
                  shape = RoundedCornerShape(12.dp),
                  color = Color(0xFF38BDF8).copy(alpha = 0.2f),
                  modifier = Modifier.size(44.dp)
                ) {
                  Box(contentAlignment = Alignment.Center) {
                    Icon(
                      imageVector = FeatherIcons.Download,
                      contentDescription = "New update available",
                      tint = Color(0xFF38BDF8),
                      modifier = Modifier.size(24.dp)
                    )
                  }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                      text = "New Update Available!",
                      style = MaterialTheme.typography.titleSmall,
                      fontWeight = FontWeight.Bold,
                      color = if (resolvedDark) Color(0xFFF0F9FF) else Color(0xFF0369A1)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                      shape = RoundedCornerShape(6.dp),
                      color = Color(0xFF38BDF8)
                    ) {
                      Text(
                        text = "v${availableUpdate.versionName}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                      )
                    }
                  }
                  Spacer(modifier = Modifier.height(2.dp))
                  Text(
                    text = "Tap to download automatically without hassle",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (resolvedDark) Color(0xFFBAE6FD) else Color(0xFF0284C7)
                  )
                }
                Surface(
                  shape = RoundedCornerShape(12.dp),
                  color = Color(0xFF38BDF8),
                  modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                      onDownloadUpdate(availableUpdate.downloadUrl, availableUpdate.versionName)
                    }
                    .testTag("settings_download_update_button")
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Icon(
                      imageVector = FeatherIcons.Download,
                      contentDescription = "Download",
                      tint = Color.Black,
                      modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                      text = "Update",
                      style = MaterialTheme.typography.labelMedium,
                      fontWeight = FontWeight.Bold,
                      color = Color.Black
                    )
                  }
                }
              }
            }
          }

          SettingsFolderGroupHeader("Browser Settings", Modifier.widthIn(max = 720.dp))

          // Category-wise Folders Card
          Card(
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth()
          ) {
            Column(modifier = Modifier.fillMaxWidth()) {
              val homeSummary = if (settings.homepageUrl.isBlank()) "Start page" else UrlValidator.extractHost(settings.homepageUrl).ifEmpty { "Custom" }
              SettingsFolderRow(
                icon = Icons.Filled.Search,
                iconTint = MaterialTheme.colorScheme.primary,
                title = SettingsFolder.GENERAL.title,
                subtitle = "Search: ${settings.searchEngine.label} · Home: $homeSummary · Modes",
                tag = "settings_folder_general",
                onClick = { activeFolder = SettingsFolder.GENERAL }
              )
              FolderDivider()
              SettingsFolderRow(
                icon = if (resolvedDark) FeatherIcons.NightMoon else FeatherIcons.SunLight,
                iconTint = FeatherMintDark,
                title = SettingsFolder.APPEARANCE.title,
                subtitle = "${if (settings.themeMode == ThemeMode.SYSTEM) "System default" else settings.themeMode.name.lowercase().replaceFirstChar { it.uppercase() }} · ${settings.textZoomPercent}% zoom · Night web",
                tag = "settings_folder_appearance",
                onClick = { activeFolder = SettingsFolder.APPEARANCE }
              )
              FolderDivider()
              val permNote = if (sitePermissions.isNotEmpty()) " · ${sitePermissions.size} rules" else ""
              SettingsFolderRow(
                icon = FeatherIcons.Shield,
                iconTint = FeatherSecureGreen,
                title = SettingsFolder.PRIVACY.title,
                subtitle = "Tracker shield: ${if (settings.trackerBlockingEnabled) "On" else "Off"} · HTTPS-only: ${if (settings.httpsOnlyEnabled) "On" else "Off"}$permNote",
                tag = "settings_folder_privacy",
                onClick = { activeFolder = SettingsFolder.PRIVACY }
              )
              FolderDivider()
              SettingsFolderRow(
                icon = FeatherIcons.DataSaver,
                iconTint = FeatherWarningAmber,
                title = SettingsFolder.DATA_SAVER.title,
                subtitle = "Data saver: ${if (settings.dataSaverEnabled) "On" else "Off"} · Block images: ${if (settings.noImageMode) "On" else "Off"}",
                tag = "settings_folder_data_saver",
                onClick = { activeFolder = SettingsFolder.DATA_SAVER }
              )
              FolderDivider()
              SettingsFolderRow(
                icon = FeatherIcons.Play,
                iconTint = Color(0xFFEC4899),
                title = SettingsFolder.MEDIA.title,
                subtitle = "Background playback: ${if (settings.backgroundPlayEnabled) "On" else "Off"} · WebRTC isolation",
                tag = "settings_folder_media",
                onClick = { activeFolder = SettingsFolder.MEDIA }
              )
              FolderDivider()
              SettingsFolderRow(
                icon = FeatherIcons.OpenInNew,
                iconTint = FeatherPrivateAccent,
                title = SettingsFolder.TABS.title,
                subtitle = if (settings.retainOpenTabs) "Session restore active · Grid switcher" else "Clean session on launch · Low memory",
                tag = "settings_folder_tabs",
                onClick = { activeFolder = SettingsFolder.TABS }
              )
              FolderDivider()
              SettingsFolderRow(
                icon = FeatherIcons.Download,
                iconTint = Color(0xFF6366F1),
                title = SettingsFolder.STORAGE.title,
                subtitle = "HTML bookmark import/export · Granular data manager",
                tag = "settings_folder_storage",
                onClick = { activeFolder = SettingsFolder.STORAGE }
              )
            }
          }

          SettingsFolderGroupHeader("Support & Info", Modifier.widthIn(max = 720.dp))

          // Help & Feedback + About Kio Folders Card
          Card(
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth()
          ) {
            Column(modifier = Modifier.fillMaxWidth()) {
              SettingsFolderRow(
                icon = FeatherIcons.Help,
                iconTint = MaterialTheme.colorScheme.primary,
                title = SettingsFolder.HELP_FEEDBACK.title,
                subtitle = "Guide, gestures, keyboard shortcuts & feedback",
                tag = "settings_folder_help_feedback",
                onClick = { activeFolder = SettingsFolder.HELP_FEEDBACK }
              )
              FolderDivider()
              SettingsFolderRow(
                icon = Icons.Filled.Info,
                iconTint = FeatherSecureGreen,
                title = SettingsFolder.ABOUT.title,
                subtitle = "Kio v${BuildConfig.VERSION_NAME} · Privacy & policies",
                tag = "settings_folder_about",
                onClick = { activeFolder = SettingsFolder.ABOUT }
              )
            }
          }
          Spacer(modifier = Modifier.height(16.dp))
        }

        SettingsFolder.GENERAL -> {
          SettingsSectionCard("Search Engine", Modifier.widthIn(max = 720.dp)) {
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
          }

          SettingsSectionCard("Homepage", Modifier.widthIn(max = 720.dp)) {
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
              label = { Text("Homepage URL") },
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
          }
        }

        SettingsFolder.APPEARANCE -> {
          SettingsSectionCard("Appearance & Display", Modifier.widthIn(max = 720.dp)) {
            Text("Theme", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              ThemeMode.entries.forEach { mode ->
                FilterChip(
                  selected = settings.themeMode == mode,
                  onClick = {
                    onUpdateSettings {
                      when (mode) {
                        ThemeMode.LIGHT -> it.copy(themeMode = ThemeMode.LIGHT, nightWebMode = false)
                        ThemeMode.DARK -> it.copy(themeMode = ThemeMode.DARK, nightWebMode = true)
                        ThemeMode.SYSTEM -> it.copy(themeMode = ThemeMode.SYSTEM, nightWebMode = effectiveSystemDark)
                      }
                    }
                  },
                  label = {
                    Text(
                      if (mode == ThemeMode.SYSTEM) "System default"
                      else mode.name.lowercase().replaceFirstChar { it.uppercase() }
                    )
                  }
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
              title = "Grayscale mode (Black & White)",
              subtitle = "Shifts entire browser screen and web content to pure black and white",
              checked = settings.grayscaleModeEnabled,
              tag = "setting_grayscale_mode",
              onCheckedChange = { v -> onUpdateSettings { it.copy(grayscaleModeEnabled = v) } }
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            SettingSwitchRow(
              title = "One-handed mode",
              subtitle = "Lowers screen viewport for effortless single-thumb surfing",
              checked = settings.oneHandedModeEnabled,
              tag = "setting_one_handed_mode",
              onCheckedChange = { v -> onUpdateSettings { it.copy(oneHandedModeEnabled = v) } }
            )
            if (settings.oneHandedModeEnabled) {
              Spacer(modifier = Modifier.height(6.dp))
              Text("Hand alignment", style = MaterialTheme.typography.bodyMedium)
              Spacer(modifier = Modifier.height(4.dp))
              FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                OneHandedPreference.entries.forEach { pref ->
                  FilterChip(
                    selected = settings.oneHandedHandPreference == pref,
                    onClick = { onUpdateSettings { it.copy(oneHandedHandPreference = pref) } },
                    label = { Text(pref.label) }
                  )
                }
              }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            SettingSwitchRow(
              title = "Clean Mode",
              subtitle = "Distraction-free browsing: automatically hides cookie walls, popups, and sticky banner overlays",
              checked = settings.cleanModeEnabled,
              tag = "setting_clean_mode",
              onCheckedChange = { v -> onUpdateSettings { it.copy(cleanModeEnabled = v) } }
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            SettingSwitchRow(
              title = "Screen filter (eye care & dimmer)",
              subtitle = "Dims screen below system minimum with blue-light reduction",
              checked = settings.screenFilterEnabled,
              tag = "setting_screen_filter",
              onCheckedChange = { v -> onUpdateSettings { it.copy(screenFilterEnabled = v) } }
            )
            if (settings.screenFilterEnabled) {
              Spacer(modifier = Modifier.height(8.dp))
              Text("Filter Intensity: ${(settings.screenFilterOpacity * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
              Slider(
                value = settings.screenFilterOpacity,
                onValueChange = { v -> onUpdateSettings { it.copy(screenFilterOpacity = v) } },
                valueRange = 0.10f..0.80f,
                steps = 14
              )
            }
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

        SettingsFolder.PRIVACY -> {
          SettingsSectionCard("Shield & Tracking Protection", Modifier.widthIn(max = 720.dp)) {
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
              subtitle = "Pure ad-free browsing and video streaming ad shield",
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
          }

          SettingsSectionCard("Blocked Domains", Modifier.widthIn(max = 720.dp)) {
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

          if (sitePermissions.isNotEmpty()) {
            SettingsSectionCard("Site Permissions", Modifier.widthIn(max = 720.dp)) {
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

        SettingsFolder.DATA_SAVER -> {
          SettingsSectionCard("Data Saver & Bandwidth", Modifier.widthIn(max = 720.dp)) {
            SettingSwitchRow(
              title = "Data saver mode",
              subtitle = "Compress web traffic, send Save-Data header, and avoid background data usage",
              checked = settings.dataSaverEnabled,
              tag = "setting_data_saver",
              onCheckedChange = { v -> onUpdateSettings { it.copy(dataSaverEnabled = v) } }
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            SettingSwitchRow(
              title = "Block images",
              subtitle = "Save data on slow connections",
              checked = settings.noImageMode,
              tag = "setting_no_image",
              onCheckedChange = { v -> onUpdateSettings { it.copy(noImageMode = v) } }
            )
          }
        }

        SettingsFolder.MEDIA -> {
          SettingsSectionCard("Media & Playback", Modifier.widthIn(max = 720.dp)) {
            SettingSwitchRow(
              title = "Background stream playback",
              subtitle = "Keep video and audio playing when browser is in background",
              checked = settings.backgroundPlayEnabled,
              tag = "setting_background_play",
              onCheckedChange = { v -> onUpdateSettings { it.copy(backgroundPlayEnabled = v) } }
            )
          }
        }

        SettingsFolder.TABS -> {
          SettingsSectionCard("Tabs & Session", Modifier.widthIn(max = 720.dp)) {
            SettingSwitchRow(
              title = "Retain open web pages",
              subtitle = "Keep open tabs and pages across restarts",
              checked = settings.retainOpenTabs,
              tag = "setting_retain_tabs",
              onCheckedChange = { v -> onUpdateSettings { it.copy(retainOpenTabs = v) } }
            )
          }
        }

        SettingsFolder.STORAGE -> {
          if (!isPrivateMode) {
            SettingsSectionCard("Bookmarks Backup", Modifier.widthIn(max = 720.dp)) {
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
            }
          }

          SettingsSectionCard("Clear Local Data", Modifier.widthIn(max = 720.dp)) {
            Text(
              text = "Erase browsing history, cookies, cached favicons, and saved site permissions stored on this device.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
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

        SettingsFolder.HELP_FEEDBACK -> {
          val faqs = remember {
            listOf(
              "Why is Kio so fast and lightweight?" to
                "Kio is architected with zero tracking bloat, hardware-accelerated 120Hz/60Hz rendering, and direct native memory management — all engineered strictly under 2 MB.",
              "How do I sniff and download videos and media?" to
                "Tap the menu (⋮) → Media to view detected video/audio streams on the current page. You can download the stream, copy its direct URL, or play it in external video players like VLC.",
              "How do I save all images from a webpage?" to
                "Tap the menu (⋮) → Images to scrape all images on the page into an image gallery, choose which ones to keep, and batch download them directly to your Pictures folder.",
              "How do I capture a full page scrolling screenshot?" to
                "Tap the menu (⋮) → Capture to render the entire vertical document into a high-resolution PNG image saved directly to your device's gallery under Pictures/Kio.",
              "What is Screen Filter and Clean Mode?" to
                "Screen Filter dims your display below standard Android brightness with eye-care amber or OLED night dimmer. Clean Mode automatically hides intrusive cookie walls, popup banners, and sticky ad overlays.",
              "How does the Content Announcer / Reader Mode protect my privacy?" to
                "Open the menu (⋮) → Narrate to read and listen to any page with pure on-device NLP narration. Text exists only in volatile RAM while open, cannot be copied or exported, and is wiped immediately when closed.",
              "How do I toggle Full Screen mode?" to
                "Open the menu (⋮) and tap Full screen to hide the toolbar and system bars edge-to-edge. Tap the floating Exit pill or press Back to return.",
              "How do I switch viewing modes?" to
                "Tap the menu button (⋮) and tap Modes to select Desktop, iPhone, or Mobile. Kio instantly adjusts the User-Agent and viewport layout to match your preference.",
              "How does Private Mode keep my browsing safe?" to
                "Private Mode runs isolated from Standard Mode: cookies, cache, and history are never shared or saved. Bookmarks and sharing are disabled for isolation, screenshots are blocked, and all private session data is wiped on exit.",
              "Why are streaming popups and ad redirects blocked?" to
                "Kio intercepts popunder ad redirects (including tsyndicate, ad.* subdomains, and click-hijack overlays) before they can open spam tabs or interrupt video playback."
            )
          }

          SettingsSectionCard("Quick Help & FAQ", Modifier.widthIn(max = 720.dp)) {
            faqs.forEachIndexed { idx, (question, answer) ->
              var expanded by remember(idx) { mutableStateOf(idx == 0) }
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable { expanded = !expanded }
                  .padding(vertical = 6.dp)
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Text(
                    text = question,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                  )
                  Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                  )
                }
                AnimatedVisibility(visible = expanded) {
                  Text(
                    text = answer,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                  )
                }
              }
              if (idx < faqs.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
              }
            }
          }

          var feedbackTopic by remember { mutableStateOf("Bug Report") }
          var feedbackMessage by remember { mutableStateOf("") }
          val diagnosticsSummary = remember(systemWebViewInfo) {
            "Kio v${BuildConfig.VERSION_NAME}\n" +
              "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
              "Device: ${Build.MANUFACTURER} ${Build.MODEL}\n" +
              "Engine: ${systemWebViewInfo ?: "Android System WebView"}"
          }

          SettingsSectionCard("Send Feedback", Modifier.widthIn(max = 720.dp)) {
            Text(
              text = "Share suggestions, report a website issue, or send diagnostic info.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              listOf("Bug Report", "Feature Request", "Website Issue", "General").forEach { topic ->
                FilterChip(
                  selected = feedbackTopic == topic,
                  onClick = { feedbackTopic = topic },
                  label = { Text(topic) }
                )
              }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
              value = feedbackMessage,
              onValueChange = { feedbackMessage = it },
              label = { Text("Describe your feedback") },
              placeholder = { Text("Tell us what happened or what you'd like improved...") },
              minLines = 3,
              maxLines = 5,
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.fillMaxWidth().testTag("feedback_input")
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
              Button(
                onClick = {
                  val fullReport = buildString {
                    append("[$feedbackTopic]\n")
                    append(feedbackMessage.trim().ifEmpty { "(No message entered)" })
                    append("\n\n--- Diagnostics ---\n")
                    append(diagnosticsSummary)
                  }
                  try {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                      type = "text/plain"
                      putExtra(Intent.EXTRA_SUBJECT, "Kio Browser Feedback: $feedbackTopic")
                      putExtra(Intent.EXTRA_TEXT, fullReport)
                      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Send Feedback"))
                  } catch (_: Throwable) {
                    onCopyText(fullReport, "Feedback copied to clipboard")
                  }
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("feedback_send_button")
              ) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Send Feedback")
              }
              OutlinedButton(
                onClick = { onCopyText(diagnosticsSummary, "Diagnostics copied") },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.minimumInteractiveComponentSize().testTag("feedback_copy_diag_button")
              ) {
                Icon(FeatherIcons.Copy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Copy Info")
              }
            }
          }
        }

        SettingsFolder.ABOUT -> {
          Card(
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(0.9.dp, FeatherSecureGreen.copy(alpha = 0.4f)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth()
          ) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(18.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF050810),
                border = BorderStroke(1.2.dp, Color(0xFF38BDF8).copy(alpha = 0.45f)),
                modifier = Modifier.size(72.dp)
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Image(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = "Kio Icon",
                    modifier = Modifier.size(68.dp)
                  )
                }
              }
              Spacer(modifier = Modifier.height(12.dp))
              Text(
                text = "Kio Browser",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = "Version ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Spacer(modifier = Modifier.height(8.dp))
              Text(
                text = "High-performance, privacy-hardened Android web browser with hardware-accelerated rendering and on-device text narration — engineered strictly under 2 MB.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }

          SettingsSectionCard("Software Update", Modifier.widthIn(max = 720.dp)) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = if (availableUpdate != null) "Update Available: v${availableUpdate.versionName}" else "Version ${BuildConfig.VERSION_NAME}",
                  style = MaterialTheme.typography.bodyMedium,
                  fontWeight = FontWeight.Medium
                )
                Text(
                  text = if (availableUpdate != null) "Tap download to start update without hassle" else "Kio is up to date",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
              Button(
                onClick = {
                  if (availableUpdate != null) {
                    onDownloadUpdate(availableUpdate.downloadUrl, availableUpdate.versionName)
                  } else {
                    onCheckForUpdates()
                  }
                },
                colors = ButtonDefaults.buttonColors(
                  containerColor = if (availableUpdate != null) Color(0xFF38BDF8) else MaterialTheme.colorScheme.primaryContainer,
                  contentColor = if (availableUpdate != null) Color.Black else MaterialTheme.colorScheme.onPrimaryContainer
                ),
                shape = RoundedCornerShape(10.dp)
              ) {
                Text(if (availableUpdate != null) "Download" else "Check Now")
              }
            }
          }

          SettingsSectionCard("Policies & Licenses", Modifier.widthIn(max = 720.dp)) {
            AboutPolicyRow("Privacy Policy") {
              infoDialogContent = "Privacy Policy" to
                "Kio collects zero analytics, zero telemetry, and zero personal data. Bookmarks, history, and preferences stay strictly on your device. Content Announcer & Reader Mode processes text exclusively in volatile RAM with zero saving or copying. Private tabs run in RAM, block screenshots, and wipe all cookies, cache, and storage on exit."
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            AboutPolicyRow("Security Architecture") {
              infoDialogContent = "Security Architecture" to
                "• Powered by the OS-maintained Android System WebView with Google Safe Browsing.\n" +
                "• Local settings encrypted via Android Keystore AES-256-GCM.\n" +
                "• Strict URL scheme filtering, intent sanitization, and HTTPS-Only warnings.\n" +
                "• Built-in ad, tracker, popunder, and WebRTC LAN IP leak protection."
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            AboutPolicyRow("Open-Source Components") {
              infoDialogContent = "Open-Source Components" to
                "Kio is built with Kotlin, AndroidX Jetpack Compose, Material 3, and AndroidX Lifecycle under Apache 2.0 licenses, with zero proprietary tracking SDKs or bloated third-party libraries."
            }
          }
        }
      }
      }
    }
  }

  infoDialogContent?.let { (title, body) ->
    AlertDialog(
      onDismissRequest = { infoDialogContent = null },
      title = { Text(title) },
      text = { Text(body, style = MaterialTheme.typography.bodyMedium) },
      confirmButton = {
        TextButton(onClick = { infoDialogContent = null }) { Text("Close") }
      }
    )
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
          ClearOptionRow("Cache & site logos", clearCache) { clearCache = it }
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
private fun SettingsFolderGroupHeader(title: String, modifier: Modifier = Modifier) {
  Text(
    text = title.uppercase(),
    style = MaterialTheme.typography.labelMedium,
    fontWeight = FontWeight.SemiBold,
    color = MaterialTheme.colorScheme.primary,
    modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)
  )
}

@Composable
private fun SettingsFolderRow(
  icon: ImageVector,
  iconTint: Color,
  title: String,
  subtitle: String,
  tag: String,
  onClick: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .testTag(tag)
      .minimumInteractiveComponentSize()
      .clickable(onClick = onClick)
      .padding(horizontal = 14.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Surface(
      shape = RoundedCornerShape(12.dp),
      color = iconTint.copy(alpha = 0.14f),
      modifier = Modifier.size(38.dp)
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = iconTint,
          modifier = Modifier.size(20.dp)
        )
      }
    }
    Spacer(modifier = Modifier.width(14.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
    Spacer(modifier = Modifier.width(8.dp))
    Icon(
      imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(20.dp)
    )
  }
}

@Composable
private fun FolderDivider() {
  HorizontalDivider(
    modifier = Modifier.padding(start = 66.dp, end = 14.dp),
    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
  )
}

@Composable
private fun AboutPolicyRow(label: String, onClick: () -> Unit) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .minimumInteractiveComponentSize()
      .clickable(onClick = onClick)
      .padding(vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.Medium,
      modifier = Modifier.weight(1f)
    )
    Icon(
      imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(18.dp)
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
