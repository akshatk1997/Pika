package com.example

import android.app.SearchManager
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Color
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.ThemeMode
import com.example.security.UrlValidator
import com.example.ui.BrowserViewModel
import com.example.ui.screens.BrowserScreen
import com.example.ui.theme.FeatherTheme

class MainActivity : ComponentActivity() {

  internal val viewModel: BrowserViewModel by viewModels()

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

  private val importExtensionLauncher = registerForActivityResult(
    ActivityResultContracts.OpenDocument()
  ) { uri ->
    if (uri != null) {
      try {
        val fileName = queryDisplayName(uri) ?: uri.lastPathSegment ?: "extension.crx"
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
        if (bytes != null && bytes.isNotEmpty()) {
          viewModel.installExtensionFromBytes(bytes, fileName)
        } else {
          viewModel.showToast("Selected extension file is empty")
        }
      } catch (_: Throwable) {
        viewModel.showToast("Failed to read extension file")
      }
    }
  }

  private fun queryDisplayName(uri: Uri): String? {
    return try {
      contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
      }
    } catch (_: Throwable) {
      null
    }
  }

  private val webFileChooserLauncher = registerForActivityResult(
    ActivityResultContracts.OpenMultipleDocuments()
  ) { uris ->
    viewModel.deliverChosenFiles(if (uris.isEmpty()) null else uris.toTypedArray())
  }

  private val requestNotificationPermissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { _ -> }

  private val requestMediaPermissionsLauncher = registerForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
  ) { results ->
    if (results[android.Manifest.permission.RECORD_AUDIO] == true) {
      viewModel.onAudioPermissionGranted()
    }
  }

  private val requestAudioPermissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { isGranted ->
    if (isGranted) {
      viewModel.onAudioPermissionGranted()
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    try {
      window.setFlags(
        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
      )
    } catch (_: Throwable) {
    }

    try {
      WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
    } catch (_: Throwable) {
    }

    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
      if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
        requestNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
      }
    }

    viewModel.onRequestMediaPermissions = {
      val needed = mutableListOf<String>()
      if (checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
        needed.add(android.Manifest.permission.CAMERA)
      }
      if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
        needed.add(android.Manifest.permission.RECORD_AUDIO)
      }
      if (needed.isNotEmpty()) {
        requestMediaPermissionsLauncher.launch(needed.toTypedArray())
      }
    }

    viewModel.onAudioPermissionRequested = {
      if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
        requestAudioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
      } else {
        viewModel.onAudioPermissionGranted()
      }
    }

    viewModel.onLaunchFileChooser = { acceptTypes ->
      try {
        val parsedTypes = acceptTypes
          ?.flatMap { it.split(",") }
          ?.map { it.trim().lowercase() }
          ?.filter { it.isNotBlank() }
          ?.map { token ->
            when {
              token == "*/*" -> "*/*"
              token.startsWith(".") -> {
                android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(token.removePrefix(".")) ?: "*/*"
              }
              token.contains("/") -> token
              else -> "*/*"
            }
          }
          ?.distinct()
          ?.toTypedArray()
        val targetTypes = if (parsedTypes.isNullOrEmpty() || parsedTypes.contains("*/*")) {
          arrayOf("*/*")
        } else {
          parsedTypes
        }
        webFileChooserLauncher.launch(targetTypes)
      } catch (_: Throwable) {
        viewModel.deliverChosenFiles(null)
        viewModel.showToast("No file picker available")
      }
    }

    handleIncomingIntent(intent)

    setContent {
      val settings by viewModel.settings.collectAsStateWithLifecycle()
      val tabs by viewModel.tabs.collectAsStateWithLifecycle()
      val activeTabId by viewModel.activeTabId.collectAsStateWithLifecycle()
      val isPrivateActive = remember(tabs, activeTabId) {
        tabs.firstOrNull { it.id == activeTabId }?.isPrivate == true
      }

      DisposableEffect(isPrivateActive) {
        val window = this@MainActivity.window
        try {
          if (isPrivateActive) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
          } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
          }
        } catch (_: Throwable) {
        }
        onDispose {
          try {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
          } catch (_: Throwable) {
          }
        }
      }

      val systemDark = isSystemInDarkTheme()
      val activeOverlay by viewModel.activeOverlay.collectAsStateWithLifecycle()
      val darkTheme = when (settings.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
      }
      val useDarkStatusBar = darkTheme || activeOverlay == com.example.ui.ActiveOverlay.NONE
      val fullscreenView by viewModel.fullscreenView.collectAsStateWithLifecycle()
      val isBrowserFullscreen by viewModel.isBrowserFullscreen.collectAsStateWithLifecycle()
      val isImmersiveFullscreen = fullscreenView != null || isBrowserFullscreen

      DisposableEffect(darkTheme, useDarkStatusBar, isImmersiveFullscreen) {
        val window = this@MainActivity.window
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)

        if (isImmersiveFullscreen) {
          try {
            insetsController.systemBarsBehavior =
              WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
              window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
              View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
          } catch (_: Throwable) {
          }
        } else {
          try {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
              window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
            }
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            enableEdgeToEdge(
              statusBarStyle = if (useDarkStatusBar) {
                SystemBarStyle.dark(Color.TRANSPARENT)
              } else {
                SystemBarStyle.light(Color.TRANSPARENT, Color.argb(0x66, 0x11, 0x18, 0x27))
              },
              navigationBarStyle = if (darkTheme) {
                SystemBarStyle.dark(Color.TRANSPARENT)
              } else {
                SystemBarStyle.light(Color.TRANSPARENT, Color.argb(0x66, 0x11, 0x18, 0x27))
              }
            )
          } catch (_: Throwable) {
          }
        }

        onDispose {
          try {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
              window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
            }
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
          } catch (_: Throwable) {
          }
        }
      }
      FeatherTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
        BrowserScreen(
          viewModel = viewModel,
          onExportBookmarks = {
            try {
              exportBookmarksLauncher.launch("kio-bookmarks.html")
            } catch (_: Throwable) {
              viewModel.showToast("Export failed")
            }
          },
          onImportBookmarks = {
            try {
              importBookmarksLauncher.launch(arrayOf("text/html", "application/json", "text/plain"))
            } catch (_: Throwable) {
              viewModel.showToast("Import failed")
            }
          },
          onPickLocalExtensionFile = {
            try {
              importExtensionLauncher.launch(arrayOf("*/*"))
            } catch (_: Throwable) {
              viewModel.showToast("No file picker available")
            }
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

  override fun onUserLeaveHint() {
    super.onUserLeaveHint()
    try {
      if (viewModel.settings.value.backgroundPlayEnabled) {
        viewModel.ensureMediaServiceRunningSynchronously()
        viewModel.checkAndSyncMediaPlayback(forceStart = true)
      }
    } catch (_: Throwable) {
    }
  }

  override fun onPause() {
    super.onPause()
    try {
      if (viewModel.settings.value.backgroundPlayEnabled) {
        viewModel.ensureMediaServiceRunningSynchronously()
        viewModel.checkAndSyncMediaPlayback(forceStart = true)
      }
    } catch (_: Throwable) {
    }
  }

  override fun onStop() {
    super.onStop()
    try {
      viewModel.flushRetainedTabs()
    } catch (_: Throwable) {
    }
  }

  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    if (hasFocus) {
      val isImmersive = viewModel.fullscreenView.value != null || viewModel.isBrowserFullscreen.value
      if (isImmersive) {
        try {
          val insetsController = WindowCompat.getInsetsController(window, window.decorView)
          insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
          insetsController.hide(WindowInsetsCompat.Type.systemBars())
          window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
          window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
              WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
          }
          @Suppress("DEPRECATION")
          window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
              or View.SYSTEM_UI_FLAG_FULLSCREEN
              or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
              or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
              or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
              or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
          )
        } catch (_: Throwable) {
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleIncomingIntent(intent)
  }

  private fun handleIncomingIntent(intent: Intent?) {
    try {
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
    } catch (_: Throwable) {
    }
  }

  override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
      val ctrlOnly = event.isCtrlPressed && !event.isAltPressed
      if (ctrlOnly) {
        when (event.keyCode) {
          KeyEvent.KEYCODE_T -> {
            viewModel.openNewTab(isPrivate = false)
            return true
          }
          KeyEvent.KEYCODE_N -> if (event.isShiftPressed) {
            viewModel.openNewTab(isPrivate = true)
            return true
          }
          KeyEvent.KEYCODE_W -> {
            viewModel.closeTab(viewModel.activeTab().id)
            return true
          }
          KeyEvent.KEYCODE_F -> {
            if (viewModel.activeTab().url.isNotBlank()) {
              viewModel.openFindInPage()
              return true
            }
          }
          KeyEvent.KEYCODE_R -> {
            if (viewModel.activeTab().url.isNotBlank()) {
              viewModel.reloadOrStop()
              return true
            }
          }
        }
      } else if (event.isAltPressed && !event.isCtrlPressed) {
        when (event.keyCode) {
          KeyEvent.KEYCODE_DPAD_LEFT -> {
            viewModel.goBack()
            return true
          }
          KeyEvent.KEYCODE_DPAD_RIGHT -> {
            viewModel.goForward()
            return true
          }
        }
      } else if (event.keyCode == KeyEvent.KEYCODE_F5 && viewModel.activeTab().url.isNotBlank()) {
        viewModel.reloadOrStop()
        return true
      }
    }
    return super.dispatchKeyEvent(event)
  }

  private fun pinHomeScreenShortcut(url: String, title: String) {
    if (!UrlValidator.isSafeWebUrl(url)) return
    try {
      val sm = getSystemService(ShortcutManager::class.java)
      if (sm == null || !sm.isRequestPinShortcutSupported) {
        viewModel.showToast("Launcher does not support pinned shortcuts")
        return
      }
      val safeLabel = UrlValidator.sanitizeDisplayText(title, 32)
        .ifEmpty { UrlValidator.extractHost(url).ifEmpty { "Web link" } }
      val launchIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url), this, MainActivity::class.java)
      val shortcut = ShortcutInfo.Builder(this, "kio_${url.hashCode()}")
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
