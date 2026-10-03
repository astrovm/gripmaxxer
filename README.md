# Gripmaxxer

Android workout tracker that watches you through the front camera and counts for you.

Hang from a bar and it times the hang. Do pull-ups and it counts them. Every set is saved on its own.

## 🏋️ What it does

- Tracks dead hangs, active hangs, pull-ups, chin-ups, hanging leg raises, push-ups, squats and dips.
- Saves a set when you let go of the bar, or after a few seconds of rest on the floor.
- Also counts with the phone in your front pocket, using the motion sensors.
- Mixes exercises in one workout. Switch any time from the live screen.
- Plays your music or video while you're in a set and pauses it when you stop.
- Shows a floating timer over other apps, so you can watch something while you hang.
- Times your rest between sets.
- Keeps the workout clock and a **Finish** button in the notification.
- Beeps on each rep and reads hold times out loud every 10 seconds.
- Lets you add, fix or delete sets by hand, also in past workouts. Deleted a set by mistake? Tap **Undo**.
- Keeps your history and personal bests, and shows your best and last time while you train.

No account. Your workouts stay on the phone.

## 📦 Installing and updating

Download the APK from [Releases](https://github.com/astrovm/gripmaxxer/releases), or add `https://github.com/astrovm/gripmaxxer` in Obtainium to get updates.

Version 0.2.1 switches to a permanent release signing key. If you installed 0.2.0 or earlier, Android requires a one-time reinstall, which removes local workout history. Later releases update normally.

## 📱 Using it

1. Prop the phone up so your whole body is in frame, hands included.
2. Pick an exercise in **Workout** and tap **Start**.
3. Train. Switch exercises with the chips under the camera.
4. Tap **Finish**. The workout shows up in **History**.

The camera keeps running with the app in the background, so you can open YouTube or anything else while you train.

### In your pocket

Put the phone in a front trouser pocket and it switches to the motion sensors on its own. Take it out and the camera takes over again.

- Squats and leg raises count from how far your thigh leans.
- Pull-ups, chin-ups, dips and push-ups count from how far you move up and down.
- On the bar, jump up to it, or just start your first pull-up. Dropping off and landing ends the set.
- Floor sets need at least 2 reps, so sitting down and getting up doesn't count as a squat.

## 🔐 Permissions

- **Camera**: needed to count.
- **Notifications**: shows the running workout, with a **Finish** button.
- **Notification access** (optional): lets Gripmaxxer play and pause other apps' media.
- **Display over other apps** (optional): for the floating timer.

Turn the optional ones on from **Profile**.

## 🛠️ Building

Needs JDK 21 and the Android SDK.

```sh
./gradlew :app:assembleDebug
```

Checks CI runs on every pull request, 100% line coverage included:

```sh
./gradlew :app:ci
```

## 🚀 Releasing

After merging the changes, push a `vX.Y.Z` tag or run **Release** from **Actions** with a new tag on **main**.

The [release workflow](.github/workflows/release.yml) tests, checks 100% line coverage, runs lint and builds the APK. Separate jobs sign it with the permanent key, verify it against a fresh build, and publish it to GitHub Releases. Obtainium picks up the attached APK.

Versions come from Git: the tag sets the version name and the full commit count sets the version code. Every release must contain a new commit so Android sees a higher version code.

The **release** environment needs `RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS` and `RELEASE_KEY_PASSWORD` secrets. The repository variable `RELEASE_CERT_SHA256` pins the signing certificate. Back up the keystore and its passwords outside GitHub and keep using the same key for every release.

## 🧭 How the code is laid out

- `tracking/`: plain Kotlin, no Android. Turns body poses, or pocket motion, into reps and sets. Start here to tune detection.
- `camera/`: front camera and ML Kit pose detection.
- `service/`: keeps the camera, or the motion sensors in a pocket, on while a workout is open.
- `data/`: Room database for workouts and DataStore for settings.
- `media/`, `feedback/`: media control, sounds, voice and the floating timer.
- `ui/`: Compose screens.
