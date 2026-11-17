# Kio

**Kio** is a lightning-fast, privacy-hardened Android web browser built around the operating system's maintained **Android System WebView** (`android.webkit.WebView`) — featuring **Desktop Chrome Extensions**, a **Pure On-Device NLP Content Announcer / Reader Mode (zero data copy or save)**, **True Edge-to-Edge Full Screen Mode**, **Real Website Logos**, and a **signed R8-minified release APK strictly under 2 MB**.

> **Engine Statement:** Kio delegates web rendering, TLS/HTTP transport, and sandboxing to the OS-maintained **Android System WebView**, adding zero-dependency Desktop Chrome Extension execution, privacy shields, and on-device NLP narration without bundling a 150 MB Chromium binary.

---

## Download APK

Pre-built signed release APK (< 2 MB) is tracked at [`releases/Kio-v1.9.0.apk`](releases/Kio-v1.9.0.apk) and published in GitHub Releases (`v1.9.0`).

---

## Core Highlights (v1.9.0 — Build 62)

- **Desktop Chrome Extensions (Kiwi-Style):** Install desktop Chrome extensions directly from Chrome Web Store URLs/IDs (native CRX2/CRX3 binary unpacker) or local `.crx`, `.zip`, and `.user.js` packages; supports Manifest V2 & V3 `content_scripts` (JS & CSS), `_locales` localization, static `declarative_net_request` blocking rulesets, persistent `chrome.storage` / `chrome.runtime` / `chrome.i18n` / `chrome.tabs` bridges, interactive Extension Popups & Options pages, and 1-tap Chrome Web Store detection.
- **Content Announcer & Reader Mode (Pure On-Device NLP, Zero Data Copy/Save):** Accessible right inside the 3-dot menu (`Narrate`), extracts readable prose into volatile RAM only, normalizes web prose for natural spoken cadence, segments sentences with abbreviation awareness, narrates on-device with live sentence highlighting & speed/skip controls, disables copying/saving completely, and wipes volatile RAM immediately on close.
- **True Edge-to-Edge Full Screen Mode:** Placed in the 3-dot menu (`Full screen`), hides the browser toolbar and Android system bars edge-to-edge with zero inset bars and an unobtrusive Exit pill + Back restoration.
- **Real Website Logos (Favicons):** Displays authentic site logos across Homepage Bookmarks, Bookmarks, History, Open Tabs, and Recent Tabs (RAM-only in Private Mode).
- **Pure Pitch-Black Dark UI & Category Settings:** Unified OLED black homepage & search header, organized category-wise Settings folders, plus dedicated **Help & Feedback** and **About Kio** sections.
- **Creative "K" App Launcher Icon & Preserved Homepage Emblem:** Features a creative geometric **K** (denoting Kio) adaptive + monochrome app icon while preserving the homepage emblem untouched.
- **Lightning-Fast & Strictly Under 2 MB:** Zero telemetry, zero bloat, deduplicated early WebView script injections, and aggressive R8 optimization keeping the signed release APK strictly under 2 MB.
