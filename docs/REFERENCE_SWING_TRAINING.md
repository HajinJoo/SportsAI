# Curated Swing Reference Training

SportsAI can build a small positive-only reference profile from local batting clips. The result
measures **similarity to the supplied examples**. It is not an objective best-swing score and it
does not learn the difference between all good and bad swings.

No reference profile currently ships in the app.

## Input

Create this local, Git-ignored directory:

```text
training-data/best-swings/
|-- swing_1.mp4
|-- swing_2.mp4
|-- ...
`-- swing_10.mp4
```

Each file must:

- Be a complete MP4 larger than 1 KiB, not a chat attachment header or shortcut.
- Be 30 seconds or shorter and contain one complete swing.
- Keep the batter's hands, hips, knees, and feet visible.
- Prefer a steady side view without camera cuts or people crossing the batter.
- Be footage that may lawfully be stored and processed for the selected use.

The runner requires the exact numbered filenames and rejects missing or extra MP4 files. Raw
videos are ignored by Git and are deleted from the connected device after every run.

## Rights Mode

The default is `prototype-only`. Use it for private local evaluation when commercial ML rights
have not been documented.

Use `licensed-commercial-ml` only when written permission covers video storage and preprocessing,
creation and retention of derived features and model data, and commercial distribution of the
derived model and outputs. Selecting this argument records the claim in the private report; it
does not itself grant rights.

## Run

Connect and authorize one Android 10 or newer device, then run from the repository root:

```powershell
.\scripts\train-swing-reference.ps1
```

To identify a specific device or a licensed dataset explicitly:

```powershell
.\scripts\train-swing-reference.ps1 `
    -DeviceSerial '<adb-serial>' `
    -ProfileId 'curated-swings-v1' `
    -RightsStatus 'licensed-commercial-ml'
```

For a diagnostic pass on fewer than five clips, set `-ExpectedCount` to the exact number present.
The job will still export clip diagnostics, but it cannot create a reference model until at least
five examples pass every metric gate.

The script builds an isolated `com.example.sportsai.debug` package, installs only the APK matching
the phone's ABI, stages and copies each video into that package's private storage using `run-as`,
runs only `SwingReferenceTrainingInstrumentedTest`, exports the JSON outputs through `run-as`, and
verifies every source SHA-256. Only one extra staged source file exists at a time. The signed
production app and its data are never replaced. Device videos, staging files, and both disposable
debug packages are removed in a `finally` block.

The runner temporarily keeps the phone awake while it remains connected over USB and restores the
exact previous stay-awake setting afterward. Instrumentation emits a status line after each clip so
long batches show progress instead of appearing idle.

Before installation, the runner compares free phone storage with the actual input and APK sizes. It
includes APK staging and installed-size overhead plus Android's device-reported low-storage
threshold, and stops before copying anything when space is insufficient.

## Acceptance Gates

For each clip, the batch job requires:

1. Batter Lock selects one continuous athlete rather than the catcher or umpire.
2. The timeline produces all five offline batting metrics: Hip Rotation, Ball Tracking/head
   stability, Swing Extension, Lower-Body Load, and Bat Speed Potential.
3. At least five clips pass before an aggregate can be calculated.
4. All expected clips pass before the instrumentation job succeeds.
5. Every accepted example scores at least 40/100 against the aggregate. A lower value flags a
   likely outlier, incompatible camera view, or incorrect clip label for manual review.
6. The generated JSON round-trips through the current artifact schema and uses the same offline
   analysis profile as the app.

The trainer uses per-feature medians and median absolute deviation, with a minimum scale floor, so
one unusual example cannot move the center or collapse a feature's variation.

A successful Batter Lock is necessary but not sufficient. For example, a clip can track the batter
through the complete swing while still being rejected when the knees or feet are not visible often
enough to calculate Lower-Body Load. Replace or reframe that clip instead of treating the missing
measurement as poor technique or silently averaging a partial example.

## Outputs

Outputs are written under the Git-ignored `reference-training-output/` directory:

- `swing-reference-profile.json`: aggregate centers, robust scales, profile compatibility, label,
  and accepted-example count. It contains no raw frames, paths, pose timelines, or source hashes.
- `swing-reference-training-report.json`: private per-clip filenames, hashes, tracking diagnostics,
   mechanics metrics, analysis elapsed time, rejection reasons, and reference similarities.

The reference artifact intentionally keeps camera view and handedness as `unverified`. Runtime
analysis has an explainable side/rear geometry heuristic, but that signal is not strong enough to
certify dataset compatibility or handedness for training. Review both properties manually and
train separate profiles when camera geometry makes the examples incomparable.

If instrumentation fails after analyzing clips, the script still attempts to pull the private
report so rejected clips can be replaced.

## Production Promotion

Do not copy the aggregate into app assets until all of these are true:

- Every clip passes the automated gates.
- Camera view and handedness have been reviewed and incompatible groups are separated.
- Commercial ML and derived-model rights are documented.
- A separate held-out set shows that similarity is stable across athletes and recording devices.
- The UI says "Similarity to curated reference swings" and leaves the existing explainable
  mechanics score intact.

Ten positive examples are enough for a prototype reference distribution, not a general batting
quality classifier. A production model needs a larger, diverse, rights-cleared dataset with held-out
evaluation and appropriate negative or outcome-labeled examples.