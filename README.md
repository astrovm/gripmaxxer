# Gripmaxxer

Gripmaxxer is an Android app (Kotlin + Compose) that uses the **front camera** and **ML Kit Pose Detection** to:
- Detect when you are hanging from a pull-up bar.
- Send **system-wide media play/pause** commands through active MediaSession controllers.
- Show an always-on-top numeric overlay stopwatch while hanging.
- Count pull-up/chin-up reps during hanging sessions.

Made by **Astro Labs** (`astrolabs`).

## What It Does
- **HANGING** -> sends `play()` to the active media session.
- **NOT HANGING** -> sends `pause()`.
- Overlay stopwatch behavior:
  - While hanging: shows only seconds with 1 decimal (`12.3`) and updates every 100ms.
  - While not hanging: hides overlay and resets to `0.0`.
  - On rep event: temporarily shows only rep number (for 800ms), then returns to stopwatch value.
- Rep mode selector is manual (`Pull-up` / `Chin-up`) and only labels the rep counter.

## Why Notification Access Is Required
Android does not allow unrestricted access to active media sessions without privileged permissions.
This app uses a `NotificationListenerService` (`HangNotificationListener`) and requires notification access so `MediaSessionManager.getActiveSessions(...)` can retrieve media controllers and send transport commands.

## Foreground Camera Service + Privacy
Continuous camera analysis runs inside `HangCamService` (a foreground service) with:
- `foregroundServiceType="camera"`
- `FOREGROUND_SERVICE_CAMERA` permission (Android 14+ requirement)
- Persistent notification with stop action

The Android camera privacy indicator will show while monitoring is active.

## Overlay Permission Notes
The stopwatch overlay uses `TYPE_APPLICATION_OVERLAY`, so overlay permission is required (`SYSTEM_ALERT_WINDOW`).
Some system screens or OEM-specific UIs may still hide overlays.

## Build / Run
1. Open in Android Studio (target SDK 35, min SDK 26).
2. Let Gradle sync and install SDK components.
3. Run on a real device (front camera + media app use case).
4. Grant:
   - Camera permission
   - Notification access
   - Overlay permission (if overlay is enabled)
5. Start playback in your target app (YouTube, TikTok, browser, etc.).
6. Tap **Start monitoring**.

## Troubleshooting
- Start media playback first in the target app before monitoring.
- If `play()` does not work for a specific app/player, `pause()` may still work.
- Improve pose detection with better lighting and a front camera angle that keeps shoulders and at least one arm visible; face visibility at the top is optional.
- FAST mode is lighter on battery/heat; ACCURATE mode is heavier but can improve robustness.
- Long continuous camera processing can increase battery usage and device temperature.
