# Privacy Policy

**Last Updated:** November 2026

**Kai** is built with a single privacy commitment: **your browsing activity belongs exclusively to you**. This application does not collect, track, transmit, or monetize your personal data.

---

## 1. Information Collection & Zero Telemetry

- **Zero Third-Party Telemetry:** We do not embed analytics SDKs (e.g. Firebase Analytics, Google Analytics, Mixpanel, Segment, AppsFlyer) or crash-reporting libraries that harvest telemetry.
- **Zero Advertising SDKs:** The application does not contain advertising networks or promotional SDKs.
- **Zero Account Requirements:** Kai does not require account registration, login credentials, or user profiling.

---

## 2. On-Device Storage & Encryption

- **Sensitive Settings & Site Permissions:** Encrypted at rest in app-private storage using **Android Keystore (AES-256-GCM)**.
- **Bookmarks & History:** Stored exclusively in local SQLite database files on your device. You can clear history or export/delete bookmarks at any time via Settings.
- **Private Browsing Mode:** When using Private Tabs, cookies are ephemeral (memory-only), no cache is written to disk, zero history is recorded, and OS recent-tasks screen capture is prevented via `FLAG_SECURE`.

---

## 3. Web Privacy Shields

- **Global Privacy Control (GPC):** Emits `Sec-GPC: 1` HTTP headers and injects `navigator.globalPrivacyControl = true` into the DOM, signaling legal opt-out of data sale/sharing under GDPR and CCPA.
- **Do Not Track (DNT):** Emits `DNT: 1` request headers.
- **Third-Party Cookie Blocking:** Third-party cookies are blocked by default to prevent cross-site behavioral tracking.
- **Tracker & Ad Blocking:** Intercepts subresource network requests matching over 150 known analytics, fingerprinting, and advertising domains.
- **Query Parameter Stripping:** Automatically removes invasive tracking tags (`fbclid`, `gclid`, UTM parameters) from URLs before loading.
- **Fingerprinting Defenses:** Injects subtle entropy to protect against Canvas, WebGL, AudioContext, and battery status device fingerprinting.

---

## 4. Artificial Intelligence & Content Privacy

- **100% On-Device Processing:** AI Page Summarization, Grounded Q&A, and Scam Detection execute locally on your device.
- **Zero Cloud Transmission:** Page text, user questions, and browsing history are never uploaded to remote servers or cloud AI providers.
- **No Credentials Access:** The AI engine cannot access stored passwords, cookies, or non-active tabs.
- **Automatic PII Scrubbing:** Credit card numbers, emails, and phone numbers are automatically redacted from text before semantic analysis.

---

## 5. Compliance with DPDP Act (India) & GDPR (EU)

- **Data Minimization:** No personal data is collected or stored off-device.
- **Right to Erasure:** A 1-tap **Clear Browsing Data** feature in Settings allows immediate and permanent deletion of local cookies, web storage, cache, and history.
- **Granular Consent:** Device sensors (camera, microphone, location) are never accessed without origin-specific runtime user confirmation.

---

## 6. Google Play Data Safety Declaration

| Data Type | Collected | Shared with Third Parties | Handled Securely |
| :--- | :--- | :--- | :--- |
| Personal Info (Name, Email, Phone) | **No** | **No** | N/A |
| Financial Info | **No** | **No** | N/A |
| Location | **No** (Processed locally by websites only on consent) | **No** | Yes (Per-origin prompt) |
| Web Browsing History | **No** (Stored locally on-device only) | **No** | Yes (Keystore AES-256-GCM) |
| Device or Other Identifiers | **No** | **No** | N/A |

---

## 7. Contact

For privacy questions or data protection inquiries, please file an issue in this repository.
