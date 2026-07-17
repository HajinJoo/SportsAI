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

- `PoseAnalyzer`: frame extraction and sport-specific dispatch to MediaPipe or ML Kit
- `BatterPoseDetector`: bundled MediaPipe Pose Landmarker in video mode, configured for CPU inference and up to four poses per frame
- `BatterPoseSelector`: persistent geometric track association plus batting-evidence scoring and an explicit abstention gate
- `TechniqueAnalyzer`: explainable sport-specific biomechanics rules; batting reaches it only after Batter Lock accepts one athlete timeline
- `CoachingFrameExtractor`: high-resolution frame extraction focused on the detected action window
- `GeminiCoach`: optional Gemini 3.5 Flash multimodal analysis with visibility validation and frame-cited evidence
- `GeminiApiKeyStore`: AES-GCM encryption backed by a non-exportable Android Keystore key
- `HighlightExtractor`: normalized, smoothed sport-specific action scoring for pitch release, torso-relative coordinated swing-hand motion, and shot release
- `VideoClipExporter`: local Media3 Transformer clipping for precise MP4 cutting and re-cutting
- `HighlightVideoPlayer`: lifecycle-aware Media3 ExoPlayer playback on a dialog-safe `TextureView`, with exact seeking and source-to-private-file fallback
- `HistoryRepository`: app-private JSON timeline persistence

### Models

Models represent landmarks, frame poses, animation frames, sports, findings, reports, analysis results, and timeline sessions.

## Analysis flow

1. Android Photo Picker returns a video URI.
2. `PoseAnalyzer` reads the container duration and samples frames without copying the original video into app storage.
3. For pitching and basketball shooting, ML Kit Accurate Pose Detection samples approximately five frames per second and returns one athlete pose.
4. For batting, the bundled MediaPipe Pose Landmarker samples at least ten frames per second and returns as many as four 33-landmark pose candidates. Empty detections remain in the sequence so coverage and occlusion gaps are measured honestly.
5. `BatterPoseSelector` associates candidates across time using torso location, scale, recent velocity, and landmark shape. It then ranks continuous tracks using coverage, landmark completeness, confidence, two-hand proximity, batting hand height, stance, and coordinated wrist motion relative to the torso. It deliberately does not assume that the largest or most central person is the batter.
6. A batting track is accepted only when it has enough coverage, complete joints, grip evidence, swing motion, and separation from the next-best candidate. Ambiguous clips enter `BATTER_NOT_CONFIDENT`: the UI requests a better clip and does not create a technique score, highlight, or saved timeline entry.
7. For an accepted track, the analyzer retains coordinates for the full scan, then reopens only a capped set of frames for skeleton replay and a representative key frame. At most 48 replay frames are kept in memory.
8. `HighlightExtractor` identifies the strongest complete sport-specific action from that accepted athlete timeline.
9. If the device user has saved a Gemini key, `CoachingFrameExtractor` reopens up to eight 1280-pixel frames across that action and pairs each with its pose timestamp.
10. `GeminiCoach` labels each frame, sends normalized key-joint context, and requests structured coaching using the `x-goog-api-key` header. Results must pass the athlete-visibility and frame-citation gate or the app uses honest offline coaching.
11. If Gemini cannot run or fails its evidence gate, `TechniqueAnalyzer` generates a local report from the same accepted pose timeline.
12. `VideoClipExporter` cuts the detected action range into a separate app-private MP4 file.
13. `AnalysisViewModel` exposes the report, replay, metric filters, and highlight to Compose.
14. After the user chooses a filming date, the full report and highlight reference are stored locally.
15. Tapping a timeline date reconstructs the historical report. Editing a highlight re-cuts the original picker URI when that persisted permission is still available.

## Batter Lock decision boundary

MediaPipe supplies neural pose detections; it is not a trained identity classifier for baseball roles. Batter Lock makes a temporary, clip-local decision from motion and posture evidence. Track association tolerates a brief detector gap. Batting motion is measured in torso-aligned coordinates so translation, zoom, in-plane camera roll, an official stepping, or a catcher standing up is less likely to look like a swing; the peak must persist across a direction-consistent three-interval burst. Screen-horizontal and relative model-depth travel form the transverse swing-plane gate, which keeps front-angle swings eligible without treating purely vertical two-hand movement as batting.

Only the selected batter's 33-point pose reaches downstream scoring, skeleton playback, metrics, highlight selection, and optional Gemini frame selection. If no track clears the gate, SportsAI abstains rather than substituting the catcher or umpire. The reported match value is a heuristic selection score, not a calibrated probability that a person is the batter.

## Network boundary

The app does not upload the original video. MediaPipe inference, ML Kit inference, Batter Lock, offline rules, and highlight cutting happen on device. When Gemini is configured, up to eight action-focused frames are JPEG-compressed and included in a Gemini API request. See [Privacy Notes](PRIVACY.md).

## Bring-your-own-key operation

Gradle has no API-key input and the APK contains no SportsAI developer credential. `GeminiApiKeyStore` encrypts a user-entered key with AES-GCM and keeps the wrapping key inside Android Keystore. Settings can save and validate, replace, or remove it. The encrypted preferences file is excluded from cloud backup and device transfer because its Keystore key is device-bound. Without a saved key, local rules and CI builds work normally.

## Main dependencies

- Jetpack Compose Material 3
- AndroidX Lifecycle and Navigation Compose
- Kotlin coroutines
- ML Kit Accurate Pose Detection
- MediaPipe Tasks Vision 0.10.35
- Bundled Pose Landmarker Full float16 model
- Coil Compose
- Media3 ExoPlayer and Transformer

The model source, checksum, license, and test-asset attributions are recorded in [Third-Party Notices](../THIRD_PARTY_NOTICES.md).

## Measurement boundary

The pose models return body landmarks; they do not detect or measure a bat, baseball, basketball flight, bat-ball contact, eye gaze, radar speed, launch angle, or physical distance. SportsAI therefore describes speed values as pose-based movement-potential scores. The batting “Ball Tracking” metric is a head-stability proxy, not proof of gaze direction or ball tracking. Gemini is instructed to make equipment or contact claims only when the submitted image frames visibly support them.

## Storage

`HistoryRepository` writes `session_history.json` in `Context.filesDir`. Entries contain the selected sport, filming date, overall score, summary, findings, metric scores, AI overview, detection rate, source picker URI, video duration, and highlight metadata. Generated MP4 highlights live under `Context.filesDir/highlights`; deleting a session deletes its private highlight files. Both paths, along with encrypted key preferences, are excluded from Android cloud backup and device transfer. Pose bitmaps and animation frames remain memory-only, and the original source video is not copied into app storage.

