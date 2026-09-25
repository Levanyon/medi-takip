# Verification — 2026-09-21

Built on Windows with a project-local Microsoft JDK 17.0.18, Gradle 8.11.1, Android SDK 35 and Build-Tools 35.0.0. No WSL.

All development gates were executed in order:

| Phase | Check | Result |
|---|---|---|
| Base app | assembleDebug | Passed |
| Room/repository | testDebugUnitTest, assembleDebug | Passed |
| People/medications | assembleDebug | Passed |
| Today/Taken/navigation | testDebugUnitTest, assembleDebug | Passed |
| Notifications | testDebugUnitTest, lintDebug, assembleDebug | Passed |
| History/settings | testDebugUnitTest, assembleDebug | Passed |
| Final | testDebugUnitTest, lintDebug, assembleDebug, assembleRelease | Passed |

20 tests pass: 8 schedule/date tests, 4 reminder-plan tests, 6 real Room repository tests and 2 Android reminder integration tests using Robolectric. The latter verify next-alarm rebuilding/cancellation and notification dispatch idempotence/Taken cleanup in a simulated Android environment. The test JVM uses English locale to avoid a Conscrypt Windows-library lookup bug under Turkish locale; application locale is unaffected.

Android lint has zero errors. Remaining warnings concern pinned dependency versions and optional Kotlin extension style. The APK manifest has no Internet permission. Cloud backup and automatic transfer are disabled. The debug APK signature was verified with Android apksigner (APK v2).

Release packaging passes, but the generated release artifact is unsigned until the owner supplies a private signing key. No release keystore or credentials were created or committed.

No physical phone/emulator was connected (`adb devices` was empty). Visual interaction, actual OEM background delivery, Android 13 permission prompts and reboot delivery have not been verified on hardware. Follow DEVICE-TESTS.md. Local backup is explicitly deferred to V2.
