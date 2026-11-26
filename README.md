# Kio

**Kio** is a lightning-fast, privacy-hardened Android web browser built around the operating system's maintained **Android System WebView** (`android.webkit.WebView`) — featuring **Hardware-Accelerated 120Hz/60Hz Rendering**, a **Pure On-Device NLP Content Announcer / Reader Mode (zero data copy or save)**, **Unified Media Hub**, **Real Website Logos**, and a **signed R8-minified release APK strictly under 2 MB**.

> **Engine Statement:** Kio delegates web rendering, TLS/HTTP transport, and sandboxing to the OS-maintained **Android System WebView**, maximizing hardware-accelerated responsiveness, privacy shields, and on-device NLP narration without bundling a 150 MB Chromium binary.

---

## Download APK

Pre-built signed release APK (< 2 MB) is tracked at [`releases/Kio-v2.1.4.apk`](releases/Kio-v2.1.4.apk) and published in GitHub Releases (`v2.1.4`).

---

## Core Highlights (v2.1.4)

- **Aesthetic Materialistic Stardust Logo (v2.1.4):**
  - Sculpted 3D celestial sphere with specular glint hotspot, dual-layer ambient diffusion wavefront arcs, obsidian depth gradient, and metallic chamfered border brush while maintaining zero-allocation procedural canvas rendering.
- **Omnibox Bookmark Star (v2.1.4):**
  - Instant one-tap bookmarking directly inside the website address/search pill: dynamic gold star (`#FFB300`) when bookmarked, crisp outline star when not bookmarked, with tactile haptic feedback.
- **Unified Media Downloader Hub (v2.1.4):**
  - Merged Images and Media into a single streamlined "Media" dialog in the 3-dot overflow menu, providing tabbed access to detected video/audio streams and sniffed page images.
- **Clean 3-Dot Overflow Menu (v2.1.4):**
  - Browser-wide full-screen toggle mode and floating exit surface removed completely.
  - "Add shortcut" removed from the 3-dot menu.
  - "Clean Mode" moved into Settings under browsing preferences.
- **Permanent Release Signing & Seamless In-Place APK Upgrades:**
  - Completely resolved the Android package installer "package is not compatible" / signature mismatch error (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`).
  - Established a permanent, committed release signing keystore ensuring an identical cryptographic signing certificate across every release.
  - Future APK updates now install directly over previous versions seamlessly without requiring uninstallation or losing data.
- **Universal Video Playback & Complete Touch Responsiveness:**
  - Fully resolved playback issues across YouTube, Vimeo, Dailymotion, Twitter/X, and web video streaming sources.
  - Video player control buttons (play/pause, seekbar/scrubber, volume, settings gear, full-screen toggle) are 100% visible, touchable, and responsive in default mode without any manual configuration.
  - Full hardware-accelerated 120Hz/60Hz GPU compositing across all media and web pages.
- **Cosmic Stardust Ripple Logo & Brand Emblem:**
  - Brand emblem and Android launcher icon inspired by gravitational wave resonance and stardust pointillism.
  - Features a central radiant star orb surrounded by expanding concentric crescent stardust ripple wavefronts.
  - Monochromatic palette with deep space OLED black (`#000000`), diamond silver-white (`#FFFFFF` / `#E2E8F0`), and soft celestial bloom halo.
  - Zero-allocation procedural canvas rendering for ultra-fluid 120Hz/60Hz touch responsiveness.
- **Full Android Tablet Compatibility (Android 10+):**
  - Unified A-to-Z feature parity across all Android devices: tablets (7", 10", 12"+), foldables, and smartphones alike.
  - Ergonomically centered 5-button balanced toolbar with the collapsible lock-tap omnibox, tactile reload gesture, and full feature suite on all form factors.
  - Declared full `<supports-screens>` compatibility covering `smallScreens`, `normalScreens`, `largeScreens`, and `xlargeScreens`.
- **Streamlined & Space-Optimized Foundation:**
  - Streamlined codebase by removing redundant tablet-specific duplicate code paths, maximizing execution speed, snappiness, and memory efficiency while preserving 100% of core browser capabilities.
  - Zero bloat, zero telemetry, and lean repository footprint.
- **Comprehensive Creative Feature Showcase:**
  - Distinctive display presentation combining all browser capabilities: Stealth Privacy Vault, Integrated Ad & Video Shield, Ergonomic One-Handed & Grayscale Modes, Tactile Reload & Omnibox Lock, Pure On-Device NLP Announcer, and Smart Media Sniffer.
  - Interactive category filtering chips (`All Features`, `Privacy & Shield`, `Gestures & Flow`, `AI & Utilities`) and tap-to-expand architectural spotlight badges with tactile feedback.
- **Clean Repository Footprint:**
  - Preserved a lean repository footprint with zero redundant legacy APKs.
- **Clean & Minimalist About Settings:**
  - Removed redundant engine & system info and build numbers from About Kio for a cleaner, polished presentation.
- **Refined 3-Dot Quick Menu:**
  - Removed dark mode bar, capture, and open-external options to provide a snappy, uncluttered 3-dot bottom sheet.
- **Typewriter Animated Cursive Script Intro:**
  - Distinctive cursive script typography header (*"One browser, every thing you are."*) dynamically types out letter by letter upon opening the browser with smooth hardware-accelerated timing.
- **Intentional Press-Slide Down Reload Gesture:**
  - Deliberate press and downward slide refreshes or reloads the web page with tactile haptic feedback; standard casual scrolling remains completely unaffected.
- **Hardware-Accelerated Rendering & Silk Smoothness:**
  - Priority rendering policy (`WebSettings.RenderPriority.HIGH`) and hardware-accelerated layer (`View.LAYER_TYPE_HARDWARE`) ensuring ultra-fluid 120Hz/60Hz touch responsiveness with zero stutter or lag.

- **One-Handed Mode (Comfortable Single-Thumb Browsing):**
  - Lowers the browsing viewport smoothly into the lower half of the screen, placing top website controls, inputs, and navigation within easy reach of one thumb.
  - Includes a tap-to-expand shaded top backdrop and a quick floating thumb button to easily toggle one-handed operation.
  - Supports Hand Preference alignment (Right Hand, Left Hand, or Centered) in Settings.
- **Grayscale Mode (Pure Black & White):**
  - Full-screen hardware-accelerated monochrome filter converting the entire browser UI, web content, videos, and images into pure black and white for distraction-free reading, reduced eye fatigue, and OLED power conservation.
- **Clean Settings Header:**
  - Streamlined settings header by removing redundant brand cards and focusing directly on browser configuration and instant update alerts.
- **Automatic In-Settings Release Update Detection & Instant Downloader:**
  - Automatically queries official releases on opening Settings.
  - When a new version is released, a prominent update banner automatically appears at the top of Settings.
  - Tapping anywhere on the banner or "Update" button immediately triggers background APK download into Downloads with system notifications and direct install capability without hassle.
- **Collapsible Lock Symbol Address Bar:**
  - Removed permanent upper omnibox bar on web pages, merging control into a balanced bottom navigation bar.
  - Symmetrical 5-button toolbar with center lock icon (`[←] [→] [ 🔒 ] [3] [⋮]`).
  - Tapping lock option reveals full address bar (security state, URL, copy URL, and blocked tracker shield badge count). Tapping again collapses it smoothly.
- **Natural Slide-Down Pull-to-Refresh Gesture:**
  - Intuitive pull-down gesture on web pages with a floating circular refresh card indicator, live spinning animation, and tactile haptic feedback to reload the page.
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
