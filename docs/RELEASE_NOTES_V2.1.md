# SportsAI 2.1

SportsAI 2.1 makes Gemini coaching more visually grounded and much less likely to guess about an unseen batter or action.

## Install

Download `SportsAI-v2.1.apk` from the GitHub release Assets section. Existing public v2.0 installations update in place because v2.1 uses the same permanent release certificate.

## What changed

- Finds the strongest sport-specific action before asking Gemini to coach it.
- Reopens up to eight frames from that action at a maximum 1280-pixel dimension instead of relying on 480-pixel replay frames spread across the full clip.
- Labels every image with its frame number, timestamp, temporal region, image size, athlete box, and reliable key-joint tracker context.
- Uses an evidence-only system instruction and detailed batting, pitching, and shooting checklists.
- Requires Gemini to confirm the athlete is visibly present across at least three real submitted frames.
- Requires strengths and issues to cite valid frame labels; unsupported citations are discarded.
- Never treats pose landmarks as proof of a bat, ball, contact event, gaze direction, speed, or ball flight.
- Falls back to clearly labeled on-device pose coaching when visual confidence is insufficient instead of letting Gemini guess.

## Verification

- Package: `com.example.sportsai`
- Version: 2.1 (`versionCode` 6)
- Minimum Android version: Android 10 / API 29
- APK SHA-256: `43468AE1B78EE114D35300AE35CEDB2D23A74614F300DB4AFDDC189FEE7128E9`
- Signing-certificate SHA-256: `97:7C:68:3B:51:AE:C2:4F:AE:E7:1B:E3:D2:6F:DE:13:B1:9A:E6:C7:09:9F:7C:EF:41:38:F6:99:17:36:E8:D9`

The release passed a clean unit-test, release-lint, release-assembly, and Android-test build; two connected-device tests; zip-alignment and signature verification; a credential scan; matching PC/phone checksum verification; and a Samsung SM-S721W cold launch.
