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
import com.example.model.PageError
import com.example.model.PageErrorType
import com.example.model.ReaderArticle
import com.example.model.SettingsState
import com.example.model.SniffedMediaType
import com.example.model.SniffedResource
import com.example.model.ThemeMode
import com.example.model.UserAgentMode
import com.example.model.WebPermissionKind
import com.example.security.TrackerBlocker
import com.example.security.UrlValidator
import org.json.JSONObject
import java.io.ByteArrayInputStream
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
  fun onResourceSniffed(tabId: String, count: Int)
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
    private const val MAX_LIVE_WEBVIEWS = 4
    private const val MAX_SNIFFED_PER_TAB = 50
    private const val DESKTOP_USER_AGENT =
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36"
    private const val IPHONE_USER_AGENT =
      "Mozilla/5.0 (iPhone; CPU iPhone OS 18_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.3 Mobile/15E148 Safari/604.1"
  }

  private val appContext: Context = context.applicationContext
  private val liveViews = object : LinkedHashMap<String, WebView>(4, 0.75f, true) {}
  private val savedStates = HashMap<String, Bundle>()
  private val sniffedResourcesByTab = ConcurrentHashMap<String, LinkedHashMap<String, SniffedResource>>()
  private val currentPageHosts = ConcurrentHashMap<String, String>()
  private val tabUserAgentModes = ConcurrentHashMap<String, UserAgentMode>()
  private val destroyedViews = Collections.newSetFromMap(WeakHashMap<WebView, Boolean>())
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
      // recognize Kai as authentic Google Chrome Mobile rather than a stripped in-app WebView
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

  fun getSniffedResources(tabId: String): List<SniffedResource> {
    val map = sniffedResourcesByTab[tabId] ?: return emptyList()
    return synchronized(map) { map.values.toList().reversed() }
  }

  fun clearSniffedResources(tabId: String) {
    sniffedResourcesByTab.remove(tabId)
  }

  fun scanDomMedia(tabId: String, callback: ((List<SniffedResource>) -> Unit)? = null) {
    val webView = getLiveWebView(tabId) ?: run {
      callback?.invoke(emptyList())
      return
    }
    val js = """
      (function() {
        var found = [];
        function add(u) {
          if (u && typeof u === 'string') {
            var trimmed = u.trim();
            if (trimmed.indexOf('http://') === 0 || trimmed.indexOf('https://') === 0) {
              if (found.indexOf(trimmed) === -1) found.push(trimmed);
            }
          }
        }
        try {
          var vids = document.querySelectorAll('video, audio');
          for (var i = 0; i < vids.length; i++) {
            var v = vids[i];
            add(v.currentSrc);
            add(v.src);
            var sources = v.querySelectorAll('source');
            for (var j = 0; j < sources.length; j++) {
              add(sources[j].src);
            }
          }
          var iframes = document.querySelectorAll('iframe');
          for (var k = 0; k < iframes.length; k++) {
            var isrc = iframes[k].src;
            if (isrc && (isrc.indexOf('/embed/') !== -1 || isrc.indexOf('player.') !== -1 || isrc.indexOf('video') !== -1)) {
              add(isrc);
            }
          }
        } catch(e) {}
        return JSON.stringify(found);
      })();
    """.trimIndent()

    try {
      webView.evaluateJavascript(js) { jsonResult ->
        if (jsonResult.isNullOrBlank() || jsonResult == "null" || jsonResult == "\"[]\"" || jsonResult == "[]") {
          callback?.invoke(getSniffedResources(tabId))
          return@evaluateJavascript
        }
        try {
          val rawJson = if (jsonResult.startsWith("\"") && jsonResult.endsWith("\"")) {
            org.json.JSONTokener(jsonResult).nextValue().toString()
          } else {
            jsonResult
          }
          val array = org.json.JSONArray(rawJson)
          val map = sniffedResourcesByTab.getOrPut(tabId) { LinkedHashMap() }
          var added = 0
          for (i in 0 until array.length()) {
            val url = array.optString(i).trim()
            if (url.isNotEmpty() && UrlValidator.isSafeWebUrl(url)) {
              val res = TrackerBlocker.inferSniffedResource(url) ?: run {
                val host = UrlValidator.extractHost(url)
                val cleanHost = host.replace(Regex("[^a-zA-Z0-9]"), "_").trim('_')
                val hash = kotlin.math.abs(url.hashCode() % 10000)
                SniffedResource(
                  url = url,
                  host = host,
                  fileName = "video_${cleanHost}_$hash.mp4",
                  type = SniffedMediaType.VIDEO
                )
              }
              synchronized(map) {
                if (!map.containsKey(res.url) && map.size < MAX_SNIFFED_PER_TAB) {
                  map[res.url] = res
                  added++
                }
              }
            }
          }
          if (added > 0) {
            val currentSize = synchronized(map) { map.size }
            callbacks.onResourceSniffed(tabId, currentSize)
          }
          callback?.invoke(getSniffedResources(tabId))
        } catch (_: Throwable) {
          callback?.invoke(getSniffedResources(tabId))
        }
      }
    } catch (_: Throwable) {
      callback?.invoke(getSniffedResources(tabId))
    }
  }

  fun loadUrl(tabId: String, url: String) {
    val webView = getLiveWebView(tabId) ?: return
    sniffedResourcesByTab.remove(tabId)
    currentPageHosts[tabId] = UrlValidator.extractHost(url)
    val s = settingsProvider()
    val headers = mutableMapOf(
      "Sec-GPC" to "1",
      "DNT" to "1"
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
        window.__kai_desktop_mode = true;
        function enforceDesktopViewport() {
          try {
            if (!window.__kai_desktop_mode) return;
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
        if (!window.__kai_desktop_observer && window.MutationObserver) {
          var target = document.head || document.documentElement;
          if (target) {
            var obs = new MutationObserver(function() {
              if (window.__kai_desktop_mode) enforceDesktopViewport();
            });
            obs.observe(target, { childList: true, subtree: true, attributes: true, attributeFilter: ['content'] });
            window.__kai_desktop_observer = obs;
          }
        }
      })();
      """.trimIndent()
    } else {
      """
      (function() {
        window.__kai_desktop_mode = false;
        if (window.__kai_desktop_observer) {
          try { window.__kai_desktop_observer.disconnect(); } catch(e) {}
          window.__kai_desktop_observer = null;
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
        if (!window.__kai_search_sametab_observer && window.MutationObserver) {
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
            window.__kai_search_sametab_observer = obs;
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
        if (window.__kai_link_tracker_installed) return;
        window.__kai_link_tracker_installed = true;
        window.__kai_touch_ctx = { link: '', image: '', ts: 0 };

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

            window.__kai_touch_ctx = { link: link, image: img, ts: Date.now() };
          } catch(e) {}
        }

        document.addEventListener('touchstart', function(e) { captureTarget(e.target); }, { capture: true, passive: true });
        document.addEventListener('pointerdown', function(e) { captureTarget(e.target); }, { capture: true, passive: true });
        document.addEventListener('contextmenu', function(e) { captureTarget(e.target); }, { capture: true, passive: true });

        window.__kai_get_touch_ctx = function() {
          var ctx = window.__kai_touch_ctx || { link: '', image: '', ts: 0 };
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
        val uiMode = appContext.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        uiMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
      }
      ThemeMode.DARK -> true
      ThemeMode.LIGHT -> false
    }
  }

  fun applyThemeMode(webView: WebView, isDark: Boolean, nightWebMode: Boolean) {
    if (!settingsProvider().javascriptEnabled || destroyedViews.contains(webView)) return
    val modeStr = if (isDark) "dark" else "light"
    val js = """
      (function() {
        var isDark = $isDark;
        var mode = '$modeStr';
        if (window.__kaiSetTheme) {
          window.__kaiSetTheme(mode);
        }
        var id = 'kai-theme-mode-style';
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
          var nightStyle = document.getElementById('kai-night-style');
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
    sniffedResourcesByTab.remove(tabId)
    currentPageHosts.remove(tabId)
    tabUserAgentModes.remove(tabId)
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
    for ((_, wv) in liveViews.toMap()) {
      if (destroyedViews.contains(wv)) continue
      try {
        wv.clearCache(true)
        wv.clearHistory()
        @Suppress("DEPRECATION")
        wv.clearFormData()
        wv.clearSslPreferences()
      } catch (_: Throwable) {
      }
    }
    try {
      CookieManager.getInstance().apply {
        removeSessionCookies(null)
        removeAllCookies(null)
        flush()
      }
    } catch (_: Throwable) {
    }
    try {
      WebStorage.getInstance().deleteAllData()
    } catch (_: Throwable) {
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
      val jobName = UrlValidator.sanitizeDisplayText(title, 64).ifEmpty { "Kai_Page" }
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
    sniffedResourcesByTab.clear()
    currentPageHosts.clear()
    tabUserAgentModes.clear()
    val ids = liveViews.keys.toList()
    ids.forEach { destroyTab(it) }
  }

  private fun applyNightWebCss(webView: WebView, enabled: Boolean) {
    if (!settingsProvider().javascriptEnabled || destroyedViews.contains(webView)) return
    val js = if (enabled) {
      """
      (function(){
        var id='kai-night-style';
        if(document.getElementById(id))return;
        var s=document.createElement('style');
        s.id=id;
        s.textContent='html{filter:invert(0.92) hue-rotate(180deg)!important;background:#111!important;color-scheme:dark!important;}img,video,picture,canvas,svg,iframe{filter:invert(1) hue-rotate(180deg)!important;}';
        (document.head||document.documentElement).appendChild(s);
      })();
      """.trimIndent()
    } else {
      "(function(){var s=document.getElementById('kai-night-style');if(s)s.remove();})();"
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
        if (window.__kai_adblock_installed) return;
        window.__kai_adblock_installed = true;

        try {
          var styleId = 'kai-adblock-shield-style';
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
              '.adsbygoogle',
              '[id^="google_ads_"]',
              '[id^="div-gpt-ad"]',
              '[class*="sponsored-post"]',
              '.taboola-ad',
              '.outbrain-ad',
              '.trc_related_container',
              '.mgid-ad',
              '.ad-container',
              '.ad-div',
              '.ad-banner',
              '.advertisement',
              '.advertisement-container'
            ].join(',') + '{display:none!important;visibility:hidden!important;height:0!important;min-height:0!important;opacity:0!important;pointer-events:none!important;}';
            (document.head || document.documentElement).appendChild(s);
          }
        } catch(e) {}

        function skipStreamingAds() {
          try {
            var isYouTube = /youtube\.com|youtu\.be/.test(window.location.hostname);
            if (!isYouTube) return;
            var player = document.querySelector('#movie_player') || document.querySelector('.html5-video-player');
            var isAdActive = Boolean(player && (player.classList.contains('ad-showing') || player.classList.contains('ad-interrupting')));

            if (isAdActive) {
              if (typeof player.skipAd === 'function') {
                try { player.skipAd(); } catch(e) {}
              }

              var skipButtons = document.querySelectorAll(
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
                  v.__kai_ad_muted = true;
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
                if (cv.__kai_ad_muted) {
                  cv.muted = false;
                  delete cv.__kai_ad_muted;
                }
              }
            }
          } catch(e) {}
        }

        setInterval(skipStreamingAds, 300);

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
        window.__kai_adblock_installed = false;
        var s = document.getElementById('kai-adblock-shield-style');
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
        if (window.__kai_bg_play_installed) return;
        window.__kai_bg_play_installed = true;

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
        window.__kai_bg_play_installed = false;
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
          var s = document.getElementById('kai-data-saver-style');
          if (s) s.remove();
          window.__kai_data_saver_active = false;
        })();
      """.trimIndent()
      try { webView.evaluateJavascript(cleanupJs, null) } catch (_: Throwable) {}
      return
    }
    val js = """
      (function() {
        window.__kai_data_saver_active = true;
        var host = (window.location && window.location.hostname) || '';
        var isStreamingSite = host.indexOf('youtube.com') !== -1 ||
                              host.indexOf('youtu.be') !== -1 ||
                              host.indexOf('vimeo.com') !== -1 ||
                              host.indexOf('twitch.tv') !== -1 ||
                              host.indexOf('dailymotion.com') !== -1;

        var id = 'kai-data-saver-style';
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
   * Injects universal HTML5 video enhancement engine:
   * - Enforces playsinline, webkit-playsinline, and x5-playsinline on all HTML5 video elements.
   * - Safely catches unhandled HTMLMediaElement.play() Promise rejections (falling back to muted preview when needed).
   * - Dynamically patches newly mounted video players (React, Vue, Video.js, JWPlayer, Hls.js) via MutationObserver.
   * - Bypasses transparent clickjack / invisible ad overlays placed over video players on streaming sites.
   */
  fun applyVideoCompatibilityEngine(webView: WebView) {
    if (!settingsProvider().javascriptEnabled || destroyedViews.contains(webView)) return
    val js = """
      (function() {
        if (window.__kai_video_engine_installed) return;
        window.__kai_video_engine_installed = true;

        // 1. Comprehensive canPlayType polyfill across all media element prototypes
        try {
          var patchCanPlay = function(proto) {
            if (!proto || !proto.canPlayType) return;
            var orig = proto.canPlayType;
            proto.canPlayType = function(type) {
              if (!type) return '';
              var t = String(type).toLowerCase();
              if (t.indexOf('application/x-mpegurl') !== -1 ||
                  t.indexOf('application/vnd.apple.mpegurl') !== -1 ||
                  t.indexOf('audio/x-mpegurl') !== -1 ||
                  t.indexOf('audio/mpegurl') !== -1 ||
                  t.indexOf('video/mp2t') !== -1 ||
                  t.indexOf('application/dash+xml') !== -1) {
                return 'maybe';
              }
              var res = orig.apply(this, arguments);
              if (!res && (t.indexOf('mp4') !== -1 || t.indexOf('webm') !== -1 || t.indexOf('hls') !== -1 || t.indexOf('m3u8') !== -1 || t.indexOf('ogg') !== -1 || t.indexOf('mkv') !== -1)) {
                return 'maybe';
              }
              return res;
            };
          };
          patchCanPlay(HTMLMediaElement.prototype);
          if (window.HTMLVideoElement && HTMLVideoElement.prototype) patchCanPlay(HTMLVideoElement.prototype);
          if (window.HTMLAudioElement && HTMLAudioElement.prototype) patchCanPlay(HTMLAudioElement.prototype);
        } catch(e) {}

        // 2. MediaSource.isTypeSupported polyfill for adaptive streaming codecs
        try {
          if (window.MediaSource && MediaSource.isTypeSupported) {
            var origIsTypeSupported = MediaSource.isTypeSupported;
            MediaSource.isTypeSupported = function(type) {
              if (!type) return false;
              var res = origIsTypeSupported.apply(this, arguments);
              if (res) return true;
              var t = String(type).toLowerCase();
              if (t.indexOf('avc1') !== -1 || t.indexOf('mp4a') !== -1 || t.indexOf('video/mp4') !== -1 || t.indexOf('video/webm') !== -1 || t.indexOf('vp9') !== -1 || t.indexOf('vp8') !== -1 || t.indexOf('opus') !== -1) {
                return true;
              }
              return res;
            };
          }
        } catch(e) {}

        // 3. Patch video elements (inline playback, source resolution, and CORS error recovery)
        function patchVideo(video) {
          if (!video) return;
          try {
            if (!video.hasAttribute('playsinline')) video.setAttribute('playsinline', '');
            if (!video.hasAttribute('webkit-playsinline')) video.setAttribute('webkit-playsinline', '');
            if (!video.hasAttribute('x5-playsinline')) video.setAttribute('x5-playsinline', '');

            // If video has child <source> elements but no active src, populate src directly
            if (!video.src && video.querySelector) {
              var s = video.querySelector('source[src]');
              if (s && s.src) {
                video.src = s.src;
                try { video.load(); } catch(err) {}
              }
            }

            // Remove any failing crossorigin attribute on error and retry fallback sources
            if (!video.__kai_error_bound) {
              video.__kai_error_bound = true;
              video.addEventListener('error', function() {
                try {
                  if (video.hasAttribute('crossorigin')) {
                    video.removeAttribute('crossorigin');
                    try { video.load(); video.play(); } catch(err) {}
                    return;
                  }
                  var allSources = video.querySelectorAll ? video.querySelectorAll('source[src]') : [];
                  for (var i = 0; i < allSources.length; i++) {
                    var candidate = allSources[i].src;
                    if (candidate && candidate !== video.src) {
                      video.src = candidate;
                      try { video.load(); video.play(); } catch(err) {}
                      break;
                    }
                  }
                } catch(err) {}
              }, true);
            }
          } catch(e) {}
        }

        // 4. Universal play() wrapper catching autoplay restrictions
        try {
          var origPlay = HTMLMediaElement.prototype.play;
          HTMLMediaElement.prototype.play = function() {
            var self = this;
            patchVideo(self);
            var res = origPlay.apply(self, arguments);
            if (res && typeof res.catch === 'function') {
              res.catch(function(err) {
                if (err && err.name === 'NotAllowedError' && !self.muted) {
                  self.muted = true;
                  try { origPlay.apply(self, arguments); } catch(e) {}
                }
              });
            }
            return res;
          };
        } catch(e) {}

        // 5. Ensure all iframes permit fullscreen, autoplay, and encrypted media (EME/Widevine DRM)
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
                  else if (node.querySelectorAll) {
                    var subVids = node.querySelectorAll('video');
                    for (var k = 0; k < subVids.length; k++) patchVideo(subVids[k]);
                    if (node.querySelector('iframe')) patchIframes();
                  }
                }
              }
            }
          });
          obs.observe(document.documentElement || document.body, { childList: true, subtree: true });
        }

        // 6. Intercept and drop window.open calls to ad popunder and syndicate networks
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

        // 7. Neutralize transparent clickjacking overlays & ad links hovering over video players
        document.addEventListener('click', function(e) {
          try {
            var target = e.target;
            if (!target) return;

            var anchor = target.closest ? target.closest('a') : null;
            if (anchor && anchor.href && isAdPopUrl(anchor.href)) {
              e.preventDefault();
              e.stopPropagation();
              e.stopImmediatePropagation();
              var vids = document.querySelectorAll('video');
              for (var i = 0; i < vids.length; i++) {
                if (vids[i].paused) try { vids[i].play(); } catch(err) {}
              }
              return;
            }

            if (target.tagName === 'VIDEO') return;
            var style = window.getComputedStyle(target);
            var isCover = (style.position === 'absolute' || style.position === 'fixed') &&
                          (parseFloat(style.opacity) < 0.05 || (style.backgroundColor && style.backgroundColor.indexOf('rgba(0, 0, 0, 0)') !== -1)) &&
                          target.offsetWidth >= 200 && target.offsetHeight >= 150;
            if (isCover) {
              var parent = target.parentElement;
              var under = (parent && parent.querySelector('video')) ||
                          (target.closest && target.closest('[class*="player"], [id*="player"]') ? target.closest('[class*="player"], [id*="player"]').querySelector('video') : null) ||
                          document.querySelector('video');
              if (under) {
                target.style.pointerEvents = 'none';
                try { under.play(); } catch(err) {}
              }
            }
          } catch(e) {}
        }, true);

        // 8. Auto-recover HTML5 video player errors (Flowplayer, JW Player, Video.js)
        function recoverHtml5VideoErrors() {
          try {
            var errorContainers = document.querySelectorAll('.fp-message, .flowplayer.is-error, .jw-error, .vjs-error-display, [class*="error-message"], [class*="player-error"]');
            for (var i = 0; i < errorContainers.length; i++) {
              var c = errorContainers[i];
              var text = (c.innerText || c.textContent || '').toLowerCase();
              if (text.indexOf('video file not found') !== -1 || text.indexOf('html5') !== -1 || text.indexOf('could not be played') !== -1 || text.indexOf('no compatible source') !== -1) {
                var root = c.closest ? (c.closest('.flowplayer') || c.closest('.jwplayer') || c.closest('.video-js') || c.parentElement) : c.parentElement;
                if (root) {
                  root.classList.remove('is-error');
                  if (c.classList.contains('fp-message') || c.classList.contains('vjs-error-display')) {
                    c.style.display = 'none';
                  }
                  var vid = root.querySelector('video');
                  if (vid) {
                    if (vid.hasAttribute('crossorigin')) vid.removeAttribute('crossorigin');
                    var srcEl = root.querySelector('source[src]');
                    if (srcEl && srcEl.src && srcEl.src !== vid.src) {
                      vid.src = srcEl.src;
                    }
                    try {
                      vid.load();
                      var p = vid.play();
                      if (p && p.catch) p.catch(function() {});
                    } catch(err) {}
                  }
                }
              }
            }
          } catch(e) {}
        }

        setInterval(recoverHtml5VideoErrors, 1000);
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
        if (window.__kai_stealth_privacy_shield_installed) return;
        window.__kai_stealth_privacy_shield_installed = true;

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
    } catch (_: Throwable) {
    }
    try {
      webView.settings.apply {
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
        databaseEnabled = true
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          try {
            val isDark = resolveIsDark(settingsState)
            isAlgorithmicDarkeningAllowed = isDark || settingsState.nightWebMode
          } catch (_: Throwable) {
          }
        }
      }
    } catch (_: Throwable) {
    }

    try {
      val cookieMgr = CookieManager.getInstance()
      // First-party cookies are accepted so websites, sessions, and Cloudflare/auth challenges work
      cookieMgr.setAcceptCookie(true)
      // Allow third-party cookies if not explicitly disabled in settings so cross-domain video CDNs and embeds work.
      // In private mode, cookies remain strictly ephemeral in RAM and are completely purged on exit.
      cookieMgr.setAcceptThirdPartyCookies(webView, !settingsState.blockThirdPartyCookies)
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
      webView.setLayerType(View.LAYER_TYPE_NONE, null)
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
                wv.evaluateJavascript("(function(){return window.__kai_get_touch_ctx?window.__kai_get_touch_ctx():'{}';})();") { rawJson ->
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
          callbacks.onExternalSchemePrompt(tabId, url, UrlValidator.extractScheme(url))
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
          if (currentSettings.dataSaverEnabled) {
            val purpose = request?.requestHeaders?.get("Purpose") ?: request?.requestHeaders?.get("Sec-Purpose") ?: ""
            if (purpose.contains("prefetch", ignoreCase = true) || purpose.contains("prerender", ignoreCase = true)) {
              return emptyBlockedResponse()
            }
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
          TrackerBlocker.inferSniffedResource(reqUrl)?.let { res ->
            val map = sniffedResourcesByTab.getOrPut(tabId) { LinkedHashMap() }
            val newCount = synchronized(map) {
              if (!map.containsKey(res.url) && map.size < MAX_SNIFFED_PER_TAB) {
                map[res.url] = res
              }
              map.size
            }
            callbacks.onResourceSniffed(tabId, newCount)
          }
          null
        } catch (_: Throwable) {
          null
        }
      }

      override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        if (url != null) {
          currentPageHosts[tabId] = UrlValidator.extractHost(url)
          if (view != null) {
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
          }
          callbacks.onPageStarted(tabId, url)
        }
      }

      override fun onPageFinished(view: WebView?, url: String?) {
        if (url != null && view != null && !destroyedViews.contains(view)) {
          currentPageHosts[tabId] = UrlValidator.extractHost(url)
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
          val safeTitle = try { view.title } catch (_: Throwable) { null }
          val canBack = try { view.canGoBack() } catch (_: Throwable) { false }
          val canFwd = try { view.canGoForward() } catch (_: Throwable) { false }
          callbacks.onPageFinished(
            tabId = tabId,
            url = url,
            title = safeTitle,
            canGoBack = canBack,
            canGoForward = canFwd
          )
          scanDomMedia(tabId)
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
        if (view != null && newProgress >= 10 && !destroyedViews.contains(view)) {
          if (tabUserAgentModes[tabId] == UserAgentMode.DESKTOP) {
            applyDesktopModeEngine(view, true)
          }
          applyLinkTargetTrackerEngine(view)
          applyVideoCompatibilityEngine(view)
        }
        if (view != null && newProgress >= 15 && !destroyedViews.contains(view)) {
          applyStealthPrivacyShield(view)
        }
        if (view != null && newProgress >= 25 && !destroyedViews.contains(view)) {
          val liveUrl = try { view.url.orEmpty() } catch (_: Throwable) { "" }
          if (liveUrl.isNotBlank()) {
            applySearchSameTabEngine(view, liveUrl)
          }
        }
        if (view != null && newProgress >= 30 && !destroyedViews.contains(view)) {
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
          callbacks.onTitleReceived(tabId, UrlValidator.sanitizeDisplayText(title, 180))
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
              view.evaluateJavascript("(function(){return window.__kai_get_touch_ctx?window.__kai_get_touch_ctx():'{}';})();") { rawJson ->
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
            callbacks.onExternalSchemePrompt(tabId, targetUrl, UrlValidator.extractScheme(targetUrl))
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
