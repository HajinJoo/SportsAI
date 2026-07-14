# SportsAI

SportsAI is an Android coaching app that turns sports clips into practical technique feedback. It combines on-device ML Kit pose detection with optional Gemini multimodal analysis, then presents strengths, opportunities, drills, skeleton playback, and progress over time.

> **Status:** active prototype. Feedback is educational and is not a replacement for a qualified coach, clinician, or medical professional.

> **Upgrading from 1.3 or earlier:** those local editions supported a build-time key. Do not distribute an older APK that was built with a personal credential; revoke that credential in Google AI Studio, then use the keyless 2.0 build.

## Final experience

<p align="center">
  <img src="docs/screenshots/home.png" width="30%" alt="SportsAI premium Home dashboard" />
  <img src="docs/screenshots/upload.png" width="30%" alt="SportsAI Upload screen" />
  <img src="docs/screenshots/timeline.png" width="30%" alt="SportsAI Timeline screen" />
</p>

The project began as a single simple upload screen and evolved into a four-destination athlete dashboard with Home, Upload, Timeline, and Settings. See the complete [development journey](docs/DEVELOPMENT_JOURNEY.md), including an authentic before/after comparison.

## Features

- **Three supported movements:** baseball pitching, baseball batting, and basketball shooting
- **On-device pose tracking:** samples video frames and detects body landmarks with ML Kit
- **Optional Gemini coaching:** sends a small set of selected frames for multimodal technique feedback
- **Bring-your-own Gemini key:** every user can securely add, test, replace, or remove their own API key in Settings; no developer key ships in the app
- **Offline fallback:** an explainable biomechanics rules engine produces feedback if Gemini is unavailable
- **Skeleton replay:** overlays tracked joints and bones on analyzed motion
- **Structured report:** overall score, strengths, issues, and actionable drills
- **3–4 sentence skill overview:** AI summarizes the athlete's level, strongest area, weakest area, and next direction
- **Sport-specific improvement filters:** compare pitch-speed potential, bat-speed potential, ball tracking, release speed, balance, and other movement-specific scores
- **Clickable progress timeline:** tap a chart date or session date to reopen the complete saved analysis
- **Sport-aware video highlight:** pitching, batting, and shooting each use movement-specific action scoring to select one focused play
- **Immediate highlight playback:** lifecycle-aware Media3 ExoPlayer playback renders on first open, seeks to the exact selected range, and uses the private MP4 as a fallback
- **Precise highlight editor:** adjust the start and end, preview the source range, then save a replacement cut without earlier keyframe footage
- **Private local history:** full reports, metric scores, and generated highlight clips are stored in app-private storage
- **Premium Compose UI:** responsive dashboard, compact floating navigation, dark/light themes, and accessible progress semantics
- **Adaptive icon:** color and Android 13+ monochrome launcher artwork

## How it works

```text
Video selected with Android Photo Picker
        |
        v
MediaMetadataRetriever samples frames
        |
        v
ML Kit Accurate Pose Detection (on device)
        |
        +-------------------------------+
        |                               |
        v                               v
Explainable biomechanics rules     Selected JPEG frames
(always available)                 sent to Gemini when configured
        |                               |
        +---------------+---------------+
                        v
                 TechniqueReport + skill metrics
                        |
        +---------------+----------------+
        |                                |
        v                                v
  Compose results               AI moment selection
        |                                |
        v                                v
  Clickable timeline          App-private MP4 highlights
                                         |
                                         v
                                  In-app video editor
```

More detail is available in [Architecture](docs/ARCHITECTURE.md).

## Requirements

- Android Studio with Android SDK 36
- JDK 17 or newer
- Android 10 / API 29 or newer device or emulator
- Optional: a Gemini API key from [Google AI Studio](https://aistudio.google.com/apikey)

## Setup

1. Clone the repository:

   ```powershell
   git clone https://github.com/HajinJoo/SportsAI.git
   Set-Location SportsAI
   ```

2. Open the project in Android Studio and allow Gradle sync to finish.

3. Build and test:

   ```powershell
   .\gradlew.bat testDebugUnitTest lintDebug assembleDebug
   ```

4. Install on a connected device:

   ```powershell
   adb install -r app\build\outputs\apk\debug\app-debug.apk
   ```

On macOS or Linux, use `./gradlew` and `/` path separators.

5. Open **Settings** in SportsAI. Create your own key in [Google AI Studio](https://aistudio.google.com/apikey), paste it into the protected field, and tap **Save & test key**. This step is optional; offline coaching remains available without a key.

## Public, keyless build

SportsAI 2.0 has no build-time Gemini credential path. Every APK is built keyless and uses the local pose pipeline plus sport-specific biomechanics rules until that device's user adds their own key in Settings. Public CI verifies the same no-secret build.

## API-key storage and warning

The user-provided key is encrypted at rest with an app-specific, non-exportable Android Keystore AES key. Its encrypted preferences are excluded from Android cloud backup and device transfer, the complete key is not shown again after saving, and requests send it to Google using the `x-goog-api-key` header.

Client-side storage cannot fully protect an API key on a compromised or instrumented device. Use a dedicated key, monitor quota, and revoke it if the device is lost. A centrally operated production service should still call Gemini through an authenticated backend with quotas, abuse protection, and key rotation.

If a key has ever been posted publicly, revoke it in Google AI Studio and generate a new one.

## Filming tips

- Keep the athlete's full body visible.
- Record from the side recommended by the selected sport.
- Use bright, even lighting and a steady camera.
- Avoid other people crossing the frame.
- Use short clips focused on one movement repetition.

## Privacy

Pose detection and MP4 highlight cutting run locally. When the device owner enables Gemini, their API key and selected image frames are transmitted to Google's Gemini API for analysis; the original video and generated MP4 highlights are not sent by this app. Timeline reports, the encrypted key, and highlight files remain in app-private local storage. Read the complete [Privacy Notes](docs/PRIVACY.md) before testing with another person's video.

## Project structure

```text
app/src/main/java/com/example/sportsai/
|-- data/        # Pose analysis, Gemini client, rules, local history, MP4 cutting
|-- model/       # Sports, poses, reports, timeline entries
|-- ui/          # Premium dashboard and skeleton rendering
|-- ui/theme/    # Color, typography, shapes
`-- viewmodel/   # Analysis state and orchestration
```

## Documentation

- [Development journey](docs/DEVELOPMENT_JOURNEY.md)
- [Development record through version 2.0](developement-record.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Privacy notes](docs/PRIVACY.md)
- [Security policy](SECURITY.md)
- [Contributing](CONTRIBUTING.md)

## License

Licensed under the [Apache License 2.0](LICENSE).

