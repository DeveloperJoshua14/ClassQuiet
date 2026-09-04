# Quiet Classes

Quiet Classes is a native Android app that activates Do Not Disturb only when both conditions are true:

1. A saved class is currently in session.
2. The phone is physically inside that class's saved location radius.

This is version 1.2.0. It is designed for a Pixel running Android 17. The app compiles against stable API 36 and targets API 36, which remains forward-compatible with Android 17. Its unchanged package name is `com.joshua.classquiet`, so this release can update earlier ClassQuiet and Quiet Classes builds while preserving their stored classes.

## Download and install

Download the newest signed APK from the [GitHub Releases page](https://github.com/DeveloperJoshua14/ClassQuiet/releases/latest).

1. Under **Assets**, download the file ending in `.apk` (for example, `Quiet-Classes-v1.2.0.apk`). Do not download GitHub's automatically generated **Source code** ZIP files unless you intend to build the app yourself.
2. Open the APK on the Android phone.
3. If Android asks, allow **Install unknown apps** for the browser, Files app, or other app used to open the APK.
4. Complete the six setup requirements shown inside Quiet Classes.

Android may show a Play Protect notice because the APK is installed directly from GitHub instead of Google Play. Each user must grant location, background location, notification, Do Not Disturb, and exact-alarm access on their own device.

> [!IMPORTANT]
> A release APK cannot update a copy installed directly from Android Studio if that copy was signed with Android's debug key. Export the Quiet Classes configuration, uninstall the debug build, install the signed release APK, and then import the configuration. After that one-time switch, later APKs signed with the same release key can update the app normally.

## What is included

- Add, edit, enable, disable, and delete recurring classes.
- Pick weekdays, start and end times, a building or room, and a 50–500 m location radius.
- Use time-only classes, one class location, or several valid locations for the same class.
- Find a street address, save the current GPS location, enter coordinates manually, or drop a pin on an OpenStreetMap map that displays the selected radius.
- Optionally extend DND for 30 seconds beyond the scheduled end.
- Duplicate an existing class and adjust the copy.
- View the schedule in either the normal class list or a seven-day calendar showing time and location.
- Choose one of four DND levels for every class:
  - **Visual only:** notifications remain visible but make no sound or vibration; alarms and media may play.
  - **Alarms only:** calls and notifications are suppressed; alarms can interrupt.
  - **Total silence:** calls, notifications, alarms, vibration, and general media audio are blocked.
  - **Custom:** independently configure alarms, media, system sounds, reminders, events, repeat callers, priority channels, calls, messages, conversations, and Android's visual notification effects.
- Rename the app-owned Android Mode from the Settings tab.
- Show a silent ongoing notification containing the active class and DND level.
- Temporarily mute media when a class activates, unless media is already playing. Restore the earlier level only if Quiet Classes muted it and the user did not override it.
- Export all classes, locations, schedules, DND choices, custom policies, and app settings to one JSON backup; import it on another device.
- Hide the first-run setup card automatically once all six requirements are ready.
- If qualifying classes overlap, apply the most restrictive configured policy.
- Use exact alarms at class boundaries, geofences for arrivals and departures, and a 15-minute WorkManager check to repair missed events.
- Re-register alarms and geofences after reboot, app upgrade, clock changes, and timezone changes.
- Keep schedules and coordinates on-device unless the user explicitly exports a backup. There is no account, analytics service, or custom server.

## Build from source

### Android Studio

1. Extract the project ZIP.
2. In Android Studio, open the inner `ClassQuiet` folder—the folder containing `settings.gradle.kts`.
3. If Android Studio asks for the Gradle JVM, choose **JVM 21** or its bundled JDK 21. Do not use JVM 25 with Gradle 8.13.
4. Let Gradle Sync finish. The first sync downloads Gradle and dependencies and may take several minutes. If prompted, install Android SDK Platform 36 and accept the licenses.
5. On the Pixel, enable Developer options and USB debugging.
6. Connect the phone by USB, approve the debugging prompt, select the Pixel in Android Studio, and press **Run**.

The wrapper pins Gradle 8.13 and verifies the official distribution with SHA-256. The first sync therefore needs working internet access.

### Windows terminal

From the extracted `ClassQuiet` directory:

```powershell
.\gradlew.bat test
.\gradlew.bat installDebug
```

The debug APK is produced at `app\build\outputs\apk\debug\app-debug.apk`.

## Updating from an earlier build

Install version 1.2.0 on the same Pixel. Because the application ID is unchanged and the version code is higher, Android accepts it as an update and retains the existing class list when both APKs were signed with the same key.

Do not uninstall the earlier app first unless Android reports a signing conflict; uninstalling removes its local data. Export a backup before replacing or uninstalling any existing installation.

## First-run setup on the Pixel

The home screen shows six requirements and opens the relevant Android settings:

1. **Precise location:** choose precise rather than approximate location.
2. **Background location:** select **Permissions → Location → Allow all the time** in the app's Android settings.
3. **Do Not Disturb access:** allow Quiet Classes to manage its own Android Mode.
4. **Alarms & reminders:** allow exact alarms so boundary checks occur at the scheduled minute.
5. **Location Services:** keep the phone's system location switch on.
6. **Notifications:** allow notifications so the app can show its silent active-mode status.

Once all six are ready, the setup card disappears. Android intentionally grants these privileges separately; an app cannot silently grant them to itself.

## Back up or transfer the setup

Open **Settings → Backup and transfer** inside Quiet Classes:

- **Export backup** creates a human-readable `.json` file using Android's document picker.
- Move that file by Drive, USB, email, or another method of your choice.
- On the other phone, install Quiet Classes and choose **Import backup**. Import replaces the classes and app settings currently stored on that device, after confirmation.

Android permissions are not transferable and must be granted on each device.

## Recommended first test

1. Stand in the location you want to test.
2. Add a temporary class starting two or three minutes in the future and ending five minutes later.
3. Tap **Use here**, select a 150 m radius, and choose **Visual only**.
4. Lock the phone and wait for the start time. Android should activate the named Mode and Quiet Classes should post its silent status notification.
5. Repeat once with **Total silence** or **Custom**, then delete the temporary class.

For real classes, 150–250 m is a reasonable starting radius for a campus building. Increase it if GPS drift causes missed activation; decrease it where nearby buildings overlap.

## Reliability notes

- Android can batch background geofence transitions by a couple of minutes. Quiet Classes also schedules an exact boundary alarm and requests a current GPS fix at class start, so it does not rely on geofencing alone.
- The app uses the last confirmed geofence state only when Android cannot provide a fresh location. It still turns class mode off at the scheduled end or after a confirmed geofence exit.
- Force-stopping an Android app disables its alarms and receivers until the app is opened again. Swiping it out of Recents does not force-stop it.
- Geofencing depends on Google Play services, which is present on a Pixel.
- The optional 2D map needs internet access and loads Leaflet resources and OpenStreetMap tiles. Address/current-location/manual-coordinate selection remains available without the map.
- Real DND, exact-alarm, notification, and geofence behavior must be tested on a physical phone because plain JVM tests do not provide those Android system services.

## Project structure

- `model/` — class schedule, DND profile, and custom-policy data.
- `data/` — schedules, settings, runtime state, and backup serialization.
- `util/ScheduleEngine.kt` — time-window, overnight-class, distance, and overlap logic.
- `dnd/` — the named Android Mode and `ZenPolicy` integration.
- `notification/` — the active-mode status notification.
- `location/` — current location, geocoding, and geofence registration.
- `audio/` — guarded media muting and restoration for active class sessions.
- `background/` — exact alarms, receivers, WorkManager evaluation, and reboot recovery.
- `ui/` — Jetpack Compose class list, seven-day view, settings, setup, and class editor.

Run local unit tests with `gradlew test`.

## Create a GitHub release

Release builds must be signed. In Android Studio:

1. Increase `versionCode` and update `versionName` in `app/build.gradle.kts`.
2. Run the tests and confirm that the app works on a physical phone.
3. Select **Build → Generate Signed App Bundle or APK**.
4. Choose **APK**, select the `app` module, and choose the `release` build variant.
5. Select the existing Quiet Classes `.jks` keystore and key alias. Always use the same release key so Android can install the APK as an update.
6. Create the APK. Android Studio normally writes it to `app/build/outputs/apk/release/app-release.apk`.
7. Rename the file to include the version, such as `Quiet-Classes-v1.2.0.apk`.
8. On GitHub, open **Releases → Draft a new release**, create a matching tag such as `v1.2.0`, attach the renamed APK under **Assets**, add release notes, and publish it.

Keep the signing keystore and its passwords private, backed up, and outside the repository. Anyone with the key can publish an update that Android trusts as this app, while losing the key prevents future APKs from updating existing installations.

## Publishing note

GitHub Releases are suitable for direct APK distribution and sideloading. Google Play distribution uses an Android App Bundle (`.aab`) and restricts exact-alarm and background-location permissions. Publishing through Google Play would require appropriate store disclosures, permission declarations, and policy review.

Project information is available at [classquiet.nafzigers.us](https://classquiet.nafzigers.us). See the [privacy policy](https://classquiet.nafzigers.us/privacy) and [terms and conditions](https://classquiet.nafzigers.us/terms).
