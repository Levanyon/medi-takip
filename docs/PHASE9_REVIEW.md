# Phase 9 — Lock Screen & Privacy

Accepted baseline: `17460f47186b6f4f5e5799253519ee88830b2a3b`.
Branch: `ui/premium-redesign-v2`. Initial working tree clean; local origin comparison
was 0 behind / 8 ahead. No commit, push, fetch, Phase 10, or WSL operation is included.

## Audit and scope

Inspected Reminders/ReminderPlan, receivers, notification channels, exact-alarm fallback,
boot/time/timezone/package-replacement handling, MainActivity, Home, AppViewModel,
Preferences, repository snapshots/completions, Schedule.day, Medication Detail's next-dose
helper, Settings, permissions, manifest, backup settings, and reminder/UI/screenshot tests.
Reviewed Phase 8's report, package repository integration and scanner navigation boundaries.

Room schema, database/repository writes, package/scanner/catalog code, prescription and
history rules, recurrence generation, backup format, dependencies and manifest are unchanged.
The prior direct TAKEN broadcast write was removed as explicitly required by this phase.

## Approved design

[Figma Phase 9 board](https://www.figma.com/design/XXHVX8smUwa5kVRcsfzm0d?node-id=31-161),
page `31:104`, notification component `31:160`, switch `31:109`, Settings `31:421` / `31:480`.
Read design context and board screenshot before editing; reviewed light/dark Settings context.

Settings reuses MediTheme, typography roles, SettingsSection, surface tokens and responsive
content width. The approved back, pill, eye/eye-off, pin, check and light/dark switch artwork
was downloaded and converted using the existing Android Svg2Vector workflow and bundled JDK.
All assets are local vectors, with original 18/20dp icon and 46×28dp switch dimensions.
The switch artwork sits inside a labelled, full-width accessible switch row; it is not
a 28dp touch target. Visibility options expose RadioButton/selected semantics and stack at
large text/narrow widths. Preview content uses real data, or a truthful empty state.

There is no custom lock screen, wallpaper, system clock, foreground service or ongoing loop.
Android owns notification chrome. The preview reflects the approved information hierarchy;
absolute scheduled date/time replaces a static countdown to avoid stale remaining-time claims.

## Architecture and preferences

`NextDose.forMedication` is the existing Medication Detail selector moved unchanged into
domain code. It retains current-revision, treatment, archive and handled-state checks and
generates occurrences only through Schedule.day. `NextDose.next` selects the earliest of
those real occurrences across active people/medications. Like Today and Medication Detail,
today's earliest unhandled overdue dose remains the focus until handled or the date rolls over.
Future starts and weekly recurrence are handled by the same existing search rule.

`LockScreenNotifications` supplies content, Android visibility, the status channel and
activity PendingIntents. It does not schedule doses or write business data. Reminders.rebuild
invokes it with the existing transactional repository snapshot and clock.

Device-local `LockScreenSettings` lives in the existing `settings` SharedPreferences file:

| Key | Default | Meaning |
| --- | --- | --- |
| `lockVisible` | true | Request lock-screen visibility |
| `lockDetailed` | false | Gizli by default |
| `lockOngoing` | true | Post the ongoing next-dose status |
| `lockTakenAction` | false | Show an app-opening İçildi action; never disable unlock safety |

These choices are not added to medical backups and are not overwritten by restoring one.
The existing AppSettings remains unchanged. Operational `nextReminderEvent` records the
real next reminder for the health screen; `nextAlarm` remains the actual alarm wakeup and
delivery lower bound. This prevents a silent date-rollover refresh from being presented
as an invented medication reminder.

## Lifecycle and time

Existing AppViewModel.perform/completion refreshes cover person/medication edits, archives,
restores, treatment edits, scanner-created medication, taken/skip/undo and settings changes.
Existing MainActivity.onResume and permission/settings-return hooks cover startup/resume.
ReminderReceiver continues to cover alarm execution, reboot, time/timezone changes, package
replacement and exact-alarm access changes. Restore continues through Reminders.restoreData.

The same existing alarm-clock + inexact safety-alarm path also considers the next midnight
when today's unhandled dose is displayed. It performs a silent refresh, not a new dose event.
No separate scheduling engine, periodic worker, timer or polling loop was introduced.
The status has an Android timeout at the end of its occurrence's local date, so a delayed
midnight alarm cannot leave yesterday's notification indefinitely visible.

No valid occurrence, disabled persistent status, disabled app reminders, denied notification
permission, or blocked status channel cancels the status. Master lock-screen visibility OFF
does not cancel an otherwise eligible shade status. Taking/skipping a dose advances to the
next real occurrence; undo restores eligibility. Archives/stops/deletions remove eligibility.
Timezone/DST conversion remains Occurrence.instant / java.time, matching existing scheduling.
All text uses current person identity, real revision names and the actual planned date/time.

## Privacy and channels

| Setting | Requested Android visibility | Content |
| --- | --- | --- |
| Master OFF | `VISIBILITY_SECRET` | Eligible ongoing status remains in unlocked shade |
| Gizli | `VISIBILITY_PRIVATE` | Sıradaki ilaç + safe scheduled date/time; no name/dose |
| Ayrıntılı | `VISIBILITY_PUBLIC` | Real medication and person; safe publicVersion also supplied |

Existing visible dose, renewal and low-stock reminders are updated silently when privacy/actions
change; Gizli content and grouped summaries contain no medication/person identity. Renewal uses
“Reçete hatırlatması” / “Yaklaşan bir reçete yenileme hatırlatman var.”; low stock uses
“Stok azalıyor” / “Bir ilaç için stok uyarısı var.”. Detailed content is rebuilt from the current
repository snapshot, never cached sensitive strings. The master also applies to renewal/stock/test
notifications and retains VISIBILITY_SECRET when OFF.
Every medication group summary has a redacted public version. Android/OEM/system/channel
settings remain authoritative; the app cannot override them or relax an existing user's channel policy.

The existing high-importance `medication` channel is retained without altering sound/vibration
configuration. New stable channel `next_dose` uses IMPORTANCE_LOW, no sound/vibration or badge.
Status ID 6 is stable, uses setOngoing(true), setOnlyAlertOnce(true), and BigText for long names.
Normal FIRST/RETRY/SNOOZE delivery and dispatch deduplication remain ReminderPlan's responsibility.
No separate “approaching dose” timing rule was added.

## Quick-action security

The optional action is an immutable activity PendingIntent with a distinct occurrence URI.
MainActivity handles both initial and new intents, passes the key to Home, and displays a
confirmation for the actual current occurrence. Navigation itself never records a dose.
The activity does not use showWhenLocked, turnScreenOn, keyguard dismissal or full-screen intents.
API 31+ additionally requests setAuthenticationRequired(true).

The confirmation resolves the key from current Snapshot/Schedule.day, rejects handled,
archived/stopped/missing entries and malformed keys, and checks KeyguardManager at the actual
click. Only an unlocked explicit confirmation calls Home's existing markTaken → AppViewModel.taken
→ repository completion flow (including existing stock/undo behavior).

ReminderActionReceiver accepts only the existing SNOOZE action. Old TAKEN PendingIntents are
inert after upgrade. There is no direct notification/receiver write to medication history,
people, medication or treatment data. Snooze changes only existing reminder preferences.

References checked against official Android documentation:
[action authentication](https://developer.android.com/reference/android/app/Notification.Action.Builder#setAuthenticationRequired(boolean)),
[notification privacy](https://developer.android.com/develop/ui/views/notifications#lockscreenNotification),
[Android 14 ongoing dismissal](https://developer.android.com/about/versions/14/behavior-changes-all#non-dismissable-notifications).

## Validation

Native Windows PowerShell; `. ./tools/env.ps1`; bundled JDK 17 / cached Gradle; sequential
Gradle commands only. The sandbox process launcher initially failed before command execution;
read/build commands ran with explicit escalation. No Java/JDK process was manually killed.

Focused post-fix run: 75 tests passed, zero failures/errors/skips, including all 34 Phase 9
tests (24 notification/domain/platform, 4 Settings/security interactions, 6 screenshot and
accessibility cases), 13 reminder integration and 4 shared next-dose regressions, plus the
existing Settings regression cases. No existing assertion was relaxed. The former test that
required direct broadcast history mutation was replaced with the required inert-legacy-action
assertions; the other reminder tests explicitly select detailed/action-enabled mode and disable
the separately tested ongoing status so their existing delivery assertions remain intact.

The first full run completed normally: 707 tests, 701 passed / 6 failed, no daemon loss.
Traces identified Room observers from an unnecessary main-Settings Snapshot collection
surviving test database teardown (Illegal connection pointer / UncaughtExceptionsBeforeTest).
Collection is now scoped to the open lock-screen preview, which actually needs medication data.
All six affected cases passed in the post-fix run without test changes. A further health-time
test verifies that the silent midnight wakeup is not displayed as a medication reminder.

Phase 9 renders: 18 PNGs under ignored `app/build/screenshots/phase9` (private, detailed and
actions × light/dark × 390dp/100%, 320dp/200%, 1200dp/100%). Native tests check labelled,
minimum-48dp controls, selected radio state, scrolling/reachability and long real identity text.
Light/dark 390dp and large-font long-text renders were visually reviewed against the Figma board.
This is semantic/layout/render verification, not a pixel-baseline or physical TalkBack audit.

`git diff HEAD --check` passed. Source manifest and all protected-layer diffs are empty.
The merged debug manifest contains only CAMERA, POST_NOTIFICATIONS, RECEIVE_BOOT_COMPLETED,
SCHEDULE_EXACT_ALARM (maxSdk 32), USE_EXACT_ALARM and the AndroidX internal
`com.levanyon.meditakip.debug.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`.
No network, storage, contacts, location, accessibility or foreground-service permission was added.
Build/screenshots/tool outputs remain ignored; none are tracked under app/build, build or .tools.

Final validation on 2026-10-10:

- Full `testDebugUnitTest` with `MEDITAKIP_SCREENSHOTS=1`: **708 passed, 0 failures,
  0 errors, 0 skipped**; BUILD SUCCESSFUL (1m 51s).
- All **256 screenshot test cases** passed, including 6 Phase 9 cases / 18 Phase 9 PNGs.
- Existing **68 AccessibilityTest** cases passed, plus the six Phase 9 responsive/target checks.
- Phase 8 ScannerFlowTest: **11 passed**; ScannerScreenshotTest: **24 passed**.
- `assembleDebug`: BUILD SUCCESSFUL; APK at `app/build/outputs/apk/debug/app-debug.apk`
  (45,569,356 bytes), ignored by Git.
- `lint`: BUILD SUCCESSFUL; **0 errors, 10 existing warnings** (7 dependency-update notices,
  target API, scanner vector dimensions, unused scanner string). No Phase 9 finding.
- Final `git diff HEAD --check`: passed. Merged-manifest permissions rechecked; no permission diff.
- Git: **8 modified tracked files, 18 added/untracked files, nothing staged**, no deletions.
  HEAD remains the accepted baseline; local origin comparison remains **8 ahead / 0 behind**.
  No commit or push. No Phase 10 work.

Evidence is retained in ignored `build/phase9`: focused/post-fix/full JUnit reports,
`full-suite-final.log`, `build.log`, `lint.log`; current HTML/XML reports remain in app/build/reports.
No known unresolved Phase 9 software defect remains after these checks. Physical-device QA below
is still required; automated tests do not establish OEM/secure-lock-screen behavior on real hardware.

## Privacy audit correction — 2026-10-11

The final read-only audit found a certification blocker: Gizli renewal notifications still
displayed medication names, low-stock notifications displayed medication/person identity,
and privacy refresh rewrote dose notifications but left renewal/stock content detailed.

The narrow correction is confined to `Reminders.kt`: two private content helpers are used
both when posting and when refreshing renewal/stock notifications. Gizli uses the exact generic
title/body listed above. Ayrıntılı retains the existing string resources, including both stock
days-left and quantity fallback formats. Refresh resolves renewal events and low-stock items
from the current repository snapshot. Missing items are canceled rather than retained with stale
content. Existing dose, summary and next-dose refresh paths remain in use. Public versions remain
identity-free; protection does not depend solely on VISIBILITY_PRIVATE. Master OFF still requests
VISIBILITY_SECRET and keeps otherwise eligible notifications in the shade.

No Room/schema, scanner, catalog, package tracking, stock math/threshold, scheduling or business
mutation code was changed. Regression tests compare complete snapshots (including package data,
prescription/treatment/person fields and populated TAKEN/SKIPPED history), dispatch records and
low-stock notification bookkeeping across privacy switches. All remain unchanged. Restoring
Ayrıntılı is tested after repository person/medication names and stock quantity change, proving
that presentation uses current data rather than cached sensitive strings.

Validation with native Windows PowerShell and `. .\tools\env.ps1`:

- Added **8 regression tests** to `Phase9NotificationTest.kt`; no existing test was weakened.
- `./gradlew.bat --offline testDebugUnitTest --tests '*Phase9NotificationTest' --tests '*ReminderIntegrationTest'`:
  **45 passed, 0 failures, 0 errors, 0 skipped** (32 Phase 9 notification tests, 13 integration tests).
  An intermediate new quantity-fallback assertion had incorrect expected wording; it was corrected
  to the existing resource text and the full focused selection passed afterward.
- `./gradlew.bat --offline assembleDebug lint`: **BUILD SUCCESSFUL** (42s).
  Debug APK produced; lint **0 errors, 3 existing warnings**: OldTargetApi, UnusedResources,
  VectorRaster. The earlier dependency-update warnings were not emitted by this offline run.
- `git diff HEAD --check`: passed.
- Git remains **8 modified tracked files, 18 untracked files, nothing staged, no deletions**,
  including the pre-existing uncommitted Phase 9 implementation. Only `Reminders.kt`,
  `Phase9NotificationTest.kt` and this review were edited for this correction.
- `git rev-list --left-right --count origin/ui/premium-redesign-v2...HEAD`: **0 / 8**
  (0 behind / 8 ahead of the local origin ref). HEAD remains
  `17460f47186b6f4f5e5799253519ee88830b2a3b`.
- The 708-test suite was not rerun. No Java/Gradle process was manually killed.
  No commit, push or Phase 10 work was performed.

No known Phase 9 notification-content privacy defect remains after this correction and focused
verification. Physical-device/OEM lock-screen QA below remains outstanding.

## Files

Modified:

- `app/src/main/java/com/levanyon/meditakip/MainActivity.kt` — occurrence intents.
- `app/src/main/java/com/levanyon/meditakip/data/Preferences.kt` — local privacy and reminder display time.
- `app/src/main/java/com/levanyon/meditakip/reminders/Reminders.kt` — lifecycle/privacy/security integration.
- `app/src/main/java/com/levanyon/meditakip/ui/AppViewModel.kt` — preference access through existing perform flow.
- `app/src/main/java/com/levanyon/meditakip/ui/Home.kt` — occurrence confirmation route.
- `app/src/main/java/com/levanyon/meditakip/ui/MedicationComponents.kt` — shared selector delegation.
- `app/src/main/java/com/levanyon/meditakip/ui/Settings.kt` — Phase 9 entry and page.
- `app/src/test/java/com/levanyon/meditakip/data/ReminderIntegrationTest.kt` — safe-action requirement and explicit delivery-test preferences.

Added:

- `app/src/main/java/com/levanyon/meditakip/domain/NextDose.kt`
- `app/src/main/java/com/levanyon/meditakip/reminders/LockScreenNotifications.kt`
- `app/src/main/java/com/levanyon/meditakip/ui/LockScreenSettings.kt`
- `app/src/main/java/com/levanyon/meditakip/ui/NotificationDoseConfirmation.kt`
- `app/src/main/res/drawable/ic_p9_{back,check,eye,eye_off,pill,pin,switch_off,switch_off_dark,switch_on,switch_on_dark}.xml` (10 files).
- `app/src/test/java/com/levanyon/meditakip/data/Phase9NotificationTest.kt`
- `app/src/test/java/com/levanyon/meditakip/ui/Phase9SettingsTest.kt`
- `app/src/test/java/com/levanyon/meditakip/ui/Phase9ScreenshotTest.kt`
- `docs/PHASE9_REVIEW.md`

## Physical-device QA required

- Secure PIN/password/biometric lock-screen behavior on API 26–30 and 31+; cancel unlocking,
  tap both notification body/action, relock before confirmation, app cold/warm/recreated.
- OEM notification rendering, grouping, truncation, BigText, shade behavior and TalkBack.
- Android system hidden-sensitive-content settings, per-channel visibility/importance,
  master OFF, private/detailed mode changes and existing channel policies after upgrade.
- Android 13+ POST_NOTIFICATIONS grant, denial and later revocation; blocked individual channels.
- Reboot, exact-alarm access/fallback, Doze/battery restrictions, manual time/timezone changes,
  midnight and DST transitions; verify silent maintenance does not replay a due alarm.
- Android 14+ may permit dismissal of ongoing notifications. Ongoing is an OS request,
  not a guarantee of permanent visibility; ordinary lifecycle refreshes can restore it.
- Force-stop and OEM restrictions can prevent background delivery until Android permits it.
  Static absolute timing stays truthful; a delayed midnight refresh can cause a temporary
  absence after timeout rather than leaving an outdated day's status.
- No medical advice/dose inference is added; barcode/catalog data never determines dose.
