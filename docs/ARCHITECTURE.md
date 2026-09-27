# MediTakip 1.2 architecture

Native offline Android app, minimum Android 8 (API 26). A single Gradle application module uses Compose Material 3, ViewModels with StateFlow, repositories, and Room. No network permission or cloud backup.

## Layout

`data/`: Room entities, DAO, database and repository.
`domain/`: pure date, schedule and occurrence rules.
`reminders/`: AlarmManager integration and recovery receiver.
`ui/`: four destinations — Bugün (`Home.kt`), Kişiler with person and medication detail (`People.kt`, `Medications.kt`), Geçmiş (`History.kt`) and Ayarlar (`Settings.kt`) — plus editors (`Editors.kt`) and shared components.

Rules for working on the code are in `CLAUDE.md`; the verified current state is in `docs/AUDIT.md`.

## Data relationships

Person (1) → Medication (many) → Schedule revision (many).
Each revision captures medication name, dose, note, selected weekdays and times, date range, and its effective period. Changes retire the previous revision and create a replacement. Older schedule definitions remain available for history. "Arşivle" archives the person/medication and stops future reminders; "Arşivden Çıkar" restores it with a new revision effective from today. A revision closed on its first day (`effectiveTo = effectiveFrom − 1`) is "never effective". Person names are always read from `people`, never from the revision's `personName` copy. Completion records reference a revision, local date and scheduled minute, recording an actual UTC completion instant. A unique occurrence key makes Taken idempotent; Undo clears completion.

History derives scheduled occurrences from versioned definitions, including dates when the app was closed. No dosage interpretation, recommendations or missed-dose instructions exist.

## Reminders

AlarmManager schedules the next outstanding event. A receiver posts dose notifications grouped by person, with İçildi and 10 dk ertele actions, then schedules the next alarm. Persisted dispatch records prevent duplicates. An optional retry is a separate event derived from the original occurrence and suppressed after Taken/Skipped. Recovery runs on boot, package replacement, wall-clock changes, time-zone changes, exact permission grant and app resume. Wall-clock schedules follow the device's local zone; completion timestamps remain absolute instants. DST gaps shift forward and overlaps use the earlier offset once.

The sideloaded app uses USE_EXACT_ALARM on API 33+ and SCHEDULE_EXACT_ALARM only through API 32. setAlarmClock is paired with an inexact safety alarm. Exact access is checked before each scheduling call. Without access, use an inexact idle-allowed alarm and explain potential delays in Settings. Notification runtime permission is requested from a user action. Force-stop, powered-off devices, notification blocking and manufacturer restrictions cannot be bypassed. No foreground service, network, contacts or broad storage access is needed.

## Verification gates

1. Base project: assembleDebug.
2. Room/repository: JVM tests and assembleDebug.
3. People/medication editors: assembleDebug.
4. Today/Taken: tests and assembleDebug.
5. Notifications/recovery: tests, lint and assembleDebug.
6. History/settings: tests and assembleDebug.
7. Final: testDebugUnitTest, lintDebug, assembleDebug, assembleRelease; physical-device checklist remains explicit.

## Current data and backup boundaries

Room schema 2 keeps the explicit MIGRATION_1_2; both schema files remain committed. Schedule sequence is persisted, and logs store TAKEN/SKIPPED, takenAt, recordedAt and optional notes. Stock writes and status transitions share repository transactions.

BackupCodec uses strict, bounded JSON (format 1, schema 2) with explicit property names, relation validation and revision order. Backups.inspect never writes. A confirmed restore validates again, captures/flushes/verifies an AtomicFile safety copy within the same Room transaction before replacing all tables. SharedPreferences is a separate store, not cross-store atomic with SQLite; preferences are applied after the Room commit. NonCancellable work prevents normal ViewModel cancellation from interrupting this sequence, but sudden process/device death remains a physical recovery test. See DECISIONS D4.6.

ViewModel file work runs on Dispatchers.IO. SAF restricts pickers to local providers; no broad storage or network permission. Alarm delivery/actions and restore share the existing reminder mutex. Successful restore clears old visible notifications and schedules future events without a historical backlog. Scheduling/platform cleanup failures are recorded in ReminderLog, not falsely described as a database rollback.

DoctorReports selects current active medications and recorded occurrences for today and the previous 29 days using Schedule.day. ReportPdf paginates with Android PdfDocument/StaticLayout. Only cache/reports is exposed through the non-exported ReportProvider with a temporary read grant on explicit share. Backups and databases are never FileProvider paths. Old report cache files are removed on subsequent report generation after 24 hours; cache can also be removed by Android. Auto Backup and automatic device transfer remain disabled.