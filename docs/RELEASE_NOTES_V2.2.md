# SportsAI 2.2

SportsAI 2.2 improves the offline batting experience by locking the skeleton and all downstream feedback to one continuous swinging batter instead of assuming that the most prominent person is the athlete.

## Install

Download the universal `SportsAI-v2.2.apk` from the GitHub release Assets section. It supports arm64-v8a, armeabi-v7a, x86, and x86_64 devices. Android 10 / API 29 or newer is required. Existing public v2.0 and v2.1 installations can update in place because the APK uses the same permanent SportsAI release certificate.

The final signed APK is 178,769,967 bytes. Verify it with `SHA256SUMS-v2.2.txt`; its SHA-256 is `924EAD0C1067ADE6D54124C6E58F064FBF06CCA9D20DE15D21CF35D624C408AE`.

## Offline Batter Lock

- Bundles MediaPipe Pose Landmarker Full and runs it on the device without a Gemini key.
- Detects up to four 33-landmark people in each sampled batting frame.
- Associates candidates across time instead of picking the largest or most central person.
- Looks for one complete track with reliable joints, two-hand grip evidence, batting posture, and direction-consistent swing-plane motion relative to the torso.
- Uses both image-plane and model-depth movement so a front-angle swing can qualify while a catcher simply standing with both hands together does not.
- Tolerates short pose gaps and reduces false swing evidence from camera translation, zoom, roll, or whole-body movement.
- Requires the best multi-person track to beat the next candidate by a minimum margin.
- Abstains when the batter cannot be separated confidently from a catcher, umpire, or another person.
- Labels batting highlights `Best swing · peak hand speed` instead of `contact` because pose landmarks can identify coordinated hand motion, not bat-ball contact.

An accepted track supplies the offline technique report, sport metrics, 3–4 sentence skill overview, skeleton replay, local highlight selection, editor, timeline history, and optional Gemini frame selection. When Batter Lock abstains, SportsAI shows filming guidance and creates no technique score, highlight, or saved session.

## Performance and filming

Batting uses at least 10 pose samples per second and keeps only coordinates during the full scan. It reopens a capped set of frames for replay instead of retaining every sampled bitmap. Batting clips are limited to 30 seconds; a short, steady, side-on clip with one complete swing and the batter's hands and legs visible gives the best result.

## What remains unchanged

- Pitching and basketball shooting continue to use the ML Kit single-person pose path.
- Gemini is optional and uses the key that the device owner adds in Settings; no SportsAI developer key is bundled.
- Original videos and generated highlight MP4 files are not sent to Gemini.
- Timeline reports, encrypted key data, and highlight files remain in app-private storage.

## Honest measurement boundary

The bundled models detect body landmarks, not equipment or ball flight. SportsAI does not claim to detect or measure the bat, ball, bat-ball contact, gaze direction, radar speed, launch angle, flight path, or physical distance. Bat Speed Potential and Pitch Speed Potential are pose-based movement scores. Ball Tracking is a head-stability proxy, not proof of gaze or ball tracking.

## Known limitations

- Severe occlusion, camera cuts, distant athletes, cropped hands or feet, and several people crossing can make Batter Lock abstain.
- A successful lock means one track cleared the app's motion-and-posture gate; it is not a calibrated probability, identity match, or guarantee that every landmark is exact.
- SportsAI remains an educational coaching aid, not a medical, scouting, sensor, or officiating system.

## Release identifiers

- Package: `com.example.sportsai`
- Version: 2.2 (`versionCode` 7)
- Minimum Android version: Android 10 / API 29
- MediaPipe Tasks Vision: 0.10.35
- Pose model SHA-256: `4EAA5EB7A98365221087693FCC286334CF0858E2EB6E15B506AA4A7ECDCEC4AD`
- Expected signing-certificate SHA-256: `97:7C:68:3B:51:AE:C2:4F:AE:E7:1B:E3:D2:6F:DE:13:B1:9A:E6:C7:09:9F:7C:EF:41:38:F6:99:17:36:E8:D9`

Model provenance and test-media attribution are listed in [Third-Party Notices](../THIRD_PARTY_NOTICES.md). Final build, signature, device, delivery, and APK checksum results are recorded in the GitHub release and [development record](../developement-record.md).
