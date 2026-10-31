# Contributing to Googlebook Feature Checker

Thank you for contributing to **Googlebook Feature Checker**!

## Development Setup & Verification

1. **Clone & Build**:
   ```bash
   ./gradlew assembleDebug
   ```
2. **Run the Full Test & Fuzzing Suite**:
   ```bash
   ./gradlew testDebugUnitTest
   ```
3. **Run Android Lint Static Analysis**:
   ```bash
   ./gradlew lintDebug
   ```

## Code & Security Guidelines

- **Adaptive UI**: Every screen and component must support both Googlebook desktop two-pane layouts (`>= 600dp`) with keyboard/trackpad shortcuts and compact Android phone layouts (`< 600dp`).
- **Public SDK APIs Only**: Do not introduce reflection on hidden Android framework methods (`android.os.SystemProperties`, hidden `WifiInfo` methods, etc.).
- **Input Sanitization**: Pass any external string (Wi-Fi SSID, package version, companion payload) through `SecurityGuard.sanitizeUntrustedText`.
- **License**: All contributions are licensed under the [Apache License 2.0](LICENSE).
