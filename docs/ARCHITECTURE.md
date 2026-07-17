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
Idle -> Analyzing(tracking) -> Analyzing(mechanics) -> Analyzing(coaching) -> Done
                                                                          -> Error
Timeline date -> ViewingPastSession -> Timeline
```

The ViewModel is scoped above bottom navigation, so changing tabs does not cancel analysis.

### Data and analysis

- `PoseAnalyzer`: frame extraction and sport-specific dispatch to MediaPipe or ML Kit
- `BatterPoseDetector`: bundled MediaPipe Pose Landmarker in video mode, configured for CPU inference and up to four poses per frame
- `BatBallDetector`: separate MediaPipe EfficientDet-Lite0 video task restricted to COCO `baseball bat` and `sports ball` detections
- `BatterPoseSelector`: persistent geometric track association plus batting-evidence scoring and an explicit abstention gate
- `TechniqueAnalyzer`: explainable sport-specific biomechanics rules; batting reaches it only after Batter Lock accepts one athlete timeline
- `SwingMechanicsAnalyzer`: stance/stride/impact-zone/follow-through segmentation, numeric measurements, and evidence-gated `HEAD_DROP`, `FRONT_KNEE_COLLAPSE`, and `EARLY_OPEN` labels
- `SwingAnalysisJsonCodec`: stable local JSON contract shared by history persistence and optional LLM coaching
- `CoachingFrameExtractor`: high-resolution frame extraction focused on the detected action window
- `GeminiCoach`: optional Gemini 3.5 Flash multimodal analysis with visibility validation and frame-cited evidence
- `GeminiApiKeyStore`: AES-GCM encryption backed by a non-exportable Android Keystore key
- `HighlightExtractor`: normalized, smoothed sport-specific action scoring for pitch release, torso-relative coordinated swing-hand motion, and shot release
- `VideoClipExporter`: local Media3 Transformer clipping for precise MP4 cutting and re-cutting
- `HighlightVideoPlayer`: lifecycle-aware Media3 ExoPlayer playback on a dialog-safe `TextureView`, with exact seeking and source-to-private-file fallback
- `HistoryRepository`: app-private JSON timeline persistence
- `SwingReferenceModel`: prototype-only robust similarity profile for curated positive batting examples; no reference artifact currently ships in the app

### Models

Models represent landmarks, frame poses, animation frames, sports, findings, reports, analysis results, and timeline sessions.

## Analysis flow

1. Android Photo Picker returns a video URI.
2. `PoseAnalyzer` reads the container duration and samples frames without copying the original video into app storage.
3. For pitching and basketball shooting, ML Kit Accurate Pose Detection samples approximately five frames per second and returns one athlete pose.
4. For batting, the bundled MediaPipe Pose Landmarker samples at least ten frames per second and returns as many as four 33-landmark pose candidates. A separate EfficientDet-Lite0 task checks the same decoded frames for confidence-gated bat and sports-ball boxes. Empty detections remain empty; pose landmarks are never converted into equipment claims.
5. `BatterPoseSelector` associates candidates across time using torso location, scale, recent velocity, and landmark shape. It then ranks continuous tracks using coverage, landmark completeness, confidence, two-hand proximity, batting hand height, stance, and coordinated wrist motion relative to the torso. It deliberately does not assume that the largest or most central person is the batter.
6. A batting track is accepted only when it has enough coverage, complete joints, grip evidence, swing motion, and separation from the next-best candidate. Ambiguous clips enter `BATTER_NOT_CONFIDENT`: the UI requests a better clip and does not create a technique score, highlight, or saved timeline entry.
7. For an accepted track, the analyzer retains coordinates for the full scan, then reopens only a capped set of frames for skeleton replay and a representative key frame. At most 48 replay frames are kept in memory.
8. `SwingMechanicsAnalyzer` first classifies projected pose geometry as side, rear, or unconfirmed from the median shoulder/hip width normalized by torso length. It then finds the coordinated three-interval hand-speed peak and segments stance, stride, impact zone, and follow-through. Side view enables projected front/trail-knee and hand-load rules; rear view enables spine/head stability and rotation-sequence rules. Unconfirmed geometry keeps common measurements and withholds view-specific labels. “Impact zone” is a motion phase and never asserts bat-ball contact.
9. `TechniqueAnalyzer` always creates the authoritative local score, metrics, structured swing summary, and issue tags before any network request. `SwingAnalysisJsonCodec` serializes that result.
10. `HighlightExtractor` identifies the strongest complete sport-specific action from that accepted athlete timeline.
11. If the device user has saved a Gemini key, `CoachingFrameExtractor` reopens up to eight 1280-pixel frames across that action and pairs each with its pose timestamp.
12. `GeminiCoach` receives the authoritative local analysis JSON plus labeled frames and may generate only the narrative summary, strengths, issue explanations, drills, and overview. It cannot replace local scores, measurements, phases, or issue codes. Results must pass the athlete-visibility and frame-citation gate or the app keeps honest offline coaching.
13. `VideoClipExporter` cuts the detected action range into a separate app-private MP4 file.
14. `AnalysisViewModel` exposes the report, replay, local swing model, metric filters, and highlight to Compose.
15. After the user chooses a filming date, the full report, structured swing JSON, and highlight reference are stored locally.
16. Tapping a timeline date reconstructs the historical report. Editing a highlight re-cuts the original picker URI when that persisted permission is still available.

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

The camera-view result is an explainable geometric heuristic, not a separately trained image classifier or a calibrated probability. It uses multiple reliable pose frames and abstains in the overlap between side and rear geometry. Diagonal framing, loose clothing, occlusion, and pose errors can therefore produce an unconfirmed result.

The pose models return body landmarks and do not imply equipment. The separate COCO object detector can return confidence-gated `baseball bat` and `sports ball` boxes, but a missed box does not prove absence and a detected sports ball does not establish pitch identity, contact, trajectory, or outcome. An object box also does not identify the bat head, sweet spot, or swing plane. No local model measures bat-ball contact, eye gaze, radar speed, launch angle, or physical distance. Joint angles are 2D projections rather than clinical or 3D measurements. SportsAI therefore describes speed values as pose-based movement-potential scores. The batting “Ball Tracking” metric remains a head-stability proxy, not proof of gaze direction or ball tracking.

## Storage

`HistoryRepository` writes `session_history.json` in `Context.filesDir`. Entries contain the selected sport, filming date, overall score, summary, findings, metric scores, AI overview, detection rate, source picker URI, video duration, and highlight metadata. Generated MP4 highlights live under `Context.filesDir/highlights`; deleting a session deletes its private highlight files. Both paths, along with encrypted key preferences, are excluded from Android cloud backup and device transfer. Pose bitmaps and animation frames remain memory-only, and the original source video is not copied into app storage.

