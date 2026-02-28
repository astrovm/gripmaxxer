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

## Release Process
Use this flow for public releases (optimized and installable APK):

1. Ensure `main` is clean and pushed:
   - `git status --short --branch`
   - `git push origin main`
2. Create release tag before building (versionName is Git-derived):
   - `git tag vX.Y.Z`
   - `git push origin refs/tags/vX.Y.Z`
3. Build optimized release APK:
   - `ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Android/Sdk}}" JAVA_HOME="${JAVA_HOME:-$HOME/android-studio/jbr}" ./gradlew :app:assembleRelease --rerun-tasks`
4. Verify generated metadata:
   - `cat app/build/outputs/apk/release/output-metadata.json`
   - Confirm `versionName` is `X.Y.Z` and matches the release tag.
5. Sign APK (never publish unsigned APK):
   - Input: `app/build/outputs/apk/release/app-release-unsigned.apk`
   - `APKSIGNER="$(ls "$ANDROID_SDK_ROOT"/build-tools/*/apksigner | sort -V | tail -n1)"`
   - `"$APKSIGNER" sign --ks /path/to/release.keystore --ks-key-alias <alias> --ks-pass pass:<storepass> --key-pass pass:<keypass> --out app/build/outputs/apk/release/Gripmaxxer-vX.Y.Z.apk app/build/outputs/apk/release/app-release-unsigned.apk`
   - `"$APKSIGNER" verify --print-certs app/build/outputs/apk/release/Gripmaxxer-vX.Y.Z.apk`
6. Publish GitHub release:
   - `gh release create vX.Y.Z app/build/outputs/apk/release/Gripmaxxer-vX.Y.Z.apk --repo astrovm/gripmaxxer --title "Gripmaxxer vX.Y.Z" --notes "<changes-only notes>"`
7. Verify published APK is signed:
   - `gh release download vX.Y.Z --repo astrovm/gripmaxxer --pattern "Gripmaxxer-vX.Y.Z.apk" --dir /tmp/gripmaxxer-release-check`
   - `"$APKSIGNER" verify /tmp/gripmaxxer-release-check/Gripmaxxer-vX.Y.Z.apk`

Notes:
- `versionName`/`versionCode` are derived from Git in `app/build.gradle.kts`; do not hardcode for releases.
- If multiple local `v*` tags point to the same commit, check `git describe --tags --abbrev=0` before building.
- Use a dedicated release keystore for production distribution (debug keystore only for internal testing).

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
