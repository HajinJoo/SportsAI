# SportsAI Development Record

## 2026-07-13 — Interactive history, skill progress, and video highlights

Repository: `https://github.com/HajinJoo/SportsAI`

### Delivered

- Made timeline session dates and chart endpoint dates open the complete saved analysis.
- Expanded local history so reopened results include the score, findings, sport metrics, AI overview, and highlight metadata.
- Added filterable improvement metrics for pitching, batting, and basketball shooting.
- Added pose-based Pitch Speed Potential, Bat Speed Potential, Release Speed, Ball Tracking, and movement-specific mechanics scores.
- Added clear Improved, Down, No change, and Baseline comparisons against the prior session.
- Added a 3–4 sentence AI skill overview to cloud and offline reports.
- Added AI moment selection for release, contact, peak action, and best-form ranges.
- Added local MP4 highlight cutting with Android media APIs.
- Added highlight video playback and an editor that re-cuts corrected start/end boundaries.
- Kept speed scores honest: they are movement-potential scores, not radar mph/km/h measurements.
- Removed broad media-library permission because the Android system video picker grants only the selected clip.
- Bumped the Android app to version 1.1 (`versionCode` 2).

### Verification

- Kotlin debug compilation: passed.
- Unit tests for metric coverage, overview length, highlight ranges, and historical report reconstruction: passed.
- Android lint, including media flags, picker permissions, storage, Compose, and API checks: passed.
- Connected-device instrumentation on Samsung SM-S721W / Android API 36: passed.
- Combined `testDebugUnitTest lintDebug assembleDebug` release gate: passed.

### APK delivery

- PC: `F:\SportsAI\SportsAI-v1.1-debug.apk` (123,990,459 bytes)
- Phone: `/sdcard/Download/SportsAI-v1.1-debug.apk` (123,990,459 bytes)
- Installed package: `com.example.sportsai`, version 1.1 (`versionCode` 2)
- SHA-256: `F5401752D19EC5627CB8F260A1D5C64DDF401758E4DB9BDB82DA0ECA6028E874`
- Connected Samsung SM-S721W: version 1.1 installed and cold-launched successfully.

### Release checklist

1. Run unit tests, Android lint, and APK assembly.
2. Copy the APK to the PC delivery location.
3. If an authorized Android phone is connected, install the APK and copy it to the phone's Downloads folder.
4. Commit the source and this record.
5. Push `main` to the public GitHub repository and confirm the remote commit.

## 2026-07-14 — Version 1.2 highlight reliability follow-up

### Delivered

- Fixed highlight-card taps so they open and play the selected action immediately, just like the editor preview.
- Added automatic fallback from the original picker URI to the saved private highlight file.
- Replaced generic peak-motion, best-tracking, and pose-position picks with one focused sport-aware action.
- Pitching now scores throwing-wrist speed, elbow extension, torso rotation, and release height.
- Batting now scores both-hand speed, hip/shoulder separation, torso turn, and arm action.
- Basketball shooting now scores upward wrist travel, elbow extension, leg drive, and release height.
- Normalized movement against body size and elapsed time, then smoothed adjacent samples to reject camera scale and one-frame pose jitter.
- Rejects static pose sequences instead of incorrectly labeling them as highlights.
- Replaced previous-keyframe MP4 remuxing with Media3 Transformer clipping so saved cuts start at the chosen action.
- Bumped the Android app to version 1.2 (`versionCode` 3).

### Regression coverage

- Verifies one focused, correctly labeled highlight for pitching, batting, and basketball shooting.
- Verifies that the chosen range includes the action and excludes idle video at both ends.
- Verifies that static poses produce no highlight.

### Verification and APK delivery

- Kotlin compilation and unit tests: passed.
- Android lint: passed.
- APK assembly: passed.
- Connected-device instrumentation on Samsung SM-S721W / Android API 36: passed.
- PC APK: `F:\SportsAI\SportsAI-v1.2-debug.apk` (131,134,594 bytes)
- Phone APK: `/sdcard/Download/SportsAI-v1.2-debug.apk` (SHA-256 verified against the PC copy)
- Installed package: `com.example.sportsai`, version 1.2 (`versionCode` 3)
- SHA-256: `459D847CF4EDFA24906973645CA91566754F81BAC6EFAC96E83BED9B80213D88`

## 2026-07-14 — Version 1.3 first-open video fix

### Delivered

- Replaced the highlight dialog's platform `VideoView` with Media3 ExoPlayer and `PlayerView`.
- Uses a `TextureView` so video rendering is attached reliably inside the Compose dialog.
- Registers playback state and error listeners before preparing the media.
- Starts playback immediately when the dialog is already in the foreground, pauses on app background, resumes on return, and releases the decoder when the dialog closes.
- Uses exact seeking and a clipped media item for the selected highlight range.
- Keeps automatic fallback from the original picker URI to the private generated MP4.
- Added visible loading and playback-failure states instead of leaving an unexplained black rectangle.
- Bumped the Android app to version 1.3 (`versionCode` 4).

### Real-device verification

- Installed the debug build on Samsung SM-S721W / Android API 36 without clearing saved data.
- Opened the saved batting result through Timeline and tapped `Best swing · contact` once.
- The first captured screen already contained a decoded video frame; the app was not backgrounded or resumed.
- A second capture contained a later frame, confirming active playback rather than a static thumbnail.
- Unit tests, Android lint, APK assembly, and connected-device instrumentation: passed.

### APK delivery

- PC APK: `F:\SportsAI\SportsAI-v1.3-debug.apk` (132,067,107 bytes)
- Phone APK: `/sdcard/Download/SportsAI-v1.3-debug.apk`
- Installed package: `com.example.sportsai`, version 1.3 (`versionCode` 4)
- PC and phone SHA-256: `136B714E25619711F72932FEF02BA66A9C179D850A3E72D484BF7BC129C53296`

## 2026-07-14 — Version 2.0 public bring-your-own-key edition

### Local-version preservation

- Preserved the exact version 1.3 source commit as the annotated Git tag `local-v1.3` and pushed that tag to the public repository.
- Kept `F:\SportsAI\SportsAI-v1.3-debug.apk` as the personal/local APK snapshot.
- Version 1.3 and earlier APKs must not be redistributed if they were built with a personal key; that key should be revoked before sharing version 2.0.

### Delivered

- Removed the Gradle `local.properties` Gemini-key reader, generated `BuildConfig` credential, and query-string key transport.
- Public APKs now ship with no SportsAI developer Gemini key and work in offline mode immediately.
- Added a fourth Settings destination and a tappable Home badge that clearly shows `OFFLINE` or `GEMINI` status.
- Added protected key entry plus save-and-test, retest, replace, and confirmed remove flows for each device owner's own API key.
- Prevented plaintext key input from entering Compose/Android saved-instance state.
- Encrypts saved keys with AES-256-GCM and a non-exportable app-specific Android Keystore key.
- Excludes the encrypted preferences file from Android cloud backup and device transfer.
- Sends authentication only through Google's `x-goog-api-key` request header and never includes the key in a request URL or user-facing error.
- Updated cloud coaching to the stable `gemini-3.5-flash` model and validates the saved key against that exact model.
- Preserved automatic offline coaching when no key is saved, a key is rejected, quota is exhausted, or Gemini is unavailable.
- Updated README, contributing, security, privacy, architecture, CI, and development-journey documentation for the public keyless distribution model.
- Bumped the Android app to version 2.0 (`versionCode` 5).

### Security and regression coverage

- Added unit coverage for key-provider configuration, stable model selection, and credential-safe error messages.
- Added a real-device Android Keystore round-trip test that saves, decrypts, masks, verifies ciphertext does not contain plaintext, and removes a test-only credential under an isolated alias/preferences file.
- Clean-build APK scan confirmed the machine-local Gemini credential is absent.
- Clean-build APK scan confirmed legacy `GEMINI_API_KEY` and key-in-query markers are absent.
- APK feature scan confirmed `gemini-3.5-flash`, `x-goog-api-key`, encrypted-settings storage, and Settings actions are present.
- APK signature verification passed with Android APK Signature Scheme v2.

### Verification and APK delivery

- Clean `testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest` gate: passed with 0 lint errors.
- Connected-device instrumentation: 2/2 tests passed on Samsung SM-S721W / Android API 36.
- Real-device cold launch: passed; the Home badge showed `OFFLINE` with no bundled or saved key.
- Real-device Settings inspection: passed; the BYOK explanation, password field, disabled empty save action, AI Studio action, and four-tab navigation rendered correctly.
- Installed package: `com.example.sportsai`, version 2.0 (`versionCode` 5).
- PC APK: `F:\SportsAI\SportsAI-public-v2.0-debug.apk` (132,182,039 bytes).
- Phone APK: `/sdcard/Download/SportsAI-public-v2.0-debug.apk` (132,182,039 bytes).
- PC and phone SHA-256: `FBFC9B2EB1950B29CAE93416DF71031C9F1C2B8066DB0E3C604DEE72501DE177`.
