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

- Never commit `local.properties`, `.env` files, keystores, APKs, or API keys.
- Treat keys embedded in an Android APK as public and extractable.
- Rotate any credential that has been pasted into chat, logs, screenshots, commits, or releases.
- A production deployment should keep Gemini credentials on an authenticated backend.

## Supported versions

This is an active prototype. Security fixes are applied to the latest `main` branch only.

