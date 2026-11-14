package com.example.model

enum class SecurityState {
  HOME,
  SECURE_HTTPS,
  INSECURE_HTTP,
  PRIVATE,
  ERROR
}

enum class PageErrorType {
  OFFLINE,
  DNS_FAILURE,
  CERTIFICATE_ERROR,
  BLOCKED,
  GENERIC
}

data class PageError(
  val type: PageErrorType,
  val failingUrl: String,
  val host: String,
  val reason: String,
  val certificateDetail: String? = null,
  val upgradedFromHttpUrl: String? = null
)

data class HttpWarningState(
  val originalUrl: String,
  val host: String,
  val upgradeAttemptFailed: Boolean
)

data class ExternalLinkPrompt(
  val url: String,
  val scheme: String,
  val targetSummary: String
)

enum class WebPermissionKind {
  LOCATION,
  CAMERA_MIC,
  PROTECTED_MEDIA,
  POPUP,
  FULLSCREEN,
  CLIPBOARD
}

data class WebPermissionPrompt(
  val id: String,
  val origin: String,
  val kind: WebPermissionKind,
  val detail: String
)

data class LinkContextTarget(
  val url: String,
  val isImage: Boolean
)

data class ClearDataOptions(
  val clearHistory: Boolean = true,
  val clearCookies: Boolean = true,
  val clearCache: Boolean = true,
  val clearSitePermissions: Boolean = false
)

enum class UserAgentMode(val id: String, val label: String) {
  MOBILE("mobile", "Mobile"),
  DESKTOP("desktop", "Desktop"),
  IPHONE("iphone", "iPhone");

  companion object {
    fun fromId(id: String): UserAgentMode = entries.firstOrNull { it.id == id } ?: MOBILE
  }
}

enum class SniffedMediaType(val label: String) {
  VIDEO("Video"),
  AUDIO("Audio"),
  IMAGE("Image"),
  DOCUMENT("File")
}

data class SniffedResource(
  val url: String,
  val host: String,
  val fileName: String,
  val type: SniffedMediaType
)

data class TabInfo(
  val id: String,
  val title: String = "New Tab",
  val url: String = "",
  val isPrivate: Boolean = false,
  val userAgentMode: UserAgentMode = UserAgentMode.MOBILE,
  val upgradedFromHttpUrl: String? = null,
  val isLoading: Boolean = false,
  val progress: Int = 0,
  val canGoBack: Boolean = false,
  val canGoForward: Boolean = false,
  val securityState: SecurityState = SecurityState.HOME,
  val trackersBlockedCount: Int = 0,
  val sniffedResourcesCount: Int = 0,
  val errorState: PageError? = null,
  val httpWarning: HttpWarningState? = null,
  val faviconInitial: String = "",
  val lastActiveAt: Long = System.currentTimeMillis()
) {
  val isDesktopMode: Boolean get() = userAgentMode == UserAgentMode.DESKTOP
}

data class ClosedTabRecord(
  val title: String,
  val url: String,
  val closedAt: Long = System.currentTimeMillis()
)

data class BookmarkItem(
  val id: Long = 0L,
  val title: String,
  val url: String,
  val folder: String = "Bookmarks",
  val createdAt: Long = System.currentTimeMillis()
)

data class HistoryItem(
  val id: Long = 0L,
  val title: String,
  val url: String,
  val visitedAt: Long = System.currentTimeMillis()
)

enum class DownloadStatus {
  PENDING_CONSENT,
  DOWNLOADING,
  COMPLETED,
  CANCELLED,
  FAILED
}

data class PendingDownloadConsent(
  val id: String,
  val url: String,
  val fileName: String,
  val host: String,
  val mimeType: String,
  val contentLengthBytes: Long,
  val isHttps: Boolean,
  val isPrivateTab: Boolean
)

data class DownloadRecord(
  val id: String,
  val url: String,
  val fileName: String,
  val mimeType: String,
  val totalBytes: Long,
  val downloadedBytes: Long,
  val status: DownloadStatus,
  val localFilePath: String? = null,
  val contentUriString: String? = null,
  val errorMessage: String? = null,
  val isPrivate: Boolean = false,
  val createdAt: Long = System.currentTimeMillis()
)

enum class SearchEngine(
  val id: String,
  val label: String,
  val queryTemplate: String
) {
  DUCKDUCKGO("ddg", "DuckDuckGo", "https://duckduckgo.com/?q=%s"),
  DUCK_AI("duck_ai", "Duck.ai (AI Mode)", "https://duckduckgo.com/?ia=chat&duckchat=1&q=%s"),
  STARTPAGE("startpage", "Startpage", "https://www.startpage.com/do/dsearch?query=%s"),
  QWANT("qwant", "Qwant", "https://www.qwant.com/?q=%s"),
  BRAVE("brave", "Brave Search", "https://search.brave.com/search?q=%s"),
  WIKIPEDIA("wiki", "Wikipedia", "https://en.wikipedia.org/wiki/Special:Search?search=%s"),
  GOOGLE("google", "Google", "https://www.google.com/search?q=%s"),
  CUSTOM("custom", "Custom", "https://duckduckgo.com/?q=%s");

  companion object {
    fun fromId(id: String): SearchEngine = entries.firstOrNull { it.id == id } ?: DUCKDUCKGO
  }
}

enum class ThemeMode(val id: String) {
  SYSTEM("system"),
  LIGHT("light"),
  DARK("dark");

  companion object {
    fun fromId(id: String): ThemeMode = entries.firstOrNull { it.id == id } ?: SYSTEM
  }
}

data class SitePermissionRule(
  val origin: String,
  val kind: WebPermissionKind,
  val allowed: Boolean
)

data class SettingsState(
  val searchEngine: SearchEngine = SearchEngine.DUCKDUCKGO,
  val customSearchTemplate: String = "https://duckduckgo.com/?q=%s",
  val homepageUrl: String = "",
  val retainOpenTabs: Boolean = true,
  val httpsOnlyEnabled: Boolean = true,
  val blockThirdPartyCookies: Boolean = true,
  val trackerBlockingEnabled: Boolean = true,
  val historyEnabled: Boolean = true,
  val doNotTrackEnabled: Boolean = true,
  val javascriptEnabled: Boolean = true,
  val textZoomPercent: Int = 100,
  val themeMode: ThemeMode = ThemeMode.SYSTEM,
  val dynamicColor: Boolean = false,
  val nightWebMode: Boolean = false,
  val noImageMode: Boolean = false,
  val customBlockedHosts: List<String> = emptyList(),
  val readerFontSizeSp: Int = 18,
  val readerDarkMode: Boolean = false,
  val backgroundPlayEnabled: Boolean = true
)

data class ReaderArticle(
  val title: String,
  val siteHost: String,
  val paragraphs: List<String>
)
