# MediTakip V1

Native offline Android app, minimum Android 8 (API 26). A single Gradle application module uses Compose Material 3, ViewModels with StateFlow, repositories, and Room. No network permission or cloud backup.

## Layout

`data/`: Room entities, DAO, database and repository.
`domain/`: pure date, schedule and occurrence rules.
`reminders/`: AlarmManager integration and recovery receiver.
`ui/`: Today, People, History, Settings and editors.

## Data relationships

Person (1) → Medication (many) → Schedule revision (many).
Each revision captures medication name, dose, note, selected weekdays and times, date range, and its effective period. Changes retire the previous revision and create a replacement. Older schedule definitions remain available for history. Deletes archive the person/medication and stop future reminders. Completion records reference a revision, local date and scheduled minute, recording an actual UTC completion instant. A unique occurrence key makes Taken idempotent; Undo clears completion.

History derives scheduled occurrences from versioned definitions, including dates when the app was closed. No dosage interpretation, recommendations or missed-dose instructions exist.

## Reminders

AlarmManager schedules the next outstanding event. A receiver posts grouped due notifications and schedules the next alarm. Persisted dispatch records prevent duplicates. An optional retry is a separate event derived from the original occurrence and suppressed after Taken. Recovery runs on boot, package replacement, wall-clock changes, time-zone changes, exact permission grant and app resume. Wall-clock schedules follow the device's local zone; completion timestamps remain absolute instants. DST gaps shift forward and overlaps use the earlier offset once.

Exact access is optional and checked before each scheduling call. Without access, use an inexact idle-allowed alarm and explain potential delays in Settings. Notification runtime permission is requested from a user action. Force-stop, powered-off devices, notification blocking and manufacturer restrictions cannot be bypassed. No foreground service, network, contacts or broad storage access is needed.

## Verification gates

1. Base project: assembleDebug.
2. Room/repository: JVM tests and assembleDebug.
3. People/medication editors: assembleDebug.
4. Today/Taken: tests and assembleDebug.
5. Notifications/recovery: tests, lint and assembleDebug.
6. History/settings: tests and assembleDebug.
7. Final: testDebugUnitTest, lintDebug, assembleDebug, assembleRelease; physical-device checklist remains explicit.
