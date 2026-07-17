# Privacy Notes

SportsAI analyzes user-selected sports videos. Developers and testers should understand the following data boundaries before using real athlete footage.

## Data processed on device

- The selected video URI
- Sampled video frames
- For pitching and basketball shooting, one ML Kit pose with body landmarks and confidence values
- For batting, up to four MediaPipe pose candidates per sampled frame, each containing 33 body-landmark coordinates, relative depth, and one confidence value combining visibility and presence
- Temporary clip-local tracks used to separate a likely swinging batter from a catcher, umpire, or another person
- Local biomechanics measurements
- Skeleton replay bitmaps held in memory
- Locally selected sport-aware highlight boundaries
- Generated MP4 highlight clips stored in app-private files
- Full timeline reports stored in app-private files
- A user-provided Gemini API key, encrypted with device-bound Android Keystore key material

## Data sent to Gemini

When the device owner saves their own Gemini API key in Settings and analyzed frames are available, SportsAI sends:

- The user-provided API key in Google's `x-goog-api-key` request header
- Up to eight JPEG-compressed frames focused on the detected sports action
- The selected sport name
- A coaching prompt requesting score, summary, strengths, issues, and tips

The original video file and generated highlight MP4 files are not sent by the current implementation. Google's handling of API requests is governed by the terms and privacy policies applicable to the Gemini API account.

No developer API key is built into or distributed with SportsAI. If no user key is saved or a request fails, SportsAI uses its local pose models and rules engine. The bundled MediaPipe batting model, Batter Lock selection, sport metrics, skeleton replay, and highlight selection do not require a Gemini request.

## Batter Lock and identity

Batter Lock is not face recognition, person identification, or a biometric identity system. It does not assign a name or durable identity to a detected person. Within one selected clip, it associates poses using body geometry, position, recent motion, and landmark continuity, then scores swing-like evidence to choose one temporary track.

Only an accepted batter track is used for downstream batting feedback. If the batter cannot be separated confidently, the app shows retry guidance and does not create a batting score, generated highlight, or saved history entry. Candidate tracks and the full pose timeline are not written to history.

## API key storage

The complete key is accepted only through the protected Settings field and is not displayed again after saving. It is encrypted with AES-GCM using app-specific, non-exportable key material in Android Keystore. The encrypted preferences file is explicitly excluded from Android cloud backup and device transfer. Removing the key from Settings deletes both its ciphertext and Keystore entry.

This protects ordinary data at rest but cannot make a client-side credential immune to extraction on a rooted, compromised, or actively instrumented device. Users should use a dedicated key, monitor quota, and revoke it through Google AI Studio if the phone is lost or compromised.

## Local timeline storage

For accepted analyses, the app stores a JSON file in Android app-private storage containing:

- Sport identifier
- User-selected filming date
- Analysis score, summary, findings, and detection rate
- Sport-specific metric scores and the AI skill overview
- Highlight time ranges and app-private file paths
- The system picker URI and source-video duration, used to re-cut an edited highlight when Android still grants access

It also stores the generated highlight MP4s under the app's private files directory. It does not copy the original video, store API request or response frames, persist the full pose timeline, or retain the unselected MediaPipe pose candidates.

Users can remove individual timeline sessions from the app; this also deletes that session's generated highlight files. The encrypted key preferences, `session_history.json`, and generated `highlights/` directory are explicitly excluded from Android cloud backup and device transfer. Uninstalling or clearing application data therefore removes those app-private items. The original video remains wherever the user selected it from and is not deleted by SportsAI.

## Athlete consent

Only analyze footage you own or have permission to use. Get consent before processing another person's image—especially a child, student, patient, or team member. Avoid publishing screenshots containing faces, names, notifications, or identifiable training locations.

## Production recommendations

Before production deployment:

1. Proxy Gemini requests through an authenticated backend.
2. Publish a jurisdiction-appropriate privacy policy.
3. Add an in-app disclosure before cloud frame processing.
4. Add retention and deletion controls for any future cloud data.
5. Review child privacy, biometric, education, and health-related requirements.
6. Re-review Android backup exclusions whenever new sensitive file or preference paths are added.

## Measurement limitations

Body-pose landmarks are not equipment or ball detectors. The local pipeline does not measure or prove bat or ball position, bat-ball contact, eye gaze, ball flight, launch angle, radar speed, physical distance, or athlete identity. Pose-based speed and Ball Tracking scores are movement proxies only. Users should not treat these values as sensor, medical, scouting, or officiating measurements.

## Disclaimer

SportsAI feedback is educational. It is not medical advice, injury diagnosis, or a substitute for a qualified coach or clinician.

