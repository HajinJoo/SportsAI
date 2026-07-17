# SportsAI 2.3

SportsAI 2.3 adds a view-routed, on-device batting pipeline. After Batter Lock selects one continuous athlete, the app classifies the clip as side, rear, or unconfirmed and runs only the mechanics that the camera geometry can support.

## Install

Download the universal `SportsAI-v2.3.apk` from the GitHub release Assets section. It supports arm64-v8a, armeabi-v7a, x86, and x86_64 devices. Android 10 / API 29 or newer is required.

Existing public v2.0, v2.1, and v2.2 installations can update in place because this APK uses the same permanent SportsAI release certificate. The final signed APK is 183,505,026 bytes. Verify it with `SHA256SUMS-v2.3.txt`; its SHA-256 is `B870400C135000213574F199522EB74C299C0FCAB2676017496260A7838910F2`.

## View-aware swing analysis

- Classifies reliable multi-frame pose geometry as side, rear, or unconfirmed before applying view-specific rules.
- Side clips evaluate projected front-knee stability, trail-knee drive, and hand travel during the stride.
- Rear clips evaluate spine-angle change, head movement around the rotation axis, and hip-to-shoulder rotation timing.
- Ambiguous camera angles retain common phases and measurements while withholding view-specific issue labels.
- The report shows the inferred view, confidence, usable frame count, and geometric evidence so users can see which rule set ran.

## Structured local evidence

- Segments stance, stride, impact zone, and follow-through around a coordinated two-hand motion peak.
- Runs a separate lightweight MediaPipe EfficientDet-Lite0 task for confidence-gated baseball-bat and sports-ball observations.
- Serializes camera view, phases, measurements, issue codes, and equipment observations into schema-v2 JSON.
- Restores existing schema-v1 history with an unconfirmed view instead of breaking older sessions.
- Uses the new `offline-batter-lock-v3` profile so incompatible analysis generations are not compared as one progress series.

## Optional Gemini coaching

Gemini receives the authoritative local JSON plus up to eight labeled frames. It can write the coaching summary, explain supplied issue codes, and suggest drills, but it cannot add, remove, or replace local measurements, phase boundaries, camera view, or issue codes. Side-view rules cannot be applied to rear-view reports and vice versa.

## Honest measurement boundary

Camera view is an explainable pose-geometry heuristic, not a calibrated probability or separately trained camera classifier. Joint angles are 2D projections. Bat and ball boxes do not establish bat-head angle, sweet-spot contact, launch angle, trajectory, gaze, radar speed, or real-world distance. The impact zone is a motion window and never confirms bat-ball contact.

## Validation

- Full JVM test suite passed.
- Release lint passed.
- Debug, Android-test, and release APK assembly passed.
- A real nine-second game-angle clip passed Batter Lock, object detector execution, four ordered phases, confirmed camera-view routing, routed numeric measurements, equipment summaries, and schema-v2 JSON identity on a Samsung SM-S721W.
- The real-video connected test completed in 78.892 seconds.
- The signed production update installed in place and preserved the original first-install timestamp and app data.

## Release identifiers

- Package: `com.example.sportsai`
- Version: 2.3 (`versionCode` 8)
- Minimum Android version: Android 10 / API 29
- Universal APK size: 183,505,026 bytes
- APK SHA-256: `B870400C135000213574F199522EB74C299C0FCAB2676017496260A7838910F2`
- Signing-certificate SHA-256: `97:7C:68:3B:51:AE:C2:4F:AE:E7:1B:E3:D2:6F:DE:13:B1:9A:E6:C7:09:9F:7C:EF:41:38:F6:99:17:36:E8:D9`

Model provenance, licenses, and connected-test media attribution are listed in [Third-Party Notices](../THIRD_PARTY_NOTICES.md). Architecture and measurement boundaries are documented in [Architecture](ARCHITECTURE.md).
