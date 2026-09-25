# MediTakip

The app has four main sections: **Bugün · Kişiler · Geçmiş · Ayarlar**. Medications are managed under each person (Kişiler → person → medication), and Geçmiş shows a person-based date × medication table. Work follows [docs/ROADMAP.md](docs/ROADMAP.md); stable project rules are in [CLAUDE.md](CLAUDE.md); the current code state is described in [docs/AUDIT.md](docs/AUDIT.md). Existing Room data is retained across updates signed with the same key.

Offline native Android medication schedule tracker for families. Package: `com.levanyon.meditakip`. Android 8.0+ (API 26). Kotlin, Compose Material 3, MVVM, Room. No Firebase, network permission, cloud services, ads or analytics. It displays only user-entered schedules and provides no medical advice.

## Features

- Multiple people with notes and initial avatars; add, edit, archive and restore (Arşivle / Arşivden Çıkar).
- Medications under each person with plain-text dose, notes, multiple 24-hour times, weekdays, start/end dates (today or later) and active status; archive and restore.
- Bugün: person selector, that person's timeline for today, progress summary, İçildi and Geri Al.
- Geçmiş: person selector (archived people included) and a date × (medication, time) table; tapping a cell shows its details.
- Local notifications, optional second reminder (15/30/60/120 minutes), Android notification/exact-alarm settings, system/light/dark theme.
- Versioned schedules and UTC completion timestamps persisted by Room.

Schedule changes take effect today. Previous days are preserved; today's completed entries are retained. Archiving a person or medication stops future schedules and retains history; nothing with history is ever hard-deleted. Dates before a medication was first entered are not fabricated as historical schedules. Times follow the phone's current time zone. DST gaps shift forward; repeated local times occur once at the earlier offset.

Local backup export/import is deferred to V2. `data/LocalBackup.kt` defines the boundary and validation requirements. Android cloud backup and automatic device transfer are disabled. Uninstalling or clearing app storage removes records.

## Open and build on Windows

1. Install Android Studio for Windows. Do not use WSL. Open this folder with **File > Open**.
2. In **Tools > SDK Manager**, install Android SDK Platform 35, Android SDK Build-Tools 35.0.0, and Platform-Tools.
3. In **File > Settings > Build, Execution, Deployment > Build Tools > Gradle**, select JDK 17 (or a compatible embedded JDK). The project pins Gradle 8.11.1, AGP 8.9.1 and Kotlin 2.0.21.
4. Let Gradle sync finish. Initial builds require Internet to download development dependencies; the installed app operates fully offline.
5. `local.properties` must point to your SDK, for example `sdk.dir=C:/Users/YOUR_NAME/AppData/Local/Android/Sdk`. Android Studio normally creates this file. It is ignored by Git.
6. Select **Build > Build Bundle(s) / APK(s) > Build APK(s)** (some Studio versions label this **Generate App Bundles or APKs > Generate APKs**), with the **debug** build variant selected.
7. The manually installable file is `app/build/outputs/apk/debug/app-debug.apk`. Use this build command/menu, rather than a Run-only test APK.

From Android Studio's PowerShell terminal:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

This workspace also has an ignored, project-local Windows toolchain in `.tools`. To use it from PowerShell on this machine, dot-source `tools\env.ps1` first:

```powershell
. .\tools\env.ps1
.\gradlew.bat testDebugUnitTest assembleDebug --offline
```

## Release signing

The phone runs the **release** build, signed with MediTakip's own release key: alias `meditakip`, certificate SHA-256 `84:E8:0A:AC:…:87:E9:81:7F` (full value in `docs/RELEASE.md`).

- The key and its password live outside the repository, in `%USERPROFILE%\AndroidKeys\meditakip\`, and are backed up in two places.
- **Do not create a second key.** An update signed with a different key cannot be installed over the app.
- Everything about the key, backups, building and verifying is in [docs/RELEASE.md](docs/RELEASE.md).

```powershell
. .\tools\env.ps1
.\gradlew.bat assembleRelease --offline
```

- The APK is written to `app\build\outputs\apk\release\app-release.apk`.
- Without a configured key, `assembleRelease` stops with an error instead of producing an unsigned APK.
- Debug builds install as a separate app, "MediTakip (debug)" (`com.levanyon.meditakip.debug`), and never replace the real one.
- No Google Play Console or paid account is needed.

## Install on a physical Android phone

1. Build `app-release.apk` as above. For real use, always install the release APK.
2. Connect the unlocked phone to Windows by USB and choose **File transfer** on the phone.
3. Copy the APK to the phone's **Download** folder using Windows File Explorer.
4. Open the phone's Files app, open Downloads, and tap the APK.
5. If prompted, allow **Install unknown apps / Allow from this source** for that Files app, return to the installer, and tap **Install**. You can turn that permission off afterward.
6. Open MediTakip and complete the Turkish welcome screens. Use **Kişiler** to add a person, then open that person and tap **İlaç Ekle**. In **Ayarlar > Bildirim ve Hatırlatmalar**, allow notifications on Android 13+ and optionally allow **Alarm ve Hatırlatıcılar** on Android 12+. Without exact access, Android may delay reminders.
7. Enter a test schedule a few minutes ahead, close the app normally, lock the phone, and verify delivery. Check a reboot and optional retry using `docs/DEVICE-TESTS.md` before relying on your phone's reminder configuration.

Alternative USB debugging installation: enable Developer options and USB debugging, authorize the Windows computer on the phone, then run:

```powershell
& ".tools\android-sdk\platform-tools\adb.exe" devices
& ".tools\android-sdk\platform-tools\adb.exe" install -r .\app\build\outputs\apk\release\app-release.apk
```

Updates signed with the same release key keep the app's data: install them over the existing app **without uninstalling**. The one-time switch from the old debug-signed app to the release app (September 2026) required an uninstall. That was accepted because the app was not in real use yet.

## Reminder behavior and limitations

One next-event alarm is rebuilt after delivery, schedule/settings changes, app resume, boot, app update, clock changes, time-zone changes and exact-permission grant. Exact access is checked each time; SecurityException falls back to an inexact alarm. A separate inexact safety alarm accompanies exact scheduling. Dispatch records prevent repeat delivery. Completing a dose suppresses retry and cancels its visible notification. Recovery deliberately skips elapsed reminders after reboot/time changes and schedules future events; it does not issue a backlog of historical reminders.

Android can delay alarms while idle, especially multiple alarms close together. Force-stop prevents reminders until the app is reopened. A powered-off phone cannot notify. Manufacturer battery restrictions and blocked notification channels can also interfere. No app can bypass these controls. V1 does not promise medical-grade timing.

## Verification

JVM tests cover weekday/time generation, date boundaries, DST, inactive schedules, future starts, retry across midnight and completion suppression. Robolectric tests exercise the real Room database, repository transactions, duplicate Taken, Undo, history-preserving edits, archive behavior and rollback on invalid edits.

Reports: `app/build/reports/tests/testDebugUnitTest/index.html` and `app/build/reports/lint-results-debug.html`. Room's version-1 schema is checked in under `app/schemas`; future schema changes must supply migrations rather than destructive fallback. See `docs/ARCHITECTURE.md` and `docs/DEVICE-TESTS.md`.

Physical-device delivery and reboot tests require a connected device; passing unit tests and a build alone does not verify those behaviors.
