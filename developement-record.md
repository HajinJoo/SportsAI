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
- Combined `testDebugUnitTest lintDebug assembleDebug` release gate: passed.
- PC APK: `F:\\SportsAI\\SportsAI-v1.1-debug.apk` (123,946,940 bytes).
- APK SHA-256: `BAD5BC1DD457A76B467FBD13DF841DB0A26F616C30CED041E578D2BB0F0DA177`.
- Connected Samsung SM-S721W: version 1.1 installed and cold-launched successfully.
- Phone copy: `/sdcard/Download/SportsAI-v1.1-debug.apk`.

### Release checklist

1. Run unit tests, Android lint, and APK assembly.
2. Copy the APK to the PC delivery location.
3. If an authorized Android phone is connected, install the APK and copy it to the phone's Downloads folder.
4. Commit the source and this record.
5. Push `main` to the public GitHub repository and confirm the remote commit.
