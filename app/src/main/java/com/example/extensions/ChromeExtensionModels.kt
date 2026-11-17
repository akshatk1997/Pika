package com.example.extensions

data class ExtensionContentScript(
  val matches: List<String>,
  val excludeMatches: List<String> = emptyList(),
  val jsFiles: List<String> = emptyList(),
  val cssFiles: List<String> = emptyList(),
  val runAt: String = "document_idle",
  val allFrames: Boolean = false
)

data class ExtensionDnrRule(
  val urlFilter: String = "",
  val requestDomains: List<String> = emptyList(),
  val excludedDomains: List<String> = emptyList()
)

data class ExtensionProxyConfig(
  val extensionId: String,
  val mode: String,
  val proxyRules: List<String> = emptyList(),
  val bypassList: List<String> = emptyList()
)

data class InstalledExtension(
  val id: String,
  val name: String,
  val version: String,
  val description: String,
  val enabled: Boolean = true,
  val installDirPath: String,
  val iconRelativePath: String? = null,
  val defaultPopup: String? = null,
  val defaultTitle: String? = null,
  val optionsPage: String? = null,
  val manifestVersion: Int = 3,
  val permissions: List<String> = emptyList(),
  val hostPermissions: List<String> = emptyList(),
  val backgroundServiceWorker: String? = null,
  val backgroundIsModule: Boolean = false,
  val backgroundScripts: List<String> = emptyList(),
  val backgroundPage: String? = null,
  val contentScripts: List<ExtensionContentScript> = emptyList(),
  val dnrRulesCount: Int = 0,
  val sourceUrl: String = "",
  val installedAt: Long = System.currentTimeMillis()
) {
  val hasBackgroundRuntime: Boolean
    get() = !backgroundServiceWorker.isNullOrBlank() ||
      backgroundScripts.isNotEmpty() ||
      !backgroundPage.isNullOrBlank()
}

data class PopularExtensionPreset(
  val id: String,
  val name: String,
  val summary: String,
  val category: String
) {
  val webStoreUrl: String
    get() = "https://chromewebstore.google.com/detail/$id"
}

sealed class ExtensionInstallResult {
  data class Success(val extension: InstalledExtension) : ExtensionInstallResult()
  data class Failure(val reason: String) : ExtensionInstallResult()
}
