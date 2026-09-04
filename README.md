# ClassQuiet

ClassQuiet is a native Android app that turns on Do Not Disturb only when both conditions are true:

1. A saved class is currently in session.
2. The phone is physically inside that class's saved location radius.

It is designed for a Pixel running Android 17. The app compiles against stable API 36 and targets API 36, which is fully compatible with Android 17/API 37. The package name is `com.joshua.classquiet`.

## What is included

- Add, edit, enable, disable, and delete recurring classes.
- Pick one or more weekdays plus start and end times.
- Find a building by street address, save your current GPS location, or enter coordinates manually.
- Choose a radius from 50 to 500 meters.
- Choose a DND level for every class:
  - **Visual only:** ordinary notifications remain visible but do not make sound or vibrate; alarms and media may play.
  - **Alarms only:** calls and ordinary notifications are suppressed; alarms can interrupt.
  - **Total silence:** all notifications, vibration, alarms, and general audio streams are muted. Audio for an already-active phone call is not muted by Android.
- If qualifying classes overlap, the strictest level wins.
- Exact alarms check at class boundaries, geofences detect arriving or leaving, and a 15-minute WorkManager check repairs missed events.
- Re-registers alarms and geofences after reboot, app upgrade, clock changes, and timezone changes.
- Stores schedules and coordinates locally in Android `SharedPreferences`; there is no account, analytics service, or custom server.

## Open and run it

### Android Studio (easiest)

1. Install the current stable Android Studio.
2. Extract the project ZIP and open the `ClassQuiet` folder—not its parent folder.
3. Let Android Studio perform Gradle Sync. If prompted, install Android SDK Platform 36 and accept the SDK licenses.
4. On the Pixel, enable Developer options and USB debugging.
5. Connect the phone by USB, approve its debugging prompt, choose the Pixel in Android Studio, and press **Run**.

The included Gradle bootstrap pins Gradle 8.13 and verifies the official distribution with SHA-256 before using it. The first sync therefore needs internet access.

### Windows terminal

From the extracted `ClassQuiet` directory:

```powershell
.\gradlew.bat test
.\gradlew.bat installDebug
```

The debug APK is produced at:

```text
app\build\outputs\apk\debug\app-debug.apk
```

If `adb` is configured, `installDebug` installs it directly on the connected phone.

## First-run setup on the Pixel

The home screen shows five requirements and links to the correct Android settings:

1. **Precise location:** choose precise rather than approximate location.
2. **Background location:** open the app's settings, then select **Permissions → Location → Allow all the time**.
3. **Do Not Disturb access:** allow ClassQuiet to manage DND. On recent Android versions, ClassQuiet receives its own system Mode instead of rewriting your personal DND Mode.
4. **Alarms & reminders:** allow exact alarms so boundary checks occur at the scheduled minute.
5. **Location Services:** keep the phone's system location switch on.

Android intentionally asks for these privileges separately; an app cannot silently grant them to itself.

## Recommended first test

1. Stand in the location you want to test.
2. Add a temporary class starting two or three minutes in the future and ending five minutes later.
3. Tap **Use here**, select a 150 m radius, and choose **Visual only**.
4. Lock the phone and wait for the start time. The ClassQuiet status should change after the next time it is opened, and Android should show the ClassQuiet Mode as active.
5. Repeat once with **Total silence**, then delete the temporary class.

For real classes, 150–250 m is a reasonable starting radius for a campus building. Increase it if GPS drift causes missed activation; decrease it where nearby buildings overlap.

## Reliability notes

- Android can batch background geofence transitions by a couple of minutes. ClassQuiet also schedules an exact boundary alarm and requests a current GPS fix at class start, so it does not rely on geofencing alone.
- The app uses the last confirmed geofence state only when Android cannot provide a fresh location. It still turns class mode off at the scheduled end or after a confirmed geofence exit.
- If the user removes DND, background-location, or exact-alarm access, Android prevents the corresponding feature. The setup card will identify the missing requirement.
- Force-stopping an Android app disables its alarms and receivers until the app is opened again. Swiping it out of Recents does not force-stop it.
- Geofencing depends on Google Play services, which is present on a Pixel.

## Project structure

- `model/` — class schedule and DND profile data.
- `data/` — on-device schedule and runtime-state storage.
- `util/ScheduleEngine.kt` — time-window, overnight-class, distance, and overlap logic.
- `dnd/` — Android notification-policy integration.
- `location/` — current-location, geocoding, and geofence registration.
- `background/` — exact alarms, receivers, WorkManager evaluation, and reboot recovery.
- `ui/` — Jetpack Compose home, setup, and class editor screens.

Run local unit tests with `gradlew test`. Real DND, exact-alarm, and geofence behavior must be tested on a physical Android device because those system services are not represented by plain JVM unit tests.

## Important publishing note

This build is suitable for personal sideloading. Google Play restricts use of exact-alarm and background-location permissions. If the app is later published publicly, its store listing, permission declarations, disclosure screens, and possibly its scheduling implementation will need a Play policy review.

