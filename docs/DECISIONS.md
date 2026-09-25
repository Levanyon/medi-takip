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
