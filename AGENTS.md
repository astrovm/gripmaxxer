# AGENTS.md

## Purpose
Guidance for coding agents working in this repository.

## Project
- App name: `Gripmaxxer`
- Platform: Android (Kotlin)
- Module layout: single module `:app`
- Package name: `com.astrovm.gripmaxxer` (do not rename unless explicitly requested)
- UI: Jetpack Compose
- Camera/ML: CameraX + ML Kit Pose Detection
- Persistence: DataStore Preferences

## Build and Verify
- Preferred command:
  - `JAVA_HOME="${JAVA_HOME:-$HOME/android-studio/jbr}" ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}" ./gradlew :app:assembleDebug -x lint`
- If Android Studio/JDK or Android SDK are installed elsewhere, set `JAVA_HOME` and `ANDROID_HOME` to local paths before running.

## Key Runtime Constraints
- Camera monitoring runs only in foreground service (`HangCamService`) with persistent notification.
- Media control depends on notification access (`HangNotificationListener` + `MediaSessionManager`).
- Overlay stopwatch depends on overlay permission (`SYSTEM_ALERT_WINDOW`).
- Front camera and camera privacy indicator are expected behavior.

## Important Files
- Manifest: `app/src/main/AndroidManifest.xml`
- Service: `app/src/main/java/com/astrovm/gripmaxxer/service/HangCamService.kt`
- Main UI: `app/src/main/java/com/astrovm/gripmaxxer/ui/MainScreen.kt`
- Settings: `app/src/main/java/com/astrovm/gripmaxxer/datastore/SettingsRepository.kt`
- README: `README.md`

## Coding Guidelines
- Keep changes minimal and focused on the request.
- Preserve existing architecture package boundaries:
  - `ui`, `service`, `camera`, `pose`, `hang`, `reps`, `media`, `overlay`, `datastore`
- Avoid destructive git commands (`reset --hard`, checkout file reverts) unless explicitly asked.
- Do not introduce placeholders or TODO-only implementations for requested features.

## Before Finishing
- Run assemble debug successfully.
- Ensure no obvious permission-flow regressions in UI.
- Update `README.md` if behavior or setup steps change.
