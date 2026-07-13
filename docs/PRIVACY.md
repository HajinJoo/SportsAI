# Privacy Notes

SportsAI analyzes user-selected sports videos. Developers and testers should understand the following data boundaries before using real athlete footage.

## Data processed on device

- The selected video URI
- Sampled video frames
- ML Kit pose landmarks and confidence values
- Local biomechanics measurements
- Skeleton replay bitmaps held in memory
- Timeline entries stored in app-private files

## Data sent to Gemini

When `GEMINI_API_KEY` is configured and analyzed frames are available, SportsAI sends:

- Up to six selected JPEG-compressed frames
- The selected sport name
- A coaching prompt requesting score, summary, strengths, issues, and tips

The original video file is not sent by the current implementation. Google's handling of API requests is governed by the terms and privacy policies applicable to the Gemini API account.

If no key is configured or the request fails, SportsAI uses its local rules engine.

## Local timeline storage

The app stores a JSON file in Android app-private storage containing:

- Sport identifier
- User-selected filming date
- Analysis score
- Analysis summary

It does not store the original video, API response frames, or full pose timeline in history.

Users can remove individual timeline sessions from the app. Uninstalling or clearing application data removes app-private timeline data, subject to Android backup behavior configured by the application and device.

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

