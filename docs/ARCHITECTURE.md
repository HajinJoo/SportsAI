# Architecture

SportsAI is a single-module Android application built with Kotlin and Jetpack Compose.

## Layers

### UI

- `PremiumDashboard.kt` provides Home, Upload, and Timeline destinations.
- `AnimatedSkeleton.kt` and `SkeletonOverlay.kt` render tracked movement.
- `ui/theme` defines the application color, typography, and shape system.

### State

`AnalysisViewModel` owns the analysis state machine:

```text
Idle -> Analyzing(scanning) -> Analyzing(coaching) -> Done
                                                    -> Error
```

The ViewModel is scoped above bottom navigation, so changing tabs does not cancel analysis.

### Data and analysis

- `PoseAnalyzer`: frame extraction and ML Kit pose detection
- `TechniqueAnalyzer`: explainable sport-specific biomechanics rules
- `GeminiCoach`: optional Gemini multimodal analysis
- `HistoryRepository`: app-private JSON timeline persistence

### Models

Models represent landmarks, frame poses, animation frames, sports, findings, reports, analysis results, and timeline sessions.

## Analysis flow

1. Android Photo Picker returns a video URI.
2. `PoseAnalyzer` samples approximately five frames per second.
3. ML Kit detects landmarks on each sampled bitmap.
4. The analyzer stores a timeline, best key frame, and downscaled replay frames.
5. If a Gemini key and pose frames are available, `GeminiCoach` requests structured coaching.
6. If that request cannot run, `TechniqueAnalyzer` generates a local report.
7. `AnalysisViewModel` exposes the report and replay to Compose.
8. After the user chooses a filming date, a compact timeline entry is stored locally.

## Network boundary

The app does not upload the original video. When Gemini is configured, up to six selected frames are JPEG-compressed and included in a Gemini API request. See [Privacy Notes](PRIVACY.md).

## Keyless operation

`app/build.gradle.kts` reads `GEMINI_API_KEY` from ignored `local.properties`. Missing configuration becomes an empty `BuildConfig` value, allowing local rules and CI builds to work without secrets.

## Main dependencies

- Jetpack Compose Material 3
- AndroidX Lifecycle and Navigation Compose
- Kotlin coroutines
- ML Kit Accurate Pose Detection
- Coil Compose

## Storage

`HistoryRepository` writes `session_history.json` in `Context.filesDir`. Entries contain only the selected sport, filming date, score, and summary. Video files and pose bitmaps are not persisted by the repository.

