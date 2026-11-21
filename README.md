# Kio

**Kio** is a lightning-fast, privacy-hardened web browser built for **Android** and **Windows PC Desktop** around the operating system's native hardware-accelerated web engine (**Android System WebView** on mobile, **Microsoft Edge WebView2** on PC) — featuring **120Hz/144Hz Smooth Rendering**, the full **Soul Browser Power-User Suite** (Media & Video Downloader/Sniffer, Image Box Batch Scraper, Full-Page Scroll Capture, Screen Filter, Clean Mode), **Zero-Leak Private Browsing**, and release distributions **strictly under 2 MB**.

> **Engine Statement:** Kio delegates core web rendering, TLS/HTTP transport, and GPU hardware acceleration to the OS-maintained web engine (`android.webkit.WebView` on Android, `Microsoft.Web.WebView2` on Windows), delivering top-tier performance, instantaneous startup, and privacy shields without forcing users to download a bloated 150–200 MB Chromium binary.

---

## Downloads (< 2 MB)

| Platform | Format | Size | Download Link |
| :--- | :--- | :--- | :--- |
| **Android** | Signed Release APK | **~1.47 MB** | [`releases/Kio-v2.0.0.apk`](releases/Kio-v2.0.0.apk) |
| **Windows PC** | Portable Zip Package | **~0.25 MB (256 KB)** | [`releases/Kio-Desktop-v2.0.0-windows-x64.zip`](releases/Kio-Desktop-v2.0.0-windows-x64.zip) |

*Both releases are tracked in the repository and published under GitHub Release [`v2.0.0`](https://github.com/akshatk1997/Pika/releases/tag/v2.0.0).*

---

## Kio for PC Desktop Highlights

- **Ultra-Lightweight Portable Footprint (< 1 MB Uncompressed, ~256 KB Zipped):**
  - Requires zero heavy installation. Just extract and double-click `Kio.exe` to run immediately.
  - Leverages the OS pre-installed Microsoft Edge WebView2 Runtime (installed on 100% of Windows 10/11 PCs), keeping the entire app directory under 0.9 MB with zero system bloat.
- **Full Soul Browser Power-User Suite for PC:**
  - **Media & Video Downloader / Sniffer:** Live toolbar badge (`🎬 Media (N)`) sniffs audio/video streams (`.mp4`, `.webm`, `.m3u8`, `.mp3`) with 1-tap download into `Downloads\Kio\`, launch in VLC / system media player, and direct stream URL copying.
  - **Save All Images ("Image Box"):** 1-tap scraper extracting all images (`<img>`, `<picture>`, CSS background-images) with interactive gallery preview, "Select All", and batch download into `Pictures\Kio\`.
  - **Full-Page Scrolling Screenshot ("Scroll Capture"):** Captures full vertical documents without viewport cutoff into lossless PNGs in `Pictures\Kio\`.
  - **Screen Filter (Eye Care Amber & OLED Night Dimmer):** Built-in Warm Amber (`#FFB74D`) and OLED Dimmer with real-time 10%–80% opacity slider and click-through transparency (`WS_EX_TRANSPARENT`).
  - **Clean Mode (Distraction-Free):** 1-tap toggle button that automatically strips cookie consent walls, GDPR dialogs, newsletter popups, and sticky banner ads without breaking layouts.
  - **Mode Switcher (UA Emulation):** Instant 1-tap switching between Desktop PC, Android Mobile (Pixel), and iPhone (Safari iOS) viewports.
  - **Block Images Toggle:** 1-tap toggle to block images for low-bandwidth environments.
  - **Reader Mode & Speech Announcer:** Distills prose and narrates articles on-device using native Windows `System.Speech` TTS (100% offline, zero cloud communication).
- **Multi-Tab Architecture & Privacy Stealth Mode:**
  - Custom-painted tab bar with smooth switching, close buttons (`×`), new standard tab (`+`), and new Private Tab (`🛡️+`).
  - Private tabs run with isolated ephemeral cache and zero persistent disk history or cookies.
  - Full keyboard shortcuts (`Ctrl+T`, `Ctrl+W`, `Ctrl+Shift+P`, `Ctrl+L`, `Ctrl+R`, `F5`, `F11`, `Alt+Left`, `Alt+Right`, `Ctrl++/-`).

---

## Core Highlights (v2.0.0 — Build 72)

- **Soul Browser Power-User Feature Suite:**
  - **Media & Video Downloader / Sniffer:** Automatically sniffs video and audio streams (MP4, WEBM, M3U8/HLS, M4V, MP3) playing or loaded on any page, showing a live detected badge count in the 3-dot menu and opening a dialog with 1-tap Download, External Player launch (VLC/MX Player), and direct link copying.
  - **Save All Images / Image Sniffer & Batch Downloader ("Image Box"):** Scrapes all images (`img`, `picture source`, CSS `background-image`) into a gallery dialog with "Select All" toggle and batch downloads them directly to Pictures.
  - **Scrolling Screenshot / Full Page Long Screenshot ("Scroll Capture"):** Captures the entire vertical document as a high-resolution PNG image directly into the device's Pictures/Kio directory and Android Gallery.
  - **Screen Filter (Night Dimmer & Blue Light Eye Care):** Built-in in-app overlay with Warm Amber and OLED Night Dimmer modes and customizable 10%-80% opacity slider, allowing seamless touch pass-through.
  - **Clean Mode (Distraction-Free Browsing):** Automatically strips cookie consent walls, newsletter overlays, sticky banner ads, and floating promo bars without breaking layout.
  - **Go Back Without Refresh:** Instant cache-first history navigation eliminating page reloads and white flashes.
- **Complete Extension Engine Removal & Storage Reclamation:**
  - Permanently removed Chrome extension engine, unpacker, polyfills, virtual origins, and injection bridges for an ultra-clean, bloat-free foundation.
  - Automatically wipes and reclaims all legacy extension disk directories, downloaded CRX packages, and preferences on startup.
- **Hardware-Accelerated 120Hz/60Hz Rendering & Silk Smoothness:**
  - Enabled hardware acceleration layer (`View.LAYER_TYPE_HARDWARE`), smooth nested scrolling, and native overscroll on WebViews.
  - Eliminated script evaluation overhead and network request interception bottlenecks, delivering instantaneous page transitions.
- **3-Dot Menu Overhaul (Modes tile, Block Images, Clean Header):**
  - Cleaned up header chips, added a dedicated **Modes** tile with instant switching between Desktop, iPhone, and Mobile site viewports, and an instant **Block Images** toggle.
- **Enhanced Settings Sections with Feature Details:**
  - Upgraded all category folder subtitles with rich technical feature details, and embedded the official vector **Kio launcher icon** inside About Kio and the main settings card.
- **Professional UI Wording:**
  - Streamlined toasts, status labels, and banners for both Standard and Private Browsing modes.
- **Hidden-"K" Concentric App Launcher Icon & Preserved Homepage Emblem:**
  - Rebuilt the app icon on the classic concentric orbital logo using the exact cyan/sky/indigo/white palette, subtly embedding a hidden geometric **K** inside the inner orbital chord and prism rays while keeping the homepage emblem 100% untouched.
- **Low-Data Surfing & Ultra-Lean Android Footprint:**
  - Always-on prefetch/prerender blocking, native lazy loading & async image decoding, WebP favicon compression, automatic disk cache & temp-file trimming, and `onTrimMemory` background WebView eviction deliver top performance with minimal data and storage usage.
- **Zero-Leak Bidirectional Isolation (Standard ↔ Private):**
  - Strict mode-boundary cookie vaulting & incineration between Standard and Private tabs, ephemeral RAM-only `localStorage`/`sessionStorage` proxy in Private Mode, disabled IndexedDB/CacheStorage/third-party cookies/bookmarks/history/external apps/sharing/shortcuts/screenshots in Private Mode so zero data can leak in either direction.
- **Exclusive Private-Mode Safety Tools (3-Dot Menu):**
  - In Private Mode, Bookmarks, History, Share, Open Externally, and Add Shortcut are disabled and replaced by 5 exclusive Private-Only tools: **Burn session**, **New identity**, **Shield audit**, **Wipe clipboard**, and **Purge RAM**.
- **Full Dark Mode & Light Mode:**
  - Seamless browser-wide switching between OLED Dark Mode and crisp Light Mode across Homepage (with preserved Kio emblem), search bar, Android status & navigation bars, menus, settings, and websites.
- **Content Announcer & Reader Mode (Pure On-Device NLP, Zero Data Copy/Save):**
  - Accessible right inside the 3-dot menu (`Narrate`), extracts readable prose into volatile RAM only, normalizes web prose for natural spoken cadence, segments sentences with abbreviation awareness, narrates on-device with live sentence highlighting & speed/skip controls, disables copying/saving completely, and wipes volatile RAM immediately on close.
- **True Edge-to-Edge Full Screen Mode:**
  - Placed in the 3-dot menu (`Full screen`), hides the browser toolbar and Android system bars edge-to-edge with zero inset bars and an unobtrusive Exit pill + Back restoration.
- **Lightning-Fast & Strictly Under 2 MB:**
  - Zero telemetry, zero bloat, and aggressive R8 optimization keeping the signed release APK strictly under 2 MB.
