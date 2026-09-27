# MediTakip — Phase 0 Audit

Audit date: 2026-09-25 · Baseline: git tag `baseline-v1` (commit `bbcf4a5`) · Room schema version 1 (identity hash `813dea27…`).

> **Resolution status (Phase 1, 2026-09-25).** The sections below describe the baseline. Since then:
>
> | Finding | Status |
> |---|---|
> | B1 cell detail showed latest dose | Fixed: the detail shows the occurrence's own revision |
> | B2 stale person name | Fixed: `Occurrence.personName` comes from `people`; the revision column is no longer read |
> | B3 archived history hidden, no restore | Fixed: archived people selectable in Geçmiş; "Arşivdekiler" and "Arşivlenen ilaçlar" sections; Arşivden Çıkar |
> | B4 same-day edit interval | Kept by design: "never-effective revision" (`isNeverEffective`), documented and tested. Also fixed a related tie: newest revision is now deterministic (`ORDER BY rowid`, `MedicationViews.latestRevision`) |
> | B5 past start dates | Fixed: date pickers start at today |
> | R7 "Sil" wording | Fixed: "Arşivle" / "Arşivden Çıkar" |
> | R8 terminology | Fixed in History ("Alındı olarak işaretlenmedi"); notification text left for Phase 2 |
> | R11 stale docs | Fixed: README, ARCHITECTURE; V1.1 marked historical |
>
> Regression guard: `data/HistoryIntegrityTest`.
>
> **Later phases (2026-09-25):**
>
> | Finding | Status |
> |---|---|
> | R1 exact alarms on Android 14+ | Fixed (Phase 2): `USE_EXACT_ALARM` on 13+, `setAlarmClock` |
> | R3 swallowed reminder errors | Fixed (Phase 2): `ReminderLog`, shown on Hatırlatma Durumu |
> | R4 no migration path | Fixed (Phase 3): schema v2, `MIGRATION_1_2`, `MigrationTest` |
> | R5 follow-up off by default | Fixed (Phase 2): on by default, 30 min |
> | R6 late marking impossible | Fixed (Phase 3): Saati değiştir; history correction |
> | R10 dispatches grow forever | Fixed (Phase 2): pruned after 7 days |
> | R12 revision order depends on rowid | Fixed (Phase 3): persistent `sequence` column |
>
> Decisions are recorded in `docs/DECISIONS.md`.

This document describes the code **as it is**, so later phases can change it safely. Findings marked **[verified]** were confirmed by running a throwaway Robolectric probe against a real in-memory Room database (see §4.3); the probe itself was not committed.

---

## 1. Module and package map

Single Gradle module `:app` (Kotlin, Jetpack Compose Material 3, Room 2.6.1 via KSP, minSdk 26, target/compile 35). No network permission, no DI framework, no navigation library.

| Package | Layer | Files | Responsibility |
|---|---|---|---|
| `com.levanyon.meditakip` | App | `MediApp.kt`, `MainActivity.kt` | `MediApp` lazily creates the Room DB, `Repository`, `Preferences`, `Reminders` (manual service locator). `MainActivity` forces Turkish locale, enables edge-to-edge, shows Onboarding or `Home`, refreshes reminders on resume. |
| `data` | Model / persistence | `Database.kt`, `Repository.kt`, `Preferences.kt`, `LocalBackup.kt` | Room entities + DAO; repository transactions (save/edit/archive); SharedPreferences settings; `LocalBackup` is an **interface only** (no implementation). |
| `domain` | Pure logic (no Android) | `Schedule.kt`, `Presentation.kt`, `ReminderPlan.kt` | Occurrence generation (`Schedule.day`), history views, history matrix, progress, Turkish formatting, permission policy, reminder event planning. |
| `reminders` | Android integration | `Reminders.kt` | Notification channel, AlarmManager scheduling, notification posting, `ReminderReceiver`. |
| `ui` | ViewModel + Views | `AppViewModel.kt` + 11 Compose files | One `AndroidViewModel` for the whole app; screens are stateless composables fed a `Snapshot`. Navigation is plain state in `Home` (no back stack library). |

**MVVM shape.** `Repository.snapshots` combines four Room `Flow`s (people, medications, schedules, logs) into one immutable `Snapshot`. `AppViewModel` exposes it as `StateFlow`, overlays optimistic Taken/Undo changes (`pending` map), and funnels every write through `perform {}` (single-flight, maps validation errors to Turkish messages, then calls `reminders.refresh()`). Screens derive everything from the snapshot with `domain` functions.

Note: the **entire** database (all logs ever written) is loaded into memory and recomputed on every change. Fine at family scale today; see risk R9.

---

## 2. Room schema (version 1)

`MediDatabase` version 1, `exportSchema = true`, schema JSON committed at `app/schemas/com.levanyon.meditakip.data.MediDatabase/1.json`. The builder has **no** `fallbackToDestructiveMigration` — good: a future version bump without a migration will crash instead of silently wiping data.

| Table | Fields | Keys / relations / indices |
|---|---|---|
| `people` | `id: String`, `name`, `note`, `color: Int` (unused), `archived: Boolean` | PK `id` |
| `medications` | `id`, `personId`, `archived` | PK `id`; FK `personId → people.id` (no ON DELETE action); index `personId` |
| `schedules` (revisions) | `id`, `medicationId`, `personName` (denormalized copy), `name`, `dose`, `note`, `times` ("08:00, 20:00" text), `days` (7-bit mask, Mon = bit 0), `start`/`end` (epoch-day, end nullable), `active`, `effectiveFrom`/`effectiveTo` (epoch-day, to nullable) | PK `id`; FK `medicationId → medications.id`; index `medicationId` |
| `logs` | `key` (`"<scheduleId>:<epochDay>:<minute>"`), `scheduleId`, `date` (epoch-day), `minute` (minute of day), `takenAt` (epoch millis) | PK `key`; FK `scheduleId → schedules.id`; index `scheduleId`; **unique** (`scheduleId`,`date`,`minute`) |
| `dispatches` | `key` (occurrence key + `:first`/`:retry`), `sentAt` | PK `key`; no FK; **never pruned** (R10) |

Observations against the roadmap data model:
- Medication identity (`medications`) is separated from its definition (`schedules`), and **schedules are already versioned** (`effectiveFrom`/`effectiveTo`). Most of Roadmap 1.2 exists.
- Logs already store the **scheduled date and minute** plus the actual `takenAt` (Roadmap 1.2 second bullet ✓, 1.3 partially ✓).
- Missing for Roadmap 1.3: `status` (TAKEN/SKIPPED), `recordedAt`.
- `times` is a comma-separated string, parsed on every use.
- `personName` is copied into every schedule revision (see B2).

Non-Room state (SharedPreferences `settings`): `onboardingCompleted`, `notificationAsked`, `notifications`, `retry`, `delay`, `theme`, `nextAlarm`, `lastPerson`.

---

## 3. How "today's doses" are computed

Single function: **`Schedule.day(snapshot, date): List<Occurrence>`** (`domain/Schedule.kt`). Inputs: the whole `Snapshot` and a `LocalDate`.

1. For every schedule revision whose medication exists, include it when **all** hold: `active`, `start ≤ date ≤ end`, `effectiveFrom ≤ date ≤ effectiveTo`, weekday bit set. (Archived medications are **not** excluded here — needed for history.)
2. For each time in the revision, create an `Occurrence`, unless another revision of the same medication already has a log for that date+minute (prevents duplicates after a same-time edit).
3. Then every log of that date is added back with its own revision, so a completed dose **always** stays visible even if its schedule was later edited or archived.
4. Sort by `personName`, minute, name.

Callers (all use this one function — the roadmap's "single source of truth" is effectively already in place, though not named `expectedDoses`):
- **Bugün** (`TodayScreen`): `Schedule.day(snapshot, today)` filtered by selected person.
- **History matrix** (`HistoryMatrices.build`): `Schedule.day` per date, filtered by person (and optionally medication).
- **İlaç detayı → Son 7 gün**: same matrix builder.
- **Reminders** (`ReminderPlan.events`): `Schedule.day` for yesterday, today and the next 8 days per current schedule, then filters to untaken occurrences of current, non-archived revisions.
- `MedicationViews.history` (older helper, still used only by tests).

---

## 4. History matrix and what happens to past rows

### 4.1 Computation
`HistoryMatrices.build(snapshot, personId, from, through, medicationId?)` (`domain/Presentation.kt`):
- Walks dates from `through` back to `from`, calls `Schedule.day`, keeps the person's occurrences, and **drops days with no occurrences**.
- Columns = distinct (`medicationId`, `minute`) pairs, sorted by time then name. Column label (name **and dose**) is taken from the medication's **latest** revision.
- Cells map a column key to the `Occurrence` (taken → ✓ + time; no log → ○; no occurrence → –).
- Pure and read-only: it never writes records (unit-tested).

### 4.2 Edit semantics (`Repository.saveMedication`)
Editing never updates a revision in place. In one transaction it sets `effectiveTo = today − 1` on the current revision and inserts a new revision with `effectiveFrom = today`. "Delete" (`deleteMedication`/`deletePerson`) sets `archived = true` and closes current revisions the same way. **Nothing is ever hard-deleted.**

### 4.3 Scenarios [verified]
Setup: Parol for Anne, 08:00 and 20:00 daily from D = 2026-09-21; 08:00 on D marked taken.

| Change | Past day D afterwards | Days after the change |
|---|---|---|
| Edit times → 09:00 and dose → 2 tablet on D+1 | 08:00 ✓, 20:00 ○, **dose "1 tablet" kept** ✓ | 09:00 / 2 tablet |
| Remove a weekday (Monday) on D+7 | Monday D unchanged ✓ | Monday D+7: no doses |
| Deactivate ("İlaç kullanımda" off) on D+1 | unchanged ✓ | no doses |
| Archive medication ("İlacı Sil") on D+1 | unchanged ✓ | no doses; medication hidden from all lists |
| Archive person ("Kişiyi Sil") on D+1 | unchanged in data ✓ — **but hidden in the UI** (B3) | person and medications archived; logs kept (1 of 1) |
| Edit on the **same day** it was created (08:00 taken, times → 09:00) | 08:00 ✓ stays (from its log); untaken 20:00 for today disappears; 09:00 appears | old revision stored with `effectiveTo (D−1) < effectiveFrom (D)` (B4) |
| Rename person Anne → Annem | schedules still say **"Anne"** (B2) | — |
| Create on D with start date D−5 | D−2: **no rows** — past start dates are ignored (B5) | — |

Conclusion: **stored history is never rewritten by edits**, which is the roadmap's core requirement. The problems are in how some screens *present* past data (B1–B3) and in two confusing edge semantics (B4, B5).

---

## 5. Reminders and notifications (current behavior)

Implemented in `reminders/Reminders.kt` + `domain/ReminderPlan.kt`.

- **Channel:** `"medication"`, name "İlaç hatırlatmaları", `IMPORTANCE_HIGH`, lock-screen visibility private.
- **Planning:** `ReminderPlan.events` → untaken occurrences of current, active, non-archived revisions for yesterday…+8 days; optional retry event at `+delayMinutes` (setting "İkinci Hatırlatma", default **off**, 30 min). Settings toggle "Hatırlatmaları Etkinleştir" disables everything.
- **Scheduling:** only the **next** event is scheduled. If exact alarms are allowed: `setExactAndAllowWhileIdle` (request code 1) **plus** an inexact `setAndAllowWhileIdle` safety alarm 15 min later (code 2); otherwise only the inexact one. The target time is saved in `Preferences.nextAlarm`.
- **Delivery:** `ReminderReceiver` (`goAsync()` + coroutine ✓) calls `rebuild(deliver = true)`: posts every due, not-yet-dispatched event from `nextAlarm` up to now (max 24 h back), records it in `dispatches` (idempotent), cancels notifications whose occurrence is no longer outstanding (e.g. marked Taken), then schedules the next alarm.
- **Rescheduling triggers:** boot, `TIME_SET`, `TIMEZONE_CHANGED`, `MY_PACKAGE_REPLACED`, exact-alarm permission change, every app resume, every repository write, every Taken/Undo.
- **Notification content:** title "İlaç zamanı 💊" (or "İlaç hatırlatması" for retry); text `"<person> • <medication> • Saat 08:00"`; tap opens the app. One notification per dose (tag = occurrence key); **no** group, **no** actions.
- **Permissions:** `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`. Not `USE_EXACT_ALARM`. Onboarding + Settings request/open settings; Bugün shows a banner when notifications are blocked or disabled.

Gaps vs Phase 2: no İçildi/ertele notification actions, no grouping of same-time doses, person-first title format differs, no reminder-health screen (battery optimization, Samsung sleeping apps), no test notification, exact alarms likely **denied by default** on Android 14+ fresh installs (falls back to inexact → possible delays), follow-up default is off (roadmap wants on/30 min).

---

## 6. Existing tests (64, all JVM/Robolectric; no instrumented tests)

| File | # | Covers |
|---|---|---|
| `domain/ScheduleTest` | 8 | weekday/time generation, inclusive boundaries, inactive, retired revisions keep history, leap day/year boundary, DST gap/overlap, Taken idempotent/Undo |
| `domain/ReminderTest` | 4 | retry across midnight, Taken suppresses first+retry, disabled → no events, far-future start |
| `domain/PresentationTest` | 8 | Turkish dates, completion time/other-date, medication history with revisions, progress, permission policy |
| `domain/HistoryMatrixTest` | 5 | columns per person sorted by time, real logs → ✓/○, no record creation, omitted days + medication filter, revised schedule keeps one column |
| `data/RepositoryTest` | 6 | Taken/Undo round trip, edit keeps past, archive person keeps history, invalid edit rolls back, completed entry survives same-day edit, same-time edit no duplicate |
| `data/ReminderIntegrationTest` | 2 | exact + safety alarm scheduling/cancel, dispatch idempotence, Taken cancels notification |
| `data/PreferencesTest` | 3 | onboarding/settings persistence, permission-asked flag, last selected person |
| `ui/DesignSystemTest` | 4 | reflow at 2× font, nav bar reachable at 2×, primary action min height, centered reading column |
| `ui/ScreenLayoutTest` | 17 | Today/People/Person/Medication/Settings/History screens at 320 dp and 1.8× font, history table centering / horizontal scroll with frozen date column / cell dialog, forms' Kaydet reachable in landscape, advanced fields, Taken/Undo callbacks, confirmation dialog |
| `ui/UiFlowTest` | 7 | 4-tab navigation, People → Person → Medication, form opens in activity window, History from real logs, onboarding skips granted steps |

Not covered: **migrations** (none exist yet), person rename, same-day-edit interval semantics, reminder content wording, anything on a real device/OEM (Doze, Samsung battery), `connectedDebugAndroidTest` (no `androidTest` source set; no device configured).

---

## 7. Bugs, risks and gaps

### Bugs (should be fixed in Phase 1)
- **B1 — History cell shows the wrong dose for past days.** `HistoryCellDetail` shows `column.dose` (latest revision). After changing 1 → 2 tablet, the 21 Sep cell says "2 tablet" although that day's revision was "1 tablet". The stored data is correct; the display rewrites the past. Fix: show `occurrence.schedule.dose` (and name).
- **B2 — Renaming a person does not update `schedules.personName`.** Notifications ("Anne • Parol"), TalkBack labels on Bugün and sorting keep the old name until the medication is edited. Fix: resolve the person's name from `people` at display time (or update current revisions in the same transaction) — decide in Phase 1.
- **B3 — Archived people's history is unreachable.** Geçmiş only lists non-archived people and archived medications have no entry point, so "Kişiyi Sil" makes history invisible in the app (data still exists). Conflicts with Roadmap 1.4 ("still visible in History"). No restore action exists.
- **B4 — Same-day edit stores an inverted interval** (`effectiveTo < effectiveFrom`). Harmless today (logs keep completed doses), but it is an invalid record that future code (migration, backup validation) could reject or misinterpret. Also any **untaken** earlier dose of today silently disappears when editing mid-day. Decide the rule ("edit applies from today" vs "from now") in Phase 1.2.
- **B5 — Past start dates are silently ignored.** The form allows choosing a start date in the past, but `effectiveFrom = today` hides those days. Either restrict the picker or define the meaning.

### Risks
- **R1 — Android 14+ exact alarms.** `SCHEDULE_EXACT_ALARM` is denied by default for new installs; the fallback inexact alarm can be delayed by minutes to longer in Doze. Samsung "sleeping/deep sleeping apps" can suppress alarms entirely. Phase 2 must decide `USE_EXACT_ALARM` vs `setAlarmClock` (sideloaded → Play policy does not apply).
- **R2 — Data loss on uninstall/phone reset.** `allowBackup="false"`, no export. Reinstalling with a different signing key requires uninstall → all data lost. (Phases 4 and 6.)
- **R3 — Only one alarm is pending at a time.** If the app process is killed between alarms and the receiver fails (errors are only logged), nothing reschedules until reboot/app open. `Reminders.refresh()` swallows exceptions (`runCatching`) — failures are invisible.
- **R4 — No migration path exists yet.** The first schema change (Phase 1) must add `Migration(1,2)` plus a migration test; a migration test needs `androidx.room:room-testing` (new test dependency — requires approval).
- **R5 — Retry/follow-up off by default**, so a missed first notification has no second chance unless the user enables it.
- **R6 — Late marking impossible.** Only today's doses can be marked; yesterday's forgotten dose stays "not marked" forever (Phase 3).
- **R7 — "Sil" wording vs behavior.** Buttons say "İlacı Sil"/"Kişiyi Sil" but actually archive; the roadmap wants "Arşivle" + restore.
- **R8 — Terminology drift.** Agreed glossary (2026-09-25): history status uses **"Alındı olarak işaretlenmedi"**; code currently shows "İçildi olarak işaretlenmedi" (`not_marked` string). Fix with the copy pass (Phase 5) or together with B1.
- **R9 — Performance growth.** Whole DB in memory; `Schedule.day` checks `logs × schedules` for every occurrence; the history matrix calls it per displayed day. Fine for months of data, may become sluggish after years. Consider indexed DAO queries per date range in Phase 1.
- **R10 — `dispatches` grows forever** (one row per sent notification). Small but unbounded; prune rows older than a few days.
- **R12 — Revision order depends on `rowid` (added after Phase 1).** `MedicationViews.latestRevision` breaks ties between revisions that share an `effectiveFrom` (e.g. edit + archive on the same day) using insertion order, read via `ORDER BY rowid`. That is an SQLite internal detail. `schedules` has a TEXT primary key, so `VACUUM` may renumber rowids. A JSON backup/restore (Phase 4) would not keep the order either. If the order is lost, İlaç Detayı, the History column label and "Arşivden Çıkar" could pick an older revision in that tie case. Stored history itself is not affected. Mitigation: a persistent revision-order column in the Phase 3.0 migration (filled from the current rowid order), used by `latestRevision`, and exported/imported by the Phase 4 backup. Until then, do not run `VACUUM` or re-insert schedule rows.
- **R11 — Stale documentation.** `README.md` and `docs/V1.1.md` still describe five tabs incl. "İlaçlar"; `docs/ARCHITECTURE.md` claims notifications are "grouped" (they are not).

### Gaps vs roadmap (summary)
| Roadmap item | Status |
|---|---|
| 1.1 single expected-dose function | ✓ exists as `Schedule.day` (not person-scoped, not named) |
| 1.2 schedule versioning, logs keyed by scheduled date/time | ✓ exists; B4/B5 edge rules to decide |
| 1.3 status (TAKEN/SKIPPED), recordedAt | ✗ missing (needs schema v2) |
| 1.4 archive + restore, hard delete only without history | archive ✓, restore ✗, visibility in History ✗ (B3) |
| 1.5 Migration(1,2) + MigrationTestHelper | ✗ |
| 2.x notification actions, grouping, health screen, test notification | ✗ |
| 3.x late marking, skip, history correction, stock | ✗ |
| 4.x backup/restore/report | ✗ (`LocalBackup` interface only) |
| 5.x glossary, splash screen, font-scale matrix | partial (layout tests at 1.5–2×) |
| 6.x R8 release, keystore procedure, changelog | done except R8: release key, version policy, CHANGELOG and update procedure exist; R8 deliberately off (DECISIONS D6.2) |
