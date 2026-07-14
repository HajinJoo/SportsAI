# Security Policy

## Reporting a vulnerability

Please do not open a public issue for a suspected vulnerability or exposed credential. Use GitHub's **Report a vulnerability** / private security advisory feature for this repository.

Include:

- Affected version or commit
- Reproduction steps
- Expected impact
- Suggested mitigation, if known

Do not include real athlete videos, API keys, access tokens, or personal data in reports.

## Secrets

- Public builds do not include a SportsAI developer key. Users optionally add their own Gemini key in the app's Settings.
- User keys are AES-GCM encrypted with non-exportable Android Keystore key material, and the encrypted preferences file is excluded from backup and device transfer.
- Never commit `local.properties`, `.env` files, keystores, APKs, API keys, or decrypted settings.
- Treat keys embedded in an Android APK as public and extractable.
- Rotate any credential that has been pasted into chat, logs, screenshots, commits, or releases.
- Version 1.3 and earlier local APKs could contain a build-time key. Never redistribute them; revoke any key used to build one before sharing version 2.0.
- Client-side protection cannot defend a key on a compromised device. A centrally operated production deployment should keep service credentials on an authenticated backend.

## Supported versions

This is an active prototype. Security fixes are applied to the latest `main` branch only.

## Official APK signing

GitHub release APKs from version 2.0 onward are signed with the persistent SportsAI release certificate. Verify its SHA-256 certificate fingerprint before sideloading from anywhere other than this repository:

`97:7C:68:3B:51:AE:C2:4F:AE:E7:1B:E3:D2:6F:DE:13:B1:9A:E6:C7:09:9F:7C:EF:41:38:F6:99:17:36:E8:D9`

The private signing keystore and password are not stored in GitHub.

