package com.example.extensions

import android.annotation.SuppressLint
import android.content.Context
import android.content.MutableContextWrapper
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Lightweight Chrome Extension Background Runtime & Native IPC Router for Kio Browser.
 *
 * Responsibilities:
 * - Lazily boots headless Background Service Worker / Background Page WebViews for enabled extensions.
 * - Routes bidirectional chrome.runtime.sendMessage (async RPC + sendResponse) and full-duplex
 *   chrome.runtime.connect Ports between Popup <-> Background <-> Active Tab Content Scripts.
 * - Executes CORS-free privileged fetch() requests on a compact IO pool for extension contexts.
 * - Applies real AndroidX WebKit ProxyController overrides for chrome.proxy extensions (e.g. VPNs)
 *   and resolves proxy HTTP authentication via chrome.webRequest.onAuthRequired.
 */
class ExtensionRuntimeHost(
  context: Context,
  private val extensionManager: ChromeExtensionManager,
  private val activeTabUrlProvider: () -> String,
  private val activeTabTitleProvider: () -> String,
  private val evaluateOnActiveStandardTab: (String) -> Unit,
  private val broadcastStorageToStandardTabs: (String, String) -> Unit,
  private val onOpenTab: (String) -> Unit,
  private val onReloadActiveTab: () -> Unit,
  private val onToast: (String) -> Unit
) {

  private val appContext = context.applicationContext
  private val mainHandler = Handler(Looper.getMainLooper())
  private val fetchExecutor = Executors.newFixedThreadPool(3)

  private val bgWebViews = ConcurrentHashMap<String, WebView>()
  private val bgReady = ConcurrentHashMap<String, Boolean>()
  private val pendingBgScripts = ConcurrentHashMap<String, MutableList<String>>()
  private val pendingBgReadyCallbacks = ConcurrentHashMap<String, MutableList<() -> Unit>>()
  private val activePopupViews = ConcurrentHashMap<String, WebView>()
  private val cachedProxyAuth = ConcurrentHashMap<String, Pair<String, String>>()

  private val _activeProxy = MutableStateFlow<ExtensionProxyConfig?>(null)
  val activeProxy: StateFlow<ExtensionProxyConfig?> = _activeProxy.asStateFlow()

  @Volatile private var privateModeSuspended: Boolean = false

  fun startAllEnabledBackgrounds() {
    if (privateModeSuspended) return
    val enabledWithBg = extensionManager.listExtensions().filter { it.enabled && it.hasBackgroundRuntime }
    enabledWithBg.forEach { ext ->
      ensureBackgroundRunning(ext.id)
    }
  }

  @SuppressLint("SetJavaScriptEnabled")
  fun ensureBackgroundRunning(extId: String, onReady: (() -> Unit)? = null) {
    val ext = extensionManager.getExtension(extId) ?: run {
      onReady?.invoke()
      return
    }
    if (!ext.enabled || !ext.hasBackgroundRuntime) {
      onReady?.invoke()
      return
    }
    if (bgReady[extId] == true && bgWebViews.containsKey(extId)) {
      if (onReady != null) mainHandler.post(onReady)
      return
    }
    if (onReady != null) {
      pendingBgReadyCallbacks.computeIfAbsent(extId) { mutableListOf() }.add(onReady)
    }
    if (bgWebViews.containsKey(extId)) return

    mainHandler.post {
      if (bgWebViews.containsKey(extId)) return@post
      val wv = try {
        WebView(MutableContextWrapper(appContext))
      } catch (_: Throwable) {
        drainReadyCallbacks(extId)
        return@post
      }
      bgWebViews[extId] = wv
      bgReady[extId] = false
      try {
        wv.settings.apply {
          javaScriptEnabled = true
          domStorageEnabled = true
          @Suppress("DEPRECATION")
          databaseEnabled = true
          allowFileAccess = false
          allowContentAccess = false
          mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
          userAgentString = ChromeExtensionManager.DESKTOP_CHROME_UA
        }
        wv.addJavascriptInterface(createBridgeObject(extId, "background"), "__KioExtBridge")
        wv.webViewClient = object : WebViewClient() {
          override fun shouldInterceptRequest(
            view: WebView?,
            request: WebResourceRequest?
          ): WebResourceResponse? {
            val url = request?.url?.toString() ?: return null
            return extensionManager.serveVirtualExtensionResource(url)
          }

          override fun onPageFinished(view: WebView?, url: String?) {
            // Safety drain in case an MV2 background.html does not explicitly invoke __kioFireBackgroundStartup
            mainHandler.postDelayed({
              if (bgReady[extId] != true && bgWebViews[extId] === view) {
                markBackgroundReady(extId)
              }
            }, 900)
          }
        }
        val bgPath = ext.backgroundPage?.trimStart('/')?.takeIf { it.isNotBlank() }
          ?: ChromeExtensionManager.BG_HOST_VIRTUAL_FILE
        wv.loadUrl("https://${ChromeExtensionManager.EXT_VIRTUAL_HOST}/${ext.id}/$bgPath")
        // Safety drain: unblock waiting popups/callers within 350ms even if WebView onPageFinished is delayed
        mainHandler.postDelayed({
          if (bgReady[extId] != true && bgWebViews[extId] === wv) {
            markBackgroundReady(extId)
          }
        }, 350)
      } catch (_: Throwable) {
        stopBackground(extId)
        drainReadyCallbacks(extId)
      }
    }
  }

  private fun markBackgroundReady(extId: String) {
    bgReady[extId] = true
    val queued = pendingBgScripts.remove(extId)
    val wv = bgWebViews[extId]
    if (queued != null && wv != null) {
      mainHandler.post {
        for (js in queued) {
          try { wv.evaluateJavascript(js, null) } catch (_: Throwable) {}
        }
      }
    }
    drainReadyCallbacks(extId)
  }

  private fun drainReadyCallbacks(extId: String) {
    val cbs = pendingBgReadyCallbacks.remove(extId) ?: return
    mainHandler.post {
      cbs.forEach { runCatching { it() } }
    }
  }

  fun attachPopupWebView(extId: String, webView: WebView) {
    activePopupViews[extId] = webView
  }

  fun detachPopupWebView(extId: String, webView: WebView) {
    if (activePopupViews[extId] === webView) {
      activePopupViews.remove(extId)
    }
  }

  fun createBridgeObject(
    extId: String,
    contextKind: String,
    onOpenOptions: ((String) -> Unit)? = null,
    onClosePopup: (() -> Unit)? = null
  ): Any {
    return object {
      @JavascriptInterface
      fun storageGetAll(id: String): String = extensionManager.readStorageJson(id)

      @JavascriptInterface
      fun storageSetAll(id: String, json: String) = extensionManager.writeStorageJson(id, json)

      @JavascriptInterface
      fun broadcastStorageChange(id: String, changesJson: String) {
        val script = extensionManager.buildStorageChangeBroadcastScript(id, changesJson)
        if (contextKind != "background") evalInBackground(id, script)
        if (contextKind != "popup") evalInPopup(id, script)
        mainHandler.post { broadcastStorageToStandardTabs(id, changesJson) }
      }

      @JavascriptInterface
      fun onBackgroundReady(id: String) {
        markBackgroundReady(id)
      }

      @JavascriptInterface
      fun routeRuntimeMessage(id: String, fromCtx: String, reqId: String, msgJson: String) {
        this@ExtensionRuntimeHost.routeRuntimeMessage(id, fromCtx, reqId, msgJson)
      }

      @JavascriptInterface
      fun replyRuntimeMessage(id: String, targetCtx: String, reqId: String, replyJson: String) {
        this@ExtensionRuntimeHost.replyRuntimeMessage(id, targetCtx, reqId, replyJson)
      }

      @JavascriptInterface
      fun routePortConnect(id: String, fromCtx: String, portId: String, name: String) {
        this@ExtensionRuntimeHost.routePortConnect(id, fromCtx, portId, name)
      }

      @JavascriptInterface
      fun routePortMessage(id: String, fromCtx: String, portId: String, msgJson: String) {
        this@ExtensionRuntimeHost.routePortMessage(id, fromCtx, portId, msgJson)
      }

      @JavascriptInterface
      fun routePortDisconnect(id: String, fromCtx: String, portId: String) {
        this@ExtensionRuntimeHost.routePortDisconnect(id, fromCtx, portId)
      }

      @JavascriptInterface
      fun nativeFetch(id: String, ctx: String, fetchId: String, url: String, optionsJson: String) {
        performNativeFetchAsync(id, ctx, fetchId, url, optionsJson)
      }

      @JavascriptInterface
      fun proxySet(id: String, detailsJson: String) {
        applyProxySettings(id, detailsJson)
      }

      @JavascriptInterface
      fun proxyClear(id: String) {
        clearProxySettings(id)
      }

      @JavascriptInterface
      fun deliverProxyAuth(host: String, username: String, password: String) {
        if (host.isNotBlank() && username.isNotBlank()) {
          cachedProxyAuth[host.lowercase()] = username to password
        }
      }

      @JavascriptInterface
      fun sendMessageToActiveTab(id: String, msgJson: String) {
        val script = extensionManager.buildMessageDispatchScript(id, msgJson)
        mainHandler.post { evaluateOnActiveStandardTab(script) }
      }

      @JavascriptInterface
      fun executeScriptInActiveTab(id: String, code: String) {
        if (code.isBlank()) return
        val prelude = extensionManager.buildChromeApiPrelude(id, contextKind = "content")
        val wrapped = "(function(){\n$prelude\ntry{\n$code\n}catch(e){}\n})();"
        mainHandler.post { evaluateOnActiveStandardTab(wrapped) }
      }

      @JavascriptInterface
      fun executeScriptFileInActiveTab(id: String, file: String) {
        val code = extensionManager.readExtensionFileText(id, file) ?: return
        executeScriptInActiveTab(id, code)
      }

      @JavascriptInterface
      fun insertCssInActiveTab(id: String, css: String) {
        if (css.isBlank()) return
        val js = "(function(){try{var s=document.createElement('style');s.setAttribute('data-kio-ext',${JSONObject.quote(id)});s.textContent=${JSONObject.quote(css)};(document.head||document.documentElement).appendChild(s);}catch(e){}})();"
        mainHandler.post { evaluateOnActiveStandardTab(js) }
      }

      @JavascriptInterface
      fun insertCssFileInActiveTab(id: String, file: String) {
        val css = extensionManager.readExtensionFileText(id, file) ?: return
        insertCssInActiveTab(id, css)
      }

      @JavascriptInterface
      fun removeCssFromActiveTab(id: String) {
        val js = "(function(){try{var els=document.querySelectorAll('style[data-kio-ext=\"$id\"]');for(var i=0;i<els.length;i++){if(els[i].parentNode)els[i].parentNode.removeChild(els[i]);}}catch(e){}})();"
        mainHandler.post { evaluateOnActiveStandardTab(js) }
      }

      @JavascriptInterface
      fun closePopup(id: String) {
        mainHandler.post { onClosePopup?.invoke() }
      }

      @JavascriptInterface
      fun reloadActiveTab() {
        mainHandler.post { onReloadActiveTab() }
      }

      @JavascriptInterface
      fun openOptionsPage(id: String) {
        val opt = extensionManager.getExtension(id)?.optionsPage ?: return
        mainHandler.post { onOpenOptions?.invoke(opt) }
      }

      @JavascriptInterface
      fun openTab(url: String) {
        mainHandler.post { onOpenTab(url) }
      }

      @JavascriptInterface
      fun getActiveUrl(): String = activeTabUrlProvider()

      @JavascriptInterface
      fun getActiveTitle(): String = activeTabTitleProvider()
    }
  }

  fun routeRuntimeMessage(extId: String, fromCtx: String, reqId: String, msgJson: String) {
    val js = "(function(){try{if(window.__kioHandleIncomingRpc){window.__kioHandleIncomingRpc(" +
      "${JSONObject.quote(extId)},${JSONObject.quote(fromCtx)},${JSONObject.quote(reqId)},${JSONObject.quote(msgJson)});}}catch(e){}})();"
    when (fromCtx) {
      "background" -> {
        evalInPopup(extId, js)
        mainHandler.post { evaluateOnActiveStandardTab(js) }
      }
      "popup" -> {
        ensureBackgroundRunning(extId)
        evalInBackground(extId, js)
        mainHandler.post { evaluateOnActiveStandardTab(js) }
      }
      else -> {
        ensureBackgroundRunning(extId)
        evalInBackground(extId, js)
        evalInPopup(extId, js)
      }
    }
  }

  fun replyRuntimeMessage(extId: String, targetCtx: String, reqId: String, replyJson: String) {
    val js = "(function(){try{if(window.__kioReceiveRpcReply){window.__kioReceiveRpcReply(" +
      "${JSONObject.quote(reqId)},${JSONObject.quote(replyJson)});}}catch(e){}})();"
    when (targetCtx) {
      "popup" -> evalInPopup(extId, js)
      "background" -> evalInBackground(extId, js)
      "content" -> mainHandler.post { evaluateOnActiveStandardTab(js) }
      else -> {
        evalInPopup(extId, js)
        mainHandler.post { evaluateOnActiveStandardTab(js) }
      }
    }
  }

  fun routePortConnect(extId: String, fromCtx: String, portId: String, name: String) {
    val js = "(function(){try{if(window.__kioHandleIncomingPortConnect){window.__kioHandleIncomingPortConnect(" +
      "${JSONObject.quote(extId)},${JSONObject.quote(fromCtx)},${JSONObject.quote(portId)},${JSONObject.quote(name)});}}catch(e){}})();"
    when (fromCtx) {
      "background" -> {
        evalInPopup(extId, js)
        mainHandler.post { evaluateOnActiveStandardTab(js) }
      }
      "popup" -> {
        ensureBackgroundRunning(extId)
        evalInBackground(extId, js)
        mainHandler.post { evaluateOnActiveStandardTab(js) }
      }
      else -> {
        ensureBackgroundRunning(extId)
        evalInBackground(extId, js)
        evalInPopup(extId, js)
      }
    }
  }

  fun routePortMessage(extId: String, fromCtx: String, portId: String, msgJson: String) {
    val js = "(function(){try{if(window.__kioHandleIncomingPortMessage){window.__kioHandleIncomingPortMessage(" +
      "${JSONObject.quote(portId)},${JSONObject.quote(msgJson)});}}catch(e){}})();"
    when (fromCtx) {
      "background" -> {
        evalInPopup(extId, js)
        mainHandler.post { evaluateOnActiveStandardTab(js) }
      }
      "popup" -> {
        evalInBackground(extId, js)
        mainHandler.post { evaluateOnActiveStandardTab(js) }
      }
      else -> {
        evalInBackground(extId, js)
        evalInPopup(extId, js)
      }
    }
  }

  fun routePortDisconnect(extId: String, fromCtx: String, portId: String) {
    val js = "(function(){try{if(window.__kioHandleIncomingPortDisconnect){window.__kioHandleIncomingPortDisconnect(" +
      "${JSONObject.quote(portId)});}}catch(e){}})();"
    when (fromCtx) {
      "background" -> {
        evalInPopup(extId, js)
        mainHandler.post { evaluateOnActiveStandardTab(js) }
      }
      "popup" -> {
        evalInBackground(extId, js)
        mainHandler.post { evaluateOnActiveStandardTab(js) }
      }
      else -> {
        evalInBackground(extId, js)
        evalInPopup(extId, js)
      }
    }
  }

  private fun performNativeFetchAsync(extId: String, targetCtx: String, fetchId: String, url: String, optionsJson: String) {
    fetchExecutor.execute {
      val res = extensionManager.performPrivilegedFetch(url, optionsJson)
      val errArg = if (res.error != null) JSONObject.quote(res.error) else "null"
      val js = "(function(){try{if(window.__kioDeliverNativeFetch){window.__kioDeliverNativeFetch(" +
        "${JSONObject.quote(fetchId)},${res.status},${JSONObject.quote(res.statusText)}," +
        "${JSONObject.quote(res.headersJson)},${JSONObject.quote(res.bodyBase64)},$errArg);}}catch(e){}})();"
      when (targetCtx) {
        "background" -> evalInBackground(extId, js)
        "popup" -> evalInPopup(extId, js)
      }
    }
  }

  private fun evalInBackground(extId: String, js: String) {
    if (bgReady[extId] == true) {
      val wv = bgWebViews[extId] ?: return
      mainHandler.post {
        try { wv.evaluateJavascript(js, null) } catch (_: Throwable) {}
      }
    } else {
      val queue = pendingBgScripts.computeIfAbsent(extId) { mutableListOf() }
      synchronized(queue) {
        if (queue.size < 64) queue.add(js)
      }
    }
  }

  private fun evalInPopup(extId: String, js: String) {
    val wv = activePopupViews[extId] ?: return
    mainHandler.post {
      try { wv.evaluateJavascript(js, null) } catch (_: Throwable) {}
    }
  }

  fun applyProxySettings(extId: String, detailsJson: String) {
    val parsed = extensionManager.parseProxyConfigFromJson(extId, detailsJson) ?: return
    if (parsed.proxyRules.isEmpty() || parsed.mode == "direct" || parsed.mode == "system") {
      clearProxySettings(extId)
      return
    }
    _activeProxy.value = parsed
    if (!privateModeSuspended) {
      applySystemProxyOverride(parsed)
    }
  }

  fun clearProxySettings(extId: String) {
    val current = _activeProxy.value
    if (current == null || current.extensionId == extId) {
      _activeProxy.value = null
      clearSystemProxyOverride()
    }
  }

  /**
   * Suspends any Standard-Mode extension proxy while in Private Mode (strict isolation),
   * and restores it cleanly when returning to Standard Mode.
   */
  fun setPrivateModeSuspended(isPrivate: Boolean) {
    privateModeSuspended = isPrivate
    val current = _activeProxy.value
    if (isPrivate) {
      if (current != null) clearSystemProxyOverride()
    } else {
      if (current != null) applySystemProxyOverride(current)
    }
  }

  private fun applySystemProxyOverride(config: ExtensionProxyConfig) {
    mainHandler.post {
      try {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) return@post
        val builder = ProxyConfig.Builder()
        for (rule in config.proxyRules) {
          builder.addProxyRule(rule)
        }
        builder.addBypassRule(ChromeExtensionManager.EXT_VIRTUAL_HOST)
        builder.addBypassRule("localhost")
        builder.addBypassRule("127.0.0.1")
        for (b in config.bypassList) {
          if (b.isNotBlank()) builder.addBypassRule(b)
        }
        ProxyController.getInstance().setProxyOverride(builder.build(), { it.run() }, {})
        val extName = extensionManager.getExtension(config.extensionId)?.name ?: "Extension"
        onToast("$extName proxy connected")
      } catch (_: Throwable) {
      }
    }
  }

  private fun clearSystemProxyOverride() {
    mainHandler.post {
      try {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) return@post
        ProxyController.getInstance().clearProxyOverride({ it.run() }, {})
      } catch (_: Throwable) {
      }
    }
  }

  /**
   * Queries active extension background hosts' chrome.webRequest.onAuthRequired listeners
   * when WebView encounters an authenticated proxy server.
   */
  fun resolveProxyAuth(host: String, port: Int, realm: String, onResult: (String?, String?) -> Unit) {
    val key = host.lowercase()
    cachedProxyAuth[key]?.let { (u, p) ->
      onResult(u, p)
      return
    }
    val proxyExtId = _activeProxy.value?.extensionId
    val targetIds = listOfNotNull(proxyExtId) + bgWebViews.keys.filter { it != proxyExtId }
    if (targetIds.isEmpty()) {
      onResult(null, null)
      return
    }
    val extId = targetIds.first()
    val wv = bgWebViews[extId]
    if (wv == null) {
      onResult(null, null)
      return
    }
    val js = "(function(){return window.__kioQueryProxyAuth?window.__kioQueryProxyAuth(" +
      "${JSONObject.quote(extId)},${JSONObject.quote(host)},$port,${JSONObject.quote(realm)}):'';})();"
    mainHandler.post {
      try {
        wv.evaluateJavascript(js) { raw ->
          var user: String? = null
          var pass: String? = null
          try {
            if (!raw.isNullOrBlank() && raw != "null" && raw != "\"\"") {
              val unwrapped = JSONObject("{\"v\":$raw}").getString("v")
              if (unwrapped.startsWith("{")) {
                val obj = JSONObject(unwrapped)
                user = obj.optString("username").takeIf { it.isNotBlank() }
                pass = obj.optString("password", "")
                if (user != null) {
                  cachedProxyAuth[key] = user!! to (pass ?: "")
                }
              }
            }
          } catch (_: Throwable) {
          }
          if (user != null) {
            onResult(user, pass)
          } else {
            // Brief retry check in case listener delivered async auth via __KioExtBridge.deliverProxyAuth
            mainHandler.postDelayed({
              val asyncCreds = cachedProxyAuth[key]
              onResult(asyncCreds?.first, asyncCreds?.second)
            }, 350)
          }
        }
      } catch (_: Throwable) {
        onResult(null, null)
      }
    }
  }

  fun stopBackground(extId: String) {
    clearProxySettings(extId)
    bgReady.remove(extId)
    pendingBgScripts.remove(extId)
    pendingBgReadyCallbacks.remove(extId)
    val wv = bgWebViews.remove(extId) ?: return
    mainHandler.post {
      try {
        wv.stopLoading()
        wv.destroy()
      } catch (_: Throwable) {
      }
    }
  }

  fun stopAll() {
    clearSystemProxyOverride()
    _activeProxy.value = null
    val ids = bgWebViews.keys.toList()
    ids.forEach { stopBackground(it) }
    activePopupViews.clear()
    cachedProxyAuth.clear()
  }
}
