# Pika

**Pika** is a minimal, privacy-first, security-hardened Android web browser shell built around the operating system's maintained **Android System WebView** (`android.webkit.WebView`) — with **zero bundled rendering engine**, **zero telemetry**, **zero dangerous Android permissions**, and a verified **1.29 MB (`1,286,849` bytes)** R8-minified release APK.

> **Engine Statement:** Pika is **not** a standalone browser rendering engine. It does not bundle Chromium, Blink, Gecko, or WebKit. All web rendering, TLS/HTTP transport inside WebView, and sandboxing are delegated to the OS-maintained **Android System WebView**.

---

## Assumed Target Defaults

Because no platform overrides were supplied prior to implementation, Pika targets:
1. **Target Platform:** Android (adaptive mobile + tablet/foldable UI).
2. **Minimum / Target OS:** Android 7.0 (`minSdk = 24`) targeting Android 16 (`targetSdk = 36`).
3. **Language & UI:** Kotlin + Jetpack Compose (Material 3) with zero bloated UI frameworks or icon packs.
4. **Browser Profile:** Privacy-first general web browser shell.
5. **License / Open Source:** Open-source-ready, modular Kotlin codebase with zero proprietary SDKs.

## Download APK

Pre-built signed release APK (< 2 MB) is tracked directly in this repository at [`releases/Pika-v1.0.0.apk`](releases/Pika-v1.0.0.apk) and attached to GitHub Releases (`v1.0.0`).

---

## Features (v1.0)

- **Address Bar & Navigation:** Direct URL entry, automatic URL vs. search query detection, user-selectable default search engine (DuckDuckGo default, Startpage, Qwant, Brave Search, Wikipedia, Google, or custom HTTPS template), live HTTPS/HTTP/Private security indicator, back, forward, reload/stop, pull-to-refresh at scroll top, copy URL, share, and open in system browser.
- **Tabs & Memory Saver:** Open, close, switch, search, reorder (accessible move up/down controls), and restore closed tabs. Uses a 2-WebView LRU pool that saves navigation `Bundle` state and destroys background `WebView` instances to minimize idle RAM and battery drain.
- **Error & Interstitial Pages:** Dedicated views for Offline, DNS Failure, Blocked Schemes/Resources, HTTPS-Only HTTP Interstitial Warning, and TLS Certificate Errors (with expandable certificate details and **no bypass option**).
- **Local Bookmarks:** Add, edit, delete, folder-organize, search, and import/export via Storage Access Framework (Netscape Bookmark HTML & JSON). Stored locally in SQLite only.
- **History:** Browse, search, delete individual entries, clear all history, or disable history recording entirely in Settings.
- **Consent-First Downloads:** Prompts before every download with filename, host, MIME type, and HTTPS status. Shows real-time progress, cancel support, open file (`FileProvider` / `MediaStore.Downloads`), and share/reveal location. Private downloads are wiped when the private session ends.
- **Find in Page:** Asynchronous in-page search with match counter and previous/next navigation.
- **Private Mode:** Isolated ephemeral tabs with `LOAD_NO_CACHE`, disabled DOM storage/history/cookies, distinct visual badge, and full wipe of cookies, WebStorage, cache, form data, and private downloads when ended.
- **Reader Mode:** On-demand distraction-free native Compose article view extracted without any `addJavascriptInterface` bridge, featuring adjustable font size and dark/light reading mode.
- **Settings & Accessibility:** Theme (System / Light / Dark), text zoom (75%–200%), RTL layout + Arabic (`ar`) localization, 48dp minimum touch targets, TalkBack content descriptions, reduced-motion awareness, and per-site permission management.

---

## Security Model

- **HTTPS-Only by Default:** HTTP URLs are upgraded to HTTPS automatically when HTTPS-Only is enabled, or blocked with an explicit warning interstitial before connecting.
- **Strict TLS Validation:** `WebViewClient.onReceivedSslError` always calls `handler.cancel()` — invalid/expired/untrusted certificates can never be bypassed.
- **Mixed Content Blocked:** `WebSettings.MIXED_CONTENT_NEVER_ALLOW` is enforced unconditionally.
- **Zero Web-to-Native Bridges:** `addJavascriptInterface` and `setWebContentsDebuggingEnabled` are never used.
- **Sandboxed File & Scheme Access:** `allowFileAccess`, `allowContentAccess`, `allowFileAccessFromFileURLs`, `allowUniversalAccessFromFileURLs`, `databaseEnabled`, and `saveFormData` are all disabled. `javascript:`, `data:`, `file:`, `content:`, and `vbscript:` navigations are blocked.
- **Consent-Gated Capabilities:** External app links (`mailto:`, `tel:`, `intent:`), popups, fullscreen, geolocation, media permissions, file uploads (via zero-permission SAF), and downloads all require explicit user consent.
- **Encrypted Settings at Rest:** Settings and saved per-site permission rules are encrypted using **Android Keystore (`AES/GCM/NoPadding`, 256-bit)** in app-private storage.
- **Input Sanitization:** All user inputs, filenames, titles, and imported bookmarks strip control characters, BiDi override characters, and path traversal sequences.

---

## Privacy Policy & Network Transparency

- **Zero Telemetry & Zero Analytics:** Pika contains no analytics SDKs, crash reporters, ad SDKs, remote-config endpoints, or update checkers.
- **Zero Background Network Activity:** No background services, polling, or pre-fetching occur. Network requests happen only when you navigate, search, or explicitly approve a download.
- **Tracking Protection (Default-On):** Strips common tracking query parameters (`utm_*`, `fbclid`, `gclid`, `mc_eid`, `igshid`, etc.), blocks known third-party tracker/analytics hosts in `shouldInterceptRequest`, blocks third-party cookies by default, and sends `DNT: 1` / `Sec-GPC: 1` headers (with an explicit Settings notice explaining that websites are not forced to honor DNT).
- **Declared Android Permissions:**
  - `android.permission.INTERNET` — Required to load web pages and user-approved downloads.
  - `android.permission.ACCESS_NETWORK_STATE` — Used locally to detect offline state for error pages.
  - **Zero dangerous runtime permissions** (`CAMERA`, `RECORD_AUDIO`, `ACCESS_FINE_LOCATION`, `READ_EXTERNAL_STORAGE`, etc. are not declared). `android:allowBackup="false"` prevents silent cloud backups of local browser data.

---

## Size Budget & Release Verification

| Component | Budget | Measured (Release APK) |
|---|---:|---:|
| Application binary (`classes.dex` + `lib/` + builtins) | 900 KB | ~1,111 KB compressed (`classes.dex`: 1,037 KB + `libandroidx.graphics.path.so`: 37 KB + kotlin builtins: 12 KB) |
| UI assets | 200 KB | ~7 KB (`pika_icon_fg_1794511007288.jpg`) |
| Icons | 100 KB | ~71 KB across 10 mipmap PNGs (zero `material-icons-extended`) |
| Localized strings & resources (`resources.arsc` + XML) | 200 KB | ~21 KB (`en` + `ar` RTL only via `localeFilters`) |
| Configuration and metadata | 100 KB | ~8 KB |
| Safety margin | 500 KB | — |
| **Total Package** | **2 MB (2,097,152 B) max** | **1.29 MB (`1,286,849` bytes)** |

- **Compressed APK size:** `1,286,849` bytes (~1.23 MiB / 1.29 MB)
- **Uncompressed archive payload:** `2,359,357` bytes (~2.25 MiB)
- **Top 5 contributors (compressed inside `app-release.apk`):**
  1. `classes.dex` — `1,062,266` bytes (`2,128,092` bytes uncompressed)
  2. `res/o-.png` (`xxxhdpi/ic_launcher.png`) — `25,538` bytes
  3. `res/Gc.png` (`xxxhdpi/ic_launcher_round.png`) — `24,508` bytes
  4. `resources.arsc` — `19,544` bytes
  5. `res/RJ.png` (`xxhdpi/ic_launcher.png`) — `16,678` bytes

---

## Build & Test Instructions

- **Run Unit & Robolectric Tests:**
  ```bash
  gradle :app:testDebugUnitTest
  ```
- **Build Debug APK:**
  ```bash
  gradle :app:assembleDebug
  ```
- **Build R8-Minified Release APK:**
  Provide release keystore environment variables (`KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`) or inject signing properties:
  ```bash
  gradle :app:assembleRelease
  ```
  Output artifact: `app/build/outputs/apk/release/app-release.apk`

---

## Known Limitations

- Web rendering features, codec support, and OS-level sandboxing depend on the installed version of **Android System WebView**.
- Android `WebView` instances share a single process-wide `CookieManager` / `WebStorage` namespace across tabs; therefore, Pika enforces strict session isolation by disabling persistent cookies/DOM storage in private tabs and performing a full wipe (`removeAllCookies`, `WebStorage.deleteAllData`, cache/history/form-data purge) when private sessions end.
- Hardware permissions (Camera, Microphone, GPS) are intentionally omitted from `AndroidManifest.xml` for minimal privilege; sites requesting them show a consent/policy prompt and cannot access device hardware.

## Roadmap

- Optional user-importable custom tracker blocklists (JSON/hosts format)
- Tab groups and per-site JavaScript / zoom overrides
- Multi-window desktop/tablet keyboard shortcuts (`Ctrl+T`, `Ctrl+W`, `Ctrl+L`, `Ctrl+F`)
