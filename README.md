# Kio

**Kio** is a lightning-fast, privacy-hardened Android web browser built around the operating system's maintained **Android System WebView** (`android.webkit.WebView`) — featuring **Desktop Chrome Extensions**, a **Pure On-Device NLP Content Announcer / Reader Mode (zero data copy or save)**, **True Edge-to-Edge Full Screen Mode**, **Real Website Logos**, and a **signed R8-minified release APK strictly under 2 MB**.

> **Engine Statement:** Kio delegates web rendering, TLS/HTTP transport, and sandboxing to the OS-maintained **Android System WebView**, adding zero-dependency Desktop Chrome Extension execution, privacy shields, and on-device NLP narration without bundling a 150 MB Chromium binary.

---

## Download APK

Pre-built signed release APK (< 2 MB) is tracked at [`releases/Kio-v1.9.3.apk`](releases/Kio-v1.9.3.apk) and published in GitHub Releases (`v1.9.3`).

---

## Core Highlights (v1.9.3 — Build 65)

- **Hidden-"K" Concentric App Launcher Icon & Preserved Homepage Emblem:** Rebuilt the app icon from scratch on the classic concentric orbital logo using the exact cyan/sky/indigo/white palette, subtly embedding a hidden geometric **K** inside the inner orbital chord and prism rays while keeping the homepage emblem 100% untouched.
- **Desktop Chrome Extensions (Kiwi-Style Direct Store Install & Full MV2/MV3 Engine):** Automatically switches Chrome Web Store to Desktop Chrome mode, clears false WebView unavailability banners, enables the Store's own install button directly to Kio's resilient CRX3 downloader (up to 4,000 files), injects synchronous `chrome.*` / `browser.*` / `GM_*` APIs before popup scripts run, serves `chrome-extension://` virtual resources, and supports live Popup ↔ Active Tab messaging, `chrome.storage.onChanged` broadcasts, `chrome.scripting`, and static DNR rules (Standard Mode only).
- **Low-Data Surfing & Ultra-Lean Android Footprint:** Always-on prefetch/prerender blocking, native lazy loading & async image decoding, WebP favicon compression, automatic disk cache & temp-file trimming, and `onTrimMemory` background WebView eviction deliver top performance with minimal data and storage usage.
- **Zero-Leak Bidirectional Isolation (Standard ↔ Private):** Strict mode-boundary cookie vaulting & incineration between Standard and Private tabs, ephemeral RAM-only `localStorage`/`sessionStorage` proxy in Private Mode, disabled IndexedDB/CacheStorage/third-party cookies/extensions/bookmarks/history/external apps/sharing/shortcuts/screenshots in Private Mode so zero data can leak in either direction.
- **Exclusive Private-Mode Safety Tools (3-Dot Menu):** In Private Mode, Bookmarks, History, Extensions, Share, Open Externally, and Add Shortcut are disabled and replaced by 5 exclusive Private-Only tools: **Burn session**, **New identity**, **Shield audit**, **Wipe clipboard**, and **Purge RAM**.
- **Full Dark Mode & Light Mode:** Seamless browser-wide switching between OLED Dark Mode and crisp Light Mode across Homepage (with preserved Kio emblem), search bar, Android status & navigation bars, menus, settings, and websites.
- **Content Announcer & Reader Mode (Pure On-Device NLP, Zero Data Copy/Save):** Accessible right inside the 3-dot menu (`Narrate`), extracts readable prose into volatile RAM only, normalizes web prose for natural spoken cadence, segments sentences with abbreviation awareness, narrates on-device with live sentence highlighting & speed/skip controls, disables copying/saving completely, and wipes volatile RAM immediately on close.
- **True Edge-to-Edge Full Screen Mode:** Placed in the 3-dot menu (`Full screen`), hides the browser toolbar and Android system bars edge-to-edge with zero inset bars and an unobtrusive Exit pill + Back restoration.
- **Lightning-Fast & Strictly Under 2 MB:** Zero telemetry, zero bloat, and aggressive R8 optimization keeping the signed release APK strictly under 2 MB.
