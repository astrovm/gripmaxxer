# Gripmaxxer

Gripmaxxer is an Android app (Kotlin + Compose) that uses the **front camera** and **ML Kit Pose Detection** to:
- Track exercise activity and reps for selectable modes:
  - Pull-up
  - Push-up
  - Squat
  - Bench press
  - Dips
- Send **system-wide media play/pause** commands through active MediaSession controllers.
- Show an always-on-top numeric overlay stopwatch while exercise is active.
- Count reps in the selected exercise mode.

Made by **Astro Labs** (`astrolabs`).

## What It Does
- **Exercise active** -> sends `play()` to the active media session (if media control is enabled).
- **Exercise idle** -> sends `pause()` (if media control is enabled).
- Media control can be toggled on/off in app settings.
- Overlay stopwatch behavior:
  - While exercise is active: shows only seconds with 1 decimal (`12.3`) and updates every 100ms.
  - While exercise is idle: hides overlay and resets to `0.0`.
  - On rep event: temporarily shows only rep number (for 800ms), then returns to stopwatch value.
  - Overlay can be dragged and dropped anywhere on screen; its position is remembered.
- While this app screen is open and monitoring is active, the display is kept awake to avoid screen timeout mid-set.
- Exercise mode is selected manually in the app and persisted.

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
- Mode framing tips:
  - Pull-up: front bar framing with shoulders/arms visible
  - Push-up: front view with shoulders/elbows/torso visible
  - Squat: front full-body framing with hips/knees/ankles visible
  - Bench press: side profile focused on shoulder-elbow-wrist
  - Dips: front upper-body framing with shoulders/elbows/wrists visible
- FAST mode is lighter on battery/heat; ACCURATE mode is heavier but can improve robustness.
- Long continuous camera processing can increase battery usage and device temperature.
