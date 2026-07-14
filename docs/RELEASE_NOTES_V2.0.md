# SportsAI 2.0

SportsAI 2.0 is the first public, release-signed bring-your-own-key Android build.

## Install

1. Download `SportsAI-v2.0.apk` from the Assets section of the GitHub release.
2. On Android 10 or newer, allow installation from the browser or file manager you used to download it.
3. Open SportsAI. The complete on-device pose pipeline and offline coaching work immediately.
4. For optional Gemini coaching, open **Settings**, create your own key through Google AI Studio, paste it, and select **Save & test key**.

If a debug or local SportsAI APK is already installed, Android may require it to be uninstalled first because the public release uses a separate production signing certificate. Uninstalling clears that installation's app-private timeline and settings.

## Highlights

- No SportsAI developer Gemini key is bundled in the APK.
- Add, validate, replace, or remove a user-owned Gemini key from Settings.
- Saved keys use AES-256-GCM with non-exportable Android Keystore key material and are excluded from backup and device transfer.
- Optional multimodal coaching uses Gemini 3.5 Flash; offline coaching remains available without a key or network.
- Baseball pitching, baseball batting, and basketball shooting analysis.
- Sport-specific progress metrics, skill overviews, clickable historical results, and improvement comparisons.
- Focused AI-selected video highlights with immediate ExoPlayer playback and an exact-range clip editor.

## Verification

- Package: `com.example.sportsai`
- Version: 2.0 (`versionCode` 5)
- Minimum Android version: Android 10 / API 29
- APK SHA-256: `8A0125A16F63EDA4889C5D00E8AF2D4923CAEF58BFC79C43063CE4F4BEF16D60`
- Signing-certificate SHA-256: `97:7C:68:3B:51:AE:C2:4F:AE:E7:1B:E3:D2:6F:DE:13:B1:9A:E6:C7:09:9F:7C:EF:41:38:F6:99:17:36:E8:D9`

The APK passed unit tests, release lint, release assembly, zip-alignment, signature verification, credential scans, matching PC/phone checksum verification, and a real Samsung SM-S721W cold-launch test.
