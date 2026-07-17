# Third-Party Notices

SportsAI is licensed under the [Apache License 2.0](LICENSE). The project also uses third-party software, a bundled machine-learning model, and attributed regression-test media. This file records the items most directly associated with the offline pose pipeline; Gradle version catalogs and lock/build output remain the authoritative dependency inventory for a particular build.

## MediaPipe Tasks Vision

- Component: `com.google.mediapipe:tasks-vision:0.10.35`
- Project: [MediaPipe](https://github.com/google-ai-edge/mediapipe)
- Copyright: the MediaPipe authors / Google LLC and contributors
- License: [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)
- Use in SportsAI: on-device Pose Landmarker runtime for multi-person batting inference

The production APK includes a readable MediaPipe/model notice and the Apache 2.0 license under `assets/third_party/`.

## Pose Landmarker Full model

- Bundled file: `app/src/main/assets/pose_landmarker_full.task`
- Model: MediaPipe Pose Landmarker Full, float16, version 1
- Download source: [Google MediaPipe model storage](https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_full/float16/1/pose_landmarker_full.task)
- Model documentation: [Pose Landmarker overview](https://developers.google.com/edge/mediapipe/solutions/vision/pose_landmarker)
- Model card: [BlazePose GHUM 3D](https://storage.googleapis.com/mediapipe-assets/Model%20Card%20BlazePose%20GHUM%203D.pdf)
- Size: 9,398,198 bytes
- SHA-256: `4EAA5EB7A98365221087693FCC286334CF0858E2EB6E15B506AA4A7ECDCEC4AD`
- License: Apache License 2.0
- Modifications: none; the downloaded task bundle is stored under its original filename

The model estimates 33 body landmarks. SportsAI's separate `BatterPoseSelector` logic associates and ranks temporary tracks; the model itself is not represented as a baseball-role classifier or identity model.

## ML Kit Pose Detection

- Component: `com.google.mlkit:pose-detection-accurate:18.0.0-beta5`
- Documentation: [ML Kit pose detection](https://developers.google.com/ml-kit/vision/pose-detection)
- Terms: [Google Developers Site Terms](https://developers.google.com/terms)
- Use in SportsAI: on-device single-person pose detection for baseball pitching and basketball shooting

Google names and trademarks are the property of their respective owners. Their inclusion here does not imply endorsement.

## Connected-test image

The following asset is stored only under `app/src/androidTest/assets/` and is not packaged in the production APK.

- File: `softball_batter_catcher.jpg`
- Original title: “At Bat” (`Softball Batter and Catcher.jpg`)
- Author: Terren Peterson
- Source: [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Softball_Batter_and_Catcher.jpg)
- License: [Creative Commons Attribution 2.0 Generic](https://creativecommons.org/licenses/by/2.0/)
- SHA-1: `5647fd2ff0ddeb5e8232b02eee62040d303bd663`
- Use: connected-device regression that checks whether batter and catcher can be returned as separate pose candidates

No endorsement by the photographer or depicted athletes is implied.

## Optional connected-test video

The optional video excerpt is not committed and is not packaged in the production or Android-test APK. The connected regression runs only when a developer places it in the target app's external-files directory.

- Local test filename: `real-game-batting-9s.mp4`
- Source: [“Ohtani -40 Homerun!!! 大谷翔平40号ホームラン” on Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Ohtani_-40_Homerun!!!_%E5%A4%A7%E8%B0%B7%E7%BF%94%E5%B340%E5%8F%B7%E3%83%9B%E3%83%BC%E3%83%A0%E3%83%A9%E3%83%B3.webm)
- Author: Jiro's Channel
- License: [Creative Commons Attribution 3.0 Unported](https://creativecommons.org/licenses/by/3.0/)
- Modification for the local regression: source seconds 24–33 were trimmed and transcoded to H.264 without audio
- Test-excerpt SHA-256: `E1A5F7D64A41375BD3325A77F676D4CC67658F6D0AA1B06AB3C9DEECBDD2DDC0`

## Other open-source libraries

SportsAI also depends on AndroidX, Jetpack Compose, Kotlin, Kotlin coroutines, Coil, and AndroidX Media3. Their artifact metadata and upstream license files govern redistribution. See `gradle/libs.versions.toml` and `app/build.gradle.kts` for the build's declared components and versions.
