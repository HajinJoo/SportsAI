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
