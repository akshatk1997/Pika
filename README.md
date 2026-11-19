# Kio

**Kio** is a lightning-fast, privacy-hardened Android web browser built around the operating system's maintained **Android System WebView** (`android.webkit.WebView`) — featuring **Desktop Chrome Extensions**, a **Pure On-Device NLP Content Announcer / Reader Mode (zero data copy or save)**, **True Edge-to-Edge Full Screen Mode**, **Real Website Logos**, and a **signed R8-minified release APK strictly under 2 MB**.

> **Engine Statement:** Kio delegates web rendering, TLS/HTTP transport, and sandboxing to the OS-maintained **Android System WebView**, adding zero-dependency Desktop Chrome Extension execution, privacy shields, and on-device NLP narration without bundling a 150 MB Chromium binary.

---

## Download APK

Pre-built signed release APK (< 2 MB) is tracked at [`releases/Kio-v1.9.8.apk`](releases/Kio-v1.9.8.apk) and published in GitHub Releases (`v1.9.8`).

---

## Core Highlights (v1.9.8 — Build 70)

- **Kiwi-Style Chrome Extension Architecture & Runtime Parity:**
  - **Kiwi-Style 3-Dot Menu Extension Entry Point:** Dedicated 'Extensions' tile in the 3-dot menu grid, plus an 'EXTENSIONS' section at the bottom of the 3-dot menu listing all enabled extensions with icons and names. Tapping any extension launches its popup dialog directly over the active web page (or triggers `chrome.action.onClicked`).
  - **Single-Source RPC Message Routing:** Solved race condition where tab scripts prematurely responded `null` and closed pending popup RPCs. Runtime messages from popups route strictly to the background worker (`routeRuntimeMessage`), while `tabs.sendMessage` routes to active tab content scripts (`routeTabMessage`).
  - **Content Script Stage='all' Injection:** Guarantees all content scripts execute reliably—including `run_at: "document_start"` extensions (such as Dark Reader)—with single-run idempotency guards per page.
  - **Root-Relative Virtual Asset Resolution & GZIP Decompression:** Virtual extension resource loader safely resolves root-relative paths (`/static/...`, `/assets/...`) using HTTP Referer headers and active popup extension tracking; transparent GZIP stream decompression in privileged fetch.
- **Kiwi-Style Chrome Extension Engine Parity & Full WebExtension API Suite:** Complete polyfills for `chrome.storage.session.setAccessLevel`, `chrome.action.getUserSettings`, `openPopup`, `getTitle`, `getPopup`, `isEnabled`, `chrome.extension.getViews`, `isAllowedIncognitoAccess`, `isAllowedFileSchemeAccess`, `sendRequest`, `onRequest`, `chrome.declarativeNetRequest` (static & dynamic rules, options, constants), `chrome.webRequest.handlerBehaviorChanged`, `chrome.cookies.getAllCookieStores`, `chrome.i18n.detectLanguage`, `chrome.dom.openOrClosedShadowRoot`, and `chrome.system` (CPU, memory, storage).
- **Full Bidirectional Mesh Messaging & Ports:** Robust async `chrome.tabs.sendMessage`, `chrome.tabs.connect`, and `chrome.runtime.sendMessage` routing with fast reply handling between Popups, Background Workers, and Active Tab Content Scripts.
- **CSP Relaxation & Injected Script Execution:** Strips restrictive `Content-Security-Policy` and `X-Frame-Options` meta tags from extension popups, fixes case-sensitive disk asset resolution on Android filesystems, and auto-dismisses popups via `window.close()` / `WebChromeClient`.
- **1-Tap Popup Launch:** Installed extension cards in Extensions screen are directly clickable to open extension popups instantly.
- **3-Dot Menu Overhaul (Modes tile, Block Images, Clean Header):** Cleaned up cluttered header chips, added a dedicated **Modes** tile with instant switching between Desktop, iPhone, and Mobile site viewports, and replaced Extensions with an instant **Block Images** toggle.
- **Enhanced Settings Sections with Feature Details:** Upgraded all category folder subtitles with rich technical feature details, and embedded the official vector **Kio launcher icon** inside About Kio and the main settings card.
- **Professional UI Wording:** Streamlined toasts, status labels, and banners for both Standard and Private Browsing modes.
- **Full Chrome Extension Background Service Worker Runtime (MV3/MV2):** Runs background service workers, real async RPC/Port messaging, CORS-free privileged fetch/XHR, and AndroidX WebKit `ProxyController` integration.
- **Hidden-"K" Concentric App Launcher Icon & Preserved Homepage Emblem:** Rebuilt the app icon on the classic concentric orbital logo using the exact cyan/sky/indigo/white palette, subtly embedding a hidden geometric **K** inside the inner orbital chord and prism rays while keeping the homepage emblem 100% untouched.
- **Desktop Chrome Extensions (Kiwi-Style Direct Store Install & Full MV2/MV3 Engine):** Automatically switches Chrome Web Store to Desktop Chrome mode, clears false WebView unavailability banners, enables the Store's own install button directly to Kio's resilient CRX3 downloader (up to 4,000 files), injects synchronous `chrome.*` / `browser.*` / `GM_*` APIs before popup scripts run, serves `chrome-extension://` virtual resources, and supports live Popup ↔ Active Tab messaging, `chrome.storage.onChanged` broadcasts, `chrome.scripting`, and static DNR rules (Standard Mode only).
- **Low-Data Surfing & Ultra-Lean Android Footprint:** Always-on prefetch/prerender blocking, native lazy loading & async image decoding, WebP favicon compression, automatic disk cache & temp-file trimming, and `onTrimMemory` background WebView eviction deliver top performance with minimal data and storage usage.
- **Zero-Leak Bidirectional Isolation (Standard ↔ Private):** Strict mode-boundary cookie vaulting & incineration between Standard and Private tabs, ephemeral RAM-only `localStorage`/`sessionStorage` proxy in Private Mode, disabled IndexedDB/CacheStorage/third-party cookies/extensions/bookmarks/history/external apps/sharing/shortcuts/screenshots in Private Mode so zero data can leak in either direction.
- **Exclusive Private-Mode Safety Tools (3-Dot Menu):** In Private Mode, Bookmarks, History, Extensions, Share, Open Externally, and Add Shortcut are disabled and replaced by 5 exclusive Private-Only tools: **Burn session**, **New identity**, **Shield audit**, **Wipe clipboard**, and **Purge RAM**.
- **Full Dark Mode & Light Mode:** Seamless browser-wide switching between OLED Dark Mode and crisp Light Mode across Homepage (with preserved Kio emblem), search bar, Android status & navigation bars, menus, settings, and websites.
- **Content Announcer & Reader Mode (Pure On-Device NLP, Zero Data Copy/Save):** Accessible right inside the 3-dot menu (`Narrate`), extracts readable prose into volatile RAM only, normalizes web prose for natural spoken cadence, segments sentences with abbreviation awareness, narrates on-device with live sentence highlighting & speed/skip controls, disables copying/saving completely, and wipes volatile RAM immediately on close.
- **True Edge-to-Edge Full Screen Mode:** Placed in the 3-dot menu (`Full screen`), hides the browser toolbar and Android system bars edge-to-edge with zero inset bars and an unobtrusive Exit pill + Back restoration.
- **Lightning-Fast & Strictly Under 2 MB:** Zero telemetry, zero bloat, and aggressive R8 optimization keeping the signed release APK strictly under 2 MB.
