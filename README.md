# Gripmaxxer

Gripmaxxer is an Android workout tracker that uses your front camera to detect activity and count reps/hold time in real time.

## What You Can Do
- Pick an exercise mode and start a workout quickly.
- Track hangs, pulls, pushes, squats, dips, and other bodyweight/bar movements.
- Auto-count reps and timed holds.
- Use optional media control (play/pause) based on your activity.
- Show an optional floating stopwatch overlay.
- Review sessions in `Log` and stats/settings in `Profile`.

## Quick Start (User)
1. Install and open the app on a real Android device.
2. Go to `Workout`.
3. Select your exercise mode.
4. Grant any missing permissions shown on screen.
5. Tap `Start Workout`.
6. Use `Pause/Resume`, `Sets`, and `End` during the session.
7. Check completed workouts in `Log`.

## Permissions (Why They Are Requested)
- `Camera`: required for exercise detection and rep counting.
- `Notification access`: only required if media play/pause control is enabled.
- `Overlay`: only required if floating stopwatch overlay is enabled.

## Notes
- Camera monitoring runs in a foreground service, so a persistent notification is expected.
- Android camera privacy indicator will be visible while tracking is active.
- Overlay visibility can vary on some OEM/system screens.
- Voice cue (hold modes) is enabled by default and can be toggled in `Profile`.

## Tracking Tips
- Keep your full movement in frame.
- Use good lighting and stable phone placement.
- If tracking feels noisy, try adjusting angle/distance.
- Start your media app first if you use media control.

## Build From Source
1. Open the project in Android Studio.
2. Use SDK settings from your environment.
3. Build debug APK:
   - `JAVA_HOME="${JAVA_HOME:-$HOME/android-studio/jbr}" ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}" ./gradlew :app:assembleDebug -x lint`
4. Install/run on device (min SDK 26, target SDK 36).
