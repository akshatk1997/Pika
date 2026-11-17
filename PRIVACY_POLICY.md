# Privacy Policy

**Last Updated:** November 2026

**Kio** is built with a single privacy commitment: **your browsing activity belongs exclusively to you**. This application does not collect, track, transmit, or monetize your personal data.

---

## 1. Information Collection & Zero Telemetry

- **Zero Third-Party Telemetry:** We do not embed analytics SDKs or crash-reporting libraries that harvest telemetry.
- **Zero Advertising SDKs:** The application does not contain advertising networks or promotional SDKs.
- **Zero Account Requirements:** Kio does not require account registration, login credentials, or user profiling.

---

## 2. On-Device Storage & Encryption

- **Sensitive Settings & Site Permissions:** Encrypted at rest in app-private storage using **Android Keystore (AES-256-GCM)**.
- **Bookmarks, History & Extensions:** Stored exclusively in app-private local storage on your device. You can clear history, uninstall extensions, or export/delete bookmarks at any time.
- **Private Browsing Mode:** When using Private Tabs, cookies are ephemeral (memory-only), no cache or favicons are written to disk, zero history is recorded, and OS recent-tasks screen capture is prevented via `FLAG_SECURE`.

---

## 3. Content Announcer & Reader Mode (Zero Data Copy / Save)

- **Pure On-Device NLP Narration:** Page text extraction, normalization, and sentence segmentation execute 100% locally in volatile RAM using Android's on-device Text-to-Speech engine.
- **Zero Persistence & Zero Copy:** Content opened in Reader / Announcer Mode is never saved to disk, database, cache, logs, or clipboard, and is immediately wiped from volatile memory on close or navigation.

---

## 4. Web Privacy Shields

- **Global Privacy Control (GPC):** Emits `Sec-GPC: 1` HTTP headers and injects `navigator.globalPrivacyControl = true`.
- **Do Not Track (DNT):** Emits `DNT: 1` request headers.
- **Tracker, Ad & Popunder Blocking:** Intercepts subresource network requests matching analytics, fingerprinting, ad networks, and streaming popunder redirects.
- **Query Parameter Stripping:** Automatically removes invasive tracking tags (`fbclid`, `gclid`, UTM parameters) before loading.
- **WebRTC LAN IP Leak Defense:** Strips local host ICE candidates from SDP payloads.

---

## 5. Contact

For privacy questions or data protection inquiries, please file an issue in this repository.
