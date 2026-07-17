# SportsAI

[![Android CI](https://github.com/HajinJoo/SportsAI/actions/workflows/android.yml/badge.svg)](https://github.com/HajinJoo/SportsAI/actions/workflows/android.yml)
[![Latest release](https://img.shields.io/github/v/release/HajinJoo/SportsAI)](https://github.com/HajinJoo/SportsAI/releases/latest)
[![License](https://img.shields.io/github/license/HajinJoo/SportsAI)](LICENSE)

SportsAI is an Android coaching app that turns sports clips into practical technique feedback. It combines on-device MediaPipe multi-pose tracking for batting, ML Kit pose detection for pitching and basketball shooting, and optional Gemini multimodal analysis. The app then presents strengths, opportunities, drills, skeleton playback, editable highlights, and progress over time.

> **Status:** active prototype. Feedback is educational and is not a replacement for a qualified coach, clinician, or medical professional.

> **Upgrading from 1.3 or earlier:** those local editions supported a build-time key. Do not distribute an older APK that was built with a personal credential; revoke that credential in Google AI Studio, then use the current keyless 2.3 build.

## Download

The latest installable Android release is available on the [SportsAI GitHub Releases page](https://github.com/HajinJoo/SportsAI/releases/latest). For version 2.3, download `SportsAI-v2.3.apk`. It is a universal APK for arm64-v8a, armeabi-v7a, x86, and x86_64 devices. Android 10 / API 29 or newer is required. The final signed APK is 183,505,026 bytes and its SHA-256 is `B870400C135000213574F199522EB74C299C0FCAB2676017496260A7838910F2`; the release also includes `SHA256SUMS-v2.3.txt`.

## Final experience

<p align="center">
  <img src="docs/screenshots/home.png" width="30%" alt="SportsAI premium Home dashboard" />
  <img src="docs/screenshots/upload.png" width="30%" alt="SportsAI Upload screen" />
  <img src="docs/screenshots/timeline.png" width="30%" alt="SportsAI Timeline screen" />
</p>

The project began as a single simple upload screen and evolved into a four-destination athlete dashboard with Home, Upload, Timeline, and Settings. See the complete [development journey](docs/DEVELOPMENT_JOURNEY.md), including an authentic before/after comparison.

## Features

- **Three supported movements:** baseball pitching, baseball batting, and basketball shooting
- **Offline Batter Lock:** the bundled MediaPipe Pose Landmarker finds up to four people per batting frame, follows them over time, and selects the continuous track with the strongest direction-consistent two-hand swing evidence across screen and model-depth motion
- **Honest batting abstention:** if the batter cannot be separated confidently from the catcher, umpire, or another person, SportsAI shows filming guidance and creates no score, highlight, or saved result
- **On-device pose tracking:** pitching and basketball shooting retain ML Kit single-person tracking; batting uses the bundled MediaPipe model and does not require an API key
- **On-device bat and ball observations:** a separate lightweight MediaPipe EfficientDet-Lite0 task records confidence-gated `baseball bat` and `sports ball` boxes without treating a miss as proof of absence or claiming contact
- **View-routed structured swing model:** classifies side, rear, or unconfirmed camera geometry before segmenting stance, stride, impact zone, and follow-through. Side clips evaluate pose-supported knee and hand-load signals; rear clips evaluate spine/head stability and rotation timing. Ambiguous clips keep common measurements but withhold view-specific labels.
- **Evidence-grounded Gemini coaching:** re-extracts up to eight clear frames and sends them with the authoritative local analysis JSON; Gemini writes the coach narrative but cannot replace local scores, phases, measurements, or issue codes
- **Bring-your-own Gemini key:** every user can securely add, test, replace, or remove their own API key in Settings; no developer key ships in the app
- **Useful without an API key:** accepted on-device pose tracks feed the explainable biomechanics rules, sport metrics, skill overview, skeleton replay, and local highlight selection
- **Skeleton replay:** overlays tracked joints and bones on analyzed motion
- **Structured report:** overall score, strengths, issues, and actionable drills
- **3–4 sentence skill overview:** Gemini or the offline rules path summarizes the athlete's level, strongest area, weakest area, and next direction
- **Sport-specific improvement filters:** compare pose-based speed potential, balance, sequencing, and other movement-specific scores; these are not radar, bat-sensor, gaze, or ball-flight measurements
- **Clickable progress timeline:** tap a chart date or session date to reopen the complete saved analysis
- **Sport-aware video highlight:** pitching, batting, and shooting each use movement-specific action scoring to select one focused play
- **Immediate highlight playback:** lifecycle-aware Media3 ExoPlayer playback renders on first open, seeks to the exact selected range, and uses the private MP4 as a fallback
- **Precise highlight editor:** adjust the start and end, preview the source range, then save a replacement cut without earlier keyframe footage
- **Private local history:** full reports, metric scores, and generated highlight clips are stored in app-private storage and excluded from Android backup/device transfer
- **Premium Compose UI:** responsive dashboard, compact floating navigation, dark/light themes, and accessible progress semantics
- **Adaptive icon:** color and Android 13+ monochrome launcher artwork

## How it works

```text
Video selected with Android Photo Picker
        |
        v
MediaMetadataRetriever samples frames
        |
        +-------------------------------+
        |                               |
        v                               v
Batting: MediaPipe pose + object    Pitching/shooting: ML Kit
detection (bat/ball boxes)              |
        |                               |
        v                               |
Temporal Batter Lock                   |
        +-- uncertain -> retry; stop    |
        |                               |
        +-- accepted ---+---------------+
                        v
   Accepted pose timeline + equipment observations
                        |
                        v
        Side / rear / unconfirmed view routing
                           |
                           v
  Phase segmentation + view-specific mechanics
                        |
                        v
          Authoritative local analysis JSON
                        |
        +---------------+----------------+
        |                                |
        v                                v
Offline report + highlight       Optional JSON + action frames
                                  sent to Gemini by the user
        |                                |
        +---------------+----------------+
                        v
     Report, sport metrics, skeleton, editable MP4 highlight
                        |
                        v
              Clickable local timeline
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
   .\gradlew.bat testDebugUnitTest lintRelease assembleDebug
   ```

4. Install on a connected device:

   ```powershell
   adb install -r app\build\outputs\apk\debug\app-universal-debug.apk
   ```

On macOS or Linux, use `./gradlew` and `/` path separators.

5. Open **Settings** in SportsAI. Create your own key in [Google AI Studio](https://aistudio.google.com/apikey), paste it into the protected field, and tap **Save & test key**. This step is optional. Pose tracking, Batter Lock, local coaching, metrics, skeleton replay, and highlight selection remain available without a key.

## Public, keyless build

SportsAI 2.0 and newer have no build-time Gemini credential path. Every APK is built keyless and uses the local pose pipeline plus sport-specific biomechanics rules until that device's user adds their own key in Settings. The MediaPipe batting model is bundled in the APK and runs locally. Public CI verifies the same no-secret build.

## API-key storage and warning

The user-provided key is encrypted at rest with an app-specific, non-exportable Android Keystore AES key. Its encrypted preferences are excluded from Android cloud backup and device transfer, the complete key is not shown again after saving, and requests send it to Google using the `x-goog-api-key` header.

Client-side storage cannot fully protect an API key on a compromised or instrumented device. Use a dedicated key, monitor quota, and revoke it if the device is lost. A centrally operated production service should still call Gemini through an authenticated backend with quotas, abuse protection, and key rotation.

If a key has ever been posted publicly, revoke it in Google AI Studio and generate a new one.

## Filming tips

- Keep the athlete's full body, hands, and feet visible.
- For batting, use a clip of 30 seconds or less containing one complete swing. Record steadily from a clear side or rear angle; diagonal views may be reported as unconfirmed and receive only common measurements.
- Catchers and umpires may remain in view, but avoid camera cuts, people crossing the batter, and long occlusions.
- Record from the side recommended by the selected sport.
- Use bright, even lighting and a steady camera.
- Avoid other people crossing the frame.
- Use short clips focused on one movement repetition.

## Privacy

Pose detection, multi-person track association, biomechanics rules, and MP4 highlight cutting run locally. Batter Lock uses body landmarks and motion to associate temporary tracks; it does not recognize identity or perform face recognition. When the device owner enables Gemini, their API key and selected image frames are transmitted to Google's Gemini API for analysis; the original video and generated MP4 highlights are not sent by this app. Timeline reports, the encrypted key, and highlight files remain in app-private local storage and are excluded from Android backup/device transfer. Read the complete [Privacy Notes](docs/PRIVACY.md) before testing with another person's video.

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
- [Development record through version 2.3](developement-record.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Privacy notes](docs/PRIVACY.md)
- [Curated swing reference training](docs/REFERENCE_SWING_TRAINING.md)
- [Version 2.3 release notes](docs/RELEASE_NOTES_V2.3.md)
- [Third-party notices](THIRD_PARTY_NOTICES.md)
- [Security policy](SECURITY.md)
- [Contributing](CONTRIBUTING.md)

## License

Licensed under the [Apache License 2.0](LICENSE).

