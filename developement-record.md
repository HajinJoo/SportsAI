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

## 2026-07-14 — GitHub version 2.0 signed release

### Release signing

- Created the persistent `sportsai-release` RSA-4096 signing identity, valid until November 29, 2053.
- Signing-certificate SHA-256: `97:7C:68:3B:51:AE:C2:4F:AE:E7:1B:E3:D2:6F:DE:13:B1:9A:E6:C7:09:9F:7C:EF:41:38:F6:99:17:36:E8:D9`.
- Keystore: `F:\SportsAI\signing\sportsai-release.jks` (local only and excluded from Git).
- Password recovery: `F:\SportsAI\signing\sportsai-release-password.dpapi` (Windows DPAPI-protected, local only, and excluded from Git).
- The signing password was never printed, committed, added to release assets, or sent to GitHub.

### Production verification

- Clean unit-test, release-lint, and release-assembly gate: passed with 0 lint errors.
- Release APK zip-alignment: passed.
- APK Signature Scheme v3 verification: passed.
- Manifest verification confirmed version 2.0 (`versionCode` 5), API 29 minimum, API 36 target, and no debuggable marker.
- Binary credential scan confirmed the machine-local Gemini credential and legacy key transport markers are absent.
- Release certificate prevents accidental replacement by an APK signed with a disposable debug key.
- Replaced the empty debug test installation on Samsung SM-S721W with the exact release APK and confirmed a successful cold launch in offline mode.

### GitHub release delivery

- Release: `https://github.com/HajinJoo/SportsAI/releases/tag/v2.0`
- APK asset: `SportsAI-v2.0.apk` (121,936,514 bytes).
- PC APK: `F:\SportsAI\SportsAI-v2.0.apk`.
- Phone APK: `/sdcard/Download/SportsAI-v2.0.apk`.
- PC and phone APK SHA-256: `8A0125A16F63EDA4889C5D00E8AF2D4923CAEF58BFC79C43063CE4F4BEF16D60`.

## 2026-07-15 — Version 2.1 evidence-grounded Gemini vision

### Accuracy changes

- Removed the previous instruction to infer through blur, cropping, or incomplete footage and never admit insufficient visibility.
- Runs sport-aware action detection before Gemini coaching so the request focuses on the actual swing, pitch, or shot rather than idle frames across the full recording.
- Reopens up to eight frames from that action at a maximum 1280-pixel dimension; the 480-pixel replay frames remain only a safe extraction fallback.
- Precedes each image with its exact frame label, timestamp, temporal region, resolution, detected athlete box, and normalized key-joint tracker context.
- Added an evidence-only system instruction plus sport-specific batting, pitching, and shooting checklists.
- Batting instructions require visible proof of the batter and bat, call an unseen-ball moment a contact-zone frame rather than contact, and distinguish visible movement potential from measured bat speed or ball tracking.
- Gemini must report athlete visibility, visual confidence, inspected frame labels, visible body/equipment, and camera limitations before scoring.
- The app accepts Gemini coaching only when the athlete is reported visible with at least 55% confidence across three valid submitted frame labels.
- Strengths and issues without a valid submitted-frame citation are discarded.
- Insufficient or unsupported visual evidence produces an explicit camera-view note and on-device pose report instead of guessed Gemini feedback.
- Bumped the Android app to version 2.1 (`versionCode` 6).

### Regression and production verification

- Added tests for batting evidence constraints, unsupported-visibility rejection, accepted grounded sequences, action-window selection, and full-timeline fallback.
- Clean `testDebugUnitTest lintRelease assembleRelease assembleDebugAndroidTest` gate: passed.
- Connected Samsung SM-S721W instrumentation: 2/2 tests passed using the production signing identity.
- Release APK zip-alignment and APK Signature Scheme v3 verification: passed.
- Manifest verified version 2.1 (`versionCode` 6), API 29 minimum, and API 36 target.
- Credential scan found no bundled Gemini key, legacy `GEMINI_API_KEY`, or key-in-query transport.
- Installed as an in-place update on Samsung SM-S721W, preserving the configured Gemini state, and cold-launched successfully.

### APK delivery

- PC APK: `F:\SportsAI\SportsAI-v2.1.apk` (121,952,898 bytes).
- Phone APK: `/sdcard/Download/SportsAI-v2.1.apk` (121,952,898 bytes).
- PC and phone SHA-256: `43468AE1B78EE114D35300AE35CEDB2D23A74614F300DB4AFDDC189FEE7128E9`.
- Signing-certificate SHA-256: `97:7C:68:3B:51:AE:C2:4F:AE:E7:1B:E3:D2:6F:DE:13:B1:9A:E6:C7:09:9F:7C:EF:41:38:F6:99:17:36:E8:D9`.

## 2026-07-16 — Version 2.2 offline Batter Lock

### Problem addressed

- Game-angle batting clips showed a structural limitation in the former single-pose path: the skeleton could follow a prominent catcher or umpire instead of the batter.
- Prompt changes alone could not correct feedback built from the wrong athlete timeline.
- The new implementation uses a neural multi-pose detector plus temporal batter selection. It is not described as a trained baseball-role or identity classifier.

### Offline batting pipeline

- Added MediaPipe Tasks Vision 0.10.35 and bundled the Pose Landmarker Full float16 model so batting pose inference works without a Gemini key or network request.
- Configured MediaPipe video mode on CPU for up to four 33-landmark poses per sampled frame, with 0.45 detection, presence, and tracking thresholds.
- Raised batting sampling to at least 10 frames per second so a short swing is less likely to fall between samples; pitching and basketball retain the ML Kit single-person path.
- Added persistent track association using torso location, scale, recent velocity, landmark shape, and short-gap continuity instead of selecting the largest or most central person.
- Scores candidate tracks using temporal coverage, reliable joint completeness, detector confidence, two-hand proximity, batting hand height, stance, and coordinated wrist motion.
- Measures wrist movement in torso-aligned coordinates so camera translation, zoom, in-plane roll, an official stepping, or a catcher standing is less likely to be treated as swing evidence.
- Requires a direction-consistent transverse burst across image-horizontal or relative model-depth motion. Synthetic regressions reject a catcher rising with both hands together and accept a front-angle swing dominated by depth travel.
- Requires a clear winning margin in multi-person scenes. Incomplete, static, weak, or ambiguous tracks produce `BATTER_NOT_CONFIDENT` rather than a guessed athlete.
- Keeps only pose coordinates during the full scan, then reopens at most 24 frames within 1.5 seconds of the strongest coordinated three-interval swing burst for replay.
- Limits batting input to 30 seconds and gives explicit one-swing filming guidance.

### Downstream behavior

- An accepted Batter Lock supplies one continuous pose timeline to offline technique rules, sport metrics, the 3–4 sentence skill overview, skeleton replay, highlight selection, optional Gemini frame extraction, and the editor.
- A failed Batter Lock shows retry guidance and creates no score, scored technique findings, highlight, filming-date prompt, or saved history entry.
- Existing pitching and basketball behavior remains on the ML Kit path.
- Updated local feedback and labels to keep measurement claims honest: pose landmarks do not detect or measure the bat, ball, contact, eye gaze, radar speed, launch angle, ball flight, or physical distance.
- Bat Speed Potential and Pitch Speed Potential remain pose-based movement scores. Ball Tracking remains a head-stability proxy, not proof of gaze or ball flight.
- Bumped the Android app to version 2.2 (`versionCode` 7).

### Regression coverage completed during development

- Unit scenarios cover a swinging batter beside a larger catcher and umpire, detection-order changes, one-hand false positives, static officials, no-swing clips, sparse detections, a short swing inside a longer setup, camera translation/zoom/roll, one-frame out-and-back jitter, brief occlusion, crossing tracks, source-coordinate scaling, and rotated metadata dimensions.
- A connected-device still-image regression confirmed at the production 840-pixel input bound that the bundled model can return a batter and catcher as separate poses.
- The attributed 9-second game-angle clip passed Batter Lock in 77.1 seconds on the Samsung SM-S721W and kept the selected torso inside the manually labeled batter region at the start, middle, and end.
- The supplied-video test separately passed in 79.9 seconds and verified a capped replay plus a non-empty pose-timed highlight.
- All five connected-device test methods passed across the base suite and explicit real-video invocations; 45 JVM unit tests plus debug application and Android-test APK assembly also passed.

### Model provenance

- Asset: `app/src/main/assets/pose_landmarker_full.task`
- Source: `https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_full/float16/1/pose_landmarker_full.task`
- SHA-256: `4EAA5EB7A98365221087693FCC286334CF0858E2EB6E15B506AA4A7ECDCEC4AD`
- License: Apache License 2.0
- Complete source, model, and connected-test asset notices are recorded in `THIRD_PARTY_NOTICES.md` and bundled under `app/src/main/assets/third_party/`.

### Final release handoff

- A clean `testDebugUnitTest`, `lintRelease`, `assembleRelease`, and `assembleDebugAndroidTest` gate passed with 104 tasks completed; 45 JVM tests passed.
- Release lint completed with no blocking finding. The universal release includes `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64` native code.
- Final PC APK: `F:\SportsAI\SportsAI-v2.2.apk` (178,769,967 bytes).
- Final APK SHA-256: `924EAD0C1067ADE6D54124C6E58F064FBF06CCA9D20DE15D21CF35D624C408AE`.
- The packaged model is 9,398,198 bytes and matches SHA-256 `4EAA5EB7A98365221087693FCC286334CF0858E2EB6E15B506AA4A7ECDCEC4AD`; required third-party notices are present.
- Manifest verification: package `com.example.sportsai`, version 2.2 (`versionCode` 7), minimum API 29, target API 36, and no debuggable attribute.
- Signature verification passed with one v3 signer and release-certificate SHA-256 `97:7C:68:3B:51:AE:C2:4F:AE:E7:1B:E3:D2:6F:DE:13:B1:9A:E6:C7:09:9F:7C:EF:41:38:F6:99:17:36:E8:D9`.
- Credential-pattern scans found no packaged or workspace developer Gemini credential.
- The exact final release updated in place on Samsung SM-S721W, preserved the original install timestamp and existing app data, cold-launched in 454 ms, remained running, and produced no app crash or ANR marker.
- Phone APK: `/sdcard/Download/SportsAI-v2.2.apk`; its SHA-256 matches the PC artifact exactly.
- Public release: `https://github.com/HajinJoo/SportsAI/releases/tag/v2.2` with `SportsAI-v2.2.apk` and `SHA256SUMS-v2.2.txt` as the only release assets.
