# Security Policy

## Security Architecture & Hardening Standard

Kai is built using the **Android WebView Security Hardening Standard** with a defense-in-depth posture:

1. **Network Security & Cleartext Traffic:**
   - Global cleartext traffic is strictly disallowed (`android:usesCleartextTraffic="false"`).
   - Enforced by `network_security_config.xml` with system trust anchors.
   - HTTP connections are upgraded to HTTPS automatically or gated behind an explicit insecure warning interstitial.

2. **WebSettings Lockdown:**
   - File access disabled (`allowFileAccess = false`, `allowContentAccess = false`).
   - File URL schemes disabled (`allowFileAccessFromFileURLs = false`, `allowUniversalAccessFromFileURLs = false`).
   - Mixed content strictly prohibited (`WebSettings.MIXED_CONTENT_NEVER_ALLOW`).
   - Form autofill disabled (`saveFormData = false`).
   - Geolocation disabled by default; strictly prompted per-origin via `WebChromeClient`.
   - JavaScript interfaces: 0 `@JavascriptInterface` bridges exposed.

3. **Google Safe Browsing & TLS:**
   - Safe Browsing enabled via manifest meta-data (`android.webkit.WebView.EnableSafeBrowsing = true`).
   - `WebViewClient.onSafeBrowsingHit` intercepts known malware, phishing, and billing threats, backing out safely (`backToSafety(true)`).
   - `WebViewClient.onReceivedSslError` unconditionally cancels navigation (`handler.cancel()`); invalid certificates can never be bypassed.

4. **Intent & External Scheme Protection:**
   - `intent://` URIs parsed via `Intent.parseUri(..., URI_INTENT_SCHEME)` with mandatory `CATEGORY_BROWSABLE`.
   - Explicit `component` and `selector` targets are stripped to prevent internal activity hijacking.
   - URI permission grant flags (`FLAG_GRANT_READ/WRITE_URI_PERMISSION`) are strictly cleared.

5. **Screen & Private Tab Security:**
   - `WindowManager.LayoutParams.FLAG_SECURE` is dynamically applied whenever private tabs are active, preventing recent-tasks snapshots and unauthorized screen recordings.
   - Private tabs use `LOAD_NO_CACHE`, block persistent cookies, and record zero history.

6. **Download Security:**
   - Consent-first downloads with file name sanitization (path traversal protection).
   - Executable and dangerous file types (`.apk`, `.exe`, `.dex`, `.sh`, `.bat`) trigger an explicit high-risk security warning banner.
   - Storage uses scoped `MediaStore.Downloads` on Android 10+ and restricted `FileProvider` paths on Android 8–9.

7. **Production Release Integrity:**
   - R8/ProGuard code minification and resource shrinking enabled.
   - `WebView.setWebContentsDebuggingEnabled` tied strictly to `BuildConfig.DEBUG` (disabled in release).
   - Zero hardcoded secrets, API keys, or proprietary tracking SDKs.
   - Target SDK kept up-to-date with latest Android platform requirements.

8. **Secure-by-Design AI:**
   - Webpage content treated as **untrusted data**.
   - Sanitized by `PromptInjectionDefense` against prompt injection attacks, zero-width characters, and HTML comments.
   - Automatic redaction of PII (credit cards, emails, phone numbers) before processing.
   - AI is read-only: cannot execute clicks, fill forms, or submit transactions without explicit user consent.
   - Grounded strictly in active page content without hallucinations or cookie/password access.

---

## Supported Versions

| Version | Supported |
| :--- | :--- |
| v1.7.x (Security Hardened) | :white_check_mark: |
| < v1.7.0 | :x: |

---

## Reporting a Vulnerability

We take the security of Kai seriously. If you discover a vulnerability or security flaw, please report it responsibly:

1. **Email:** Report findings privately to the repository maintainer.
2. **Details to Include:**
   - Summary of the vulnerability.
   - Exact steps or proof-of-concept (PoC) to reproduce the issue.
   - Affected device, OS version, and Android System WebView package version.
   - Potential impact.
3. **Response Timeline:**
   - Acknowledgement within 48 hours.
   - Remediation patch developed and published within 7 days for critical vulnerabilities.

---

## Honest Disclosure of Residual Risks

No software can claim to be "100% safe". We report the following residual risks transparently:

- **Underlying Chromium/WebView Engine CVEs:** Kai relies on the OS-provided Android System WebView. Zero-day sandbox escapes in the system engine are outside application-layer control; users must update Android System WebView via Google Play.
- **Compromised / Rooted Devices:** On a rooted device with root/hooking frameworks (e.g. Magisk, Frida, Xposed), an attacker with root privileges can bypass TLS checks, inspect application memory, or extract private app sandboxes.
- **Social Engineering & Novel Phishing:** Heuristic scam detection and Google Safe Browsing mitigate known threats, but newly registered deceptive sites that emulate trusted services without matching heuristic rules may not be detected instantly before user interaction.
