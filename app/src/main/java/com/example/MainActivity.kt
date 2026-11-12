package com.example

import android.app.SearchManager
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.security.UrlValidator
import com.example.ui.BrowserViewModel
import com.example.ui.screens.BrowserScreen
import com.example.ui.theme.FeatherTheme

class MainActivity : ComponentActivity() {

  private val viewModel: BrowserViewModel by viewModels()

  private val exportBookmarksLauncher = registerForActivityResult(
    ActivityResultContracts.CreateDocument("text/html")
  ) { uri ->
    if (uri != null) {
      try {
        val html = viewModel.exportBookmarksHtml()
        contentResolver.openOutputStream(uri)?.use { out ->
          out.write(html.toByteArray(Charsets.UTF_8))
        }
        viewModel.showToast("Bookmarks exported")
      } catch (_: Throwable) {
        viewModel.showToast("Export failed")
      }
    }
  }

  private val importBookmarksLauncher = registerForActivityResult(
    ActivityResultContracts.OpenDocument()
  ) { uri ->
    if (uri != null) {
      try {
        val raw = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
        viewModel.importBookmarksFromText(raw)
      } catch (_: Throwable) {
        viewModel.showToast("Import failed")
      }
    }
  }

  private val webFileChooserLauncher = registerForActivityResult(
    ActivityResultContracts.OpenMultipleDocuments()
  ) { uris ->
    viewModel.deliverChosenFiles(if (uris.isEmpty()) null else uris.toTypedArray())
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    viewModel.onLaunchFileChooser = { acceptTypes ->
      val mimeTypes = acceptTypes?.filter { it.isNotBlank() }?.toTypedArray()
      webFileChooserLauncher.launch(if (mimeTypes.isNullOrEmpty()) arrayOf("*/*") else mimeTypes)
    }

    handleIncomingIntent(intent)

    setContent {
      val settings by viewModel.settings.collectAsStateWithLifecycle()
      FeatherTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
        BrowserScreen(
          viewModel = viewModel,
          onExportBookmarks = {
            exportBookmarksLauncher.launch("pika-bookmarks.html")
          },
          onImportBookmarks = {
            importBookmarksLauncher.launch(arrayOf("text/html", "application/json", "text/plain"))
          },
          onSavePdf = {
            val tab = viewModel.activeTab()
            if (tab.url.isBlank() || !viewModel.webViewPool.printCurrentPage(this, tab.id, tab.title)) {
              viewModel.showToast("Open a page first")
            }
          },
          onPinHomeShortcut = { url, title ->
            pinHomeScreenShortcut(url, title)
          }
        )
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    handleIncomingIntent(intent)
  }

  private fun handleIncomingIntent(intent: Intent?) {
    when (intent?.action) {
      Intent.ACTION_VIEW -> {
        val target = intent.dataString ?: return
        if (UrlValidator.isSafeWebUrl(target)) {
          viewModel.openNewTab(isPrivate = false, initialUrl = target)
        }
      }
      Intent.ACTION_WEB_SEARCH -> {
        val query = intent.getStringExtra(SearchManager.QUERY) ?: return
        val cleaned = UrlValidator.sanitizeControlChars(query)
        if (cleaned.isNotEmpty()) {
          viewModel.openNewTab(isPrivate = false, initialUrl = "")
          viewModel.navigateActiveTab(cleaned)
        }
      }
    }
  }

  private fun pinHomeScreenShortcut(url: String, title: String) {
    if (!UrlValidator.isSafeWebUrl(url)) return
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      viewModel.showToast("Requires Android 8.0+")
      return
    }
    try {
      val sm = getSystemService(ShortcutManager::class.java)
      if (sm == null || !sm.isRequestPinShortcutSupported) {
        viewModel.showToast("Launcher does not support pinned shortcuts")
        return
      }
      val safeLabel = UrlValidator.sanitizeDisplayText(title, 32)
        .ifEmpty { UrlValidator.extractHost(url).ifEmpty { "Pika Link" } }
      val launchIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url), this, MainActivity::class.java)
      val shortcut = ShortcutInfo.Builder(this, "pika_${url.hashCode()}")
        .setShortLabel(safeLabel)
        .setLongLabel(safeLabel)
        .setIcon(Icon.createWithResource(this, R.mipmap.ic_launcher))
        .setIntent(launchIntent)
        .build()
      sm.requestPinShortcut(shortcut, null)
      viewModel.showToast("Shortcut requested")
    } catch (_: Throwable) {
      viewModel.showToast("Unable to pin shortcut")
    }
  }
}
