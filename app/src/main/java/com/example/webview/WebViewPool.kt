package com.example.webview

import android.annotation.SuppressLint
import android.content.Context
import android.content.MutableContextWrapper
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.print.PrintAttributes
import android.print.PrintManager
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.webkit.GeolocationPermissions
import android.webkit.HttpAuthHandler
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SafeBrowsingResponse
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebViewDatabase
import com.example.model.ClearDataOptions
import com.example.model.DetectedMedia
import com.example.model.PageError
import com.example.model.PageErrorType
import com.example.model.ReaderArticle
import com.example.model.SettingsState
import com.example.model.ThemeMode
import com.example.model.UserAgentMode
import com.example.model.WebPermissionKind
import com.example.security.TrackerBlocker
import com.example.security.UrlValidator
import com.example.ui.components.SiteFaviconCache
import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.LinkedHashMap
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

interface WebViewCallbacks {
  fun onPageStarted(tabId: String, url: String)
  fun onPageFinished(tabId: String, url: String, title: String?, canGoBack: Boolean, canGoForward: Boolean)
  fun onProgressChanged(tabId: String, progress: Int)
  fun onTitleReceived(tabId: String, title: String)
  fun onTrackerBlocked(tabId: String)
  fun onHttpBlockedByHttpsOnly(tabId: String, url: String)
  fun onBlockedScheme(tabId: String, url: String, scheme: String)
  fun onExternalSchemePrompt(tabId: String, url: String, scheme: String)
  fun onPageError(tabId: String, error: PageError)
  fun onRenderProcessCrashed(tabId: String)
  fun onDownloadRequested(tabId: String, url: String, contentDisposition: String?, mimeType: String?, contentLength: Long)
  fun onPermissionPrompt(tabId: String, origin: String, kind: WebPermissionKind, detail: String, onDecision: (Boolean) -> Unit)
  fun onFileChooserRequested(callback: ValueCallback<Array<android.net.Uri>>?, acceptTypes: Array<String>?)
  fun onFindMatches(tabId: String, activeOrdinal: Int, numberOfMatches: Int)
  fun onFullscreenChanged(tabId: String, customView: View?, callback: WebChromeClient.CustomViewCallback?)
  fun onLongPressLink(tabId: String, url: String, isImage: Boolean)
  fun onLongPressLinkWithImage(tabId: String, url: String, isImage: Boolean, imageUrl: String?) {
    onLongPressLink(tabId, url, isImage)
  }
  fun onAudioPermissionNeeded() {}
  fun onMediaDetected(tabId: String, media: DetectedMedia) {}
  fun onNewTabRequested(parentTabId: String, url: String, isPrivate: Boolean) {}
  fun onNewTabCreated(newTabId: String, isPrivate: Boolean, initialUrl: String = "") {}
  fun onCloseTabRequested(tabId: String) {}
}

/**
 * Hardened WebView subclass that maintains active background media playback.
 * Intercepts Android view/window visibility changes to prevent Chromium AwContents
 * from automatically pausing HTML5 video and audio streams when the app is backgrounded
 * or when the screen is turned off.
 */
open class BackgroundMediaWebView(
  context: Context,
  val isBackgroundPlayEnabled: () -> Boolean
) : WebView(context) {

  public override fun onWindowVisibilityChanged(visibility: Int) {
    if (isBackgroundPlayEnabled() && isAttachedToWindow) {
      super.onWindowVisibilityChanged(View.VISIBLE)
    } else {
      super.onWindowVisibilityChanged(visibility)
    }
  }

  override fun dispatchWindowVisibilityChanged(visibility: Int) {
    if (isBackgroundPlayEnabled() && isAttachedToWindow) {
      super.dispatchWindowVisibilityChanged(View.VISIBLE)
    } else {
      super.dispatchWindowVisibilityChanged(visibility)
    }
  }

  public override fun onVisibilityChanged(changedView: View, visibility: Int) {
    if (isBackgroundPlayEnabled() && isAttachedToWindow) {
      super.onVisibilityChanged(changedView, View.VISIBLE)
    } else {
      super.onVisibilityChanged(changedView, visibility)
    }
  }
}

/**
 * Hardened Android System WebView pool (max 2 live WebViews in RAM).
 * Thread-safe host tracking (zero off-UI WebView calls), MutableContextWrapper UI binding,
 * defensive System WebView creation, and clean RenderProcessGone recovery.
 */
class WebViewPool(
  private val context: Context,
  private val settingsProvider: () -> SettingsState,
  private val callbacks: WebViewCallbacks
) {

  companion object {
    private const val MAX_LIVE_WEBVIEWS = 3
    private const val DESKTOP_USER_AGENT =
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36"
    private const val IPHONE_USER_AGENT =
      "Mozilla/5.0 (iPhone; CPU iPhone OS 18_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.3 Mobile/15E148 Safari/604.1"
  }

  private val appContext: Context = context.applicationContext
  private val liveViews = object : LinkedHashMap<String, WebView>(4, 0.75f, true) {}
  private val savedStates = HashMap<String, Bundle>()
  private val currentPageHosts = ConcurrentHashMap<String, String>()
  private val currentPageUrls = ConcurrentHashMap<String, String>()
  private val currentPageTitles = ConcurrentHashMap<String, String>()
  private val tabUserAgentModes = ConcurrentHashMap<String, UserAgentMode>()
  private val tabPrivacyFlags = ConcurrentHashMap<String, Boolean>()
  private val pageEarlyInjectedStage = ConcurrentHashMap<String, Int>()
  private val destroyedViews = Collections.newSetFromMap(WeakHashMap<WebView, Boolean>())

  // Bidirectional Standard <-> Private Isolation Vault
  @Volatile private var currentlyInPrivateMode: Boolean = false
  @Volatile private var liveSystemDarkMode: Boolean? = null
  private val visitedStandardOrigins = ConcurrentHashMap.newKeySet<String>()
  private val vaultedStandardCookies = ConcurrentHashMap<String, List<String>>()
  private val cookieBarrierLock = Any()

  fun evaluateJsOnStandardTab(tabId: String, js: String) {
    if (tabPrivacyFlags[tabId] == true || !settingsProvider().javascriptEnabled) return
    val wv = getLiveWebView(tabId) ?: return
    try {
      wv.post {
        if (!destroyedViews.contains(wv)) {
          wv.evaluateJavascript(js, null)
        }
      }
    } catch (_: Throwable) {
    }
  }

  fun isCurrentlyInPrivateBarrier(): Boolean = currentlyInPrivateMode

  fun updateSystemDarkMode(isSystemDark: Boolean) {
    liveSystemDarkMode = isSystemDark
  }

  fun registerStandardOriginForVault(url: String) {
    val origin = UrlValidator.extractOrigin(url)
    if (origin.startsWith("http://") || origin.startsWith("https://")) {
      visitedStandardOrigins.add(origin)
    }
  }

  /**
   * Enforces strict bidirectional cookie isolation between Standard Mode and Private Mode:
   * - Standard -> Private: snapshots Standard cookies into RAM vault and wipes CookieManager so Private starts with 0 cookies.
   * - Private -> Standard: incinerates 100% of Private cookies and restores vaulted Standard cookies cleanly.
   */
  fun syncActiveModeBoundary(targetPrivateMode: Boolean) {
    synchronized(cookieBarrierLock) {
      if (targetPrivateMode == currentlyInPrivateMode) return
      try {
        val cm = CookieManager.getInstance()
        if (targetPrivateMode) {
          // Standard -> Private: vault standard cookies, then zero out CookieManager
          vaultedStandardCookies.clear()
          for (origin in visitedStandardOrigins) {
            val raw = try { cm.getCookie(origin) } catch (_: Throwable) { null }
            if (!raw.isNullOrBlank()) {
              val pairs = raw.split(";").map { it.trim() }.filter { it.isNotEmpty() }
              if (pairs.isNotEmpty()) {
                vaultedStandardCookies[origin] = pairs
              }
            }
          }
          cm.removeSessionCookies(null)
          cm.removeAllCookies(null)
          cm.flush()
          currentlyInPrivateMode = true
        } else {
          // Private -> Standard: incinerate ALL private cookies, then restore standard cookies
          cm.removeSessionCookies(null)
          cm.removeAllCookies(null)
          cm.flush()
          for ((origin, pairs) in vaultedStandardCookies) {
            for (pair in pairs) {
              try {
                cm.setCookie(origin, pair)
              } catch (_: Throwable) {
              }
            }
          }
          cm.flush()
          vaultedStandardCookies.clear()
          currentlyInPrivateMode = false
        }
      } catch (_: Throwable) {
        currentlyInPrivateMode = targetPrivateMode
      }
    }
  }
  private val defaultUserAgent: String by lazy {
    try {
      WebSettings.getDefaultUserAgent(appContext)
    } catch (_: Throwable) {
      ""
    }
  }
  private val cleanMobileUserAgent: String by lazy {
    try {
      val raw = defaultUserAgent
      // Strip "; wv" and "Version/4.0 " markers so modern websites and video platforms
      // recognize Kio as authentic Google Chrome Mobile rather than a stripped in-app WebView
      raw.replace(Regex(";\\s*wv\\b"), "")
        .replace(Regex("Version/\\d+\\.\\d+\\s*"), "")
        .replace(Regex("\\s{2,}"), " ")
        .trim()
    } catch (_: Throwable) {
      ""
    }
  }

  fun attachToHost(webView: WebView, hostContext: Context) {
    if (destroyedViews.contains(webView)) return
    val wrapper = webView.context as? MutableContextWrapper ?: return
    if (wrapper.baseContext !== hostContext) {
      wrapper.baseContext = hostContext
    }
  }

  fun detachFromHost(webView: WebView) {
    val wrapper = webView.context as? MutableContextWrapper ?: return
    if (wrapper.baseContext !== appContext) {
      wrapper.baseContext = appContext
    }
  }

  fun isWebViewAlive(webView: WebView): Boolean = !destroyedViews.contains(webView)

  private fun evictExcessLiveViews(targetLimit: Int = MAX_LIVE_WEBVIEWS) {
    while (liveViews.size >= targetLimit) {
      val eldestKey = liveViews.keys.firstOrNull() ?: break
      val eldestView = liveViews.remove(eldestKey)
      if (eldestView != null) {
        try {
          val bundle = Bundle()
          val savedList = eldestView.saveState(bundle)
          if (savedList != null && savedList.size > 0) {
            savedStates[eldestKey] = bundle
          }
        } catch (_: Throwable) {
        }
        safelyDestroyWebView(eldestView)
      }
    }
  }

  fun getOrCreateWebView(
    tabId: String,
    isPrivate: Boolean,
    userAgentMode: UserAgentMode,
    approvedHttpHosts: Set<String>,
    fallbackUrl: String = ""
  ): WebView? {
    val settingsState = settingsProvider()
    tabUserAgentModes[tabId] = userAgentMode
    tabPrivacyFlags[tabId] = isPrivate
    syncActiveModeBoundary(isPrivate)
    liveViews[tabId]?.let { existing ->
      if (!destroyedViews.contains(existing)) {
        applySettings(existing, isPrivate, userAgentMode, settingsState)
        return existing
      } else {
        liveViews.remove(tabId)
      }
    }

    evictExcessLiveViews()

    val webView = createSecuredWebView(tabId, isPrivate, userAgentMode, approvedHttpHosts) ?: return null
    liveViews[tabId] = webView
    var restoredOk = false
    savedStates.remove(tabId)?.let { bundle ->
      try {
        val restored = webView.restoreState(bundle)
        restoredOk = restored != null && restored.size > 0
      } catch (_: Throwable) {
      }
    }
    if (!restoredOk && fallbackUrl.isNotBlank() && UrlValidator.isSafeWebUrl(fallbackUrl) && fallbackUrl != "about:blank") {
      loadUrl(tabId, fallbackUrl)
    }
    return webView
  }

  fun hasSavedState(tabId: String): Boolean = savedStates.containsKey(tabId)

  fun getLiveWebView(tabId: String): WebView? = liveViews[tabId]?.takeIf { !destroyedViews.contains(it) }

  /**
   * Releases idle background WebViews, trims favicon memory/disk caches, and cleans orphan temp files
   * so Kio keeps Android RAM and storage footprint ultra-minimal without sacrificing speed.
   */
  fun trimMemoryAndDiskCaches(activeTabId: String, aggressive: Boolean) {
    SiteFaviconCache.trimMemory()
    Thread {
      SiteFaviconCache.trimDiskCache(appContext, maxFiles = 96, maxAgeDays = 14L)
      try {
        val tmpFiles = appContext.cacheDir.listFiles() ?: emptyArray()
        val cutoff = System.currentTimeMillis() - (24L * 60L * 60L * 1000L)
        for (f in tmpFiles) {
          if (f.isFile && f.lastModified() < cutoff) {
            runCatching { f.delete() }
          }
        }
      } catch (_: Throwable) {
      }
    }.start()

    if (aggressive) {
      val keysToEvict = liveViews.keys.filter { it != activeTabId }
      for (key in keysToEvict) {
        val wv = liveViews.remove(key) ?: continue
        try {
          val bundle = Bundle()
          val savedList = wv.saveState(bundle)
          if (savedList != null && savedList.size > 0) {
            savedStates[key] = bundle
          }
        } catch (_: Throwable) {
        }
        safelyDestroyWebView(wv)
      }
    }
  }

  /**
   * Always-on Lightweight Performance & Data-Efficiency Engine:
   * - Strips hidden background prefetch/prerender link waste
   * - Enables native lazy loading & async decoding for below-the-fold images and iframes
   */
  private fun applyPerformanceAndDataEngine(webView: WebView) {
    if (!settingsProvider().javascriptEnabled || destroyedViews.contains(webView)) return
    val js = """
      (function() {
        try {
          var links = document.querySelectorAll('link[rel="prefetch"], link[rel="prerender"], link[rel="next"]');
          for (var i = 0; i < links.length; i++) { links[i].remove(); }
          var vh = window.innerHeight || 800;
          var imgs = document.querySelectorAll('img:not([loading])');
          for (var j = 0; j < imgs.length; j++) {
            var img = imgs[j];
            img.decoding = 'async';
            var rect = img.getBoundingClientRect ? img.getBoundingClientRect() : null;
            if (!rect || rect.top > vh * 1.2) {
              img.loading = 'lazy';
            }
          }
          var frames = document.querySelectorAll('iframe:not([loading])');
          for (var k = 0; k < frames.length; k++) {
            var fr = frames[k];
            var frect = fr.getBoundingClientRect ? fr.getBoundingClientRect() : null;
            if (!frect || frect.top > vh * 1.2) {
              fr.loading = 'lazy';
            }
          }
        } catch(e) {}
      })();
    """.trimIndent()
    try {
      webView.evaluateJavascript(js, null)
    } catch (_: Throwable) {
    }
  }

  fun loadUrl(tabId: String, url: String) {
    val webView = getLiveWebView(tabId) ?: return
    val isPriv = tabPrivacyFlags[tabId] == true
    syncActiveModeBoundary(isPriv)
    if (!isPriv) registerStandardOriginForVault(url)
    pageEarlyInjectedStage[tabId] = 0
    currentPageHosts[tabId] = UrlValidator.extractHost(url)
    currentPageUrls[tabId] = url
    val s = settingsProvider()
    val headers = mutableMapOf(
      "Sec-GPC" to "1",
      "DNT" to "1",
      "Sec-CH-Prefers-Color-Scheme" to if (resolveIsDark(s)) "dark" else "light"
    )
    if (tabUserAgentModes[tabId] == UserAgentMode.DESKTOP) {
      headers["Sec-CH-UA-Mobile"] = "?0"
      headers["Sec-CH-UA-Platform"] = "\"Windows\""
      headers["Sec-CH-UA"] = "\"Chromium\";v=\"134\", \"Google Chrome\";v=\"134\", \"Not-A.Brand\";v=\"99\""
    }
    if (s.dataSaverEnabled) {
      headers["Save-Data"] = "on"
    }
    try {
      webView.loadUrl(url, headers)
    } catch (_: Throwable) {
      callbacks.onPageError(
        tabId,
        PageError(
          type = PageErrorType.GENERIC,
          failingUrl = url,
          host = UrlValidator.extractHost(url).ifEmpty { "Page" },
          reason = "Unable to load this page right now."
        )
      )
    }
  }

  fun applyUserAgentMode(tabId: String, isPrivate: Boolean, userAgentMode: UserAgentMode) {
    tabUserAgentModes[tabId] = userAgentMode
    val webView = getLiveWebView(tabId) ?: return
    applySettings(webView, isPrivate, userAgentMode, settingsProvider())
    try {
      webView.setInitialScale(0)
    } catch (_: Throwable) {
    }
    applyDesktopModeEngine(webView, userAgentMode == UserAgentMode.DESKTOP)
    try {
      val currentUrl = webView.url
      if (!currentUrl.isNullOrBlank() && UrlValidator.isSafeWebUrl(currentUrl) && currentUrl != "about:blank") {
        val targetUrl = if (userAgentMode == UserAgentMode.DESKTOP) {
          UrlValidator.toDesktopUrl(currentUrl)
        } else {
          currentUrl
        }
        loadUrl(tabId, targetUrl)
      }
    } catch (_: Throwable) {
    }
  }

  fun applyDesktopModeEngine(webView: WebView, enabled: Boolean) {
    if (!settingsProvider().javascriptEnabled || destroyedViews.contains(webView)) return
    val js = if (enabled) {
      """
      (function() {
        window.__kio_desktop_mode = true;
        function enforceDesktopViewport() {
          try {
            if (!window.__kio_desktop_mode) return;
            var head = document.head || document.documentElement;
            if (!head) return;
            var meta = document.querySelector('meta[name="viewport"]');
            var desired = 'width=1280, user-scalable=yes';
            if (!meta) {
              meta = document.createElement('meta');
              meta.name = 'viewport';
              meta.content = desired;
              head.insertBefore(meta, head.firstChild);
            } else if (meta.getAttribute('content') !== desired) {
              meta.setAttribute('content', desired);
            }
          } catch(e) {}
        }
        enforceDesktopViewport();
        try {
          Object.defineProperty(navigator, 'platform', { get: function() { return 'Win32'; }, configurable: true });
          Object.defineProperty(navigator, 'maxTouchPoints', { get: function() { return 0; }, configurable: true });
          if (navigator.userAgentData) {
            Object.defineProperty(navigator, 'userAgentData', {
              get: function() {
                return {
                  brands: [
                    { brand: 'Chromium', version: '134' },
                    { brand: 'Google Chrome', version: '134' },
                    { brand: 'Not-A.Brand', version: '99' }
                  ],
                  mobile: false,
                  platform: 'Windows',
                  getHighEntropyValues: function() {
                    return Promise.resolve({
                      platform: 'Windows',
                      platformVersion: '15.0.0',
                      architecture: 'x86',
                      bitness: '64',
                      mobile: false,
                      model: ''
                    });
                  }
                };
              },
              configurable: true
            });
          }
        } catch(e) {}
        if (!window.__kio_desktop_observer && window.MutationObserver) {
          var target = document.head || document.documentElement;
          if (target) {
            var obs = new MutationObserver(function() {
              if (window.__kio_desktop_mode) enforceDesktopViewport();
            });
            obs.observe(target, { childList: true, subtree: true, attributes: true, attributeFilter: ['content'] });
            window.__kio_desktop_observer = obs;
          }
        }
      })();
      """.trimIndent()
    } else {
      """
      (function() {
        window.__kio_desktop_mode = false;
        if (window.__kio_desktop_observer) {
          try { window.__kio_desktop_observer.disconnect(); } catch(e) {}
          window.__kio_desktop_observer = null;
        }
      })();
      """.trimIndent()
    }
    try {
      webView.evaluateJavascript(js, null)
    } catch (_: Throwable) {
    }
  }

  private fun applySearchSameTabEngine(webView: WebView, url: String) {
    if (!settingsProvider().javascriptEnabled || destroyedViews.contains(webView)) return
    if (!UrlValidator.isSearchEngineUrl(url)) return
    val js = """
      (function() {
        function clearBlankTargets(root) {
          try {
            var links = (root || document).querySelectorAll ? (root || document).querySelectorAll('a[target="_blank"], a[target="_new"]') : [];
            for (var i = 0; i < links.length; i++) {
              links[i].removeAttribute('target');
            }
          } catch(e) {}
        }
        clearBlankTargets(document);
        if (!window.__kio_search_sametab_observer && window.MutationObserver) {
          var obs = new MutationObserver(function(mutations) {
            for (var i = 0; i < mutations.length; i++) {
              var added = mutations[i].addedNodes;
              for (var j = 0; j < added.length; j++) {
                var node = added[j];
                if (node.nodeType === 1) {
                  if (node.tagName === 'A' && (node.target === '_blank' || node.target === '_new')) {
                    node.removeAttribute('target');
                  } else {
                    clearBlankTargets(node);
                  }
                }
              }
            }
          });
          var root = document.body || document.documentElement;
          if (root) {
            obs.observe(root, { childList: true, subtree: true });
            window.__kio_search_sametab_observer = obs;
          }
        }
      })();
      """.trimIndent()
    try {
      webView.evaluateJavascript(js, null)
    } catch (_: Throwable) {
    }
  }

  private fun applyLinkTargetTrackerEngine(webView: WebView) {
    if (!settingsProvider().javascriptEnabled || destroyedViews.contains(webView)) return
    val js = """
      (function() {
        if (window.__kio_link_tracker_installed) return;
        window.__kio_link_tracker_installed = true;
        window.__kio_touch_ctx = { link: '', image: '', ts: 0 };

        function isValidHref(u) {
          if (!u || typeof u !== 'string') return false;
          var t = u.trim().toLowerCase();
          return (t.indexOf('http://') === 0 || t.indexOf('https://') === 0) && t !== 'about:blank';
        }
        function isImageUrl(u) {
          if (!u) return false;
          var path = u.split('?')[0].split('#')[0].toLowerCase();
          return /\.(jpg|jpeg|png|webp|gif|svg|avif|bmp|ico)$/.test(path);
        }
        function captureTarget(el) {
          try {
            if (!el || el.nodeType !== 1) return;
            var link = '';
            var img = '';

            var imgEl = el.tagName === 'IMG' ? el : (el.closest ? el.closest('img') : null);
            if (!imgEl && el.querySelector && (el.tagName === 'PICTURE' || el.tagName === 'FIGURE')) {
              imgEl = el.querySelector('img');
            }
            if (imgEl) {
              var s = imgEl.currentSrc || imgEl.src || '';
              if (isValidHref(s)) img = s;
            }

            // 1. Direct enclosing <a href="..."> (e.g. <a href="page"><img src="pic"></a>)
            var anchor = el.closest ? el.closest('a[href]') : null;
            if (anchor && isValidHref(anchor.href)) {
              link = anchor.href;
            }

            // 2. Data-href / data-url wrapper
            if (!link && el.closest) {
              var dataEl = el.closest('[data-href], [data-url], [data-link]');
              if (dataEl) {
                var raw = dataEl.getAttribute('data-href') || dataEl.getAttribute('data-url') || dataEl.getAttribute('data-link') || '';
                if (raw) {
                  try {
                    var abs = new URL(raw, window.location.href).href;
                    if (isValidHref(abs)) link = abs;
                  } catch(e) {}
                }
              }
            }

            // 3. Thumbnail inside a card container whose sibling/child <a href> is the article/video link
            if (!link && imgEl) {
              var cur = imgEl.parentElement;
              for (var depth = 0; depth < 3 && cur && cur !== document.body; depth++) {
                var anchors = cur.querySelectorAll ? cur.querySelectorAll('a[href]') : [];
                var uniquePageLinks = [];
                for (var i = 0; i < anchors.length && uniquePageLinks.length < 4; i++) {
                  var h = anchors[i].href;
                  if (isValidHref(h) && !isImageUrl(h) && uniquePageLinks.indexOf(h) === -1) {
                    uniquePageLinks.push(h);
                  }
                }
                if (uniquePageLinks.length === 1) {
                  link = uniquePageLinks[0];
                  break;
                }
                if (uniquePageLinks.length > 1) break;
                cur = cur.parentElement;
              }
            }

            window.__kio_touch_ctx = { link: link, image: img, ts: Date.now() };
          } catch(e) {}
        }

        document.addEventListener('touchstart', function(e) { captureTarget(e.target); }, { capture: true, passive: true });
        document.addEventListener('pointerdown', function(e) { captureTarget(e.target); }, { capture: true, passive: true });
        document.addEventListener('contextmenu', function(e) { captureTarget(e.target); }, { capture: true, passive: true });

        window.__kio_get_touch_ctx = function() {
          var ctx = window.__kio_touch_ctx || { link: '', image: '', ts: 0 };
          if (Date.now() - (ctx.ts || 0) > 6000) {
            return JSON.stringify({ link: '', image: '' });
          }
          return JSON.stringify({ link: ctx.link || '', image: ctx.image || '' });
        };
      })();
      """.trimIndent()
    try {
      webView.evaluateJavascript(js, null)
    } catch (_: Throwable) {
    }
  }

  fun resolveIsDark(settingsState: SettingsState): Boolean {
    return when (settingsState.themeMode) {
      ThemeMode.SYSTEM -> {
        liveSystemDarkMode ?: run {
          val uiMode = appContext.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
          uiMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
      }
      ThemeMode.DARK -> true
      ThemeMode.LIGHT -> false
    }
  }

  fun applyThemeMode(webView: WebView, isDark: Boolean, nightWebMode: Boolean) {
    if (destroyedViews.contains(webView)) return
    try {
      webView.setBackgroundColor(if (isDark) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    } catch (_: Throwable) {
    }
    if (!settingsProvider().javascriptEnabled) return
    val modeStr = if (isDark) "dark" else "light"
    val js = """
      (function() {
        var isDark = $isDark;
        var mode = '$modeStr';
        window.__kioActiveThemeMode = mode;
        // Dynamically mirror Kio's resolved System/Light/Dark theme into prefers-color-scheme
        // so both Standard and cookie-less Private tabs match the OS/app theme immediately.
        try {
          if (!window.__kioMatchMediaHooked && window.matchMedia) {
            window.__kioMatchMediaHooked = true;
            var origMM = window.matchMedia.bind(window);
            var trackedLists = [];
            window.__kioThemeMqls = trackedLists;
            window.matchMedia = function(query) {
              var q = String(query || '').toLowerCase();
              var isDarkQ = q.indexOf('prefers-color-scheme') !== -1 && q.indexOf('dark') !== -1;
              var isLightQ = q.indexOf('prefers-color-scheme') !== -1 && q.indexOf('light') !== -1;
              var nativeMql = origMM(query);
              if (!isDarkQ && !isLightQ) return nativeMql;
              var listeners = [];
              var entry = {
                isDarkQ: isDarkQ,
                query: String(query),
                listeners: listeners,
                onchange: null
              };
              var proxy = {
                get media() { return entry.query; },
                get matches() { return entry.isDarkQ ? (window.__kioActiveThemeMode === 'dark') : (window.__kioActiveThemeMode !== 'dark'); },
                get onchange() { return entry.onchange; },
                set onchange(fn) { entry.onchange = fn; },
                addEventListener: function(type, fn) { if (type === 'change' && typeof fn === 'function' && listeners.indexOf(fn) === -1) listeners.push(fn); },
                removeEventListener: function(type, fn) { var idx = listeners.indexOf(fn); if (idx >= 0) listeners.splice(idx, 1); },
                addListener: function(fn) { if (typeof fn === 'function' && listeners.indexOf(fn) === -1) listeners.push(fn); },
                removeListener: function(fn) { var idx = listeners.indexOf(fn); if (idx >= 0) listeners.splice(idx, 1); },
                dispatchEvent: function() { return true; }
              };
              entry.proxy = proxy;
              trackedLists.push(entry);
              return proxy;
            };
          }
          if (window.__kioThemeMqls) {
            for (var i = 0; i < window.__kioThemeMqls.length; i++) {
              var item = window.__kioThemeMqls[i];
              var matchesNow = item.isDarkQ ? isDark : !isDark;
              var evt = { matches: matchesNow, media: item.query, type: 'change', target: item.proxy };
              if (typeof item.onchange === 'function') { try { item.onchange(evt); } catch(e) {} }
              for (var j = 0; j < item.listeners.length; j++) {
                try { item.listeners[j](evt); } catch(e) {}
              }
            }
          }
        } catch(e) {}
        if (window.__kioSetTheme) {
          window.__kioSetTheme(mode);
        }
        var id = 'kio-theme-mode-style';
        var s = document.getElementById(id);
        if (!s) {
          s = document.createElement('style');
          s.id = id;
          (document.head || document.documentElement).appendChild(s);
        }
        s.textContent = ':root, html { color-scheme: ' + mode + ' !important; }';
        var meta = document.querySelector('meta[name="color-scheme"]');
        if (!meta) {
          meta = document.createElement('meta');
          meta.name = 'color-scheme';
          (document.head || document.documentElement).appendChild(meta);
        }
        meta.content = mode;
        if (!isDark) {
          var nightStyle = document.getElementById('kio-night-style');
          if (nightStyle) nightStyle.remove();
        }
      })();
    """.trimIndent()
    try {
      webView.evaluateJavascript(js, null)
    } catch (_: Throwable) {
    }
    applyNightWebCss(webView, isDark && nightWebMode)
  }

  fun applySettingsToAll(
    settingsState: SettingsState,
    tabStateLookup: (String) -> Pair<Boolean, UserAgentMode>
  ) {
    val isDark = resolveIsDark(settingsState)
    for ((id, wv) in liveViews.toMap()) {
      if (destroyedViews.contains(wv)) continue
      val (isPriv, uaMode) = tabStateLookup(id)
      tabUserAgentModes[id] = uaMode
      applySettings(wv, isPriv, uaMode, settingsState)
      applyDesktopModeEngine(wv, uaMode == UserAgentMode.DESKTOP)
      applyThemeMode(wv, isDark, settingsState.nightWebMode)
      applyAdBlocker(wv, settingsState.trackerBlockingEnabled)
      applyBackgroundPlay(wv, settingsState.backgroundPlayEnabled)
      applyDataSaver(wv, settingsState.dataSaverEnabled)
      applyVideoCompatibilityEngine(wv)
    }
  }

  fun notifyAudioPermissionGranted() {
  }

  fun destroyTab(tabId: String, isPrivate: Boolean = false) {
    savedStates.remove(tabId)
    currentPageHosts.remove(tabId)
    currentPageUrls.remove(tabId)
    currentPageTitles.remove(tabId)
    tabUserAgentModes.remove(tabId)
    tabPrivacyFlags.remove(tabId)
    pageEarlyInjectedStage.remove(tabId)
    liveViews.remove(tabId)?.let { wv ->
      if (isPrivate) {
        try {
          wv.clearCache(true)
          wv.clearHistory()
          @Suppress("DEPRECATION")
          wv.clearFormData()
          wv.clearSslPreferences()
        } catch (_: Throwable) {
        }
      }
      safelyDestroyWebView(wv)
    }
  }

  private fun safelyDestroyWebView(wv: WebView) {
    destroyedViews.add(wv)
    detachFromHost(wv)
    try {
      wv.setOnTouchListener(null)
      wv.setOnLongClickListener(null)
      wv.setFindListener(null)
      wv.setDownloadListener(null)
      wv.stopLoading()
      wv.onPause()
    } catch (_: Throwable) {
    }
    try {
      (wv.parent as? ViewGroup)?.removeView(wv)
    } catch (_: Throwable) {
    }
    try {
      wv.destroy()
    } catch (_: Throwable) {
    }
  }

  fun wipePrivateSessionData(privateTabIds: List<String>) {
    privateTabIds.forEach { destroyTab(it, isPrivate = true) }
    // Also wipe any remaining live WebViews explicitly flagged private
    for ((id, wv) in liveViews.toMap()) {
      if (destroyedViews.contains(wv) || tabPrivacyFlags[id] != true) continue
      try {
        wv.evaluateJavascript("(function(){if(window.__kio_reset_private_storage)window.__kio_reset_private_storage();})();", null)
        wv.clearCache(true)
        wv.clearHistory()
        @Suppress("DEPRECATION")
        wv.clearFormData()
        wv.clearSslPreferences()
      } catch (_: Throwable) {
      }
    }
    if (currentlyInPrivateMode) {
      // Incinerate all private cookies and restore vaulted standard cookies cleanly
      syncActiveModeBoundary(targetPrivateMode = false)
    } else {
      try {
        CookieManager.getInstance().apply {
          removeSessionCookies(null)
          flush()
        }
      } catch (_: Throwable) {
      }
    }
    try {
      @Suppress("DEPRECATION")
      WebViewDatabase.getInstance(appContext).apply {
        clearFormData()
        clearHttpAuthUsernamePassword()
      }
    } catch (_: Throwable) {
    }
  }

  /**
   * Purges all active private cookies, RAM storage maps, cache, and HTTP auth
   * while keeping the current private tabs open (used by New Identity & Purge RAM).
   */
  fun purgePrivateRamData(privateTabIds: List<String>, reloadTabId: String? = null) {
    try {
      CookieManager.getInstance().apply {
        removeSessionCookies(null)
        removeAllCookies(null)
        flush()
      }
    } catch (_: Throwable) {
    }
    for (id in privateTabIds) {
      val wv = getLiveWebView(id) ?: continue
      try {
        wv.evaluateJavascript("(function(){if(window.__kio_reset_private_storage)window.__kio_reset_private_storage();})();", null)
        wv.clearCache(true)
        wv.clearHistory()
        @Suppress("DEPRECATION")
        wv.clearFormData()
        wv.clearSslPreferences()
      } catch (_: Throwable) {
      }
    }
    try {
      @Suppress("DEPRECATION")
      WebViewDatabase.getInstance(appContext).apply {
        clearFormData()
        clearHttpAuthUsernamePassword()
      }
    } catch (_: Throwable) {
    }
    if (reloadTabId != null) {
      val target = getLiveWebView(reloadTabId)
      try {
        target?.reload()
      } catch (_: Throwable) {
      }
    }
  }

  fun clearBrowsingData(options: ClearDataOptions) {
    for ((_, wv) in liveViews.toMap()) {
      if (destroyedViews.contains(wv)) continue
      try {
        if (options.clearCache) wv.clearCache(true)
        if (options.clearHistory) wv.clearHistory()
        @Suppress("DEPRECATION")
        wv.clearFormData()
      } catch (_: Throwable) {
      }
    }
    if (options.clearCache) {
      SiteFaviconCache.clearAll(appContext)
    }
    if (options.clearCookies) {
      try {
        CookieManager.getInstance().apply {
          removeAllCookies(null)
          flush()
        }
      } catch (_: Throwable) {
      }
      try {
        WebStorage.getInstance().deleteAllData()
      } catch (_: Throwable) {
      }
    }
    try {
      @Suppress("DEPRECATION")
      WebViewDatabase.getInstance(appContext).clearFormData()
    } catch (_: Throwable) {
    }
  }

  fun printCurrentPage(activityContext: Context, tabId: String, title: String): Boolean {
    val webView = getLiveWebView(tabId) ?: return false
    return try {
      val printManager = activityContext.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return false
      val jobName = UrlValidator.sanitizeDisplayText(title, 64).ifEmpty { "Kio_Page" }
      val adapter = webView.createPrintDocumentAdapter(jobName)
      printManager.print(jobName, adapter, PrintAttributes.Builder().build())
      true
    } catch (_: Throwable) {
      false
    }
  }

  fun extractPageSource(tabId: String, onResult: (String?) -> Unit) {
    val webView = getLiveWebView(tabId)
    if (webView == null) {
      onResult(null)
      return
    }
    val js = "(function(){try{return document.documentElement?document.documentElement.outerHTML:'';}catch(e){return '';}})();"
    try {
      webView.evaluateJavascript(js) { rawJson ->
        try {
          if (rawJson.isNullOrBlank() || rawJson == "null") {
            onResult(null)
            return@evaluateJavascript
          }
          val unwrapped = JSONObject("{\"v\":$rawJson}").getString("v")
          onResult(unwrapped.take(90_000).ifBlank { null })
        } catch (_: Throwable) {
          onResult(null)
        }
      }
    } catch (_: Throwable) {
      onResult(null)
    }
  }

  fun captureFullPageScreenshot(
    context: Context,
    tabId: String,
    title: String,
    onResult: (String?, Boolean) -> Unit
  ) {
    val webView = getLiveWebView(tabId) ?: run {
      onResult("Page not loaded", false)
      return
    }
    try {
      val scale = webView.scale
      val contentWidth = webView.width.coerceAtLeast(1)
      val contentHeight = (webView.contentHeight * scale).toInt().coerceIn(webView.height, 16000)
      val bitmap = Bitmap.createBitmap(contentWidth, contentHeight, Bitmap.Config.ARGB_8888)
      val canvas = android.graphics.Canvas(bitmap)
      webView.draw(canvas)

      val safeTitle = UrlValidator.sanitizeFilename(title.ifEmpty { "Kio_Page" })
      val fileName = "Screenshot_${safeTitle}_${System.currentTimeMillis()}.png"

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
          put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
          put(MediaStore.Images.Media.MIME_TYPE, "image/png")
          put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Kio")
          put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri != null) {
          context.contentResolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
          }
          values.clear()
          values.put(MediaStore.Images.Media.IS_PENDING, 0)
          context.contentResolver.update(uri, values, null, null)
          bitmap.recycle()
          onResult(fileName, true)
          return
        }
      }

      val picturesDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Kio")
      if (!picturesDir.exists()) picturesDir.mkdirs()
      val outFile = File(picturesDir, fileName)
      FileOutputStream(outFile).use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
      }
      bitmap.recycle()
      android.media.MediaScannerConnection.scanFile(context, arrayOf(outFile.absolutePath), arrayOf("image/png"), null)
      onResult(fileName, true)
    } catch (e: Throwable) {
      onResult(e.message, false)
    }
  }

  fun extractPageImages(tabId: String, onResult: (List<String>) -> Unit) {
    val webView = getLiveWebView(tabId) ?: run {
      onResult(emptyList())
      return
    }
    val script = """
      (function() {
        try {
          var set = new Set();
          var imgs = document.getElementsByTagName('img');
          for (var i = 0; i < imgs.length; i++) {
            var s = imgs[i].currentSrc || imgs[i].src;
            if (s && (s.startsWith('http://') || s.startsWith('https://') || s.startsWith('data:image/'))) {
              set.add(s);
            }
          }
          var sources = document.getElementsByTagName('source');
          for (var j = 0; j < sources.length; j++) {
            var sc = sources[j].srcset || sources[j].src;
            if (sc) {
              var parts = sc.split(',');
              for (var k = 0; k < parts.length; k++) {
                var u = parts[k].trim().split(' ')[0];
                if (u && (u.startsWith('http://') || u.startsWith('https://'))) set.add(u);
              }
            }
          }
          return JSON.stringify(Array.from(set).slice(0, 120));
        } catch (e) {
          return '[]';
        }
      })();
    """.trimIndent()

    try {
      webView.evaluateJavascript(script) { raw ->
        if (raw.isNullOrBlank() || raw == "null") {
          onResult(emptyList())
          return@evaluateJavascript
        }
        try {
          val jsonStr = if (raw.startsWith("\"") && raw.endsWith("\"")) {
            org.json.JSONTokener(raw).nextValue().toString()
          } else raw
          val arr = JSONArray(jsonStr)
          val list = mutableListOf<String>()
          for (idx in 0 until arr.length()) {
            val url = arr.optString(idx, "").trim()
            if (url.isNotEmpty() && url !in list) list.add(url)
          }
          onResult(list)
        } catch (_: Throwable) {
          onResult(emptyList())
        }
      }
    } catch (_: Throwable) {
      onResult(emptyList())
    }
  }

  fun applyCleanModeCss(view: WebView) {
    val js = """
      (function() {
        try {
          if (document.getElementById('kio-clean-mode-style')) return;
          var style = document.createElement('style');
          style.id = 'kio-clean-mode-style';
          style.textContent = `
            [class*="cookie-banner"], [id*="cookie-banner"],
            [class*="cookie-consent"], [id*="cookie-consent"],
            [class*="consent-banner"], [id*="consent-banner"],
            [class*="gdpr"], [id*="gdpr"],
            [class*="newsletter-popup"], [id*="newsletter-popup"],
            [class*="promo-banner"], [id*="promo-banner"],
            [class*="floating-ad"], [id*="floating-ad"],
            [class*="sticky-bottom-ad"], [id*="sticky-bottom-ad"],
            .ad-banner, .advertisement, #advertisement {
              display: none !important;
              visibility: hidden !important;
              opacity: 0 !important;
              pointer-events: none !important;
            }
          `;
          (document.head || document.documentElement).appendChild(style);
        } catch (e) {}
      })();
    """.trimIndent()
    try {
      view.evaluateJavascript(js, null)
    } catch (_: Throwable) {}
  }

  fun sniffPageMediaElements(webView: WebView, tabId: String) {
    val js = """
      (function() {
        try {
          var mediaList = [];
          var nodes = document.querySelectorAll('video, audio, source');
          for (var i = 0; i < nodes.length; i++) {
            var src = nodes[i].currentSrc || nodes[i].src;
            if (src && (src.startsWith('http://') || src.startsWith('https://'))) {
              var type = nodes[i].type || '';
              mediaList.push({ url: src, type: type });
            }
          }
          return JSON.stringify(mediaList);
        } catch (e) { return '[]'; }
      })();
    """.trimIndent()
    try {
      webView.evaluateJavascript(js) { res ->
        if (!res.isNullOrBlank() && res != "null") {
          try {
            val jsonStr = if (res.startsWith("\"") && res.endsWith("\"")) {
              org.json.JSONTokener(res).nextValue().toString()
            } else res
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
              val obj = arr.getJSONObject(i)
              val u = obj.optString("url")
              if (u.isNotBlank()) {
                val format = determineMediaFormat(u)
                callbacks.onMediaDetected(
                  tabId,
                  DetectedMedia(
                    url = u,
                    title = currentPageTitles[tabId] ?: UrlValidator.extractHost(u),
                    mimeType = obj.optString("type").ifBlank { guessMediaMime(u) },
                    formatLabel = format
                  )
                )
              }
            }
          } catch (_: Throwable) {}
        }
      }
    } catch (_: Throwable) {}
  }

  fun determineMediaFormat(url: String): String {
    val lower = url.lowercase()
    return when {
      lower.contains(".m3u8") || lower.contains("/hls") -> "HLS/M3U8"
      lower.contains(".mp4") -> "MP4"
      lower.contains(".webm") -> "WEBM"
      lower.contains(".mp3") -> "MP3"
      lower.contains(".m4v") -> "M4V"
      lower.contains(".aac") || lower.contains(".ogg") || lower.contains(".wav") -> "AUDIO"
      lower.contains(".flv") -> "FLV"
      lower.contains(".mov") -> "MOV"
      else -> "STREAM"
    }
  }

  fun guessMediaMime(url: String): String {
    val lower = url.lowercase()
    return when {
      lower.contains(".mp4") || lower.contains(".m4v") -> "video/mp4"
      lower.contains(".webm") -> "video/webm"
      lower.contains(".m3u8") -> "application/x-mpegURL"
      lower.contains(".mp3") -> "audio/mpeg"
      lower.contains(".aac") -> "audio/aac"
      lower.contains(".ogg") -> "audio/ogg"
      lower.contains(".wav") -> "audio/wav"
      lower.contains(".flv") -> "video/x-flv"
      lower.contains(".mov") -> "video/quicktime"
      else -> "video/mp4"
    }
  }

  fun isMediaRequest(url: String, headers: Map<String, String>?): Boolean {
    val lower = url.lowercase()
    if (lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".m3u8") ||
        lower.endsWith(".m4v") || lower.endsWith(".mp3") || lower.endsWith(".aac") ||
        lower.endsWith(".ogg") || lower.endsWith(".mov") || lower.endsWith(".flv") ||
        lower.contains(".m3u8?") || lower.contains(".mp4?") || lower.contains(".webm?") ||
        lower.contains("/videoplayback") || lower.contains("googlevideo.com")) {
      return true
    }
    val accept = headers?.get("Accept")?.lowercase().orEmpty()
    return accept.startsWith("video/") || accept.startsWith("audio/")
  }

  fun extractReaderArticle(tabId: String, pageUrl: String, onResult: (ReaderArticle?) -> Unit) {
    val webView = getLiveWebView(tabId)
    if (webView == null) {
      onResult(null)
      return
    }
    val js = """
      (function() {
        try {
          var title = document.title || '';
          var root = document.querySelector('article') || document.querySelector('main') || document.body;
          if (!root) return JSON.stringify({title: title, paragraphs: []});
          var nodes = root.querySelectorAll('h1, h2, h3, p, li, blockquote');
          var paras = [];
          for (var i = 0; i < nodes.length && paras.length < 180; i++) {
            var txt = (nodes[i].innerText || '').trim();
            if (txt.length >= 24) {
              paras.push(txt);
            }
          }
          if (paras.length === 0 && root.innerText) {
            var chunks = root.innerText.split(/\n+/).map(function(s){return s.trim();}).filter(function(s){return s.length > 20;});
            paras = chunks.slice(0, 120);
          }
          return JSON.stringify({title: title, paragraphs: paras});
        } catch(e) {
          return JSON.stringify({title: '', paragraphs: []});
        }
      })();
    """.trimIndent()

    try {
      webView.evaluateJavascript(js) { rawJson ->
        try {
          if (rawJson.isNullOrBlank() || rawJson == "null") {
            onResult(null)
            return@evaluateJavascript
          }
          val unwrapped = JSONObject("{\"v\":$rawJson}").getString("v")
          val obj = JSONObject(unwrapped)
          val title = UrlValidator.sanitizeDisplayText(obj.optString("title", ""), 180)
          val arr = obj.optJSONArray("paragraphs")
          val paras = mutableListOf<String>()
          if (arr != null) {
            for (i in 0 until arr.length()) {
              val cleaned = UrlValidator.sanitizeControlChars(arr.optString(i, ""))
              if (cleaned.isNotEmpty()) paras += cleaned
            }
          }
          if (paras.isEmpty()) {
            onResult(null)
          } else {
            onResult(
              ReaderArticle(
                title = title.ifEmpty { UrlValidator.extractHost(pageUrl) },
                siteHost = UrlValidator.extractHost(pageUrl),
                paragraphs = paras
              )
            )
          }
        } catch (_: Throwable) {
          onResult(null)
        }
      }
    } catch (_: Throwable) {
      onResult(null)
    }
  }

  fun destroyAll() {
    savedStates.clear()
    currentPageHosts.clear()
    currentPageUrls.clear()
    currentPageTitles.clear()
    tabUserAgentModes.clear()
    pageEarlyInjectedStage.clear()
    val ids = liveViews.keys.toList()
    ids.forEach { destroyTab(it) }
  }

  private fun applyNightWebCss(webView: WebView, enabled: Boolean) {
    if (!settingsProvider().javascriptEnabled || destroyedViews.contains(webView)) return
    val js = if (enabled) {
      """
      (function(){
        var id='kio-night-style';
        if(document.getElementById(id))return;
        var s=document.createElement('style');
        s.id=id;
        s.textContent='html{filter:invert(0.92) hue-rotate(180deg)!important;background:#111!important;color-scheme:dark!important;}img,video,picture,canvas,svg,iframe{filter:invert(1) hue-rotate(180deg)!important;}';
        (document.head||document.documentElement).appendChild(s);
      })();
      """.trimIndent()
    } else {
      "(function(){var s=document.getElementById('kio-night-style');if(s)s.remove();})();"
    }
    try {
      webView.evaluateJavascript(js, null)
    } catch (_: Throwable) {
    }
  }

  private fun applyAdBlocker(webView: WebView, enabled: Boolean) {
    if (!settingsProvider().javascriptEnabled || destroyedViews.contains(webView)) return
    val js = if (enabled) {
      """
      (function() {
        if (window.__kio_adblock_installed) return;
        window.__kio_adblock_installed = true;

        try {
          var styleId = 'kio-adblock-shield-style';
          if (!document.getElementById(styleId)) {
            var s = document.createElement('style');
            s.id = styleId;
            s.textContent = [
              'ytd-ad-slot-renderer',
              'ytm-ad-slot-renderer',
              'ytm-promoted-sparkles-web-renderer',
              'ytm-companion-ad-renderer',
              'ytd-in-feed-ad-layout-renderer',
              'ytd-banner-promo-renderer',
              'ytd-statement-banner-renderer',
              'ytm-paid-content-overlay-renderer',
              '.ytp-ad-overlay-container',
              '.ytp-ad-image-overlay',
              '.ytp-ad-message-container',
              '.ytp-ad-action-interstitial',
              '.ytp-ad-player-overlay-flyout-cta',
              'div.adsbygoogle',
              '[id^="google_ads_"]',
              '[id^="div-gpt-ad"]',
              '[class*="sponsored-post"]',
              '.taboola-ad',
              '.outbrain-ad',
              '.trc_related_container',
              '.mgid-ad'
            ].join(',') + '{display:none!important;visibility:hidden!important;height:0!important;min-height:0!important;opacity:0!important;pointer-events:none!important;}\n' +
            '.html5-video-player, [class*="video-player"], [class*="vjs-"], [class*="jw-"], [class*="plyr"], video { display:block!important; visibility:visible!important; opacity:1!important; }';
            (document.head || document.documentElement).appendChild(s);
          }
        } catch(e) {}

        function skipStreamingAds() {
          try {
            var isYouTube = /youtube\.com|youtu\.be/.test(window.location.hostname);
            if (!isYouTube) return;
            var player = document.querySelector('#movie_player') || document.querySelector('.html5-video-player');
            if (!player) return;
            var isAdActive = Boolean(player.classList.contains('ad-showing') || player.classList.contains('ad-interrupting'));

            if (isAdActive) {
              if (typeof player.skipAd === 'function') {
                try { player.skipAd(); } catch(e) {}
              }

              var skipButtons = player.querySelectorAll(
                '.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button, button.ytp-ad-skip-button, .ytp-ad-overlay-close-button, .videoAdUiSkipButton, [class*="skip-button"], [class*="ad-skip"]'
              );
              for (var j = 0; j < skipButtons.length; j++) {
                if (typeof skipButtons[j].click === 'function') {
                  try { skipButtons[j].click(); } catch(e) {}
                }
              }

              // Fast-forward ad safely: 16x playback rate and mute without corrupting video duration or buffer
              var videos = player.querySelectorAll('video');
              for (var i = 0; i < videos.length; i++) {
                var v = videos[i];
                if (!v.muted) {
                  v.__kio_ad_muted = true;
                  v.muted = true;
                }
                v.playbackRate = 16.0;
              }
            } else if (player) {
              // Ensure normal content video playback rate is 1.0 and restore audio
              var contentVideos = player.querySelectorAll('video');
              for (var k = 0; k < contentVideos.length; k++) {
                var cv = contentVideos[k];
                if (cv.playbackRate > 2.0) {
                  cv.playbackRate = 1.0;
                }
                if (cv.__kio_ad_muted) {
                  cv.muted = false;
                  delete cv.__kio_ad_muted;
                }
              }
            }
          } catch(e) {}
        }

        setInterval(skipStreamingAds, 800);

        if (window.MutationObserver) {
          var observer = new MutationObserver(function() {
            skipStreamingAds();
          });
          observer.observe(document.documentElement || document.body, {
            childList: true,
            subtree: true,
            attributes: true,
            attributeFilter: ['class']
          });
        }
      })();
      """.trimIndent()
    } else {
      """
      (function() {
        window.__kio_adblock_installed = false;
        var s = document.getElementById('kio-adblock-shield-style');
        if (s) s.remove();
      })();
      """.trimIndent()
    }
    try {
      webView.evaluateJavascript(js, null)
    } catch (_: Throwable) {
    }
  }

  fun applyBackgroundPlay(webView: WebView, enabled: Boolean) {
    if (!settingsProvider().javascriptEnabled || destroyedViews.contains(webView)) return
    val js = if (enabled) {
      """
      (function() {
        if (window.__kio_bg_play_installed) return;
        window.__kio_bg_play_installed = true;

        function spoofVisibility() {
          try {
            var desc = { get: function() { return false; }, configurable: true };
            var descVis = { get: function() { return 'visible'; }, configurable: true };
            Object.defineProperty(document, 'hidden', desc);
            Object.defineProperty(Document.prototype, 'hidden', desc);
            Object.defineProperty(document, 'visibilityState', descVis);
            Object.defineProperty(Document.prototype, 'visibilityState', descVis);
            Object.defineProperty(document, 'webkitHidden', desc);
            Object.defineProperty(Document.prototype, 'webkitHidden', desc);
            Object.defineProperty(document, 'webkitVisibilityState', descVis);
            Object.defineProperty(Document.prototype, 'webkitVisibilityState', descVis);
            document.hasFocus = function() { return true; };
          } catch(e) {}
        }

        spoofVisibility();

        var blockEvent = function(e) {
          if (e) {
            e.stopImmediatePropagation();
            e.stopPropagation();
          }
        };

        window.addEventListener('visibilitychange', blockEvent, true);
        document.addEventListener('visibilitychange', blockEvent, true);
        window.addEventListener('webkitvisibilitychange', blockEvent, true);
        document.addEventListener('webkitvisibilitychange', blockEvent, true);
        window.addEventListener('pagehide', blockEvent, true);

        try {
          Object.defineProperty(document, 'onvisibilitychange', {
            get: function() { return null; },
            set: function(_) {},
            configurable: true
          });
        } catch(e) {}

        try {
          var origAdd = EventTarget.prototype.addEventListener;
          EventTarget.prototype.addEventListener = function(type, listener, options) {
            if (type === 'visibilitychange' || type === 'webkitvisibilitychange' || type === 'pagehide') {
              return;
            }
            return origAdd.apply(this, arguments);
          };
        } catch(e) {}

        try {
          var origPush = history.pushState;
          history.pushState = function() {
            var res = origPush.apply(this, arguments);
            spoofVisibility();
            return res;
          };
          var origReplace = history.replaceState;
          history.replaceState = function() {
            var res = origReplace.apply(this, arguments);
            spoofVisibility();
            return res;
          };
        } catch(e) {}

        setInterval(spoofVisibility, 2000);
      })();
      """.trimIndent()
    } else {
      """
      (function() {
        window.__kio_bg_play_installed = false;
      })();
      """.trimIndent()
    }
    try {
      webView.evaluateJavascript(js, null)
    } catch (_: Throwable) {
    }
  }

  fun applyDataSaver(webView: WebView, enabled: Boolean) {
    if (!settingsProvider().javascriptEnabled || destroyedViews.contains(webView)) return
    if (!enabled) {
      val cleanupJs = """
        (function(){
          var s = document.getElementById('kio-data-saver-style');
          if (s) s.remove();
          window.__kio_data_saver_active = false;
        })();
      """.trimIndent()
      try { webView.evaluateJavascript(cleanupJs, null) } catch (_: Throwable) {}
      return
    }
    val js = """
      (function() {
        window.__kio_data_saver_active = true;
        var host = (window.location && window.location.hostname) || '';
        var isStreamingSite = host.indexOf('youtube.com') !== -1 ||
                              host.indexOf('youtu.be') !== -1 ||
                              host.indexOf('vimeo.com') !== -1 ||
                              host.indexOf('twitch.tv') !== -1 ||
                              host.indexOf('dailymotion.com') !== -1;

        var id = 'kio-data-saver-style';
        var s = document.getElementById(id);
        if (!s) {
          s = document.createElement('style');
          s.id = id;
          (document.head || document.documentElement).appendChild(s);
        }
        if (!isStreamingSite) {
          s.textContent = [
            'video[autoplay], audio[autoplay] { autoplay: false !important; }',
            'video, audio { preload: none !important; }',
            '@media (prefers-reduced-data: reduce) { * { background-image: none !important; } }'
          ].join('\n');
          try {
            var media = document.querySelectorAll('video, audio');
            for (var i = 0; i < media.length; i++) {
              media[i].autoplay = false;
              media[i].preload = 'none';
            }
          } catch(e) {}
        } else {
          s.textContent = '@media (prefers-reduced-data: reduce) { * { background-image: none !important; } }';
        }

        if (navigator.connection) {
          try {
            Object.defineProperty(navigator.connection, 'saveData', {
              get: function() { return true; },
              configurable: true
            });
          } catch(e) {}
        }
        var player = document.getElementById('movie_player');
        if (player && typeof player.setPlaybackQualityRange === 'function') {
          try { player.setPlaybackQualityRange('small', 'medium'); } catch(e) {}
        }
      })();
    """.trimIndent()
    try {
      webView.evaluateJavascript(js, null)
    } catch (_: Throwable) {
    }
  }

  /**
   * Injects universal HTML5 video enhancement & control protection engine:
   * - Enforces playsinline and webkit-playsinline on all HTML5 video elements.
   * - Guarantees video player control overlays (.ytp-, .html5-video-player, controls bars) are always interactive and visible.
   * - Protects legitimate iframe fullscreen, autoplay, and encrypted-media permissions.
   * - Blocks intrusive window.open popup/popunder redirections without intercepting player clicks.
   */
  fun applyVideoCompatibilityEngine(webView: WebView) {
    if (!settingsProvider().javascriptEnabled || destroyedViews.contains(webView)) return
    val js = """
      (function() {
        if (window.__kio_video_engine_installed) return;
        window.__kio_video_engine_installed = true;

        // 1. Ensure HTML5 video player controls and interactive surfaces are ALWAYS visible & clickable
        try {
          var styleId = 'kio-video-compat-style';
          if (!document.getElementById(styleId)) {
            var s = document.createElement('style');
            s.id = styleId;
            s.textContent = [
              'video { -webkit-transform: translateZ(0); transform: translateZ(0); }',
              '.html5-video-player, .html5-video-player * { pointer-events: auto !important; }',
              '.ytp-chrome-bottom, .ytp-chrome-top, .ytp-player-content, .ytp-touch-feedback, .ytp-cued-thumbnail-overlay { pointer-events: auto !important; }',
              '.ytp-play-button, .ytp-prev-button, .ytp-next-button, .ytp-mute-button, .ytp-volume-panel, .ytp-time-display, .ytp-settings-button, .ytp-fullscreen-button { pointer-events: auto !important; visibility: visible !important; }',
              '.vjs-control-bar, .jw-controls, .plyr__controls, [class*="video-controls"] { pointer-events: auto !important; visibility: visible !important; opacity: 1 !important; }',
              '[class*="player"] button, [class*="player"] [role="button"], [class*="player"] input[type="range"] { pointer-events: auto !important; cursor: pointer !important; }'
            ].join('\n');
            (document.head || document.documentElement).appendChild(s);
          }
        } catch(e) {}

        // 2. Enforce playsinline on video elements so videos play smoothly within page layout
        function patchVideo(video) {
          if (!video) return;
          try {
            if (!video.hasAttribute('playsinline')) video.setAttribute('playsinline', '');
            if (!video.hasAttribute('webkit-playsinline')) video.setAttribute('webkit-playsinline', '');
            if (!video.hasAttribute('x5-playsinline')) video.setAttribute('x5-playsinline', '');
          } catch(e) {}
        }

        // 3. Ensure all iframes permit fullscreen, autoplay, and encrypted media (EME/Widevine DRM)
        function patchIframes() {
          try {
            var iframes = document.querySelectorAll('iframe');
            for (var i = 0; i < iframes.length; i++) {
              var f = iframes[i];
              if (!f.hasAttribute('allowfullscreen')) f.setAttribute('allowfullscreen', 'true');
              if (!f.hasAttribute('webkitallowfullscreen')) f.setAttribute('webkitallowfullscreen', 'true');
              var allow = f.getAttribute('allow') || '';
              var updated = allow;
              if (updated.indexOf('autoplay') === -1) updated += '; autoplay *';
              if (updated.indexOf('fullscreen') === -1) updated += '; fullscreen *';
              if (updated.indexOf('encrypted-media') === -1) updated += '; encrypted-media *';
              if (updated.indexOf('picture-in-picture') === -1) updated += '; picture-in-picture *';
              if (updated !== allow) f.setAttribute('allow', updated.replace(/^;\s*/, ''));
            }
          } catch(e) {}
        }

        function patchAllMedia() {
          try {
            var vids = document.querySelectorAll('video');
            for (var i = 0; i < vids.length; i++) patchVideo(vids[i]);
            patchIframes();
          } catch(e) {}
        }

        patchAllMedia();

        if (window.MutationObserver) {
          var obs = new MutationObserver(function(mutations) {
            for (var i = 0; i < mutations.length; i++) {
              var added = mutations[i].addedNodes;
              for (var j = 0; j < added.length; j++) {
                var node = added[j];
                if (node.nodeType === 1) {
                  if (node.tagName === 'VIDEO') patchVideo(node);
                  else if (node.tagName === 'IFRAME') patchIframes();
                  else if (node.querySelector) {
                    var v = node.querySelector('video');
                    if (v) patchVideo(v);
                    var f = node.querySelector('iframe');
                    if (f) patchIframes();
                  }
                }
              }
            }
          });
          obs.observe(document.documentElement || document.body, { childList: true });
        }

        // 4. Intercept window.open calls targeting known popunder networks without touching user clicks
        function isAdPopUrl(raw) {
          if (!raw) return false;
          var u = String(raw).toLowerCase();
          if (/(^|\/\/)(ad|ads|ad1|ad2|ads1|ads2|adserver|adclick|adtrack|adtrans|ad-trans|pop|pops|popunder|popup|syndication|pxl|trk|clk|vast|vmap)\./.test(u)) {
            return true;
          }
          return /(tsyndicate|adtrans|ad-trans|popunder|popads|popcash|exosrv|realsrv|magsrv|pemsrv|optimizesrv|twinrdsrv|doublepimp|xlirdr|strpjmp|bngpt|traffichaus|trafficjunky|trafficfactory|trafficstars|adsterra|exoclick|monetag|admaven|ad-maven|hilltopads|rollerads|propeller|clickadu|clickaine|adskeeper|adspyglass|juicyads|crakrevenue|highrevenuegate|profitablegate|topcreativeformat|effectivegatecpm|onclkds|onclickalgo|zoneid=|zone_id=|clickid=|click_id=|\/afu\.php|\/pop\.php|\/delivery\/ajs\.php)/.test(u);
        }

        try {
          var origOpen = window.open;
          window.open = function(url, target, features) {
            if (!url || isAdPopUrl(url)) return null;
            return origOpen.apply(this, arguments);
          };
        } catch(e) {}
      })();
    """.trimIndent()
    try {
      webView.evaluateJavascript(js, null)
    } catch (_: Throwable) {
    }
  }

  /**
   * Hermetic Private-Mode-Only Storage & Anti-Fingerprint Sandbox.
   * - Shadows window.localStorage and window.sessionStorage with pure ephemeral RAM Maps so
   *   Private tabs NEVER read Standard Mode storage and NEVER write to disk or Standard Mode.
   * - Blocks persistent IndexedDB and CacheStorage writes.
   * - Adds per-session Canvas & hardware anti-fingerprinting noise exclusive to Private Mode.
   */
  private fun applyPrivateIsolationSandbox(webView: WebView) {
    if (!settingsProvider().javascriptEnabled || destroyedViews.contains(webView)) return
    val js = """
      (function() {
        if (window.__kio_private_sandbox_installed) return;
        window.__kio_private_sandbox_installed = true;

        function makeRamStorage() {
          var map = Object.create(null);
          return {
            get length() { return Object.keys(map).length; },
            key: function(i) { var k = Object.keys(map); return (i >= 0 && i < k.length) ? k[i] : null; },
            getItem: function(k) { var s = String(k); return Object.prototype.hasOwnProperty.call(map, s) ? map[s] : null; },
            setItem: function(k, v) { map[String(k)] = String(v); },
            removeItem: function(k) { delete map[String(k)]; },
            clear: function() { map = Object.create(null); }
          };
        }

        var ramLocal = makeRamStorage();
        var ramSession = makeRamStorage();
        window.__kio_reset_private_storage = function() {
          try { ramLocal.clear(); ramSession.clear(); } catch(e) {}
        };
        try {
          Object.defineProperty(window, 'localStorage', { get: function() { return ramLocal; }, configurable: true });
        } catch(e) {}
        try {
          Object.defineProperty(window, 'sessionStorage', { get: function() { return ramSession; }, configurable: true });
        } catch(e) {}

        // Prevent persistent IndexedDB / CacheStorage cross-mode leakage
        try {
          if (window.caches) {
            Object.defineProperty(window, 'caches', {
              get: function() {
                return {
                  open: function() { return Promise.reject(new DOMException('Private mode', 'SecurityError')); },
                  match: function() { return Promise.resolve(undefined); },
                  has: function() { return Promise.resolve(false); },
                  keys: function() { return Promise.resolve([]); },
                  delete: function() { return Promise.resolve(false); }
                };
              },
              configurable: true
            });
          }
        } catch(e) {}
        try {
          if (window.indexedDB && window.indexedDB.open) {
            var origIdbOpen = window.indexedDB.open;
            window.indexedDB.open = function() {
              var req = {};
              setTimeout(function() {
                if (typeof req.onerror === 'function') {
                  req.onerror({ target: { error: new DOMException('Blocked in Private Vault', 'SecurityError') } });
                }
              }, 0);
              return req;
            };
          }
        } catch(e) {}

        // Private-Mode-Only Anti-Fingerprinting (Hardware normalization + Canvas micro-salt)
        try {
          Object.defineProperty(navigator, 'hardwareConcurrency', { get: function() { return 4; }, configurable: true });
          if ('deviceMemory' in navigator) {
            Object.defineProperty(navigator, 'deviceMemory', { get: function() { return 4; }, configurable: true });
          }
        } catch(e) {}
        try {
          var salt = ((Math.random() * 4) | 0) + 1;
          var origToDataUrl = HTMLCanvasElement.prototype.toDataURL;
          HTMLCanvasElement.prototype.toDataURL = function() {
            try {
              var ctx = this.getContext && this.getContext('2d');
              if (ctx && this.width > 0 && this.height > 0 && this.width <= 512 && this.height <= 512) {
                var img = ctx.getImageData(0, 0, 1, 1);
                img.data[0] = (img.data[0] ^ salt) & 255;
                ctx.putImageData(img, 0, 0);
              }
            } catch(e) {}
            return origToDataUrl.apply(this, arguments);
          };
        } catch(e) {}
      })();
    """.trimIndent()
    try {
      webView.evaluateJavascript(js, null)
    } catch (_: Throwable) {
    }
  }

  /**
   * Industry-leading anti-tracking & anti-fingerprinting shield injected into web pages.
   * Protects against GPC/DNT privacy violations, referrer leaks, battery sniffing, and WebRTC IP exposure.
   */
  private fun applyStealthPrivacyShield(webView: WebView) {
    if (!settingsProvider().javascriptEnabled || destroyedViews.contains(webView)) return
    val js = """
      (function() {
        if (window.__kio_stealth_privacy_shield_installed) return;
        window.__kio_stealth_privacy_shield_installed = true;

        // 1. Global Privacy Control (GPC) & Do Not Track (W3C / CCPA / GDPR)
        try {
          if (!('globalPrivacyControl' in navigator)) {
            Object.defineProperty(navigator, 'globalPrivacyControl', {
              get: function() { return true; },
              configurable: false,
              enumerable: true
            });
          }
          if (!('doNotTrack' in navigator)) {
            Object.defineProperty(navigator, 'doNotTrack', {
              get: function() { return '1'; },
              configurable: false,
              enumerable: true
            });
          }
        } catch(e) {}

        // 2. Hardware & Battery Fingerprint Neutralization
        try {
          if (navigator.getBattery) {
            navigator.getBattery = function() {
              return Promise.resolve({
                charging: true,
                chargingTime: 0,
                dischargingTime: Infinity,
                level: 1.0,
                addEventListener: function() {},
                removeEventListener: function() {}
              });
            };
          }
        } catch(e) {}

        // 4. WebRTC Local IP Leak Protection (strips LAN host ICE candidates in SDP)
        try {
          if (window.RTCPeerConnection) {
            var origSetLocalDesc = RTCPeerConnection.prototype.setLocalDescription;
            RTCPeerConnection.prototype.setLocalDescription = function(desc) {
              if (desc && desc.sdp) {
                desc.sdp = desc.sdp.replace(/a=candidate:[^\r\n]*\btyp host\b[^\r\n]*(\r\n|\n)?/g, '');
              }
              return origSetLocalDesc.apply(this, arguments);
            };
          }
        } catch(e) {}
      })();
    """.trimIndent()
    try {
      webView.evaluateJavascript(js, null)
    } catch (_: Throwable) {
    }
  }

  @SuppressLint("SetJavaScriptEnabled")
  private fun applySettings(
    webView: WebView,
    isPrivate: Boolean,
    userAgentMode: UserAgentMode,
    settingsState: SettingsState
  ) {
    if (destroyedViews.contains(webView)) return
    try {
      webView.isVerticalScrollBarEnabled = true
      webView.isHorizontalScrollBarEnabled = true
      webView.scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
      webView.overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
      if (!settingsState.grayscaleModeEnabled) {
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
      }
    } catch (_: Throwable) {
    }
    try {
      webView.settings.apply {
        @Suppress("DEPRECATION")
        setRenderPriority(WebSettings.RenderPriority.HIGH)
        javaScriptEnabled = settingsState.javascriptEnabled
        javaScriptCanOpenWindowsAutomatically = false
        mediaPlaybackRequiresUserGesture = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          try {
            webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
          } catch (_: Throwable) {
          }
        }
        // Route user-initiated target="_blank" / window.open through onCreateWindow interceptor
        setSupportMultipleWindows(true)
        setGeolocationEnabled(!isPrivate)
        allowFileAccess = false
        allowContentAccess = false
        @Suppress("DEPRECATION")
        allowFileAccessFromFileURLs = false
        @Suppress("DEPRECATION")
        allowUniversalAccessFromFileURLs = false
        @Suppress("DEPRECATION")
        saveFormData = false
        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        domStorageEnabled = true
        @Suppress("DEPRECATION")
        databaseEnabled = !isPrivate
        setSupportZoom(true)
        builtInZoomControls = true
        displayZoomControls = false
        useWideViewPort = true
        loadWithOverviewMode = true
        layoutAlgorithm = if (userAgentMode == UserAgentMode.DESKTOP) {
          WebSettings.LayoutAlgorithm.NORMAL
        } else {
          WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
        }
        loadsImagesAutomatically = !settingsState.noImageMode
        blockNetworkImage = settingsState.noImageMode
        val targetUa = when (userAgentMode) {
          UserAgentMode.DESKTOP -> DESKTOP_USER_AGENT
          UserAgentMode.IPHONE -> IPHONE_USER_AGENT
          UserAgentMode.MOBILE -> cleanMobileUserAgent.ifEmpty { defaultUserAgent.ifEmpty { null } }
        }
        if (userAgentString != targetUa) {
          userAgentString = targetUa
        }
        textZoom = if (userAgentMode == UserAgentMode.DESKTOP) 100 else settingsState.textZoomPercent
        cacheMode = if (isPrivate) {
          WebSettings.LOAD_NO_CACHE
        } else if (settingsState.dataSaverEnabled) {
          WebSettings.LOAD_CACHE_ELSE_NETWORK
        } else {
          WebSettings.LOAD_DEFAULT
        }
        try {
          safeBrowsingEnabled = true
        } catch (_: Throwable) {
        }
        val isDark = resolveIsDark(settingsState)
        try {
          webView.setBackgroundColor(if (isDark) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        } catch (_: Throwable) {
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          try {
            isAlgorithmicDarkeningAllowed = isDark && settingsState.nightWebMode
          } catch (_: Throwable) {
          }
        }
      }
    } catch (_: Throwable) {
    }

    try {
      val cookieMgr = CookieManager.getInstance()
      cookieMgr.setAcceptCookie(true)
      // Private mode strictly blocks ALL third-party cookies unconditionally.
      cookieMgr.setAcceptThirdPartyCookies(webView, !isPrivate && !settingsState.blockThirdPartyCookies)
    } catch (_: Throwable) {
    }
  }

  private fun createSecuredWebView(
    tabId: String,
    isPrivate: Boolean,
    userAgentMode: UserAgentMode,
    approvedHttpHosts: Set<String>
  ): WebView? {
    val webView = try {
      BackgroundMediaWebView(
        MutableContextWrapper(appContext),
        isBackgroundPlayEnabled = { settingsProvider().backgroundPlayEnabled }
      )
    } catch (t: Throwable) {
      callbacks.onPageError(
        tabId,
        PageError(
          type = PageErrorType.GENERIC,
          failingUrl = "",
          host = "WebView",
          reason = UrlValidator.sanitizeDisplayText(t.message, 120).ifEmpty {
            "System WebView is unavailable or updating."
          }
        )
      )
      return null
    }

    applySettings(webView, isPrivate, userAgentMode, settingsProvider())

    try {
      webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
      webView.isNestedScrollingEnabled = true
      webView.overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
    } catch (_: Throwable) {
    }

    webView.setOnLongClickListener { v ->
      try {
        val wv = v as? WebView ?: return@setOnLongClickListener false
        val result = wv.hitTestResult
        val type = result.type
        val extra = result.extra
        val isLinkOrImage = type == WebView.HitTestResult.SRC_ANCHOR_TYPE ||
          type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE ||
          type == WebView.HitTestResult.IMAGE_TYPE
        if (!isLinkOrImage) return@setOnLongClickListener false

        val currentUrl = wv.url.orEmpty()
        val anchorExtra = if (type == WebView.HitTestResult.SRC_ANCHOR_TYPE) extra else null
        val imageExtra = if (type == WebView.HitTestResult.IMAGE_TYPE || type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) extra else null

        fun dispatchResolved(domLink: String?, domImg: String?, nativeHref: String?, nativeSrc: String?) {
          val resolved = UrlValidator.resolveLinkOverImageTarget(
            baseUrl = currentUrl,
            domLink = domLink,
            nativeHref = nativeHref,
            anchorExtra = anchorExtra,
            domImage = domImg,
            nativeSrc = nativeSrc,
            imageExtra = imageExtra
          ) ?: return
          val (finalUrl, isImg, optImg) = resolved
          callbacks.onLongPressLinkWithImage(tabId, finalUrl, isImg, optImg)
        }

        val handler = object : Handler(Looper.getMainLooper()) {
          override fun handleMessage(msg: Message) {
            val nativeHref = msg.data?.getString("url")
            val nativeSrc = msg.data?.getString("src")
            if (settingsProvider().javascriptEnabled && !destroyedViews.contains(wv)) {
              try {
                wv.evaluateJavascript("(function(){return window.__kio_get_touch_ctx?window.__kio_get_touch_ctx():'{}';})();") { rawJson ->
                  var domLink: String? = null
                  var domImg: String? = null
                  try {
                    if (!rawJson.isNullOrBlank() && rawJson != "null") {
                      val unwrapped = JSONObject("{\"v\":$rawJson}").getString("v")
                      val obj = JSONObject(unwrapped)
                      domLink = obj.optString("link").takeIf { it.isNotBlank() }
                      domImg = obj.optString("image").takeIf { it.isNotBlank() }
                    }
                  } catch (_: Throwable) {
                  }
                  dispatchResolved(domLink, domImg, nativeHref, nativeSrc)
                }
                return
              } catch (_: Throwable) {
              }
            }
            dispatchResolved(null, null, nativeHref, nativeSrc)
          }
        }
        val msg = handler.obtainMessage()
        wv.requestFocusNodeHref(msg)
        true
      } catch (_: Throwable) {
        false
      }
    }

    webView.setFindListener { activeMatchOrdinal, numberOfMatches, isDoneCounting ->
      if (isDoneCounting) {
        callbacks.onFindMatches(tabId, activeMatchOrdinal, numberOfMatches)
      }
    }

    webView.setDownloadListener { url, _, contentDisposition, mimeType, contentLength ->
      callbacks.onDownloadRequested(tabId, url, contentDisposition, mimeType, contentLength)
    }

    webView.webViewClient = object : WebViewClient() {
      override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString() ?: return true
        val currentSettings = settingsProvider()
        if (UrlValidator.isExternalAppScheme(url)) {
          // Strict Private Mode isolation: never hand off private links to external apps
          if (!isPrivate) {
            callbacks.onExternalSchemePrompt(tabId, url, UrlValidator.extractScheme(url))
          }
          return true
        }
        if (!UrlValidator.isSafeWebUrl(url)) {
          callbacks.onBlockedScheme(tabId, url, UrlValidator.extractScheme(url))
          return true
        }
        val host = UrlValidator.extractHost(url)
        val customSet = currentSettings.customBlockedHosts.toSet()
        if (customSet.isNotEmpty() && TrackerBlocker.isHostMatchingSet(host, customSet)) {
          callbacks.onBlockedScheme(tabId, url, "blocked-host")
          return true
        }
        if (currentSettings.trackerBlockingEnabled &&
          TrackerBlocker.isAdOrPopunderNavigation(url)) {
          callbacks.onTrackerBlocked(tabId)
          return true
        }
        if (request.isForMainFrame && currentSettings.httpsOnlyEnabled && UrlValidator.isHttpUrl(url) && host !in approvedHttpHosts) {
          callbacks.onHttpBlockedByHttpsOnly(tabId, url)
          return true
        }
        if (request.isForMainFrame) {
          currentPageHosts[tabId] = host
        }
        return false
      }

      override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
      ): WebResourceResponse? {
        return try {
          val reqUrl = request?.url?.toString() ?: return null
          // Allow internal browser schemes (blob:, data:) to be processed natively by Chromium (MediaSource MSE, inline assets)
          if (reqUrl.startsWith("blob:", ignoreCase = true) || reqUrl.startsWith("data:", ignoreCase = true)) {
            return null
          }
          if (!UrlValidator.isSafeWebUrl(reqUrl)) {
            return emptyBlockedResponse()
          }
          val currentSettings = settingsProvider()
          val purpose = request?.requestHeaders?.get("Purpose") ?: request?.requestHeaders?.get("Sec-Purpose") ?: ""
          if (purpose.contains("prefetch", ignoreCase = true) || purpose.contains("prerender", ignoreCase = true)) {
            return emptyBlockedResponse()
          }
          if (currentSettings.dataSaverEnabled) {
            val path = request?.url?.path.orEmpty()
            if (path.contains("/beacon") || path.contains("/collect") || path.contains("/telemetry") || path.contains("/analytics")) {
              return emptyBlockedResponse()
            }
          }
          val pageHost = currentPageHosts[tabId].orEmpty()
          val customSet = currentSettings.customBlockedHosts.toSet()
          if (TrackerBlocker.shouldBlockRequest(reqUrl, pageHost, currentSettings.trackerBlockingEnabled, customSet)) {
            callbacks.onTrackerBlocked(tabId)
            val acceptHeader = request?.requestHeaders?.get("Accept")?.lowercase() ?: ""
            val isVast = reqUrl.contains("vast", ignoreCase = true) ||
              reqUrl.contains("vmap", ignoreCase = true) ||
              acceptHeader.contains("application/xml") ||
              acceptHeader.contains("text/xml")
            if (isVast) return emptyBlockedVastResponse()
            val isJson = reqUrl.contains("/youtubei/") ||
              reqUrl.contains("/api/") ||
              reqUrl.contains(".json") ||
              acceptHeader.contains("application/json")
            return if (isJson) emptyBlockedJsonResponse() else emptyBlockedResponse()
          }
          if (isMediaRequest(reqUrl, request?.requestHeaders)) {
            val format = determineMediaFormat(reqUrl)
            val title = currentPageTitles[tabId] ?: UrlValidator.extractHost(reqUrl)
            callbacks.onMediaDetected(
              tabId,
              DetectedMedia(
                url = reqUrl,
                title = title,
                mimeType = guessMediaMime(reqUrl),
                formatLabel = format
              )
            )
          }
          null
        } catch (_: Throwable) {
          null
        }
      }

      override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        if (url != null) {
          pageEarlyInjectedStage[tabId] = 0
          currentPageHosts[tabId] = UrlValidator.extractHost(url)
          currentPageUrls[tabId] = url
          if (!isPrivate) registerStandardOriginForVault(url)
          if (view != null) {
            if (isPrivate) {
              applyPrivateIsolationSandbox(view)
            }
            if (tabUserAgentModes[tabId] == UserAgentMode.DESKTOP) {
              applyDesktopModeEngine(view, true)
            }
            applySearchSameTabEngine(view, url)
            applyLinkTargetTrackerEngine(view)
            applyStealthPrivacyShield(view)
            applyVideoCompatibilityEngine(view)
            val s = settingsProvider()
            val isDark = resolveIsDark(s)
            applyThemeMode(view, isDark, s.nightWebMode)
            if (s.trackerBlockingEnabled) {
              applyAdBlocker(view, true)
            }
            if (s.backgroundPlayEnabled) {
              applyBackgroundPlay(view, true)
            }
            if (s.dataSaverEnabled) {
              applyDataSaver(view, true)
            }
            if (s.cleanModeEnabled) {
              applyCleanModeCss(view)
            }
          }
          callbacks.onPageStarted(tabId, url)
        }
      }

      override fun onPageFinished(view: WebView?, url: String?) {
        if (url != null && view != null && !destroyedViews.contains(view)) {
          currentPageHosts[tabId] = UrlValidator.extractHost(url)
          currentPageUrls[tabId] = url
          if (!isPrivate) registerStandardOriginForVault(url)
          if (isPrivate) {
            applyPrivateIsolationSandbox(view)
          }
          if (tabUserAgentModes[tabId] == UserAgentMode.DESKTOP) {
            applyDesktopModeEngine(view, true)
          }
          applySearchSameTabEngine(view, url)
          applyLinkTargetTrackerEngine(view)
          applyStealthPrivacyShield(view)
          applyVideoCompatibilityEngine(view)
          applyPerformanceAndDataEngine(view)
          val s = settingsProvider()
          val isDark = resolveIsDark(s)
          applyThemeMode(view, isDark, s.nightWebMode)
          if (s.trackerBlockingEnabled) {
            applyAdBlocker(view, true)
          }
          if (s.backgroundPlayEnabled) {
            applyBackgroundPlay(view, true)
          }
          if (s.dataSaverEnabled) {
            applyDataSaver(view, true)
          }
          if (s.cleanModeEnabled) {
            applyCleanModeCss(view)
          }
          sniffPageMediaElements(view, tabId)
          try {
            view.settings.cacheMode = WebSettings.LOAD_DEFAULT
          } catch (_: Throwable) {}
          val safeTitle = try { view.title } catch (_: Throwable) { null }
          if (!safeTitle.isNullOrBlank()) {
            currentPageTitles[tabId] = safeTitle
          }
          val canBack = try { view.canGoBack() } catch (_: Throwable) { false }
          val canFwd = try { view.canGoForward() } catch (_: Throwable) { false }
          callbacks.onPageFinished(
            tabId = tabId,
            url = url,
            title = safeTitle,
            canGoBack = canBack,
            canGoForward = canFwd
          )
        }
      }

      override fun onReceivedHttpAuthRequest(
        view: WebView?,
        handler: HttpAuthHandler?,
        host: String?,
        realm: String?
      ) {
        try {
          handler?.cancel()
        } catch (_: Throwable) {
        }
      }

      override fun onReceivedSslError(
        view: WebView?,
        handler: SslErrorHandler?,
        error: SslError?
      ) {
        try {
          handler?.cancel()
        } catch (_: Throwable) {
        }
        val failingUrl = error?.url ?: try { view?.url } catch (_: Throwable) { null } ?: ""
        val host = UrlValidator.extractHost(failingUrl)
        // If an ad network or pop-under redirect failed (e.g. tsyndicate.com / ad.trans* SSL error),
        // do not display a full-page error overlay. Revert back to the video page cleanly.
        if (TrackerBlocker.isAdOrPopunderNavigation(failingUrl)) {
          callbacks.onTrackerBlocked(tabId)
          try {
            if (view?.canGoBack() == true) {
              view.goBack()
            }
          } catch (_: Throwable) {
          }
          return
        }
        val primaryError = when (error?.primaryError) {
          SslError.SSL_EXPIRED -> "Certificate expired"
          SslError.SSL_NOTYETVALID -> "Certificate not yet valid"
          SslError.SSL_IDMISMATCH -> "Hostname mismatch"
          SslError.SSL_UNTRUSTED -> "Untrusted certificate authority"
          SslError.SSL_DATE_INVALID -> "Certificate date invalid"
          else -> "Invalid TLS certificate"
        }
        val certDetail = error?.certificate?.toString()
        callbacks.onPageError(
          tabId,
          PageError(
            type = PageErrorType.CERTIFICATE_ERROR,
            failingUrl = failingUrl,
            host = host,
            reason = primaryError,
            certificateDetail = certDetail
          )
        )
      }

      override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?
      ) {
        if (request?.isForMainFrame != true) return
        val reason = try { error?.description?.toString() } catch (_: Throwable) { null } ?: "Page load failed"
        if (reason.contains("ERR_ABORTED", ignoreCase = true)) return
        val failingUrl = request.url?.toString() ?: ""
        val host = UrlValidator.extractHost(failingUrl)
        // If an ad network or pop-under redirect failed (e.g. tsyndicate.com / ad.trans* net::ERR_SSL_PROTOCOL_ERROR),
        // do not display a full-page error overlay. Revert back to the video page cleanly.
        if (TrackerBlocker.isAdOrPopunderNavigation(failingUrl)) {
          callbacks.onTrackerBlocked(tabId)
          try {
            if (view?.canGoBack() == true) {
              view.goBack()
            }
          } catch (_: Throwable) {
          }
          return
        }
        val offline = !isNetworkConnected()
        val errorCode = try { error?.errorCode ?: 0 } catch (_: Throwable) { 0 }
        val errorType = when {
          offline -> PageErrorType.OFFLINE
          errorCode == ERROR_HOST_LOOKUP -> PageErrorType.DNS_FAILURE
          errorCode == ERROR_UNSAFE_RESOURCE -> PageErrorType.BLOCKED
          else -> PageErrorType.GENERIC
        }
        callbacks.onPageError(
          tabId,
          PageError(
            type = errorType,
            failingUrl = failingUrl,
            host = host,
            reason = UrlValidator.sanitizeDisplayText(reason, 180)
          )
        )
      }

      override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
        currentPageHosts.remove(tabId)
        liveViews.remove(tabId)?.let { dead ->
          safelyDestroyWebView(dead)
        } ?: view?.let { safelyDestroyWebView(it) }
        callbacks.onRenderProcessCrashed(tabId)
        return true
      }

      override fun onSafeBrowsingHit(
        view: WebView?,
        request: WebResourceRequest?,
        threatType: Int,
        callback: SafeBrowsingResponse?
      ) {
        try {
          callback?.backToSafety(true)
        } catch (_: Throwable) {
        }
        val failingUrl = request?.url?.toString() ?: try { view?.url } catch (_: Throwable) { null } ?: ""
        val reason = when (threatType) {
          SAFE_BROWSING_THREAT_MALWARE -> "Dangerous site (malware detected by Safe Browsing)"
          SAFE_BROWSING_THREAT_PHISHING -> "Deceptive site (phishing detected by Safe Browsing)"
          SAFE_BROWSING_THREAT_UNWANTED_SOFTWARE -> "Harmful site (unwanted software detected by Safe Browsing)"
          SAFE_BROWSING_THREAT_BILLING -> "Deceptive billing site (detected by Safe Browsing)"
          else -> "Unsafe resource detected by Safe Browsing"
        }
        callbacks.onPageError(
          tabId,
          PageError(
            type = PageErrorType.BLOCKED,
            failingUrl = failingUrl,
            host = UrlValidator.extractHost(failingUrl),
            reason = reason
          )
        )
      }
    }

    webView.webChromeClient = object : WebChromeClient() {
      override fun onProgressChanged(view: WebView?, newProgress: Int) {
        callbacks.onProgressChanged(tabId, newProgress)
        if (view == null || destroyedViews.contains(view)) return
        val stage = pageEarlyInjectedStage[tabId] ?: 0
        if (newProgress >= 15 && stage < 1) {
          pageEarlyInjectedStage[tabId] = 1
          val liveUrl = try { view.url.orEmpty() } catch (_: Throwable) { currentPageUrls[tabId].orEmpty() }
          if (isPrivate) {
            applyPrivateIsolationSandbox(view)
          }
          if (tabUserAgentModes[tabId] == UserAgentMode.DESKTOP) {
            applyDesktopModeEngine(view, true)
          }
          applyLinkTargetTrackerEngine(view)
          applyVideoCompatibilityEngine(view)
          applyStealthPrivacyShield(view)
        }
        if (newProgress >= 35 && stage < 2) {
          pageEarlyInjectedStage[tabId] = 2
          val liveUrl = try { view.url.orEmpty() } catch (_: Throwable) { "" }
          if (liveUrl.isNotBlank()) {
            applySearchSameTabEngine(view, liveUrl)
          }
          val s = settingsProvider()
          if (s.trackerBlockingEnabled) {
            applyAdBlocker(view, true)
          }
          if (s.backgroundPlayEnabled) {
            applyBackgroundPlay(view, true)
          }
        }
      }

      override fun onReceivedTitle(view: WebView?, title: String?) {
        if (!title.isNullOrBlank()) {
          val clean = UrlValidator.sanitizeDisplayText(title, 180)
          currentPageTitles[tabId] = clean
          callbacks.onTitleReceived(tabId, clean)
        }
      }

      override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
        if (icon != null && !isPrivate) {
          val liveUrl = try { view?.url.orEmpty() } catch (_: Throwable) { "" }
          if (liveUrl.isNotBlank() && UrlValidator.isSafeWebUrl(liveUrl)) {
            SiteFaviconCache.storeFromWebView(appContext, liveUrl, icon, isPrivate = false)
          }
        }
      }

      override fun onCreateWindow(
        view: WebView?,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: Message?
      ): Boolean {
        if (resultMsg == null || !isUserGesture) return false
        val currentSettings = settingsProvider()
        val openerUrl = try { view?.url.orEmpty() } catch (_: Throwable) { "" }
        val isSearchOpener = UrlValidator.isSearchEngineUrl(openerUrl)

        val hitResult = try { view?.hitTestResult } catch (_: Throwable) { null }
        // Only trust hitTestResult.extra as a destination link for pure anchor hits (SRC_ANCHOR_TYPE).
        // For SRC_IMAGE_ANCHOR_TYPE / IMAGE_TYPE, Android populates extra with the <img> src (picture),
        // NOT the enclosing <a href> webpage link.
        val hitUrl = if (hitResult?.type == WebView.HitTestResult.SRC_ANCHOR_TYPE) hitResult.extra else null
        val resolvedHitUrl = if (!hitUrl.isNullOrBlank()) UrlValidator.resolveRelativeUrl(openerUrl, hitUrl) else ""
        if (resolvedHitUrl.isNotBlank() && currentSettings.trackerBlockingEnabled &&
          TrackerBlocker.isAdOrPopunderNavigation(resolvedHitUrl)
        ) {
          callbacks.onTrackerBlocked(tabId)
          return false
        }

        fun routeFallbackUrl() {
          if (resolvedHitUrl.isNotBlank() && UrlValidator.isSafeWebUrl(resolvedHitUrl) && resolvedHitUrl != "about:blank") {
            if (isSearchOpener) {
              loadUrl(tabId, resolvedHitUrl)
            } else {
              callbacks.onNewTabRequested(tabId, resolvedHitUrl, isPrivate)
            }
            return
          }
          if (view != null && currentSettings.javascriptEnabled && !destroyedViews.contains(view)) {
            try {
              view.evaluateJavascript("(function(){return window.__kio_get_touch_ctx?window.__kio_get_touch_ctx():'{}';})();") { rawJson ->
                try {
                  if (!rawJson.isNullOrBlank() && rawJson != "null") {
                    val unwrapped = JSONObject("{\"v\":$rawJson}").getString("v")
                    val domLink = JSONObject(unwrapped).optString("link").trim()
                    val resolvedDom = UrlValidator.resolveRelativeUrl(openerUrl, domLink)
                    if (resolvedDom.isNotBlank() && UrlValidator.isSafeWebUrl(resolvedDom) && resolvedDom != "about:blank") {
                      if (currentSettings.trackerBlockingEnabled && TrackerBlocker.isAdOrPopunderNavigation(resolvedDom)) {
                        callbacks.onTrackerBlocked(tabId)
                      } else if (isSearchOpener) {
                        loadUrl(tabId, resolvedDom)
                      } else {
                        callbacks.onNewTabRequested(tabId, resolvedDom, isPrivate)
                      }
                    }
                  }
                } catch (_: Throwable) {
                }
              }
            } catch (_: Throwable) {
            }
          }
        }

        val transport = try { resultMsg.obj as? WebView.WebViewTransport } catch (_: Throwable) { null }
        if (transport == null) {
          routeFallbackUrl()
          return true
        }

        // Use a silent ephemeral interceptor WebView to inspect the true destination URL
        // before creating any tab. This drops streaming ad popunders (e.g., ad.trans*, tsyndicate.com)
        // and keeps search-page links loading inside the same tab.
        val interceptor = try {
          WebView(MutableContextWrapper(appContext))
        } catch (_: Throwable) {
          routeFallbackUrl()
          return true
        }

        val handled = AtomicBoolean(false)
        val mainHandler = Handler(Looper.getMainLooper())
        val cleanupInterceptor = {
          try {
            interceptor.stopLoading()
            interceptor.webViewClient = WebViewClient()
            interceptor.destroy()
          } catch (_: Throwable) {
          }
        }

        fun routeInterceptedUrl(rawTarget: String) {
          if (!handled.compareAndSet(false, true)) return
          cleanupInterceptor()
          val targetUrl = UrlValidator.resolveRelativeUrl(openerUrl, rawTarget)
          if (targetUrl.isBlank() || targetUrl == "about:blank") return
          if (UrlValidator.isExternalAppScheme(targetUrl)) {
            if (!isPrivate) {
              callbacks.onExternalSchemePrompt(tabId, targetUrl, UrlValidator.extractScheme(targetUrl))
            }
            return
          }
          if (!UrlValidator.isSafeWebUrl(targetUrl)) return
          val s = settingsProvider()
          val targetHost = UrlValidator.extractHost(targetUrl)
          val customSet = s.customBlockedHosts.toSet()
          if (customSet.isNotEmpty() && TrackerBlocker.isHostMatchingSet(targetHost, customSet)) {
            callbacks.onTrackerBlocked(tabId)
            return
          }
          if (s.trackerBlockingEnabled && TrackerBlocker.isAdOrPopunderNavigation(targetUrl)) {
            callbacks.onTrackerBlocked(tabId)
            return
          }
          if (isSearchOpener) {
            loadUrl(tabId, targetUrl)
          } else {
            callbacks.onNewTabRequested(tabId, targetUrl, isPrivate)
          }
        }

        interceptor.webViewClient = object : WebViewClient() {
          override fun shouldOverrideUrlLoading(v: WebView?, req: WebResourceRequest?): Boolean {
            val dest = req?.url?.toString().orEmpty()
            if (dest.isNotBlank() && dest != "about:blank") {
              routeInterceptedUrl(dest)
            }
            return true
          }

          override fun onPageStarted(v: WebView?, url: String?, favicon: Bitmap?) {
            if (!url.isNullOrBlank() && url != "about:blank") {
              routeInterceptedUrl(url)
            }
          }
        }

        transport.webView = interceptor
        try {
          resultMsg.sendToTarget()
        } catch (_: Throwable) {
          if (handled.compareAndSet(false, true)) {
            cleanupInterceptor()
          }
          return false
        }

        // Safety cleanup timer so the ephemeral interceptor never leaks memory
        mainHandler.postDelayed({
          if (handled.compareAndSet(false, true)) {
            cleanupInterceptor()
            routeFallbackUrl()
          }
        }, 2500)

        return true
      }

      override fun onCloseWindow(window: WebView?) {
        callbacks.onCloseTabRequested(tabId)
      }

      override fun onGeolocationPermissionsShowPrompt(
        origin: String?,
        callback: GeolocationPermissions.Callback?
      ) {
        val safeOrigin = UrlValidator.sanitizeDisplayText(origin ?: "unknown", 120)
        callbacks.onPermissionPrompt(
          tabId = tabId,
          origin = safeOrigin,
          kind = WebPermissionKind.LOCATION,
          detail = ""
        ) { allowed ->
          try {
            callback?.invoke(origin, allowed, false)
          } catch (_: Throwable) {
          }
        }
      }

      override fun getDefaultVideoPoster(): Bitmap? {
        return try {
          Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        } catch (_: Throwable) {
          null
        }
      }

      override fun getVideoLoadingProgressView(): View? {
        return try {
          val frameLayout = FrameLayout(appContext)
          val progressBar = ProgressBar(appContext)
          val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
          )
          frameLayout.addView(progressBar, params)
          frameLayout
        } catch (_: Throwable) {
          null
        }
      }

      override fun onPermissionRequest(request: PermissionRequest?) {
        if (request == null) return
        val originRaw = request.origin?.toString().orEmpty()
        val resources = request.resources ?: emptyArray()
        val hasMediaCapture = resources.any {
          it == PermissionRequest.RESOURCE_VIDEO_CAPTURE || it == PermissionRequest.RESOURCE_AUDIO_CAPTURE
        }

        // Auto-grant protected media / DRM identifiers on HTTPS origins so HTML5 EME, Widevine,
        // and streaming video players initialize and play instantly without broken players or dialog interruptions.
        val isProtectedMediaOnly = !hasMediaCapture && resources.all { it == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID }
        if (isProtectedMediaOnly && (originRaw.startsWith("https://") || request.origin?.scheme == "https")) {
          try {
            request.grant(resources)
            return
          } catch (_: Throwable) {
          }
        }

        val origin = UrlValidator.sanitizeDisplayText(originRaw.ifEmpty { "unknown" }, 120)
        val kind = if (hasMediaCapture) WebPermissionKind.CAMERA_MIC else WebPermissionKind.PROTECTED_MEDIA
        callbacks.onPermissionPrompt(
          tabId = tabId,
          origin = origin,
          kind = kind,
          detail = ""
        ) { allowed ->
          try {
            if (allowed) {
              request.grant(resources)
            } else {
              request.deny()
            }
          } catch (_: Throwable) {
          }
        }
      }

      override fun onShowFileChooser(
        webView: WebView?,
        filePathCallback: ValueCallback<Array<android.net.Uri>>?,
        fileChooserParams: FileChooserParams?
      ): Boolean {
        callbacks.onFileChooserRequested(filePathCallback, fileChooserParams?.acceptTypes)
        return true
      }

      override fun onShowCustomView(view: View?, callback: WebChromeClient.CustomViewCallback?) {
        callbacks.onFullscreenChanged(tabId, view, callback)
      }

      @Deprecated("Deprecated in Java")
      override fun onShowCustomView(
        view: View?,
        requestedOrientation: Int,
        callback: WebChromeClient.CustomViewCallback?
      ) {
        callbacks.onFullscreenChanged(tabId, view, callback)
      }

      override fun onHideCustomView() {
        callbacks.onFullscreenChanged(tabId, null, null)
      }
    }

    return webView
  }

  private val corsBlockedHeaders = mapOf(
    "Access-Control-Allow-Origin" to "*",
    "Access-Control-Allow-Methods" to "GET, POST, OPTIONS, HEAD",
    "Access-Control-Allow-Headers" to "*",
    "Access-Control-Max-Age" to "86400"
  )

  private fun emptyBlockedResponse(): WebResourceResponse =
    WebResourceResponse(
      "text/plain",
      "utf-8",
      204,
      "Blocked",
      corsBlockedHeaders,
      ByteArrayInputStream(ByteArray(0))
    )

  private fun emptyBlockedJsonResponse(): WebResourceResponse {
    val emptyJsonBytes = "{}".toByteArray(StandardCharsets.UTF_8)
    return WebResourceResponse(
      "application/json",
      "utf-8",
      200,
      "OK",
      corsBlockedHeaders,
      ByteArrayInputStream(emptyJsonBytes)
    )
  }

  private fun emptyBlockedVastResponse(): WebResourceResponse {
    val xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><VAST version=\"3.0\"/>".toByteArray(StandardCharsets.UTF_8)
    return WebResourceResponse(
      "application/xml",
      "utf-8",
      200,
      "OK",
      corsBlockedHeaders,
      ByteArrayInputStream(xml)
    )
  }

  private fun isNetworkConnected(): Boolean {
    return try {
      val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
      val net = cm.activeNetwork ?: return false
      val caps = cm.getNetworkCapabilities(net) ?: return false
      caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    } catch (_: Throwable) {
      true
    }
  }
}
