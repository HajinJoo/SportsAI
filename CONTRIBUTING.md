# Contributing

Contributions are welcome.

## Development setup

1. Install Android Studio, Android SDK 36, and JDK 17+.
2. Fork and clone the repository.
3. Let Android Studio create your machine-local `local.properties`.
4. Run verification before opening a pull request:

   ```powershell
   .\gradlew.bat testDebugUnitTest lintDebug assembleDebug
   ```

## Pull requests

- Keep changes focused.
- Preserve the keyless build, per-device Settings flow, and offline fallback behavior.
- Never add an API key to Gradle, `BuildConfig`, source, tests, screenshots, logs, or fixtures.
- Do not add a sport without dedicated mechanics, filming guidance, and tests.
- Include tests for analysis or persistence changes.
- Include screenshots for meaningful UI changes.
- Do not commit generated builds, private footage, credentials, or machine-specific files.
- Update documentation when behavior or privacy boundaries change.

## Style

- Follow existing Kotlin and Compose conventions.
- Prefer stateless composables and ViewModel-owned state.
- Use semantic theme colors rather than hardcoded text colors.
- Preserve accessible touch targets and content descriptions.

## Responsible feedback

Coaching language should be constructive and should not claim medical certainty. Avoid presenting heuristic scores as professional diagnosis.

