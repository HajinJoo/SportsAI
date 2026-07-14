# Architecture

SportsAI is a single-module Android application built with Kotlin and Jetpack Compose.

## Layers

### UI

- `PremiumDashboard.kt` provides Home, Upload, Timeline, and Settings destinations.
- `SettingsScreen.kt` provides the bring-your-own Gemini key controls and security disclosure.
- `AnimatedSkeleton.kt` and `SkeletonOverlay.kt` render tracked movement.
- `ui/theme` defines the application color, typography, and shape system.

### State

`AnalysisViewModel` owns the analysis state machine:

```text
Idle -> Analyzing(scanning) -> Analyzing(coaching) -> Done
                                                    -> Error
Timeline date -> ViewingPastSession -> Timeline
```

The ViewModel is scoped above bottom navigation, so changing tabs does not cancel analysis.

### Data and analysis

- `PoseAnalyzer`: frame extraction and ML Kit pose detection
- `TechniqueAnalyzer`: explainable sport-specific biomechanics rules
- `GeminiCoach`: optional Gemini 3.5 Flash multimodal analysis using the current device user's key
- `GeminiApiKeyStore`: AES-GCM encryption backed by a non-exportable Android Keystore key
- `HighlightExtractor`: pose-timeline ranking for release, contact, form, and peak-action moments
- `HighlightExtractor`: normalized, smoothed sport-specific action scoring for pitch release, swing contact, and shot release
- `VideoClipExporter`: local Media3 Transformer clipping for precise MP4 cutting and re-cutting
- `HighlightVideoPlayer`: lifecycle-aware Media3 ExoPlayer playback on a dialog-safe `TextureView`, with exact seeking and source-to-private-file fallback
- `HistoryRepository`: app-private JSON timeline persistence

### Models

Models represent landmarks, frame poses, animation frames, sports, findings, reports, analysis results, and timeline sessions.

## Analysis flow

1. Android Photo Picker returns a video URI.
2. `PoseAnalyzer` samples approximately five frames per second.
3. ML Kit detects landmarks on each sampled bitmap.
4. The analyzer stores a timeline, best key frame, and downscaled replay frames.
5. If the device user has saved a Gemini key and pose frames are available, `GeminiCoach` requests structured coaching using the `x-goog-api-key` header.
6. If that request cannot run, `TechniqueAnalyzer` generates a local report.
7. `HighlightExtractor` selects the most useful movement moments.
8. `VideoClipExporter` cuts those ranges into separate app-private MP4 files.
9. `AnalysisViewModel` exposes the report, replay, metric filters, and highlights to Compose.
10. After the user chooses a filming date, the full report and highlight references are stored locally.
11. Tapping a timeline date reconstructs the historical report. Editing a highlight re-cuts the original picker URI when that persisted permission is still available.

## Network boundary

The app does not upload the original video. When Gemini is configured, up to six selected frames are JPEG-compressed and included in a Gemini API request. See [Privacy Notes](PRIVACY.md).

## Bring-your-own-key operation

Gradle has no API-key input and the APK contains no SportsAI developer credential. `GeminiApiKeyStore` encrypts a user-entered key with AES-GCM and keeps the wrapping key inside Android Keystore. Settings can save and validate, replace, or remove it. The encrypted preferences file is excluded from cloud backup and device transfer because its Keystore key is device-bound. Without a saved key, local rules and CI builds work normally.

## Main dependencies

- Jetpack Compose Material 3
- AndroidX Lifecycle and Navigation Compose
- Kotlin coroutines
- ML Kit Accurate Pose Detection
- Coil Compose
- Media3 ExoPlayer and Transformer

## Storage

`HistoryRepository` writes `session_history.json` in `Context.filesDir`. Entries contain the selected sport, filming date, overall score, summary, findings, metric scores, AI overview, detection rate, source picker URI, video duration, and highlight metadata. Generated MP4 highlights live under `Context.filesDir/highlights`; deleting a session deletes its private highlight files. Pose bitmaps and animation frames remain memory-only, and the original source video is not copied into app storage.

