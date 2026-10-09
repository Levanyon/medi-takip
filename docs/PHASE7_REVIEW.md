# Phase 7 — V2 data model, treatment lifecycle and prescription renewal

Base: `fb6339e12d572e6a9ff59064828734eb3f9e6a03`, branch `ui/premium-redesign-v2`.
Inspection verified the accepted HEAD and a clean working tree before edits. Changes remain uncommitted.

## Inspection and model decisions

Room was version **2**. Treatment start (`schedules.start`), nullable end (`schedules.end`), active state,
times, weekdays, revision validity and revision sequence already existed. Those columns and their dose
generation semantics are reused. Stock already belongs to medication identity and remains unchanged.
Prescription written/renewal dates and renewal reminder configuration were genuinely missing.

Room is now version **3**. Only `medications` gains these columns:

| Column | SQL type | Existing-row default | Meaning |
| --- | --- | --- | --- |
| prescriptionDate | nullable INTEGER | NULL | Optional written date, epoch day |
| renewalDate | nullable INTEGER | NULL | Optional single renewal/rewrite date, epoch day |
| renewalReminderEnabled | INTEGER NOT NULL | 0 | Explicit opt-in reminder |
| renewalLeadDays | INTEGER NOT NULL | 0 | Days before renewal; zero means the same day |

Prescription metadata is current medication state, independent of dose revisions. There is no recurring
prescription subsystem, medical advice, renewal history, or package/batch entity. No dependency was added.

## Migration and data preservation

`MIGRATION_2_3` performs four additive `ALTER TABLE` statements. `ALL_MIGRATIONS` retains 1→2 and adds
2→3; no destructive fallback or reset exists. The generated schema is `app/schemas/com.levanyon.meditakip.data.MediDatabase/3.json`.
People, medication identity/archive state, all schedule revisions, start/end dates, dose history, dispatches,
and stock values remain untouched. SharedPreferences is not involved in migration.

The production-v2 migration test builds the actual committed v2 schema and seeds finite/indefinite
treatments, active/archived people and medications, stock decimals, taken/skipped history and dispatch state.
It compares every original column before/after, checks Room's schema validation, safe prescription defaults,
history output and unchanged preferences. Existing v1 upgrade coverage now validates the full chain to v3.

## Lifecycle and UI

Add/Edit continue using the same `MedicationEditor`. Existing treatment start/end pickers, no-end clearing,
schedule times/days and active switch remain. Edits keep their existing revision behavior: previous revisions
close and a new revision applies from today; historical dose entries remain attached to their original revision.

The new prescription group uses the existing Phase 4 components and native date pickers. Dates are optional,
load from medication identity, can be updated/cleared, and have no hardcoded production values. A renewal date
without a written date is allowed. The reminder defaults off; lead days are editable when enabled. Clearing
renewal disables its reminder. Disabling/clearing also normalizes an invalid unsaved lead entry so a hidden
field cannot block saving. Valid lead values are retained.

Detail displays the existing treatment start and finite end, or **Sürekli** for a null end. It displays each
prescription date only when present and omits the entire prescription section when both dates are absent.
No Phase 4 visual redesign was performed. Screenshot cases cover light/dark at 390dp, 320dp with 200% font,
and 1200dp; actions are scrollable and retain their minimum touch size.

## Renewal reminders

`RenewalPlan` is pure planning, called by the existing `Reminders.rebuild` under the same mutex. The engine
selects the earliest future dose or renewal and uses the same alarm-clock/inexact fallback and safety alarm.
There is no second alarm engine or background service. An injectable clock supports deterministic tests;
production still reads the current instant and current system timezone on each rebuild.

The trigger is **09:00 local time on renewalDate minus leadDays**, converted using the current zone's rules.
No date, disabled reminder, globally disabled reminders, archived medication/person, inactive/closed latest
revision, ended treatment, or past renewal date produces a renewal reminder. A future treatment can have an
earlier renewal reminder, since obtaining a prescription can precede treatment start.

Every rebuild derives events from current persisted values. Editing/clearing/disabling/archiving removes
the old event; the existing alarm pair is cancelled/replaced and stale visible renewal notifications are removed.
Dispatches use `renewal:<medicationId>:<renewalEpochDay>:<leadDays>` and the existing dispatch table. An event
already dispatched is not repeated. Already-past triggers are not rolled forward or automatically repeated.
Delivery uses the existing scheduled lower bound and 24-hour window, with no install/restore backlog.

Boot, timezone change, clock change, app update, app resume/restart and permission rescheduling continue through
the existing hooks. System-change receivers rebuild without historical delivery. Timezone changes recompute
09:00 locally. Notification permissions, dose retry/snooze behavior and channel policy remain unchanged.

Renewal notifications use **ID 5** and their dedicated renewal tag (dose ID 1, summary 2, test 3, stock 4).
They use the existing medication channel, private visibility and a public version containing only the app name.
The content intent has a distinct renewal URI and medication ID, opening the real medication detail; Edit
then uses the existing editor. It handles cold starts and new intents. There are no taken/skipped/snooze
buttons for renewals. No renewal writes a dose-history entry or adjusts stock.

## Validation and backup compatibility

Prescription dates use the existing backup-supported range 2000-01-01 through 2200-01-01. Renewal cannot
precede written date when both are present. Lead days must be a nonnegative Int; no fixed medical interval
is assumed. Repository validation precedes revision writes in the existing transaction. Treatment validation
also rejects out-of-range dates and retains end-before-start rejection.

Backup envelope format remains **1**; `schemaVersion` is now **3**. Export includes all four fields. Restore
accepts prior schema **2** with null/off/zero defaults and schema **3** with strict required field/type/range
validation. Future versions are rejected explicitly. The existing safety copy, transactional data replacement,
reference/history validation and reminder cleanup are preserved. Device-local dispatch state remains excluded.
Older app versions correctly reject schema-3 exports rather than silently dropping fields.

## Verification

Final run: **607 tests passed, 0 failures, 0 errors, 0 skipped**. This adds 38 cases to the accepted
569-case suite. `assembleDebug` passed; `lint` passed with **No issues found**. Combined result:
**BUILD SUCCESSFUL**. `git diff HEAD --check` passed. No tests were weakened or failures suppressed.

| Coverage | Result |
| --- | --- |
| Existing v1 migration + production-v2 prescription migration | 4 passed |
| Prescription validation/lifecycle/planning | 9 passed |
| Prescription persistence/atomic save/old-new backup compatibility | 5 passed |
| Existing backup codec/restore safety | 12 passed |
| Existing reminder domain/plan/platform integration | 25 passed |
| New renewal alarm/delivery/reschedule integration | 4 passed |
| New prescription editor/detail UI | 6 passed |
| Renewal detail/edit navigation | 1 passed |
| New prescription native screenshots/layout | 12 passed |

All existing accessibility, layout, Phase 1–6 screenshot and behavior tests ran in the full suite.
The 12 new native render cases produce 18 images under ignored `app/build/screenshots/phase7`.
Light/dark prescription/detail layouts and 320dp/200% font controls were visually inspected, including
the corrected lead-time label and reachable Save action. No generated PNG/log/APK is tracked.

Evidence: `build/phase7-final.log`, `app/build/test-results/testDebugUnitTest`,
`app/build/reports/lint-results-debug.txt`. APK: `app/build/outputs/apk/debug/app-debug.apk`.
No known unresolved Phase 7 defect remains. Git status: 17 modified and 11 untracked source/schema/test/doc
files, nothing staged; accepted HEAD unchanged, branch 6 ahead / 0 behind its local origin tracking ref.

Native Windows PowerShell command:

```powershell
. .\tools\env.ps1
$env:MEDITAKIP_SCREENSHOTS = '1'
.\gradlew.bat --offline assembleDebug testDebugUnitTest lint '-Pkotlin.incremental=false' --continue --console=plain
git diff HEAD --check
```

Intermediate findings: Kotlin incremental output retained callers of the previous entity constructor; a
non-incremental compile corrected the stale binaries. A navigation assertion matched stock and lead values
both equal to 5; it now targets the labeled lead field. The first full suite exposed the new receiver test's
direct invocation without an Android PendingResult; the test now sends an actual Robolectric broadcast and
awaits completion. No unrelated history production code or assertion was changed. The lead label was shortened
after inspecting 200% font screenshots. No warning suppression was added.

## Limits and scope

No physical-device/emulator delivery or real reboot has been claimed; alarm, notification and broadcast integration
is exercised with Robolectric. OEM power restrictions and user notification settings still govern actual delivery.
An already-past reminder time is not caught up merely because a prescription is entered late; users can choose
another date/lead time. Renewal is a one-off date, with no automatic recurring interval or separate renewal history.

Stock math, dose-history semantics, Today/People behavior, onboarding, launch animation, theme system and permission
flows are unchanged. No camera permission, barcode/DataMatrix/GTIN/catalog/scanner/package integration, biometric lock,
database encryption, backup encryption redesign, or release work was started. No real user data was used in tests.

## Exact files changed

Modified:

```text
app/src/main/java/com/levanyon/meditakip/MainActivity.kt
app/src/main/java/com/levanyon/meditakip/data/Backup.kt
app/src/main/java/com/levanyon/meditakip/data/Database.kt
app/src/main/java/com/levanyon/meditakip/data/Repository.kt
app/src/main/java/com/levanyon/meditakip/domain/Schedule.kt
app/src/main/java/com/levanyon/meditakip/reminders/Reminders.kt
app/src/main/java/com/levanyon/meditakip/ui/AppViewModel.kt
app/src/main/java/com/levanyon/meditakip/ui/Editors.kt
app/src/main/java/com/levanyon/meditakip/ui/Home.kt
app/src/main/java/com/levanyon/meditakip/ui/Medications.kt
app/src/test/java/com/levanyon/meditakip/data/MigrationTest.kt
app/src/test/java/com/levanyon/meditakip/ui/AccessibilityTest.kt
app/src/test/java/com/levanyon/meditakip/ui/MedicationRedesignTest.kt
app/src/test/java/com/levanyon/meditakip/ui/MedicationScreenshotTest.kt
app/src/test/java/com/levanyon/meditakip/ui/ScreenLayoutTest.kt
app/src/test/java/com/levanyon/meditakip/ui/ScreenshotTest.kt
app/src/test/java/com/levanyon/meditakip/ui/UiFlowTest.kt
```

Added:

```text
app/schemas/com.levanyon.meditakip.data.MediDatabase/3.json
app/src/main/java/com/levanyon/meditakip/domain/Prescription.kt
app/src/main/res/values/phase7.xml
app/src/test/java/com/levanyon/meditakip/data/PrescriptionMigrationTest.kt
app/src/test/java/com/levanyon/meditakip/data/PrescriptionPersistenceTest.kt
app/src/test/java/com/levanyon/meditakip/data/RenewalIntegrationTest.kt
app/src/test/java/com/levanyon/meditakip/domain/PrescriptionTest.kt
app/src/test/java/com/levanyon/meditakip/ui/PrescriptionScreenshotTest.kt
app/src/test/java/com/levanyon/meditakip/ui/PrescriptionUiTest.kt
app/src/test/java/com/levanyon/meditakip/ui/RenewalNavigationTest.kt
docs/PHASE7_REVIEW.md
```

Existing UI test edits only adapt medication-save lambdas to the new prescription argument; the existing
migration assertion now expects Room version 3. Their existing behavioral assertions remain intact.
