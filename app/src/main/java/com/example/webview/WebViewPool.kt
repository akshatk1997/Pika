package com.example.webview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Message
import android.view.View
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
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
import com.example.model.WebPermissionKind
import com.example.security.TrackerBlocker
import com.example.security.UrlValidator
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.LinkedHashMap

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
  fun onDownloadRequested(tabId: String, url: String, contentDisposition: String?, mimeType: String?, contentLength: Long)
  fun onPermissionPrompt(tabId: String, origin: String, kind: WebPermissionKind, detail: String, onDecision: (Boolean) -> Unit)
  fun onFileChooserRequested(callback: ValueCallback<Array<android.net.Uri>>?, acceptTypes: Array<String>?)
  fun onFindMatches(tabId: String, activeOrdinal: Int, numberOfMatches: Int)
  fun onFullscreenChanged(tabId: String, customView: View?, callback: WebChromeClient.CustomViewCallback?)
  fun onLongPressLink(tabId: String, url: String, isImage: Boolean)
}

/**
 * Manages hardened WebView instances with an LRU pool (max 2 live WebViews in RAM).
 * Reads live SettingsState dynamically via settingsProvider so changes take effect immediately.
 */
class WebViewPool(
  private val context: Context,
  private val settingsProvider: () -> SettingsState,
  private val callbacks: WebViewCallbacks
) {

  companion object {
    private const val MAX_LIVE_WEBVIEWS = 2
    private const val DESKTOP_USER_AGENT =
      "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
  }

  private val liveViews = object : LinkedHashMap<String, WebView>(4, 0.75f, true) {}
  private val savedStates = HashMap<String, Bundle>()
  private val defaultUserAgent: String by lazy {
    try {
      WebSettings.getDefaultUserAgent(context)
    } catch (_: Throwable) {
      ""
    }
  }

  fun getOrCreateWebView(
    tabId: String,
    isPrivate: Boolean,
    isDesktopMode: Boolean,
    approvedHttpHosts: Set<String>
  ): WebView {
    val settingsState = settingsProvider()
    liveViews[tabId]?.let { existing ->
      applySettings(existing, isPrivate, isDesktopMode, settingsState)
      return existing
    }

    while (liveViews.size >= MAX_LIVE_WEBVIEWS) {
      val eldestKey = liveViews.keys.firstOrNull() ?: break
      val eldestView = liveViews.remove(eldestKey)
      if (eldestView != null) {
        val bundle = Bundle()
        eldestView.saveState(bundle)
        savedStates[eldestKey] = bundle
        eldestView.onPause()
        eldestView.stopLoading()
        (eldestView.parent as? android.view.ViewGroup)?.removeView(eldestView)
        eldestView.destroy()
      }
    }

    val webView = createSecuredWebView(tabId, isPrivate, isDesktopMode, approvedHttpHosts)
    savedStates.remove(tabId)?.let { bundle ->
      webView.restoreState(bundle)
    }
    liveViews[tabId] = webView
    return webView
  }

  fun getLiveWebView(tabId: String): WebView? = liveViews[tabId]

  fun loadUrl(tabId: String, url: String) {
    val webView = liveViews[tabId] ?: return
    val headers = if (settingsProvider().doNotTrackEnabled) {
      mapOf("DNT" to "1", "Sec-GPC" to "1")
    } else {
      emptyMap()
    }
    webView.loadUrl(url, headers)
  }

  fun applyDesktopMode(tabId: String, isPrivate: Boolean, isDesktopMode: Boolean) {
    val webView = liveViews[tabId] ?: return
    applySettings(webView, isPrivate, isDesktopMode, settingsProvider())
    if (!webView.url.isNullOrBlank()) {
      webView.reload()
    }
  }

  fun applySettingsToAll(
    settingsState: SettingsState,
    tabStateLookup: (String) -> Pair<Boolean, Boolean>
  ) {
    for ((id, wv) in liveViews) {
      val (isPriv, isDesktop) = tabStateLookup(id)
      applySettings(wv, isPriv, isDesktop, settingsState)
    }
  }

  fun destroyTab(tabId: String) {
    savedStates.remove(tabId)
    liveViews.remove(tabId)?.let { wv ->
      wv.stopLoading()
      wv.onPause()
      (wv.parent as? android.view.ViewGroup)?.removeView(wv)
      wv.destroy()
    }
  }

  fun wipePrivateSessionData(privateTabIds: List<String>) {
    privateTabIds.forEach { destroyTab(it) }
    try {
      CookieManager.getInstance().removeAllCookies(null)
      CookieManager.getInstance().flush()
      WebStorage.getInstance().deleteAllData()
      WebViewDatabase.getInstance(context).clearFormData()
      WebView(context).apply {
        clearCache(true)
        clearHistory()
        @Suppress("DEPRECATION")
        clearFormData()
        destroy()
      }
    } catch (_: Throwable) {
    }
  }

  fun clearBrowsingData(options: ClearDataOptions) {
    for ((_, wv) in liveViews) {
      if (options.clearCache) wv.clearCache(true)
      if (options.clearHistory) wv.clearHistory()
      @Suppress("DEPRECATION")
      wv.clearFormData()
    }
    try {
      if (options.clearCookies) {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
      }
      WebViewDatabase.getInstance(context).clearFormData()
    } catch (_: Throwable) {
    }
  }

  fun extractReaderArticle(tabId: String, pageUrl: String, onResult: (ReaderArticle?) -> Unit) {
    val webView = liveViews[tabId]
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
  }

  fun destroyAll() {
    savedStates.clear()
    val ids = liveViews.keys.toList()
    ids.forEach { destroyTab(it) }
  }

  @SuppressLint("SetJavaScriptEnabled")
  private fun applySettings(
    webView: WebView,
    isPrivate: Boolean,
    isDesktopMode: Boolean,
    settingsState: SettingsState
  ) {
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
      userAgentString = if (isDesktopMode) DESKTOP_USER_AGENT else defaultUserAgent.ifEmpty { null }
      textZoom = if (isDesktopMode) 90 else settingsState.textZoomPercent
      cacheMode = if (isPrivate) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        safeBrowsingEnabled = true
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        isAlgorithmicDarkeningAllowed = settingsState.themeMode == com.example.model.ThemeMode.DARK
      }
    }

    val cookieMgr = CookieManager.getInstance()
    cookieMgr.setAcceptCookie(!isPrivate)
    cookieMgr.setAcceptThirdPartyCookies(webView, !isPrivate && !settingsState.blockThirdPartyCookies)
  }

  private fun createSecuredWebView(
    tabId: String,
    isPrivate: Boolean,
    isDesktopMode: Boolean,
    approvedHttpHosts: Set<String>
  ): WebView {
    val webView = WebView(context)
    applySettings(webView, isPrivate, isDesktopMode, settingsProvider())

    webView.setOnLongClickListener { v ->
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
        if (currentSettings.httpsOnlyEnabled && UrlValidator.isHttpUrl(url) && host !in approvedHttpHosts) {
          callbacks.onHttpBlockedByHttpsOnly(tabId, url)
          return true
        }
        return false
      }

      override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
      ): WebResourceResponse? {
        val reqUrl = request?.url?.toString() ?: return null
        if (!UrlValidator.isSafeWebUrl(reqUrl)) {
          return emptyBlockedResponse()
        }
        val pageHost = UrlValidator.extractHost(view?.url)
        if (TrackerBlocker.shouldBlockRequest(reqUrl, pageHost, settingsProvider().trackerBlockingEnabled)) {
          callbacks.onTrackerBlocked(tabId)
          return emptyBlockedResponse()
        }
        return null
      }

      override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        if (url != null) {
          callbacks.onPageStarted(tabId, url)
        }
      }

      override fun onPageFinished(view: WebView?, url: String?) {
        if (url != null && view != null) {
          callbacks.onPageFinished(
            tabId = tabId,
            url = url,
            title = view.title,
            canGoBack = view.canGoBack(),
            canGoForward = view.canGoForward()
          )
        }
      }

      override fun onReceivedSslError(
        view: WebView?,
        handler: SslErrorHandler?,
        error: SslError?
      ) {
        handler?.cancel()
        val failingUrl = error?.url ?: view?.url ?: ""
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
        val failingUrl = request.url?.toString() ?: ""
        val host = UrlValidator.extractHost(failingUrl)
        val offline = !isNetworkConnected()
        val errorType = when {
          offline -> PageErrorType.OFFLINE
          error?.errorCode == ERROR_HOST_LOOKUP -> PageErrorType.DNS_FAILURE
          error?.errorCode == ERROR_UNSAFE_RESOURCE -> PageErrorType.BLOCKED
          else -> PageErrorType.GENERIC
        }
        val reason = error?.description?.toString() ?: "Page load failed"
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
        val targetUrl = view?.hitTestResult?.extra ?: return false
        if (!isUserGesture || !UrlValidator.isSafeWebUrl(targetUrl)) return false
        val origin = UrlValidator.extractOrigin(view.url)
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
          detail = "Site requested location access (restricted by Pika privacy policy)."
        ) { allowed ->
          callback?.invoke(origin, allowed, false)
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
          detail = resources.joinToString(", ")
        ) { allowed ->
          if (allowed) {
            request.grant(resources)
          } else {
            request.deny()
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

      override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
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
      val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
      val net = cm.activeNetwork ?: return false
      val caps = cm.getNetworkCapabilities(net) ?: return false
      caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    } catch (_: Throwable) {
      true
    }
  }
}
