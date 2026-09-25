# MediTakip — rules for Claude Code

Stable rules only. Visual design details live in `DESIGN.md`; scope and order of work live in `docs/ROADMAP.md`; the current state of the code is described in `docs/AUDIT.md`.

## Product

- Offline Android app for a family to track medications. Primary user: a non-technical mother managing her own and her child's medications on one Samsung phone.
- Judge every change by one question: does it make daily use clearer and reminders more reliable, without risking her data?
- Turkish is the only UI language. Never give medical advice (UI text, notifications, reports). The app only records what the user entered.
- Out of scope: cloud, accounts, sync, analytics, ads, Firebase, paid APIs, drug databases, dosage guidance.

## How to work

- Follow `docs/ROADMAP.md` one phase per session. At the start of a phase, read the code and present a plan (files, schema impact, risks, tests), then wait for approval. Never start the next phase without explicit approval.
- Explain important decisions in plain language (the developer is learning).
- Small increments: build, test and commit after each one. Update the ROADMAP checkboxes at the end of a phase and give a manual Samsung test checklist.
- No new dependencies (including test dependencies) without asking first: purpose, alternative without it, size/risk. Prefer Android/Jetpack built-ins.
- No new Android permissions without asking.

## Stack

Kotlin, Jetpack Compose Material 3, one `AndroidViewModel` (`ui/AppViewModel.kt`), Room 2.6 via KSP, single module `:app`, minSdk 26, targetSdk 35.
Packages: `data` (Room, repository, preferences) · `domain` (pure Kotlin, no Android imports) · `reminders` (AlarmManager, receiver) · `ui` (Compose).

## Data safety (absolute)

- Never use destructive migrations or `fallbackToDestructiveMigration`. Never silently reset data.
- Every schema change: bump the version, write an explicit `Migration(n, n+1)`, add a migration test, and commit the new schema JSON in `app/schemas/`. Never edit a committed schema JSON. Current schema: version 2 (`MIGRATION_1_2`, tested by `MigrationTest` against the committed `1.json`).
- Never hard-delete anything that has history. Removing a person or medication means archiving it ("Arşivle"). Restoring it ("Arşivden Çıkar") adds a new revision effective from today and never reopens or edits a past revision. Restoring a person does not restore their medications.
- Schedules are versioned. An edit closes the current revision (`effectiveTo`) and inserts a new one. Never update a past revision in place.
- A revision with `effectiveTo = effectiveFrom − 1` is "never effective" (it was edited or archived on the day it was created). It is valid data: never "repair" or delete it. See `isNeverEffective`.
- Pick the newest revision only with `MedicationViews.latestRevision`. It uses the persistent `sequence` column (schema v2; new revisions get max + 1). The DAO reads schedules `ORDER BY sequence, rowid`.
- Dose records have a status: `TAKEN` or `SKIPPED` (with an optional note). `recordedAt` is when the entry was made, and `takenAt` is the actual time. A dose has at most one record.
- Stock changes only through `Repository.Completions` (a newly inserted TAKEN record decrements it once; removing it restores it) and `addStock`. An edit without stock settings keeps the stock.
- Person names are always read from `people` (use `Occurrence.personName`). The column `schedules.personName` is still written but never read.
- Logs (dose records) are keyed by revision, scheduled date and scheduled minute. `takenAt` is the actual instant in epoch millis. Dates are epoch-day, times are minutes-of-day. Use `java.time` and never hardcode a timezone.
- Past history must never change because of a later edit, rename, deactivation or archive. This includes what screens display for past dates.

## Single source of truth

- Expected doses for a date come only from `domain/Schedule.day` (or its Phase 1 successor). Today, History and Reminders must all use it. No screen computes schedules on its own.
- The History matrix comes from `domain/HistoryMatrices.build`. It is read-only and must never create or modify records.

## Navigation (do not regress)

- Exactly four destinations: **Bugün · Kişiler · Geçmiş · Ayarlar**. There is no top-level "İlaçlar". Medications live under a person: Kişiler → Kişi Detay → İlaç Detayı.
- Bugün:
  - Shows a person selector, then that person's doses for today.
  - Only the earliest untaken dose is highlighted, but every pending dose has its own **İçildi** button.
  - Marking a dose shows a Snackbar with **Geri Al**, and taken rows keep a Geri Al button.
- Kişi Detay is for management only and never repeats today's timeline.
- Ayarlar contains only settings.

## Forms (do not regress)

- Add/edit forms use `FormScreen` (`ui/ScreenComponents.kt`). It is drawn in the activity window, never in a `Dialog` window, because dialog-window insets hid the Save button under Samsung three-button navigation.
- **Kaydet** sits inside the scrollable content, after the fields.
- Apply bottom safe-drawing insets (navigation bar, gesture area, IME) inside the scroll. Do not use a pinned bottom bar or guessed fixed padding.
- Small confirmations and details use `MediDialog` (the platform dialog frame).
- Common fields come first. Rarely used ones go under "Diğer seçenekler".

## History (do not regress)

- A person selector (shared with Bugün, persisted in preferences) sits above a date × (medication, time) matrix.
- The date column stays fixed while the medication columns scroll horizontally together, sharing one `ScrollState`.
- A small table is centered. Columns scale with font size, and no column may become unreachable.
- Cell states: ✓ with the actual time, ○ not marked, – not planned. Tapping a cell opens its details.
- A past cell must show the name and dose that were valid on that date.

## Terminology (Turkish glossary)

- Button: **İçildi**. Undo: **Geri Al**.
- History state: **Alındı olarak işaretlenmedi**. Taken: "✓ İçildi • 08:04".
- Planned: **Planlanan**. Inactive medication: **Durduruldu**. Archive: **Arşivle**. Skip: **Atla**, state **Atlandı**. Stock: **Stok ekle**, low **Azalıyor**.
- Never use threatening or judgmental wording.

## Accessibility

- Touch targets are at least 48 dp; primary actions at least 56 dp.
- Status is never shown by color alone; always add an icon and text.
- Screens must work at font scales 1.0 to 2.0, on 320 dp wide screens and in landscape. Nothing clipped or hidden behind system bars.
- Provide meaningful TalkBack descriptions.

## Windows build (native only — never WSL, Linux, Docker or a VM)

The JDK, Gradle distribution and Android SDK live in the git-ignored `.tools\` folder. There is no system Java. In PowerShell, from the repo root:

```
. .\tools\env.ps1
.\gradlew.bat assembleDebug --offline
.\gradlew.bat testDebugUnitTest --offline
.\gradlew.bat testDebugUnitTest --offline --tests "com.levanyon.meditakip.domain.*"
.\gradlew.bat lint --offline
```

- The debug APK is written to `app\build\outputs\apk\debug\app-debug.apk`.
- If Kotlin reports bogus "Unresolved reference" errors after a source file is deleted, add `-Pkotlin.incremental=false`.
- Robolectric tests run with an English JVM locale (a Conscrypt workaround); the app itself always forces Turkish.
- During development, run targeted tests. Run the full suite and `assembleDebug` once at the end of a phase. Run lint only when needed.

## Release signing

- The phone runs the **release** build, signed with the key in `%USERPROFILE%\AndroidKeys\meditakip\` (certificate SHA-256 `84:E8:0A:AC:...:87:E9:81:7F`, full value in `docs/RELEASE.md`). Never create, replace or rotate this key.
- Never read, print, copy or commit `keystore.properties` or the `.p12` file. The developer runs `tools\new-release-key.ps1` themselves.
- `assembleRelease` must fail when no key is configured. Never make it produce an unsigned or debug-signed release.
- Before handing over an APK, check with `apksigner verify --print-certs` that its SHA-256 matches `docs/RELEASE.md`. Never leave an APK signed with any other key in `app\build\outputs\apk\release`.
- Debug builds are a separate app (`.debug` suffix). Real use always gets the release APK, installed over the existing app without uninstalling.

## Git

- Branch `main`. The baseline tag is `baseline-v1`.
- Commit messages are imperative and explain *why*.
- Never commit `.tools/`, build output, `local.properties`, keystores or signing passwords.
