# Security Policy

## Security Architecture & Hardening Standard

**Kio** is built using the **Android WebView Security Hardening Standard** with a defense-in-depth posture:

1. **WebSettings Lockdown:**
   - File access disabled (`allowFileAccess = false`, `allowContentAccess = false`).
   - File URL schemes disabled (`allowFileAccessFromFileURLs = false`, `allowUniversalAccessFromFileURLs = false`).
   - Form autofill disabled (`saveFormData = false`).
   - Geolocation disabled in private mode and strictly prompted per-origin via `WebChromeClient`.

2. **Desktop Chrome Extension Sandboxing:**
   - Zip-Slip canonical path validation on every `.crx` / `.zip` entry with strict file count and uncompressed size quotas.
   - Local extension resources are served exclusively over a virtual origin (`https://kio-ext.invalid/<extId>/...`) without enabling `file://` access.
   - Extension bridge (`__KioExtBridge`) strictly scopes storage per extension ID and validates all `chrome.tabs.create` URLs via `UrlValidator.isSafeWebUrl`.

3. **Google Safe Browsing & TLS:**
   - Safe Browsing enabled via manifest meta-data (`android.webkit.WebView.EnableSafeBrowsing = true`).
   - `WebViewClient.onSafeBrowsingHit` safely backs out of malware, phishing, and billing threats.
   - `WebViewClient.onReceivedSslError` unconditionally cancels navigation (`handler.cancel()`).

4. **Intent & External Scheme Protection:**
   - `intent://` URIs parsed via `Intent.parseUri(..., URI_INTENT_SCHEME)` with mandatory `CATEGORY_BROWSABLE`.
   - Explicit `component` and `selector` targets are stripped, and URI permission grant flags are cleared.

5. **Screen & Private Tab Security:**
   - `WindowManager.LayoutParams.FLAG_SECURE` is dynamically applied whenever private tabs are active.
   - Private tabs use `LOAD_NO_CACHE`, record zero history or disk favicons, and purge cookies/storage on exit.

6. **Download Security:**
   - Consent-first downloads with filename sanitization (path traversal defense) and high-risk executable warnings (`.apk`, `.exe`, `.dex`, `.sh`, `.bat`).

7. **Production Release Integrity:**
   - R8/ProGuard code minification and resource shrinking strictly under 2 MB.
   - `WebView.setWebContentsDebuggingEnabled` tied strictly to `BuildConfig.DEBUG`.
   - Zero hardcoded secrets, API keys, or proprietary tracking SDKs.

---

## Reporting a Vulnerability

If you discover a security vulnerability in Kio, please report it privately to the repository maintainer with reproduction steps, affected Android version, and System WebView version.
