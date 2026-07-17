# Development Journey

This document records how SportsAI evolved during its initial development session. It is a product narrative based on the retained source files and completed implementation—not fabricated historical Git commits. Git was initialized only after the prototype reached its public-release state.

## Before and after

| Original single-screen prototype | Final premium dashboard |
| --- | --- |
| <img src="screenshots/before.png" width="320" alt="Original SportsAI upload screen" /> | <img src="screenshots/home.png" width="320" alt="Final SportsAI Home dashboard" /> |

The original interface is still represented by `ui/HomeScreen.kt`. The final application entry point uses `ui/PremiumDashboard.kt`.

## 1. The initial idea

The starting idea was simple: choose a baseball or sports clip, let AI inspect the athlete's movement, explain what is good and what needs work, and recommend ways to improve.

The first user flow contained:

1. A SportsAI title
2. A few sport chips
3. A video picker button
4. A progress indicator
5. A score and text findings

It proved the flow, but it looked like a basic Android prototype and had no long-term athlete experience.

## 2. Building the pose pipeline

`PoseAnalyzer` became the core on-device computer-vision layer:

- `MediaMetadataRetriever` samples frames from the selected clip.
- ML Kit Accurate Pose Detection finds body landmarks.
- Frame timestamps and landmark confidence are retained.
- A representative key frame is selected.
- Downscaled frame/pose pairs are produced for replay.

This kept the expensive pose work local and made the feedback pipeline independent from the UI.

That original single-person path remains appropriate for pitching and basketball shooting. Version 2.2 later added a separate multi-person batting path after game-angle footage showed that a one-person detector could return the catcher or umpire instead of the batter.

## 3. Adding explainable biomechanics feedback

`TechniqueAnalyzer` added sport-specific local coaching rules for:

- Baseball pitching
- Baseball batting
- Basketball shooting

The rules inspect joint angles, knee flexion, trunk movement, alignment, balance, and detection quality. This created an offline fallback and made feedback possible even without a cloud API key.

## 4. Adding optional Gemini coaching

`GeminiCoach` added multimodal review:

- Selects a small spread of analyzed frames
- Compresses them as JPEG
- Requests strict JSON output
- Parses score, summary, strengths, issues, and tips
- Falls back to the local rules engine on configuration or network failure

The app intentionally remains usable when Gemini is not configured.

## 5. Visualizing what the model saw

Two Compose renderers made pose tracking understandable:

- `SkeletonOverlay` draws a skeleton on a representative frame.
- `AnimatedSkeleton` loops analyzed frames with the tracked body.

The overlay was refined to show only major body joints above a confidence threshold, reducing visual noise and jitter. Replay can be tapped to pause or continue.

## 6. Expanding movement support

The sport model grew from pitching into three focused movement types. Each has:

- A display name and visual marker
- A filming recommendation
- A dedicated biomechanics path
- A separate progress timeline

The scope is intentionally honest: the current analyzer does not claim to support every sport.

## 7. Creating progress history

A lightweight `HistoryRepository` stores analyzed sessions as JSON in app-private storage. Each entry contains:

- Sport
- User-selected filming date
- Score
- Summary

The app asks for the filming date rather than extracting video metadata. Sessions are sorted chronologically and compared with the previous score.

## 8. Adding the timeline

The timeline introduced:

- Latest, best, and change statistics
- A score trend chart
- Per-session score deltas
- Sport filtering
- Delete confirmation
- Empty-state guidance

This shifted SportsAI from a one-time analyzer toward a training companion.

## 9. Designing a visual identity

The default Android purple template was replaced by an athletic visual system:

- Court green primary color
- Field navy surfaces
- Cyan tracking accent
- Orange energy accent
- Semantic green, amber, blue, and red feedback colors
- Complete typography and shape scales

A custom adaptive launcher icon combined a trophy, sports ball, motion trail, and connected AI nodes. A monochrome layer supports Android 13+ themed icons.

## 10. Rebuilding the UI

The final UI was rebuilt as `PremiumSportsDashboard` with:

- Layered gradient and radial backgrounds
- A compact brand bar
- A bold athlete-focused hero
- Clear upload and filming guidance
- Animated two-stage analysis feedback
- Responsive score gauge
- Media-first motion replay
- Structured coaching insight groups
- Better contrast and explicit content colors

A contrast audit fixed black text inherited by translucent custom surfaces.

## 11. Adding real app navigation

The original all-in-one scroll was split into three destinations:

### Home

An overview with quick-start actions, training statistics, and the latest session.

### Upload

Sport selection, clip picking, analysis progress, errors, and full results.

### Timeline

Sport filtering, charts, summary statistics, and session management.

One shared `AnalysisViewModel` lives above the tabs, so switching destinations does not cancel analysis or lose results.

## 12. Refining the bottom bar

The standard thick Material navigation bar was replaced by a compact floating control:

- 60dp-high rounded surface
- Maximum 340dp width
- Close to the safe bottom edge
- 48–50dp accessible tab targets
- Custom line icons
- Circular selected state
- Upload status dot for analyzing, ready, and error states

## 13. Public-release preparation

Before publication, the project received:

- Strict ignore rules for local keys and generated output
- Keyless CI builds
- Privacy and security documentation
- Apache 2.0 licensing
- Authentic before/after screenshots
- Secret scanning and clean-build verification

## 14. Reopening historical analyses

Timeline dates and chart endpoint dates became interactive. A saved session now retains enough structured data to rebuild the complete score, overview, metric, finding, and highlight result instead of opening only a summary row.

## 15. Adding sport-specific improvement metrics

Each movement received a dedicated 0–100 metric set and filterable trend view. The timeline shows whether a metric improved, declined, or stayed level compared with the previous session. Speed entries are explicitly described as pose-based potential scores—not radar-measured ball or bat speed—and ball tracking is presented as a visual/head-stability signal.

## 16. Adding AI skill overviews

Both Gemini and the offline rules path now produce a concise 3–4 sentence overview. It identifies the athlete's current level, strongest metric, clearest opportunity, and a practical direction for the next comparison.

## 17. Creating real editable highlights

Pose timing now identifies peak action, best form, release moments, and a batting contact-zone estimate based on body motion. Android's local media APIs cut those ranges into private MP4 files. Tapping a highlight opens real video playback, and the in-app editor can adjust the start/end boundaries and replace the saved cut.

## 18. Making highlights focused and dependable

Real-device testing exposed two weaknesses in the first highlight pass: a normal tap used the generated file while the editor used the more reliable source URI, and keyframe-based remuxing could include video from well before the selected action. Playback now opens the exact source range immediately and falls back to the private clip only when needed. Media3 Transformer creates precise replacement clips, while a smoothed, body-normalized selector chooses one complete sport-specific action using release-arm speed and extension for pitching, peak hand speed and torso rotation for batting, or upward release, elbow extension, and leg drive for basketball shooting. Static poses and isolated one-frame tracking jumps are rejected.

## 19. Removing the first-open black video

The platform `VideoView` could prepare a fast local clip before its callback and dialog surface were ready. The result was a black player until the app was backgrounded and resumed. Highlight playback now uses Media3 ExoPlayer attached to a `TextureView`, prepares only while the lifecycle is started, pauses in the background, releases with the dialog, and displays an explicit loading or failure state. Exact seeking and clipping are handled by the player, and a real Samsung test confirmed that a saved highlight rendered and advanced frames on its first open without an app restart.

## 20. Turning the local build into a public bring-your-own-key app

The exact personal/local source was preserved as the `local-v1.3` Git tag. The public 2.0 build removed the Gradle and `BuildConfig` secret path completely, added a fourth Settings destination, and made every APK keyless by default. Each device owner can add, test, replace, or remove their own Gemini key. The saved value is AES-GCM encrypted with Android Keystore key material and excluded from backups and device transfer; SportsAI continues with offline coaching when no key is present or Gemini is unavailable.

## 21. Locking onto the batter offline

Game-angle batting clips exposed a deeper problem than prompt quality: ML Kit's single-pose result could follow the most prominent catcher or umpire. Cloud coaching could not repair a skeleton and movement timeline built from the wrong person.

Version 2.2 introduced an offline batting-specific path:

- The bundled MediaPipe Pose Landmarker Full model detects up to four people at each sampled frame.
- A persistent tracker associates candidates through time using torso position and scale, landmark shape, recent velocity, and short-gap continuity.
- Batter evidence combines track coverage, reliable joint completeness, two-hand proximity, hand height, stance, and coordinated wrist movement relative to the torso.
- Swing motion is measured in torso-aligned coordinates so camera translation, zoom, in-plane roll, and an official stepping do not automatically look like bat-hand movement. A transverse gate combines screen and relative-depth motion, rejecting a vertical catcher rise while keeping a front-angle swing eligible.
- Replay and highlight timing use the strongest direction-consistent three-interval hand-motion burst instead of a single fastest pose interval.
- The largest or most central person receives no automatic preference.
- If the best track is incomplete, lacks swing evidence, or is too close to the runner-up, SportsAI abstains. It creates no score, highlight, or saved batting result and instead explains how to film a clearer clip.

Once Batter Lock succeeds, the selected track supplies the same offline coaching, sport metrics, skeleton replay, local highlight selector, editor, and timeline features that previously depended on a single detected pose. Sensitive key, history, and generated-highlight paths are excluded from Android backup and device transfer. Gemini remains optional. This is still pose analysis rather than equipment tracking: it does not detect the bat or ball, prove contact or gaze, or measure radar speed, ball flight, launch angle, or physical distance.

## Final architecture snapshot

```text
Photo Picker
    -> PoseAnalyzer
       -> batting: MediaPipe multi-pose -> BatterPoseSelector -> accept or abstain
       -> pitching/shooting: ML Kit single pose
    -> TechniqueAnalyzer (local, accepted athlete track only)
    -> GeminiCoach (optional selected-frame analysis)
    -> HighlightExtractor (local sport-aware moment selection)
    -> VideoClipExporter (private MP4 cut/edit)
    -> AnalysisViewModel
    -> PremiumSportsDashboard (Home / Upload / Timeline / Settings)
    -> HistoryRepository (local timeline)
```

## What remains for production

- Move Gemini calls behind an authenticated backend
- Add user accounts and encrypted cloud sync if desired
- Validate coaching ranges with qualified sport professionals
- Add automated Compose UI and screenshot tests
- Add camera capture and clip trimming
- Add more movements only with dedicated mechanics and test data
- Validate Batter Lock across a larger, consented set of camera angles, uniforms, lighting, body types, and levels of play
- Add dedicated bat/ball detection only if future features need equipment, contact, or flight measurements
- Complete accessibility review with TalkBack and large font settings

SportsAI should continue to present itself as an educational coaching aid—not a medical assessment or a substitute for in-person coaching.

