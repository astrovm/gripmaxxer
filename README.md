# Gripmaxxer

Android workout tracker that watches you through the front camera and counts for you.

Hang from a bar and it times the hang. Do pull-ups and it counts them. Every set is saved on its own.

## 🏋️ What it does

- Tracks dead hangs, active hangs, pull-ups, chin-ups, hanging leg raises, push-ups, squats and dips.
- Saves a set when you let go of the bar, or after a few seconds of rest on the floor.
- Mixes exercises in one workout. Switch any time from the live screen.
- Plays your music or video while you're in a set and pauses it when you stop.
- Shows a floating timer over other apps, so you can watch something while you hang.
- Times your rest between sets.
- Keeps the workout clock and a **Finish** button in the notification.
- Beeps on each rep and reads hold times out loud every 10 seconds.
- Lets you add, fix or delete sets by hand, also in past workouts. Deleted a set by mistake? Tap **Undo**.
- Keeps your history and personal bests, and shows your best and last time while you train.

No account. Your workouts stay on the phone.

## 📱 Using it

1. Prop the phone up so your whole body is in frame, hands included.
2. Pick an exercise in **Workout** and tap **Start**.
3. Train. Switch exercises with the chips under the camera.
4. Tap **Finish**. The workout shows up in **History**.

The camera keeps running with the app in the background, so you can open YouTube or anything else while you train.

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
./gradlew :app:testDebugUnitTest :app:koverVerifyDebug :app:assembleDebug
```

## 🧭 How the code is laid out

- `tracking/`: plain Kotlin, no Android. Turns body poses into reps and sets. Start here to tune detection.
- `camera/`: front camera and ML Kit pose detection.
- `service/`: keeps the camera on while a workout is open.
- `data/`: Room database for workouts and DataStore for settings.
- `media/`, `feedback/`: media control, sounds, voice and the floating timer.
- `ui/`: Compose screens.
