# Gripmaxxer

Gripmaxxer is an Android app (Kotlin + Compose) that uses the **front camera** and **ML Kit Pose Detection** to:
- Track exercise activity and reps for selectable modes:
  - Dead hang
  - Active hang
  - One-arm dead hang
  - One-arm active hang
  - Handstand hold
  - Plank hold
  - Middle split hold
  - Pull-up
  - Chin-up
  - Muscle-up
  - One-arm pull-up
  - One-arm chin-up
  - Hanging leg raise
  - Push-up
  - Pike push-up
  - One-arm push-up
  - Squat
  - Archer squat
  - Pistol squat
  - Lunge
  - Bulgarian split squat
  - Hip thrust
  - Bench press
  - Dip
- Send **system-wide media play/pause** commands through active MediaSession controllers.
- Show an always-on-top numeric overlay stopwatch while exercise is active.
- Count reps in the selected exercise mode.
- Provide a simple workout shell with bottom tabs: `Log`, `Workout`, `Profile`.

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
- Workout tab includes a minimal pre-session picker:
  - Defaults to the first trackable mode on first use, then remembers your last used mode
  - Select one camera-trackable exercise mode
  - Prompts for any missing required permissions for enabled features
  - Tap `Start Workout`
- Active workout runs in a fullscreen camera tracker with live HUD:
  - Exercise mode
  - Elapsed workout time
  - Current set reps/time
  - Completed set count
  - Controls: `Pause/Resume`, `End`, and `Sets` editor
- Sets are auto-created on activity end (`active -> idle`) from camera events.
- Set correction (edit/delete) is available both during tracking and in `Log` session detail.
- Workouts with zero sets are discarded when ended (not saved to Log).
- Log entries can be deleted directly from the `Log` feed.
- Log tab shows completed sessions feed, calendar aggregation, and editable detail.
- Profile stats are separated by exercise mode (not global across all exercises).
- Profile tab shows essential stats/settings (media control, rep sound, overlay, camera preview).
- App theme defaults to Black/White and supports additional palettes in Profile settings (Black/Pink, Black/Blue, Black/Red, Black/Green, Black/Purple, Black/Orange, Windows 98 with classic beveled controls).

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
4. Start playback in your target app (YouTube, TikTok, browser, etc.).
5. Open `Workout` tab and grant required permissions when prompted:
   - Camera permission
   - Notification access (if media control is enabled)
   - Overlay permission (if overlay is enabled)
6. Select an exercise mode and tap **Start Workout**.
7. During tracking, use **Pause/Resume** or **End**, and open **Sets** to edit/delete finalized sets.

## Troubleshooting
- Start media playback first in the target app before monitoring.
- If `play()` does not work for a specific app/player, `pause()` may still work.
- Mode framing tips for auto-track:
  - Dead hang: bar framing with straight elbows visible
  - Active hang: bar framing with bent elbows and upper body visible
  - One-arm dead hang: keep support shoulder/elbow/wrist fully visible
  - One-arm active hang: keep support shoulder/elbow/wrist fully visible
  - Handstand hold: works for wall-assisted and freestanding; keep hands/shoulders/hips/legs in frame
  - Plank hold: side angle works best; keep shoulders/hips/legs visible
  - Middle split hold: keep hips, knees, and ankles fully visible in a wide straddle
  - Pull-up: front bar framing with shoulders/arms visible
  - Chin-up: front bar framing with shoulders/arms visible
  - Muscle-up: include bar + full upper body with both wrists visible through transition
  - One-arm pull-up: keep support-side arm and torso centered in frame
  - One-arm chin-up: keep support-side arm and torso centered in frame
  - Hanging leg raise: front bar framing with shoulders/hips/knees visible
  - Push-up: front view with shoulders/elbows/torso visible
  - Pike push-up: keep hips and shoulders fully in frame from a side/front angle
  - One-arm push-up: side/front angle with support arm, shoulder, and hip visible
  - Squat: front full-body framing with hips/knees/ankles visible
  - Archer squat: keep a wide stance in frame so both knees and ankles remain visible
  - Pistol squat: full-body framing with support-side hip/knee/ankle visible
  - Lunge: full-body framing with both knees and ankles visible
  - Bulgarian split squat: keep both ankles and knees visible in split stance
  - Hip thrust: side angle with shoulders/hips/knees visible is most reliable
  - Bench press: side profile focused on shoulder-elbow-wrist
  - Dip: front upper-body framing with shoulders/elbows/wrists visible
- FAST mode is lighter on battery/heat; ACCURATE mode is heavier but can improve robustness.
- Long continuous camera processing can increase battery usage and device temperature.
