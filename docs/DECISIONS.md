# MediTakip — decision log

From 2026-09-25 the developer asked Claude Code to run Phases 2 → 5 → 3 → 4 → 6 without waiting for approval between steps. Each phase starts with a short plan here; every important decision is recorded with its reason, so it can be reviewed later. Claude Code stops and asks only for: data-loss risk, a new library dependency (except `androidx.room:room-testing`, pre-approved for Phase 3), irreversible actions, and anything involving the release key.

Decisions the developer made in advance are marked **(developer)**.

---

## Phase 2 — Reliable reminders

### Plan
1. Permissions: add `USE_EXACT_ALARM` (Android 13+), limit `SCHEDULE_EXACT_ALARM` to Android 12/12L with `maxSdkVersion="32"`.
2. Scheduling: `setAlarmClock` for the next reminder, plus the existing inexact safety alarm. Extract the pure "what is due / what is next" logic into `ReminderPlan` with unit tests.
3. Notification: person first, dose and time, `İçildi` and `10 dk ertele` actions (a new `ReminderActionReceiver`), a group summary when several doses are due at the same time, and a public lock-screen version without medication names.
4. Snooze persisted in preferences (survives reboot, no schema change). Follow-up reminder on by default after 30 minutes.
5. R3: failures are recorded in a small local log (file, last 50 entries) and shown on the new screen. R10: prune dispatch records older than 7 days.
6. Ayarlar → **Hatırlatma Durumu** screen: every requirement with status and a `Düzelt` button, battery optimization, Samsung "sleeping apps" guidance, `Test bildirimi gönder` (immediately) and a 1-minute test through the real alarm path, and the last recorded problem. The Bugün banner links there.
7. Tests: unit tests for next-trigger, due, grouping and snooze logic; Robolectric tests for the notification actions, notification text, pruning and the error log; Compose tests for the new screen.

### Decisions

**D2.1 Exact-alarm permission: `USE_EXACT_ALARM` on 33+, `SCHEDULE_EXACT_ALARM` only up to 32.** (developer asked to evaluate)
- `USE_EXACT_ALARM` is granted at install time and the user cannot revoke it. `SCHEDULE_EXACT_ALARM` is *denied by default* for new installs on Android 14+ and can be revoked at any time. That was risk R1.
- Google Play only allows `USE_EXACT_ALARM` for alarm-clock and calendar apps. MediTakip is sideloaded, so Play policy does not apply. A medication reminder is also exactly the kind of user-facing, time-critical alarm the permission exists for.
- On Android 12/12L (API 31–32) `SCHEDULE_EXACT_ALARM` is still granted by default, so declaring it with `maxSdkVersion="32"` covers those versions. Below 31 no permission is needed.
- Result: exact alarms are available on every supported Android version without the user doing anything. The onboarding and settings screens still check `canScheduleExactAlarms()`, because on 12/12L a user could revoke it.

**D2.2 Use `setAlarmClock` instead of `setExactAndAllowWhileIdle`.**
- In Doze, `setExactAndAllowWhileIdle` alarms may be deferred (roughly one per 9 minutes per app). Some OEM battery managers, Samsung's among them, also treat them as ordinary background work.
- `setAlarmClock` is the strongest guarantee Android offers: it exits Doze and fires on time, and OEMs keep alarm-clock alarms working even for restricted apps.
- Trade-off: Android shows an alarm-clock icon in the status bar and may list the next reminder as "next alarm" on the lock screen and in quick settings. For a medication reminder this is acceptable, and even useful ("the next medication time is set"). Its "show" intent opens MediTakip.
- The inexact safety alarm 15 minutes later stays, as a second line of defence.

**D2.3 One notification per dose, grouped by time.** Each dose needs its own `İçildi` button, so doses are not merged into one notification. When two or more are due at the same time, they share a group and Android shows them bundled under a summary ("2 ilaç zamanı · 08:00").

**D2.4 Notification wording (glossary).**
- Title: `Kardeş · Parol`. Text: `1 tablet · 08:00`.
- Follow-up: the title adds "Hatırlatma", and the text says the dose is `henüz alındı olarak işaretlenmedi`. **(developer: status wording uses "Alındı", the button uses "İçildi")**
- Lock screen (public version): only "MediTakip · İlaç zamanı", with no person or medication names, because other people can see a locked phone.

**D2.5 Snooze = 10 minutes, stored in SharedPreferences.**
- A snooze must survive a reboot, but a Room table would need a schema change. The single migration is planned for Phase 3.
- Snoozes are stored as `occurrenceKey → epoch millis` in preferences. Entries are dropped when the dose is marked or skipped, when the medication is edited or archived (the key no longer matches an outstanding occurrence), or 24 hours after the snooze time.

**D2.6 Follow-up reminder on by default, after 30 minutes.** (developer)
- This changes the stored default from `false` to `true`. Only a user who never touched the setting is affected, and the phone has a fresh install.
- The setting can still be turned off.

**D2.7 Error log instead of silently swallowed errors (R3).**
- Failures in `refresh()`, the alarm receiver and the action receiver go to `ReminderLog`: a small text file in app storage, keeping the last 50 entries with time, place and exception message.
- It contains no medication data, and it is never sent anywhere.
- The Hatırlatma Durumu screen shows the latest entry. This makes problems visible without adding analytics.

**D2.8 Prune dispatch records older than 7 days (R10).**
- A dispatch record is only needed to avoid showing the same reminder twice. Reminders are never delivered for events older than 24 hours, so 7 days is a safe margin.
- It is a DAO query only; no schema change.

**D2.9 Battery optimization: open the system list, do not request the exemption directly.**
- Asking directly (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) needs the extra permission `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. The project rules say no new permissions without asking.
- `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` needs no permission. The screen explains in plain Turkish what to select there.
- The Samsung "sleeping apps" steps are written instructions, shown on Samsung devices.

**D2.10 Hatırlatma Durumu reads the in-app switch from the ViewModel.**
- System state (notification permission, exact alarms, battery) is re-read every 2 seconds while a screen that shows it is open. That covers returning from the Android settings screens.
- The in-app "Hatırlatmaları Etkinleştir" value comes directly from the ViewModel, so a Düzelt tap shows its result at once instead of up to 2 seconds later.
- Found through a test that only failed when the whole test class ran: two sources of truth for the same value.

**D2.11 Ayarlar keeps only a summary row.**
- The permission rows, battery, Samsung guidance and test buttons moved to the Hatırlatma Durumu screen. Ayarlar shows one row: "Hatırlatmalar hazır" or "Dikkat gerekiyor".
- The Bugün banner appears only when reminders cannot work: reminders off, notifications blocked, or no exact alarms. Battery optimization alone does not trigger it, because it is on by default for every app and would make the banner permanent.

---

## Phase 5 — UX, accessibility and polish

### Plan
1. **Visual review with real renderings.** A development-only screenshot test (`ScreenshotTest`, runs only when `MEDITAKIP_SCREENSHOTS` is set) renders every main screen in light and dark to `app/build/screenshots/`. Findings drive the fixes below.
2. **Fix the Bugün timeline spacing.** Large gaps appeared between dose cards.
3. **Brand:** an adaptive launcher icon with a monochrome layer (Android 13 themed icons), the same mark in onboarding, an app window theme, and a splash screen through platform attributes (Android 12+). No new library.
4. **First run:** onboarding ends with "İlk kişiyi ekleyin" → person form → medication form → a snackbar that leads to Hatırlatma Durumu. Skippable.
5. **Accessibility checks as tests:**
   - Every main screen at font scales 1.0, 1.3, 1.5 and 2.0 on a 320 dp phone: the primary action stays reachable and at least 48 dp tall.
   - Every clickable element has a text or description.
   - WCAG AA contrast for the text/background colour pairs of both themes.
6. **Glossary** in `DESIGN.md`, applied to all strings.
7. **Clean-up:** remove unused strings and code (lint `UnusedResources` once), plus prototype leftovers.

### Decisions
**D5.1 Review the UI through real renderings.**
- `ScreenshotTest` draws each screen's view tree to a PNG with Robolectric's native graphics, in light and dark. `captureToImage` times out under Robolectric.
- It is a development aid, skipped unless `MEDITAKIP_SCREENSHOTS` is set, so it never slows or flakes the normal suite.
- It found the Bugün timeline gap: the status rail used `IntrinsicSize.Min`, which over-measured the text. The connector is now drawn with `drawBehind`, so rows keep their natural height.

**D5.2 Brand: a two-tone capsule mark, used everywhere, with no library.**
- It is an adaptive icon (background, foreground and a monochrome layer for Android 13 themed icons), drawn inside the 66 dp safe zone.
- The same mark is the notification icon and the onboarding hero.
- The window theme `Theme.MediTakip` sets a window background matching the Compose theme, so there is no white flash.
- The Android 12+ splash screen uses the platform attributes `windowSplashScreenBackground` and `windowSplashScreenAnimatedIcon`: brand green with the mark. The `core-splashscreen` library would only add the same splash on Android 8–11, and that is not worth a dependency.

**D5.3 First-run setup is offered, not forced.**
- Onboarding's last step says "İlk kişiyi ekle" (primary) or "Daha sonra". Choosing it opens the person form, then the medication form. After the first medication, a snackbar offers "Kontrol et", which opens Hatırlatma Durumu.
- The flag is stored in preferences, so the flow resumes if the app is closed midway. Closing either form ends the guided flow.
- "Daha sonra" relies on the Bugün empty state, which already leads to Kişi Ekle.
**D5.4 Accessibility is checked by tests, not by eye.**
- `ContrastTest` computes the WCAG 2.1 ratio of every text/background pair the app uses, in both themes: 4.5:1 for text, 3:1 for status markers. The existing palette already passes; no colours changed.
- `AccessibilityTest` renders 10 main screens at font scales 1.0, 1.3, 1.5 and 2.0 on a 320 dp phone (40 cases). The primary action must be reachable. Every tappable element must have a text or TalkBack label and a layout size of at least 48 × 48 dp.
- The size check uses the element's layout size, not its visible part, because an element half scrolled out of a list is full size once the user reaches it.
**D5.5 Clean-up and documented lint exceptions.**
- Lint found 60 unused strings, all from earlier designs. They were deleted.
- `MedicationViews.history` was unused since the History table replaced it. It was removed, and its two tests now run against `HistoryMatrices`.
- `app/lint.xml` ignores two checks, with reasons:
  - `PluralsCandidate`: Turkish uses the singular after numbers ("2 ilaç").
  - `UseKtx`: style-only suggestions.

**D5.6 Glossary.**
- `DESIGN.md` now has the glossary table (use and never-use terms).
- Strings were aligned to it: "aktif ilaç" became "ilaç kullanımda"; the follow-up settings text uses "alındı olarak işaretlenmedi"; onboarding says "Tam Zamanında Hatırlatma", matching the status screen.

---

## Phase 3 — Daily-use features and schema v2

### Plan
1. **Schema v2 in one migration (1→2)** (developer): dose-log `status` and `recordedAt`, a revision-order column (R12), stock fields. Migration test first, then features.
2. Log status plumbing: taken or skipped, the actual time and the recording time. Every screen and reminders treat a skipped dose as "handled".
3. **Bugün:** `Atla` with an optional short reason; `Saati değiştir` on a taken dose (late marking); `Geri Al` for both.
4. **Geçmiş:** tapping a cell allows `İçildi olarak işaretle` (with time), `Atla`, `Saati değiştir` and `Kaydı kaldır`, with a confirmation for past days. The legend gains `Atlandı`.
5. **Stock (optional per medication):** quantity, amount per dose, low threshold. İçildi decrements and Geri Al restores. `Stok ekle`. Bugün shows "Parol azalıyor · ~5 gün". One notification when the threshold is reached.
6. Repeat the Phase 5 accessibility matrix for the new screens and dialogs. (Required by the new order: Phase 5 ran first.)

### Decisions

**D3.1 Migration 1→2 adds columns only; nothing is rebuilt or deleted.**
- `logs`:
  - `status TEXT NOT NULL DEFAULT 'TAKEN'`: every existing record was a taken dose.
  - `recordedAt INTEGER NOT NULL DEFAULT 0`, then `UPDATE logs SET recordedAt = takenAt`: for old records the best available value.
  - `note TEXT`: the optional skip reason, NULL for existing rows.
- `schedules`: `sequence INTEGER NOT NULL DEFAULT 0`, then `UPDATE schedules SET sequence = rowid`. This keeps today's rowid order as permanent data (R12).
- `medications`: `stockEnabled` (0), `stockQuantity` (0.0), `stockPerDose` (1.0), `stockThreshold` (0.0). Stock is off for everyone.
- `ALTER TABLE … ADD COLUMN` never touches existing values, so there is no copy-and-swap and no risk of losing rows. The migration runs inside Room's transaction: it either fully succeeds or leaves version 1 untouched.

**D3.2 Migration test without `room-testing`.**
- The developer pre-approved `room-testing`. It is not needed.
- The test creates a database with the **exact v1 statements** from the committed `1.json`, inserts realistic v1 data (people, archived items, revisions including a never-effective one, logs) with plain SQL, then opens it through Room with `MIGRATION_1_2`.
- Room validates every table against the v2 entities when it opens the database, and throws if the migration left anything different. The test then checks every row and value.
- `MigrationTestHelper` would read schema JSON from instrumentation assets, which unit tests cannot easily provide.
- Result: one fewer dependency, and an equally strict check.

**D3.3 A skipped dose is a log row with status `SKIPPED`.**
- `takenAt` is `NOT NULL` in v1. Making it nullable would need a table rebuild in SQLite, so for a skipped dose it holds the moment of skipping (same as `recordedAt`).
- The app never shows `takenAt` for skipped doses: `Occurrence.takenAt` is `null` for them and `Occurrence.skipped` is `true`.
- Reminders, the next-dose highlight and "remaining" counts treat taken or skipped as **handled**.

**D3.4 Progress counts handled doses.** "3 / 5 tamamlandı" counts taken and skipped doses, because both are decisions the user has made. The rows themselves say which is which (✓ İçildi or Atlandı).

**D3.5 Late marking.**
- `İçildi` stays one tap and records "now". A taken row gets `Saati değiştir`: a time picker, default the current recorded time.
- The chosen time is stored as `takenAt` on the dose's own date. `recordedAt` stays the moment the entry was made, so late marking can be told apart.
- In Geçmiş, marking a past dose asks for the time; the default is the planned time.

**D3.6 Correcting history needs confirmation for past days only.** Today's doses behave like Bugün: undo is always available. For earlier days a short dialog names the date before anything is written or removed. "Kaydı kaldır" deletes only that one dose record (as Geri Al does); schedules and other days are never touched.

**D3.7 Stock lives on the medication, not on the schedule revision.**
- Stock is current state, not a definition. It must not be versioned or rolled back by an edit.
- `saveMedication` now **keeps** the existing stock fields, archive state and ids when a schedule is edited. Before this it re-created the medication row with defaults, which would have reset the stock.
- Amounts are decimals, so half tablets work.
- A dose decrements exactly `stockPerDose`, only when a new taken record is actually inserted. A repeated tap or notification action inserts nothing, so it never decrements twice. Undo and "Kaydı kaldır" of a taken dose add the same amount back.
- Stock may go below 0; the UI shows 0. This keeps undo exactly reversible.
- Days left = quantity ÷ (amount per dose × average doses per day over the week). A medication is "low" when a threshold is set (> 0) and quantity ≤ threshold.

**D3.8 One low-stock notification per crossing.**
- When a medication goes low, one notification says "Parol azalıyor" / "Anne · yaklaşık 5 günlük ilaç kaldı". It is neutral and gives no advice.
- It is not repeated until the stock goes above the threshold again (e.g. after `Stok ekle`). The "already notified" set lives in preferences.

**D3.9 Correcting history: actions per state, one window at a time.**
- A Geçmiş cell's detail offers actions based on the dose's state:
  - not marked: `İçildi olarak işaretle` (time picker, default the planned time) and `Atla`
  - taken: `Saati değiştir` and `Kaydı kaldır`
  - skipped: `Kaydı kaldır`
- For past days, the confirmation names the date and the exact change ("22 Eylül 2026, Parol 08:00: İçildi olarak işaretlenecek (saat 08:00)"). Nothing is written before it.
- For Atla, the skip window itself shows that line and serves as the confirmation, so the user never sees two windows in a row.
- Future dates cannot be changed.
- Screens call a small `DoseCorrections` interface: `AppViewModel` implements it, and tests use a recording fake.

**D3.10 A Robolectric test bug, not an app bug.**
- Closing a Compose dialog that contains a text field froze tests whose config set a screen-size qualifier. This is a known Robolectric issue (robolectric/robolectric#8460).
- Removing the text field made the freeze disappear. The same flows pass in a test class without the qualifier (`SkipDialogFlowTest`).
- Layout at small sizes is still covered by the qualifier-based layout and accessibility tests.
