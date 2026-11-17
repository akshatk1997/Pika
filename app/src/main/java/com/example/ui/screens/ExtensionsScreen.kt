package com.example.ui.screens

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.extensions.ChromeExtensionManager
import com.example.extensions.ExtensionRuntimeHost
import com.example.extensions.InstalledExtension
import com.example.ui.components.FeatherIcons
import com.example.ui.theme.FeatherSecureGreen
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionsScreen(
  extensions: List<InstalledExtension>,
  isInstalling: Boolean,
  extensionManager: ChromeExtensionManager,
  extensionRuntimeHost: ExtensionRuntimeHost? = null,
  activeProxyExtId: String? = null,
  activePageUrl: String,
  activePageTitle: String,
  onInstallFromStoreInput: (String) -> Unit,
  onPickLocalExtensionFile: () -> Unit,
  onOpenChromeWebStore: () -> Unit,
  onToggleEnabled: (String, Boolean) -> Unit,
  onRemoveExtension: (String) -> Unit,
  onOpenUrlInNewTab: (String) -> Unit,
  onBroadcastStorageChange: (String, String) -> Unit = { _, _ -> },
  onSendMessageToActiveTab: (String, String) -> Unit = { _, _ -> },
  onExecuteScriptInActiveTab: (String, String) -> Unit = { _, _ -> },
  onExecuteScriptFileInActiveTab: (String, String) -> Unit = { _, _ -> },
  onInsertCssInActiveTab: (String, String) -> Unit = { _, _ -> },
  onInsertCssFileInActiveTab: (String, String) -> Unit = { _, _ -> },
  onReloadActiveTab: () -> Unit = {},
  onClose: () -> Unit
) {
  BackHandler { onClose() }

  var storeInput by remember { mutableStateOf("") }
  var popupTarget by remember { mutableStateOf<Pair<InstalledExtension, String>?>(null) }

  Scaffold(
    contentWindowInsets = WindowInsets.safeDrawing,
    topBar = {
      TopAppBar(
        title = { Text("Extensions (${extensions.size})") },
        navigationIcon = {
          IconButton(
            onClick = onClose,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("extensions_back_button")
          ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
          }
        },
        actions = {
          TextButton(
            onClick = onPickLocalExtensionFile,
            enabled = !isInstalling,
            modifier = Modifier.testTag("extensions_pick_file_button")
          ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(".crx / .zip")
          }
        }
      )
    }
  ) { padding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding),
      contentAlignment = Alignment.TopCenter
    ) {
      LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
          .widthIn(max = 760.dp)
          .fillMaxSize()
          .testTag("extensions_list")
      ) {
        // Chrome Web Store & Local Package Installer Card
        item {
          Card(
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(0.9.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(16.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                  shape = RoundedCornerShape(12.dp),
                  color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                  modifier = Modifier.size(40.dp)
                ) {
                  Box(contentAlignment = Alignment.Center) {
                    Icon(
                      FeatherIcons.Extension,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(22.dp)
                    )
                  }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "Desktop Chrome Extensions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                  )
                  Text(
                    text = "Install desktop Chrome extensions (.crx, .zip, .user.js) or directly from the Chrome Web Store.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }

              Spacer(modifier = Modifier.height(12.dp))

              OutlinedTextField(
                value = storeInput,
                onValueChange = { storeInput = it },
                label = { Text("Chrome Web Store URL or Extension ID") },
                placeholder = { Text("https://chromewebstore.google.com/detail/...") },
                singleLine = true,
                enabled = !isInstalling,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = {
                  if (storeInput.isNotBlank() && !isInstalling) {
                    onInstallFromStoreInput(storeInput.trim())
                    storeInput = ""
                  }
                }),
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("extensions_store_input")
              )

              Spacer(modifier = Modifier.height(10.dp))

              Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
              ) {
                Button(
                  onClick = {
                    if (storeInput.isNotBlank()) {
                      onInstallFromStoreInput(storeInput.trim())
                      storeInput = ""
                    }
                  },
                  enabled = storeInput.isNotBlank() && !isInstalling,
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("extensions_install_button")
                ) {
                  if (isInstalling) {
                    CircularProgressIndicator(
                      strokeWidth = 2.dp,
                      modifier = Modifier.size(16.dp),
                      color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Installing...")
                  } else {
                    Text("Install")
                  }
                }
                OutlinedButton(
                  onClick = onOpenChromeWebStore,
                  enabled = !isInstalling,
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("extensions_open_webstore_button")
                ) {
                  Icon(FeatherIcons.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Web Store")
                }
              }
            }
          }
        }

        // Installed Extensions Section
        item {
          Text(
            text = "INSTALLED EXTENSIONS",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 4.dp)
          )
        }

        if (extensions.isEmpty()) {
          item {
            Card(
              shape = RoundedCornerShape(16.dp),
              border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              modifier = Modifier.fillMaxWidth()
            ) {
              Text(
                text = "No extensions installed yet. Pick a .crx / .zip / .user.js package, open the Chrome Web Store, or install one from the catalog below.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
              )
            }
          }
        } else {
          items(extensions, key = { it.id }) { ext ->
            InstalledExtensionCard(
              ext = ext,
              isProxyActive = activeProxyExtId == ext.id,
              onToggleEnabled = { onToggleEnabled(ext.id, it) },
              onOpenPopup = { page -> popupTarget = ext to page },
              onRemove = { onRemoveExtension(ext.id) }
            )
          }
        }

        // Popular Desktop Chrome Extensions Catalog (1-tap official CRX install)
        item {
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "POPULAR CHROME EXTENSIONS",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 4.dp)
          )
        }

        items(ChromeExtensionManager.POPULAR_PRESETS, key = { it.id }) { preset ->
          val alreadyInstalled = extensions.any { it.id == preset.id }
          Card(
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier.padding(14.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                modifier = Modifier.size(40.dp)
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(
                    FeatherIcons.Extension,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                  )
                }
              }
              Spacer(modifier = Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = preset.name,
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.SemiBold,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
                Text(
                  text = preset.summary,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis
                )
              }
              Spacer(modifier = Modifier.width(8.dp))
              if (alreadyInstalled) {
                Surface(
                  shape = RoundedCornerShape(10.dp),
                  color = FeatherSecureGreen.copy(alpha = 0.14f)
                ) {
                  Text(
                    text = "Added",
                    style = MaterialTheme.typography.labelSmall,
                    color = FeatherSecureGreen,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                  )
                }
              } else {
                OutlinedButton(
                  onClick = { onInstallFromStoreInput(preset.id) },
                  enabled = !isInstalling,
                  shape = RoundedCornerShape(12.dp),
                  contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                  Text("Add")
                }
              }
            }
          }
        }
      }
    }
  }

  popupTarget?.let { (ext, pagePath) ->
    ExtensionPopupDialog(
      extension = ext,
      relativePagePath = pagePath,
      extensionManager = extensionManager,
      extensionRuntimeHost = extensionRuntimeHost,
      activePageUrl = activePageUrl,
      activePageTitle = activePageTitle,
      onOpenUrlInNewTab = { url ->
        popupTarget = null
        onOpenUrlInNewTab(url)
      },
      onBroadcastStorageChange = onBroadcastStorageChange,
      onSendMessageToActiveTab = onSendMessageToActiveTab,
      onExecuteScriptInActiveTab = onExecuteScriptInActiveTab,
      onExecuteScriptFileInActiveTab = onExecuteScriptFileInActiveTab,
      onInsertCssInActiveTab = onInsertCssInActiveTab,
      onInsertCssFileInActiveTab = onInsertCssFileInActiveTab,
      onReloadActiveTab = onReloadActiveTab,
      onOpenOptionsPage = { optPath ->
        popupTarget = ext to optPath
      },
      onDismiss = { popupTarget = null }
    )
  }
}

@Composable
private fun InstalledExtensionCard(
  ext: InstalledExtension,
  isProxyActive: Boolean = false,
  onToggleEnabled: (Boolean) -> Unit,
  onOpenPopup: (String) -> Unit,
  onRemove: () -> Unit
) {
  val iconBitmap: ImageBitmap? = remember(ext.installDirPath, ext.iconRelativePath) {
    try {
      val rel = ext.iconRelativePath ?: return@remember null
      val f = File(ext.installDirPath, rel.trimStart('/'))
      if (f.isFile) BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap() else null
    } catch (_: Throwable) {
      null
    }
  }

  Card(
    shape = RoundedCornerShape(16.dp),
    border = BorderStroke(
      0.9.dp,
      if (ext.enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
      else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    ),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    modifier = Modifier.fillMaxWidth().testTag("extension_card_${ext.id}")
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = Color(0xFFF1F5F9),
          modifier = Modifier.size(42.dp)
        ) {
          Box(contentAlignment = Alignment.Center) {
            if (iconBitmap != null) {
              Image(
                bitmap = iconBitmap,
                contentDescription = ext.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(30.dp).clip(RoundedCornerShape(6.dp))
              )
            } else {
              Icon(
                FeatherIcons.Extension,
                contentDescription = null,
                tint = Color(0xFF0F172A),
                modifier = Modifier.size(22.dp)
              )
            }
          }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = ext.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Text(
            text = "v${ext.version} · MV${ext.manifestVersion} · ${ext.contentScripts.size} scripts" +
              (if (ext.hasBackgroundRuntime) " · Worker" else "") +
              (if (ext.dnrRulesCount > 0) " · ${ext.dnrRulesCount} rules" else "") +
              (if (isProxyActive) " · Proxy Active" else ""),
            style = MaterialTheme.typography.labelSmall,
            color = if (isProxyActive) FeatherSecureGreen else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
        Switch(
          checked = ext.enabled,
          onCheckedChange = onToggleEnabled
        )
      }

      if (ext.description.isNotBlank()) {
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = ext.description,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis
        )
      }

      Spacer(modifier = Modifier.height(10.dp))
      HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
      Spacer(modifier = Modifier.height(6.dp))

      Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
      ) {
        ext.defaultPopup?.let { popup ->
          OutlinedButton(
            onClick = { onOpenPopup(popup) },
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
          ) {
            Text("Open Popup", style = MaterialTheme.typography.labelMedium)
          }
        }
        ext.optionsPage?.let { opt ->
          OutlinedButton(
            onClick = { onOpenPopup(opt) },
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
          ) {
            Text("Options", style = MaterialTheme.typography.labelMedium)
          }
        }
        Spacer(modifier = Modifier.weight(1f))
        IconButton(
          onClick = onRemove,
          modifier = Modifier.minimumInteractiveComponentSize()
        ) {
          Icon(
            Icons.Filled.Delete,
            contentDescription = "Remove ${ext.name}",
            tint = MaterialTheme.colorScheme.error
          )
        }
      }
    }
  }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ExtensionPopupDialog(
  extension: InstalledExtension,
  relativePagePath: String,
  extensionManager: ChromeExtensionManager,
  extensionRuntimeHost: ExtensionRuntimeHost?,
  activePageUrl: String,
  activePageTitle: String,
  onOpenUrlInNewTab: (String) -> Unit,
  onBroadcastStorageChange: (String, String) -> Unit,
  onSendMessageToActiveTab: (String, String) -> Unit,
  onExecuteScriptInActiveTab: (String, String) -> Unit,
  onExecuteScriptFileInActiveTab: (String, String) -> Unit,
  onInsertCssInActiveTab: (String, String) -> Unit,
  onInsertCssFileInActiveTab: (String, String) -> Unit,
  onReloadActiveTab: () -> Unit,
  onOpenOptionsPage: (String) -> Unit,
  onDismiss: () -> Unit
) {
  val popupUrl = "https://${ChromeExtensionManager.EXT_VIRTUAL_HOST}/${extension.id}/${relativePagePath.trimStart('/')}"
  var popupWebViewRef by remember { mutableStateOf<WebView?>(null) }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Surface(
      shape = RoundedCornerShape(20.dp),
      color = MaterialTheme.colorScheme.surface,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
      modifier = Modifier
        .fillMaxWidth(0.94f)
        .fillMaxHeight(0.84f)
    ) {
      Column(modifier = Modifier.fillMaxSize()) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0A0D14))
            .padding(horizontal = 14.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(
            FeatherIcons.Extension,
            contentDescription = null,
            tint = Color(0xFF38BDF8),
            modifier = Modifier.size(18.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = extension.defaultTitle ?: extension.name,
            style = MaterialTheme.typography.titleSmall,
            color = Color(0xFFF8FAFC),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
          )
          IconButton(onClick = { popupWebViewRef?.reload() }) {
            Icon(Icons.Filled.Refresh, contentDescription = "Reload", tint = Color(0xFFF8FAFC), modifier = Modifier.size(18.dp))
          }
          IconButton(onClick = onDismiss) {
            Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color(0xFFF8FAFC))
          }
        }

        AndroidView(
          factory = { ctx ->
            WebView(ctx).apply {
              popupWebViewRef = this
              settings.javaScriptEnabled = true
              settings.domStorageEnabled = true
              @Suppress("DEPRECATION")
              settings.databaseEnabled = true
              settings.useWideViewPort = true
              settings.loadWithOverviewMode = true
              settings.setSupportZoom(false)
              settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
              settings.allowFileAccess = false
              settings.allowContentAccess = false
              settings.userAgentString = ChromeExtensionManager.DESKTOP_CHROME_UA
              val bridge: Any = extensionRuntimeHost?.createBridgeObject(
                extId = extension.id,
                contextKind = "popup",
                onOpenOptions = onOpenOptionsPage
              ) ?: object {
                @JavascriptInterface
                fun storageGetAll(extId: String): String = extensionManager.readStorageJson(extId)

                @JavascriptInterface
                fun storageSetAll(extId: String, json: String) = extensionManager.writeStorageJson(extId, json)

                @JavascriptInterface
                fun broadcastStorageChange(extId: String, changesJson: String) {
                  post { onBroadcastStorageChange(extId, changesJson) }
                }

                @JavascriptInterface
                fun sendMessageToActiveTab(extId: String, msgJson: String) {
                  post { onSendMessageToActiveTab(extId, msgJson) }
                }

                @JavascriptInterface
                fun executeScriptInActiveTab(extId: String, code: String) {
                  post { onExecuteScriptInActiveTab(extId, code) }
                }

                @JavascriptInterface
                fun executeScriptFileInActiveTab(extId: String, file: String) {
                  post { onExecuteScriptFileInActiveTab(extId, file) }
                }

                @JavascriptInterface
                fun insertCssInActiveTab(extId: String, css: String) {
                  post { onInsertCssInActiveTab(extId, css) }
                }

                @JavascriptInterface
                fun insertCssFileInActiveTab(extId: String, file: String) {
                  post { onInsertCssFileInActiveTab(extId, file) }
                }

                @JavascriptInterface
                fun reloadActiveTab() {
                  post { onReloadActiveTab() }
                }

                @JavascriptInterface
                fun openOptionsPage(extId: String) {
                  val opt = extension.optionsPage ?: return
                  post { onOpenOptionsPage(opt) }
                }

                @JavascriptInterface
                fun openTab(url: String) {
                  post { onOpenUrlInNewTab(url) }
                }

                @JavascriptInterface
                fun getActiveUrl(): String = activePageUrl

                @JavascriptInterface
                fun getActiveTitle(): String = activePageTitle
              }
              addJavascriptInterface(bridge, "__KioExtBridge")
              extensionRuntimeHost?.attachPopupWebView(extension.id, this)
              webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                  view: WebView?,
                  request: WebResourceRequest?
                ): WebResourceResponse? {
                  val url = request?.url?.toString() ?: return null
                  return extensionManager.serveVirtualExtensionResource(url)
                }
              }
              if (extensionRuntimeHost != null && extension.hasBackgroundRuntime) {
                // Ensure extension background service worker / scripts are booted before loading popup UI
                extensionRuntimeHost.ensureBackgroundRunning(extension.id) {
                  loadUrl(popupUrl)
                }
              } else {
                loadUrl(popupUrl)
              }
            }
          },
          onRelease = { wv ->
            extensionRuntimeHost?.detachPopupWebView(extension.id, wv)
            if (popupWebViewRef === wv) popupWebViewRef = null
            try {
              wv.stopLoading()
              wv.destroy()
            } catch (_: Throwable) {
            }
          },
          modifier = Modifier.fillMaxSize()
        )
      }
    }
  }
}
