# Bat

**Bat** is a minimal, privacy-first, security-hardened Android web browser shell built around the operating system's maintained **Android System WebView** (`android.webkit.WebView`) — with **zero bundled rendering engine**, **zero telemetry**, **zero dangerous Android permissions**, and a **signed R8-minified release APK strictly under 2 MB**.

> **Engine Statement:** Bat is **not** a standalone browser rendering engine. It does not bundle Chromium, Blink, Gecko, or WebKit. All web rendering, TLS/HTTP transport inside WebView, and sandboxing are delegated to the OS-maintained **Android System WebView**.

---

## Version History

| Version | Version Code | Target Platforms & OS | Key Highlights |
|---|---:|---|---|
| **v1.1.2** | `5` | Android 8.0–17 (Phone, Tablet, Foldable, Desktop Windowing) | Fixed Android 17 & real-device crash vectors (`shouldInterceptRequest` background-thread host tracking, `MutableContextWrapper` + `AndroidView.onRelease` lifecycle binding, `graphics-path:1.1.0` 16 KB page alignment, Compose BOM `2025.12.01`, `ViewModel`/`WebViewClient` R8 keep rules, throwaway WebView removal, and SQLite/picker/clipboard error guards) |
| **v1.1.1** | `4` | Android 8.0–17 (Phone, Tablet, Foldable, Desktop Windowing) | Removed bottom search bar (top-only omnibox), streamlined clean production UI copy without internal jargon, fixed back navigation from error/HTTP warnings, aborted-load error suppression, density-scaled pull-to-refresh, overlay snackbar feedback, and bookmark/settings validation |
| **v1.1.0** | `3` | Android 8.0–17 (Phone, Tablet, Foldable, Desktop Windowing) | Rebranded to **Bat**, custom 3D Batman-inspired bat mascot icon + Material You monochrome crest, adaptive tablet `NavigationRail` + single-row wide toolbar + multi-column grids, hardware keyboard shortcuts |
| **v1.0.0** | `1–2` | Android 7.0–16 | Initial Feather/Pika browser shell, Android Keystore AES-256-GCM settings, ad/tracker shield, Via/X power tools (< 2 MB) |

---

## Assumed Target Defaults

Because no platform overrides were supplied prior to implementation, Bat targets:
1. **Target Platform:** Android Phone, Tablet, Foldable & Desktop Windowing (`resizeableActivity="true"`, adaptive `NavigationRail` + single-row wide toolbar + multi-column grids).
2. **Minimum / Target OS:** Android 8.0 (`minSdk = 26`) through Android 17 forward-compatible edge-to-edge & large-screen behavior (`targetSdk = 36`, `compileSdk = 36.1`).
3. **Language & UI:** Kotlin + Jetpack Compose (Material 3) with zero bloated UI frameworks or icon packs.
4. **Browser Profile:** Privacy-first general web browser shell.
5. **License / Open Source:** Open-source-ready, modular Kotlin codebase with zero proprietary SDKs.

## Download APK

Pre-built signed release APK (< 2 MB) is tracked directly in this repository at [`releases/Bat-v1.1.2.apk`](releases/Bat-v1.1.2.apk) and attached to GitHub Releases (`v1.1.2`).

---

## Features (v1.1.2)

- **Address Bar & Swipe Gestures:** Top-docked address & navigation bar across phone and tablet, horizontal omnibox swipe to switch tabs, direct URL entry, search engine choice (DuckDuckGo, Startpage, Qwant, Brave, Wikipedia, Google, custom HTTPS), live security badge, copy, share, and external browser handoff.
- **Power Tools (Via / X Style):** Compact 4-column Quick Grid Menu with **Media & Resource Sniffer**, **Night Web** page darkening, **No-Image** data-saver mode, **Desktop / iPhone / Mobile** User-Agent switcher, **Immersive Fullscreen**, **Save Page as PDF** (`PrintManager`), **View Page Source**, **Translate**, and **Pin Shortcut to Home Screen**.
- **Privacy & Ad Shield:** Built-in ad/tracker/popup blocker + user **Custom Block Rules** stored encrypted in Android Keystore, third-party cookie blocking, query-parameter stripping, HTTPS-Only mode, `DNT: 1` / `Sec-GPC: 1`, and isolated **Private Mode** wiped cleanly on exit.
- **Tabs, Bookmarks, History & Downloads:** 2-WebView LRU RAM saver pool with `onRenderProcessGone` crash recovery, closed tab restore, local SQLite bookmarks (Netscape HTML & JSON import/export), search/clearable history, and consent-first downloads (`MediaStore.Downloads` on Android 10–17 & `FileProvider` on Android 8–9) with editable filenames and retry support.
- **Reader, Tablet & Keyboard Support:** Adaptive tablet `NavigationRail` + single-row wide toolbar + multi-column tab/library grids, hardware keyboard shortcuts (`Ctrl+T`, `Ctrl+Shift+N`, `Ctrl+W`, `Ctrl+F`, `Ctrl+R`/`F5`, `Alt+Left/Right`), native Compose distraction-free Reader View, Find in Page, Material You Dynamic Color (Android 12+), Predictive Back (Android 14–17), reactive light/dark edge-to-edge bars (Android 8–17), RTL + Arabic (`ar`) support, and 48dp touch targets.

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

- **Zero Telemetry & Zero Analytics:** Bat contains no analytics SDKs, crash reporters, ad SDKs, remote-config endpoints, or update checkers.
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
| UI assets | 200 KB | ~15 KB (`bat_icon_fg.png` 3D mascot crest) |
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
- Android `WebView` instances share a single process-wide `CookieManager` / `WebStorage` namespace across tabs; therefore, Bat enforces strict session isolation by disabling persistent cookies/DOM storage in private tabs and performing a full wipe (`removeAllCookies`, `WebStorage.deleteAllData`, cache/history/form-data purge) when private sessions end.
- Hardware permissions (Camera, Microphone, GPS) are intentionally omitted from `AndroidManifest.xml` for minimal privilege; sites requesting them show a consent/policy prompt and cannot access device hardware.

## Roadmap

- Optional user-importable custom tracker blocklists (JSON/hosts format)
- Tab groups and per-site JavaScript / zoom overrides
