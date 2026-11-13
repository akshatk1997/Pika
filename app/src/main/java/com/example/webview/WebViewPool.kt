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
import android.os.Message
import android.print.PrintAttributes
import android.print.PrintManager
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
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
import com.example.model.SniffedResource
import com.example.model.ThemeMode
import com.example.model.UserAgentMode
import com.example.model.WebPermissionKind
import com.example.security.TrackerBlocker
import com.example.security.UrlValidator
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.Collections
import java.util.LinkedHashMap
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

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
    private const val MAX_LIVE_WEBVIEWS = 2
    private const val MAX_SNIFFED_PER_TAB = 50
    private const val DESKTOP_USER_AGENT =
      "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36"
    private const val IPHONE_USER_AGENT =
      "Mozilla/5.0 (iPhone; CPU iPhone OS 18_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.3 Mobile/15E148 Safari/604.1"
  }

  private val appContext: Context = context.applicationContext
  private val liveViews = object : LinkedHashMap<String, WebView>(4, 0.75f, true) {}
  private val savedStates = HashMap<String, Bundle>()
  private val sniffedResourcesByTab = ConcurrentHashMap<String, LinkedHashMap<String, SniffedResource>>()
  private val currentPageHosts = ConcurrentHashMap<String, String>()
  private val destroyedViews = Collections.newSetFromMap(WeakHashMap<WebView, Boolean>())
  private val defaultUserAgent: String by lazy {
    try {
      WebSettings.getDefaultUserAgent(appContext)
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

  fun getOrCreateWebView(
    tabId: String,
    isPrivate: Boolean,
    userAgentMode: UserAgentMode,
    approvedHttpHosts: Set<String>,
    fallbackUrl: String = ""
  ): WebView? {
    val settingsState = settingsProvider()
    liveViews[tabId]?.let { existing ->
      if (!destroyedViews.contains(existing)) {
        applySettings(existing, isPrivate, userAgentMode, settingsState)
        return existing
      } else {
        liveViews.remove(tabId)
      }
    }

    while (liveViews.size >= MAX_LIVE_WEBVIEWS) {
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

  fun loadUrl(tabId: String, url: String) {
    val webView = getLiveWebView(tabId) ?: return
    sniffedResourcesByTab.remove(tabId)
    currentPageHosts[tabId] = UrlValidator.extractHost(url)
    val headers = if (settingsProvider().doNotTrackEnabled) {
      mapOf("DNT" to "1", "Sec-GPC" to "1")
    } else {
      emptyMap()
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
    val webView = getLiveWebView(tabId) ?: return
    applySettings(webView, isPrivate, userAgentMode, settingsProvider())
    try {
      if (!webView.url.isNullOrBlank()) {
        webView.reload()
      }
    } catch (_: Throwable) {
    }
  }

  fun applySettingsToAll(
    settingsState: SettingsState,
    tabStateLookup: (String) -> Pair<Boolean, UserAgentMode>
  ) {
    for ((id, wv) in liveViews.toMap()) {
      if (destroyedViews.contains(wv)) continue
      val (isPriv, uaMode) = tabStateLookup(id)
      applySettings(wv, isPriv, uaMode, settingsState)
      applyNightWebCss(wv, settingsState.nightWebMode)
      applyAdBlocker(wv, settingsState.trackerBlockingEnabled)
    }
  }

  fun destroyTab(tabId: String) {
    savedStates.remove(tabId)
    sniffedResourcesByTab.remove(tabId)
    currentPageHosts.remove(tabId)
    liveViews.remove(tabId)?.let { wv ->
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
    privateTabIds.forEach { destroyTab(it) }
    for ((_, wv) in liveViews.toMap()) {
      if (destroyedViews.contains(wv)) continue
      try {
        wv.clearCache(true)
        wv.clearHistory()
        @Suppress("DEPRECATION")
        wv.clearFormData()
      } catch (_: Throwable) {
      }
    }
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
    try {
      @Suppress("DEPRECATION")
      WebViewDatabase.getInstance(appContext).clearFormData()
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
      val jobName = UrlValidator.sanitizeDisplayText(title, 64).ifEmpty { "Shiz_Page" }
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
    val ids = liveViews.keys.toList()
    ids.forEach { destroyTab(it) }
  }

  private fun applyNightWebCss(webView: WebView, enabled: Boolean) {
    if (!settingsProvider().javascriptEnabled || destroyedViews.contains(webView)) return
    val js = if (enabled) {
      """
      (function(){
        var id='shiz-night-style';
        if(document.getElementById(id))return;
        var s=document.createElement('style');
        s.id=id;
        s.textContent='html{filter:invert(0.92) hue-rotate(180deg)!important;background:#111!important;color-scheme:dark!important;}img,video,picture,canvas,svg,iframe{filter:invert(1) hue-rotate(180deg)!important;}';
        (document.head||document.documentElement).appendChild(s);
      })();
      """.trimIndent()
    } else {
      "(function(){var s=document.getElementById('shiz-night-style');if(s)s.remove();})();"
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
        if (window.__shiz_adblock_installed) return;
        window.__shiz_adblock_installed = true;

        try {
          var styleId = 'shiz-adblock-shield-style';
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
              '.ytp-ad-module',
              '.ytp-ad-overlay-container',
              '.ytp-ad-image-overlay',
              '.ytp-ad-message-container',
              '.ytp-ad-action-interstitial',
              '.ytp-ad-player-overlay',
              '.ytp-ad-player-overlay-flyout-cta',
              '.video-ads',
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
            var player = document.querySelector('#movie_player') || document.querySelector('.html5-video-player');
            var isAdActive = false;
            if (player) {
              isAdActive = player.classList.contains('ad-showing') ||
                           player.classList.contains('ad-interrupting') ||
                           Boolean(player.querySelector('.ytp-ad-player-overlay'));
            }

            var videos = document.querySelectorAll('video');
            for (var i = 0; i < videos.length; i++) {
              var v = videos[i];
              var adContainer = v.closest && v.closest('.ad-showing, .ad-interrupting, .video-ads');
              if (isAdActive || adContainer) {
                v.muted = true;
                if (isFinite(v.duration) && v.duration > 0) {
                  v.currentTime = v.duration;
                }
                v.playbackRate = 16.0;
              }
            }

            var skipButtons = document.querySelectorAll(
              '.ytp-ad-skip-button, .ytp-ad-skip-button-modern, button.ytp-ad-skip-button, .ytp-ad-overlay-close-button, .videoAdUiSkipButton, [class*="skip-button"]'
            );
            for (var j = 0; j < skipButtons.length; j++) {
              if (typeof skipButtons[j].click === 'function') {
                skipButtons[j].click();
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
        window.__shiz_adblock_installed = false;
        var s = document.getElementById('shiz-adblock-shield-style');
        if (s) s.remove();
      })();
      """.trimIndent()
    }
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
      webView.settings.apply {
        javaScriptEnabled = settingsState.javascriptEnabled
        javaScriptCanOpenWindowsAutomatically = false
        setSupportMultipleWindows(true)
        allowFileAccess = false
        allowContentAccess = false
        @Suppress("DEPRECATION")
        allowFileAccessFromFileURLs = false
        @Suppress("DEPRECATION")
        allowUniversalAccessFromFileURLs = false
        @Suppress("DEPRECATION")
        saveFormData = false
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        domStorageEnabled = !isPrivate
        @Suppress("DEPRECATION")
        databaseEnabled = false
        builtInZoomControls = true
        displayZoomControls = false
        useWideViewPort = true
        loadWithOverviewMode = true
        loadsImagesAutomatically = !settingsState.noImageMode
        blockNetworkImage = settingsState.noImageMode
        val targetUa = when (userAgentMode) {
          UserAgentMode.DESKTOP -> DESKTOP_USER_AGENT
          UserAgentMode.IPHONE -> IPHONE_USER_AGENT
          UserAgentMode.MOBILE -> defaultUserAgent.ifEmpty { null }
        }
        if (userAgentString != targetUa) {
          userAgentString = targetUa
        }
        textZoom = if (userAgentMode == UserAgentMode.DESKTOP) 90 else settingsState.textZoomPercent
        cacheMode = if (isPrivate) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
        try {
          safeBrowsingEnabled = true
        } catch (_: Throwable) {
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          try {
            isAlgorithmicDarkeningAllowed = settingsState.nightWebMode || settingsState.themeMode == ThemeMode.DARK
          } catch (_: Throwable) {
          }
        }
      }
    } catch (_: Throwable) {
    }

    try {
      val cookieMgr = CookieManager.getInstance()
      cookieMgr.setAcceptCookie(!isPrivate)
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
      WebView(MutableContextWrapper(appContext))
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

    webView.setOnLongClickListener { v ->
      try {
        val wv = v as? WebView ?: return@setOnLongClickListener false
        val result = wv.hitTestResult
        val extra = result.extra
        val isLinkOrImage = result.type == WebView.HitTestResult.SRC_ANCHOR_TYPE ||
          result.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE ||
          result.type == WebView.HitTestResult.IMAGE_TYPE
        if (isLinkOrImage && !extra.isNullOrBlank() && UrlValidator.isSafeWebUrl(extra)) {
          val isImg = result.type == WebView.HitTestResult.IMAGE_TYPE
          callbacks.onLongPressLink(tabId, extra, isImg)
          true
        } else {
          false
        }
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
          (TrackerBlocker.isTrackerHost(host) || TrackerBlocker.isAdPathOrQuery(url))) {
          callbacks.onTrackerBlocked(tabId)
          return true
        }
        if (currentSettings.httpsOnlyEnabled && UrlValidator.isHttpUrl(url) && host !in approvedHttpHosts) {
          if (request.isForMainFrame) {
            callbacks.onHttpBlockedByHttpsOnly(tabId, url)
          }
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
          if (!UrlValidator.isSafeWebUrl(reqUrl)) {
            return emptyBlockedResponse()
          }
          val currentSettings = settingsProvider()
          val pageHost = currentPageHosts[tabId].orEmpty()
          val customSet = currentSettings.customBlockedHosts.toSet()
          if (TrackerBlocker.shouldBlockRequest(reqUrl, pageHost, currentSettings.trackerBlockingEnabled, customSet)) {
            callbacks.onTrackerBlocked(tabId)
            return emptyBlockedResponse()
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
          if (view != null && settingsProvider().trackerBlockingEnabled) {
            applyAdBlocker(view, true)
          }
          callbacks.onPageStarted(tabId, url)
        }
      }

      override fun onPageFinished(view: WebView?, url: String?) {
        if (url != null && view != null && !destroyedViews.contains(view)) {
          currentPageHosts[tabId] = UrlValidator.extractHost(url)
          val s = settingsProvider()
          if (s.nightWebMode) {
            applyNightWebCss(view, true)
          }
          if (s.trackerBlockingEnabled) {
            applyAdBlocker(view, true)
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
            host = UrlValidator.extractHost(failingUrl),
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
    }

    webView.webChromeClient = object : WebChromeClient() {
      override fun onProgressChanged(view: WebView?, newProgress: Int) {
        callbacks.onProgressChanged(tabId, newProgress)
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
        val targetUrl = try { view?.hitTestResult?.extra } catch (_: Throwable) { null } ?: return false
        if (!isUserGesture || !UrlValidator.isSafeWebUrl(targetUrl)) return false
        val currentSettings = settingsProvider()
        if (currentSettings.trackerBlockingEnabled &&
          (TrackerBlocker.isTrackerHost(UrlValidator.extractHost(targetUrl)) || TrackerBlocker.isAdPathOrQuery(targetUrl))) {
          callbacks.onTrackerBlocked(tabId)
          return false
        }
        val origin = try { UrlValidator.extractOrigin(view?.url) } catch (_: Throwable) { "" }
        callbacks.onPermissionPrompt(
          tabId = tabId,
          origin = origin.ifEmpty { "current page" },
          kind = WebPermissionKind.POPUP,
          detail = targetUrl
        ) { allowed ->
          if (allowed) {
            loadUrl(tabId, targetUrl)
          }
        }
        return false
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

      override fun onPermissionRequest(request: PermissionRequest?) {
        if (request == null) return
        val origin = UrlValidator.sanitizeDisplayText(request.origin?.toString() ?: "unknown", 120)
        val resources = request.resources ?: emptyArray()
        val hasMedia = resources.any {
          it == PermissionRequest.RESOURCE_VIDEO_CAPTURE || it == PermissionRequest.RESOURCE_AUDIO_CAPTURE
        }
        val kind = if (hasMedia) WebPermissionKind.CAMERA_MIC else WebPermissionKind.PROTECTED_MEDIA
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

      override fun onHideCustomView() {
        callbacks.onFullscreenChanged(tabId, null, null)
      }
    }

    return webView
  }

  private fun emptyBlockedResponse(): WebResourceResponse =
    WebResourceResponse(
      "text/plain",
      "utf-8",
      204,
      "Blocked",
      emptyMap(),
      ByteArrayInputStream(ByteArray(0))
    )

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
