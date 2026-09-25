# MediTakip — Professional Roadmap

This file is the master plan for taking MediTakip from a working prototype to a reliable, professionally built application. It lives in the repository at `docs/ROADMAP.md` and is the single source of truth for scope and order of work.

Primary real user: one mother managing medications for herself and her child, on a single Samsung phone, offline. She is not technical. Every decision is judged by one question: will this make daily use clearer and reminders more reliable for her, without risking her data?

"Professional" here means: correct data, reliable reminders, tested logic, safe updates, accessible UI. It does NOT mean more features.

## 0. Working rules for Claude Code (apply to every phase)

1. One phase per session. Never start the next phase without explicit approval.
2. Plan first. At the start of a phase, read the relevant code, then present a short plan: files to change, schema impact, risks, test plan. Wait for approval.
3. Explain decisions. The developer is learning. For each important change, explain what was done and why, in plain language. Do not hide complexity, but do not add unnecessary complexity either.
4. Small steps. Work in small, buildable increments. After each increment: build, run tests, commit with a clear message.
5. No new dependencies without asking. State: purpose, alternative without the dependency, size/risk. Prefer Android/Jetpack built-ins.
6. Data safety is absolute. No destructive migrations, no silent resets, no hard deletes of anything that has history. Every schema change = explicit `Migration` + migration test.
7. Windows native only. Use PowerShell and `.\gradlew.bat`. No WSL/Docker/VM.
8. Never give medical advice in UI text, notifications, or reports. The app only records what the user entered.
9. Do not regress existing behavior listed in `CLAUDE.md` (navigation structure, form insets / reachable Kaydet button, History matrix, Taken/Undo).
10. End of phase report: what changed, what was tested, a manual test checklist for a real Samsung device, known limitations, and update the checkboxes in this file.

Useful commands:

```
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
.\gradlew.bat connectedDebugAndroidTest
.\gradlew.bat lint
```

## Phase 0 — Audit and safety net (no feature code)

Goal: understand exactly what exists before changing anything, and make every later change safe.

- [x] Confirm the working tree is clean; create git tag `baseline-v1`.
- [x] Ensure Room `exportSchema = true` and the schema location is configured (e.g. `app/schemas`). Generate and commit the version 1 schema JSON before any schema change. (Migration tests depend on it.)
- [x] Write `docs/AUDIT.md` containing:
  - module / package map and the MVVM layers
  - every Room entity, its fields, relations and indices
  - how "today's doses" are computed (which function, which inputs)
  - how the History matrix is computed, and specifically what happens to past rows when: a schedule time is edited, a day is removed, a medication is deactivated, a medication or person is deleted
  - how reminders/notifications currently work (or that they do not exist)
  - existing tests and what they cover
  - bugs, risks and gaps compared to this roadmap
- [x] Create or tighten `CLAUDE.md` in the repo root: concise (target under ~150 lines), containing only stable rules (product purpose, stack, navigation, data safety, form rules, History rules, Windows rules, commands). Design details stay in `DESIGN.md`.

Done when: project builds, `AUDIT.md` and `CLAUDE.md` exist, v1 schema is committed, developer has reviewed the audit.

Status (2026-09-25): all items complete; 64/64 tests pass; waiting for the developer to review `docs/AUDIT.md`. Also added: `tools/env.ps1` (project-local JDK/Gradle), `.gitattributes`.

## Phase 1 — Data correctness and history integrity

Goal: past history must never change or disappear because of a later edit. This is the foundation for everything else.

### 1.1 Single source of truth for expected doses

- [ ] One pure Kotlin domain function (no Android dependencies), e.g. `expectedDoses(personId, date): List<ExpectedDose>`, used by Today, History and Reminders. No screen computes schedules on its own.
- [ ] Unit tests: weekday selection, start date, end date, inactive medication, multiple times per day, dates across midnight, schedule changed in the middle of a period.

### 1.2 Schedule changes must not rewrite the past

Choose, justify and implement the simplest design that guarantees correctness (present options to the developer first). Recommended default:

- [ ] Schedule versioning: schedules have `validFrom` / `validTo`. Editing a schedule closes the current version (`validTo = yesterday` or today, decide and document) and creates a new one. History for a past date uses the version valid on that date.
- [ ] Dose logs store the scheduled date and scheduled time they belong to, not only a medication id and a timestamp.

### 1.3 Log model ready for the future

- [ ] Log status as an enum-like field: `TAKEN`, `SKIPPED` (SKIPPED UI comes in Phase 3, but the column is added now to avoid a second migration).
- [ ] Store `takenAt` (actual time) separately from the scheduled time, plus `recordedAt` (when the entry was made) so late marking is distinguishable.
- [ ] Use `java.time`. Store dates as epoch-day or ISO string, times as minutes-of-day, instants as epoch millis. Do not hardcode a timezone.

### 1.4 Deletion policy

- [ ] A medication or person that has any history is never hard-deleted. The action is Arşivle (hidden from Bugün and lists, still visible in History). Hard delete only when there is no history, with confirmation.
- [ ] Archived items can be restored.

### 1.5 Migration

- [ ] Explicit `Migration(1, 2)` that converts existing data without loss.
- [ ] `MigrationTestHelper` test: insert realistic v1 data, migrate, assert every person, medication, schedule and log survived with correct values.
- [ ] Verify on a real device that already contains v1 data before merging.

Done when: all tests pass, editing a schedule on a test device leaves old History rows unchanged, migration test is green.

## Phase 2 — Reliable reminders

Goal: a reminder fires at the right time, shows whose medication it is, and survives reboot, Doze and Samsung battery management.

### 2.1 Scheduling

- [ ] `NotificationChannel` "İlaç hatırlatmaları", high importance.
- [ ] Use `AlarmManager` exact alarms (`setExactAndAllowWhileIdle`, or `setAlarmClock` if justified — explain the trade-off: status-bar icon vs reliability). Schedule the next occurrence(s) computed from the Phase 1 domain function.
- [ ] Explain and decide the permission strategy: `POST_NOTIFICATIONS` (Android 13+), `SCHEDULE_EXACT_ALARM` (denied by default for new installs on Android 14+) vs `USE_EXACT_ALARM`. The app is sideloaded, not on Play Store — state how that affects the choice.
- [ ] Reschedule all alarms on: `BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED`, `MY_PACKAGE_REPLACED`, exact-alarm permission granted, and every medication/schedule/person change.
- [ ] Receivers doing database work use `goAsync()` (or WorkManager) correctly.

### 2.2 Notification content and actions

- [ ] Title shows the person first: e.g. `Kardeş · Parol` / `1 tablet · 08:00`.
- [ ] Doses due at the same time are grouped sensibly.
- [ ] Actions: İçildi (writes the log without opening the app) and 10 dk ertele. Immutable `PendingIntent`s.
- [ ] Optional follow-up reminder if not marked after N minutes (setting, default one follow-up after 30 minutes; can be turned off).
- [ ] Marking a dose in the app cancels its pending notification/follow-up.

### 2.3 Reminder health screen

- [ ] Ayarlar → Hatırlatma Durumu: shows each requirement with clear status and a "Düzelt" button that opens the right system screen: notification permission, exact alarm permission, battery optimization, Samsung "sleeping apps" guidance (plain Turkish instructions).
- [ ] "Test bildirimi gönder" button.
- [ ] If reminders are not fully working, Bugün shows a clear, non-alarming banner linking to this screen.

### 2.4 Verification

- [ ] Unit tests for next-trigger computation.
- [ ] Manual test checklist (real Samsung): app swiped away, phone rebooted, Doze forced (`adb shell dumpsys deviceidle force-idle`), time changed, three-button and gesture navigation, notification action while locked.

Done when: all checklist items pass on the real phone.

## Phase 3 — Daily-use features

Goal: handle what really happens in daily life.

- [ ] Late marking: when marking a dose, actual time defaults to now but can be changed (she often marks later). Stored in `takenAt`.
- [ ] Atla (skip) with optional short reason. History shows it distinctly from "not marked". Update the History legend.
- [ ] Correcting history: tapping a History cell allows marking taken / skipped / clearing, with confirmation for past days. Keep existing Taken/Undo on Bugün.
- [ ] Stock tracking (optional per medication): current quantity, amount per dose, low-stock threshold. Taking a dose decrements stock; undo restores it. Bugün shows "Parol azalıyor · ~5 gün" and a notification when threshold is reached. "Stok ekle" action.
- [ ] Snackbar undo for reversible actions; confirmation dialogs for irreversible ones.

Done when: features tested with unit tests (stock math, status transitions) and on device.

## Phase 4 — Backup, restore and reports

Goal: losing or resetting the phone must not mean losing the history.

- [ ] Export backup: full data as versioned JSON (`formatVersion` field) via Storage Access Framework (`CreateDocument`, no storage permission).
- [ ] Import backup: validate, show a preview (people, medications, log count, date range), then replace all data in a single transaction after explicit confirmation. Never partial writes.
- [ ] Round-trip test: export → wipe test DB → import → identical data.
- [ ] Ayarlar shows "Son yedek: …". Gentle reminder if older than 30 days.
- [ ] Evaluate Android Auto Backup (`dataExtractionRules`) as an extra layer; explain what it does and does not guarantee.
- [ ] Doktor raporu: per person, PDF with active medication list and the last 30 days of history, generated with `android.graphics.pdf.PdfDocument` (no library), shared via share sheet. Neutral wording, no interpretation, footer: "Bu rapor kullanıcının girdiği kayıtlardan oluşturulmuştur."

Done when: backup restored successfully on a second device or emulator.

## Phase 5 — UX, accessibility and polish

Goal: comfortable for a non-technical user, consistent everywhere.

- [ ] First-run onboarding: add first person → add first medication → enable reminders (links to Hatırlatma Durumu). Skippable.
- [ ] Touch targets ≥ 48dp (primary actions larger). Test at font scale 1.0, 1.3, 1.5 and 2.0 on every screen; nothing clipped, Kaydet always reachable.
- [ ] TalkBack: meaningful content descriptions, logical focus order.
- [ ] Status never communicated by color alone (icon + text).
- [ ] WCAG AA contrast in light and dark theme.
- [ ] Empty states that tell the user the next action.
- [ ] Turkish copy glossary in `DESIGN.md` (e.g. button "İçildi", history "Alındı olarak işaretlenmedi", "Atlandı", "Arşivle") and apply it consistently.
- [ ] App icon, app name, SplashScreen API, edge-to-edge verified.
- [ ] Remove prototype leftovers, debug text, unused code.

Done when: full manual pass on the real phone at two font sizes, both themes.

## Phase 6 — Quality and release

Goal: safe installation and safe future updates on the real phone.

- [ ] Test coverage for: domain schedule logic, migrations, reminder trigger computation, stock math, backup round-trip. Key UI flows with Compose tests.
- [ ] `lint` clean or documented exceptions.
- [ ] Release build with R8; verify Room/serialization still work after minification.
- [ ] Signing keystore: create once, store it and its passwords in two safe places outside the repo. An update signed with a different key cannot be installed over the old app; uninstalling deletes all data.
- [ ] `versionCode` / `versionName` policy and `CHANGELOG.md`.
- [ ] Update procedure documented in `docs/RELEASE.md`: export backup → install update over existing app → verify data.
- [ ] Parallel run: 1–2 weeks of real use alongside the family's current method before relying on the app alone.

## Out of scope (for now)

Cloud sync, accounts, multiple devices, Play Store publishing, analytics, ads, drug databases, interaction checks, dosage suggestions or any medical guidance, Wear OS.

## Possible future items (only after Phase 6)

Home-screen widget "Sıradaki ilaç", as-needed (PRN) medications, per-person colors/avatars, adherence summary per week.
