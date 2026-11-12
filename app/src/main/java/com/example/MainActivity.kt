package com.example

import android.content.Intent
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
        viewModel.showToast("Failed to export bookmarks")
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
        viewModel.showToast("Failed to import bookmarks")
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
      FeatherTheme(themeMode = settings.themeMode) {
        BrowserScreen(
          viewModel = viewModel,
          onExportBookmarks = {
            exportBookmarksLauncher.launch("pika-bookmarks.html")
          },
          onImportBookmarks = {
            importBookmarksLauncher.launch(arrayOf("text/html", "application/json", "text/plain"))
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
    if (intent?.action == Intent.ACTION_VIEW) {
      val target = intent.dataString ?: return
      if (UrlValidator.isSafeWebUrl(target)) {
        viewModel.openNewTab(isPrivate = false, initialUrl = target)
      }
    }
  }
}
