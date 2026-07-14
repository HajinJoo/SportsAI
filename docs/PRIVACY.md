# Privacy Notes

SportsAI analyzes user-selected sports videos. Developers and testers should understand the following data boundaries before using real athlete footage.

## Data processed on device

- The selected video URI
- Sampled video frames
- ML Kit pose landmarks and confidence values
- Local biomechanics measurements
- Skeleton replay bitmaps held in memory
- AI-selected highlight boundaries
- Generated MP4 highlight clips stored in app-private files
- Full timeline reports stored in app-private files

## Data sent to Gemini

When `GEMINI_API_KEY` is configured and analyzed frames are available, SportsAI sends:

- Up to six selected JPEG-compressed frames
- The selected sport name
- A coaching prompt requesting score, summary, strengths, issues, and tips

The original video file and generated highlight MP4 files are not sent by the current implementation. Google's handling of API requests is governed by the terms and privacy policies applicable to the Gemini API account.

If no key is configured or the request fails, SportsAI uses its local rules engine.

## Local timeline storage

The app stores a JSON file in Android app-private storage containing:

- Sport identifier
- User-selected filming date
- Analysis score, summary, findings, and detection rate
- Sport-specific metric scores and the AI skill overview
- Highlight time ranges and app-private file paths
- The system picker URI and source-video duration, used to re-cut an edited highlight when Android still grants access

It also stores the generated highlight MP4s under the app's private files directory. It does not copy the original video, store API response frames, or persist the full pose timeline.

Users can remove individual timeline sessions from the app; this also deletes that session's generated highlight files. Uninstalling or clearing application data removes app-private timeline data, subject to Android backup behavior configured by the application and device. The original video remains wherever the user selected it from and is not deleted by SportsAI.

## Athlete consent

Only analyze footage you own or have permission to use. Get consent before processing another person's image—especially a child, student, patient, or team member. Avoid publishing screenshots containing faces, names, notifications, or identifiable training locations.

## Production recommendations

Before production deployment:

1. Proxy Gemini requests through an authenticated backend.
2. Publish a jurisdiction-appropriate privacy policy.
3. Add an in-app disclosure before cloud frame processing.
4. Add retention and deletion controls for any future cloud data.
5. Review child privacy, biometric, education, and health-related requirements.
6. Disable or explicitly configure Android backups for sensitive history.

## Disclaimer

SportsAI feedback is educational. It is not medical advice, injury diagnosis, or a substitute for a qualified coach or clinician.

