# Security Policy

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 1.5.x   | :white_check_mark: |
| < 1.5.0 | :x:                |

## Security Architecture & Guardrails

**Googlebook Feature Checker** is built with strict defense-in-depth security controls verified by automated fuzzing and unit tests (`FuzzAndSecurityTest`):

1. **Zero Hidden API Reflection / Google Play Protect Compliance**:
   - Uses only documented public Android SDK APIs (`ConnectivityManager.NetworkCallback`, `WifiInfo`, `PackageManager`, `Build.VERSION`).
   - Requests zero high-risk sideloading permissions (no SMS, Accessibility, NotificationListener, or `REQUEST_INSTALL_PACKAGES`).
2. **SSRF & Local Loopback Socket Hardening**:
   - `SecurityGuard.isAllowedCompanionEndpoint` restricts any companion probe strictly to loopback (`127.0.0.1`, `localhost`, `::1`) on unprivileged ports (`1024..65535`).
   - `SecurityGuard.readBoundedLine` caps input streams at `4096` bytes to prevent memory exhaustion (`OutOfMemoryError` DoS).
   - `SecurityGuard.parseAndValidateCompanionJson` enforces a strict key allowlist and rejects nested JSON objects/arrays.
3. **Intent & URI Hardening**:
   - `SecurityGuard.isValidAndroidPackageName` validates package identifiers against path traversal or query injection before constructing `market://` or `package:` URIs.
   - `SystemActionLauncher` pins `market://` intents explicitly to `com.android.vending` (Google Play Store) to prevent third-party intent hijacking.
   - `SecurityGuard.isTrustedHttpsUrl` restricts external support links strictly to `https://support.google.com` and `https://play.google.com`.
4. **Privacy & Data Protection**:
   - `android:allowBackup="false"` and `@xml/data_extraction_rules` prevent unauthorized ADB backup or cloud extraction.
   - `android:usesCleartextTraffic="false"` blocks cleartext network traffic.
   - Clipboard diagnostic exports set `ClipDescription.EXTRA_IS_SENSITIVE = true` on Android 13+ (`API 33+`) to mask sensitive Wi-Fi SSID and build metadata in system clipboard previews.

## Reporting a Vulnerability

Please report suspected security vulnerabilities privately via GitHub Security Advisories rather than opening a public issue. Include steps to reproduce and the affected Android / Googlebook OS version.
