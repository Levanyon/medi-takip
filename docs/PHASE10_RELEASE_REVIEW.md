# Phase 10 — final release-readiness review

Current V3 continuation evidence is recorded in [V3_VERIFICATION.md](V3_VERIFICATION.md).
It supersedes the older UI, safety-copy plaintext, test-count and debug-artifact claims
below. Historical release-AAB validation is not validation of the current V3 worktree.

## Continuation on 2026-10-12 — manual backup security

This section supersedes the historical plaintext-export finding and pre-backup artifact
claims below. Those earlier audit records are retained for traceability, not current
validation. Physical-device and external publication gates remain open.

### Recovered state and completed work

Recovery confirmed HEAD `f8e2db5917e6e892c0cad23f2736497c05824f11`, branch
`ui/premium-redesign-v2`, 9 ahead / 0 behind the local origin reference. Existing uncommitted
work already included targetSdk 36, the unresolved-dose privacy correction, encrypted
backup implementation, password dialogs, legacy warning, regression tests and the device
checklist. Prior JUnit XML recorded 759 passing tests, including backup security; the AAB
was older than the encryption changes. No existing production implementation was rewritten.

This continuation added three missing regressions: invalid salt/IV lengths, unsupported
encrypted inner format/schema versions, and export cancellation cleanup through the real
ViewModel. It completes the final validation, documentation and new release artifact audit.

### Format, cryptography and password handling

Every new manual export uses `MEDITAKIP_ENCRYPTED_BACKUP`, envelopeVersion **1**.
The existing inner `meditakip-backup` format 1 / schema 4 remains unchanged.
The envelope contains exactly format, envelopeVersion, kdf, iterations, salt, cipher, iv
and ciphertext. No names, medical identity, schedules, history, notes or package/stock
data appear in its unencrypted metadata. Ciphertext length still reveals approximate size.

- KDF: platform `PBKDF2WithHmacSHA256`, identifier `PBKDF2-HMAC-SHA256`, **600000**
  iterations, **256-bit** derived AES key.
- Cipher: platform `AES/GCM/NoPadding`, identifier `AES-256-GCM`, **128-bit GCM tag**.
- Fresh `SecureRandom` **16-byte salt** and **12-byte IV** on every export.
- Canonical UTF-8 AAD authenticates format/version/KDF/work factor/cipher/salt/IV.
  Strict metadata allowlisting, exact supported work factor, canonical Base64, bounded
  input and exact V1 salt/IV lengths are checked before key derivation.
- Password confirmation and at least **12 Unicode code points** are required for export;
  spaces and Unicode are preserved, with a 1024 UTF-16-unit input bound. Show/hide and the
  unrecoverable-without-password warning use the existing Material 3 dialog style.
- Passwords are not serialized, saved in preferences/Room/files/BuildConfig, or logged.
  UI text uses non-saveable state and is dropped on submit/disposal. ViewModel-owned
  character arrays, PBEKeySpec password material, derived key bytes and plaintext byte
  buffers are cleared where practical. JVM/Compose immutable strings cannot be reliably
  overwritten; this is not a guarantee of zero residual process-memory copies.

### Restore, compatibility and export failure safety

Encrypted files are recognized and metadata-validated before requesting a password.
Authentication/decryption and all existing schema/domain validation complete before a
preview is offered. Explicit confirmation revalidates at the write boundary, then uses
the unchanged Room replacement transaction. Wrong passwords, tag/ciphertext/metadata
tampering, malformed Base64, unsupported envelope/inner versions and invalid decrypted
data cause zero restore mutation. The UI reports:
`Yedek açılamadı. Parola yanlış veya dosya bozuk olabilir.`
Crypto exceptions contain no provider cause, password, key or medical payload.

Supported legacy plaintext schemas **2/3/4** still use the existing validated restore
path and show `Bu eski yedek şifrelenmemiş. İçeriği hassas bilgiler içerebilir.`
Arbitrary JSON is rejected. Imported originals are never rewritten. Subsequent exports
are encrypted regardless of the imported format.

The complete payload and encrypted envelope are built in memory before the document
destination is opened. **No plaintext temporary export file is created.** Failed writes
attempt destination deletion, falling back to truncation, and do not advance last-backup
time. A truncated envelope cannot authenticate or restore. An unavailable document provider
may also refuse cleanup; provider failure/process-death behavior remains physical QA and
cannot be guaranteed by app-side cleanup alone.

The existing app-private `before-restore.json` rollback safety copy remains plaintext;
it is not a new manual export or temporary export file. Room schema/migrations are unchanged.
Database and preferences are separate commits, not a new cross-storage atomic transaction.
Rejected input leaves both unchanged; existing transaction/rollback tests remain in place.

### Final validation and current artifact

Windows-native PowerShell with `tools/env.ps1` and the existing bundled JDK **17.0.18+8**;
offline Gradle, no toolchain installation and no Java/Gradle processes killed.

- Affected backup/crypto/UI/Settings/screenshot/accessibility selection: **178 passed**,
  zero failures/errors/skips, 8 suites, BUILD SUCCESSFUL (1m37s). This includes BackupTest
  12, EncryptedBackupTest 21, BackupUiTest 6, BackupViewModelTest 5, SettingsFileFlowTest 3,
  Phase5RedesignTest 13, Phase5ScreenshotTest 50 and AccessibilityTest 68.
- Final complete `testDebugUnitTest`: **762 passed**, **0 failures, 0 errors, 0 skipped**,
  **59 suites**, counted from JUnit XML; BUILD SUCCESSFUL (2m40s).
- `MEDITAKIP_SCREENSHOTS=1`: **268 screenshot cases** and **68 dedicated accessibility
  cases** passed. Password/legacy dialogs include light/dark and 320dp/200% text renders;
  representative generated password renders were visually inspected for reachable controls.
  These are native Robolectric render/semantics checks, not physical-device certification.
- `assembleDebug`: **BUILD SUCCESSFUL** (10s).
- `lint`: **BUILD SUCCESSFUL** (52s), **0 errors / 2 existing warnings**:
  VectorRaster (`ic_scan_corners.xml:2`) and UnusedResources (`phase8.xml:55`).
- `bundleRelease`: **BUILD SUCCESSFUL** (44s), 54 tasks, 21 executed / 33 up-to-date,
  using only the unchanged legitimate release signing configuration.
- Cached bundletool **1.17.2** using the established `bundletool.args`: validate **exit 0**.
  JDK jarsigner: **jar verified**, exit 0. Certificate SHA-256 matches `docs/RELEASE.md`:
  `84:E8:0A:AC:3A:16:C7:9B:01:F5:8B:28:23:5C:6F:71:DF:E7:AC:68:96:F6:72:F8:83:F9:9D:06:87:E9:81:7F`.
  Existing untrusted/self-signed chain, absent timestamp, POSIX attributes and
  JarFile/JarInputStream ordering warnings remain. An initial signature command failed
  because PowerShell split an unquoted JVM locale argument; correctly quoting the
  argument resolved the command failure, with no source/signing change.

**Current post-backup-hardening artifact (supersedes every historical hash below):**

- Path: `app\build\outputs\bundle\release\app-release.aab`
- Size: **25,081,073 bytes**.
- SHA-256: `2E427D5508B0F57458240138697ABB9DAD38328E8D08DAA4E80DBAF66424C254`.
- **Release-signed AAB, NOT PUBLICATION-APPROVED.**
- Embedded manifest: applicationId `com.levanyon.meditakip`, versionCode **3**,
  versionName **1.2**, minSdk **26**, targetSdk/compileSdk **36**.
  No debuggable/testOnly enabled. Its dumped manifest is identical to the preceding
  privacy-corrected artifact manifest, including the unchanged permission inventory.

Ignored validation evidence: `build/phase10/backup-targeted.log`,
`backup-targeted-counts.txt`, `backup-full-suite.log`, `backup-full-counts.json`,
`backup-debug.log` and the final build/bundle verification logs in the same directory.

### Scope audit

Production backup changes already present at recovery: `data/Backup.kt`, new
`data/EncryptedBackup.kt`, `ui/AppViewModel.kt`, `ui/BackupUi.kt`, and `res/values/strings.xml`.
The prior targetSdk edit and `domain/NextDose.kt` / `reminders/Reminders.kt` privacy fix are
preserved. No new production changes were necessary during this continuation.

Backup tests: `data/BackupTest.kt`, new `data/EncryptedBackupTest.kt`, `ui/BackupUiTest.kt`,
new `ui/BackupViewModelTest.kt`, `ui/SettingsFileFlowTest.kt`, `ui/Phase5RedesignTest.kt`,
`ui/Phase5ScreenshotTest.kt`. Prior `data/Phase9NotificationTest.kt` work is preserved.
No Room/schema/migration, medication business-rule, planner, scanner/catalog, package
identity, stock arithmetic, manifest/permission, dependency or signing-config change.

Final `git diff HEAD --check`: passed. Tracked diff: **13 files, 320 insertions / 48 deletions**;
five expected untracked files (EncryptedBackup.kt, EncryptedBackupTest.kt,
BackupViewModelTest.kt and the two Phase 10 documents). No staged changes. No artifact,
credential or test output added to the source worktree inventory. HEAD/branch unchanged,
**9 ahead / 0 behind** local origin; no fetch, commit, push, tag or upload.

**Source result:** no remaining source-code blocker identified for this manual-backup
hardening scope. The plaintext-manual-export finding is addressed. Existing out-of-scope
logging/cross-storage limitations documented in the historical audit remain; the app-private
safety copy was deliberately preserved. Publication remains blocked by physical-device QA
and the external Play/version/account gates, not by missing release signing.

All physical-device checklist items remain **NOT YET VERIFIED**, including password/UI
behavior, corrupt-file rejection, provider failure cleanup, legacy import and cross-device
portability. Automated Robolectric checks do not establish Android 16/OEM behavior.

---

## Historical Phase 10 audit records (superseded where stated above)

Scope: Android 16 migration followed by the remaining release audit on 2026-10-11.
This is not production or Google Play publication certification. The initial continued
audit changed documentation only. The subsequent authorized privacy correction changes
NextDose.kt, Reminders.kt and Phase9NotificationTest.kt; its validation is recorded below.

## Baseline and change

Verified clean baseline `f8e2db5917e6e892c0cad23f2736497c05824f11`, branch
`ui/premium-redesign-v2`, 9 ahead / 0 behind the local origin ref.

The original blocker was `targetSdk = 35`. The initial application/build change was
`targetSdk = 36` in `app/build.gradle.kts`. No source behavior change or integer-only
test was added. This file and `PHASE10_DEVICE_QA.md` document the work.

Unchanged: applicationId/namespace `com.levanyon.meditakip`, minSdk 26, compileSdk 36,
versionCode 3, versionName 1.2, AGP 8.9.1, Gradle 8.11.1, Kotlin 2.0.21 and Java 17.
Release retains the non-debuggable build-type default. The subsequent audit built and
inspected the existing release-signed AAB (details below). Debug retains its separate `.debug` application ID.

## Compatibility inspection before editing

Official references:

- [Android 16 target-specific changes](https://developer.android.com/about/versions/16/behavior-changes-16)
- [Android 16 changes for all apps](https://developer.android.com/about/versions/16/behavior-changes-all)
- [Compose predictive back](https://developer.android.com/develop/ui/compose/system/predictive-back)
- [AlarmManager guidance](https://developer.android.com/develop/background-work/services/alarms)
- [Google Play target API requirements](https://support.google.com/googleplay/android-developer/answer/11926878)

| Area | Code inspection result |
| --- | --- |
| Edge-to-edge | MainActivity already calls enableEdgeToEdge. Home uses Scaffold safeDrawing insets and consumes its content padding. Navigation applies horizontal/bottom safeDrawing insets with zero nested NavigationBar insets. FormScreen handles top/horizontal and scrollable bottom/IME safe areas; onboarding uses safeDrawingPadding. No old opt-out or manual decor-fit override. No duplicate padding added. |
| Predictive back | Home, Settings, History, onboarding and FormScreen use AndroidX Compose BackHandler. Medication editors and scanner use FormScreen; confirmation uses Compose Dialog dismissal. No legacy onBackPressed override, KEYCODE_BACK interception or global predictive-back opt-out. Gesture progress animations are not newly implemented or device-certified. |
| Large screens | No orientation/aspect-ratio/resizability restrictions or restricted-resizability opt-out. Existing constrained-width/adaptive/scrolling Compose layouts and responsive tests retained. Rotation and window recreation remain manual QA. |
| Fixed-rate scheduling | NOT APPLICABLE: no scheduleAtFixedRate, ScheduledThreadPoolExecutor, Timer or TimerTask in production source. |
| Background jobs | NOT APPLICABLE to app reminder architecture: no JobScheduler, WorkManager, JobService or DownloadManager use in production source. |
| Alarms/reminders | Existing AlarmManager alarm-clock + inexact safety/fallback path retained. Permission/channel checks, immutable explicit PendingIntents, occurrence-specific URI identities and unlocked confirmation are unchanged. Boot, time/timezone, package replacement and exact-alarm permission restoration remain in the existing receiver. No new foreground service, scheduler or background polling. |

No target-36-specific source correction was identified. No dependency, toolchain,
permission, Room/schema, backup, scanner/package, stock or medication-rule change was made.
The existing foreground Home clock is not a new background reminder scheduler.

## Validation

Native Windows PowerShell, established `tools/env.ps1`, bundled JDK 17.0.18+8.
Full suite invoked with `MEDITAKIP_SCREENSHOTS=1` so screenshot tests execute rather
than skip. Sequential validation completed successfully:

- `./gradlew.bat --offline testDebugUnitTest`: **716 passed, 0 failures, 0 errors,
  0 skipped**, counted from 57 JUnit XML suite reports; BUILD SUCCESSFUL (1m 59s).
- All **256 screenshot cases** passed (including 6 Phase 9 cases). Screenshot generation
  was enabled; ignored outputs remain under app/build. This is existing render/layout
  coverage, not a new visual baseline comparison or API 36 system-UI test.
- **68 AccessibilityTest cases** passed; Phase 9 responsive/accessible-control checks
  also passed within its 6 screenshot cases. No separate test task was needed.
- `./gradlew.bat --offline assembleDebug`: **BUILD SUCCESSFUL** (1s).
- `./gradlew.bat --offline lint`: **BUILD SUCCESSFUL** (18s), **0 errors, 2 existing
  warnings**: VectorRaster (scanner vector dimensions) and UnusedResources
  (scan_review_schedule). The prior OldTargetApi warning is gone.
- `git diff HEAD --check`: passed. Production source, manifest, Room/schema and backup
  diffs are empty. The only tracked diff is the one-line targetSdk change.
- Final worktree: modified app/build.gradle.kts; untracked docs/PHASE10_DEVICE_QA.md
  and docs/PHASE10_RELEASE_REVIEW.md. Nothing staged; no commit or push.

No Java/Gradle process was manually killed. No test was weakened or changed.

## Manifest and release boundary

Regenerated debug manifest: minSdk 26, targetSdk 36, versionCode 3, versionName
1.2-debug, application ID com.levanyon.meditakip.debug. Debug is intentionally debuggable.
Permission inventory remains CAMERA, POST_NOTIFICATIONS, RECEIVE_BOOT_COMPLETED,
SCHEDULE_EXACT_ALARM (maxSdk 32), USE_EXACT_ALARM and the AndroidX internal signature
DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION. No INTERNET/storage/location/contact or
foreground-service permission was added. Source manifest is unchanged.

API 36 device/emulator execution has not occurred. The existing Robolectric SDK
configurations and screenshot tests cannot certify Android 16 system behavior.
See [manual device checklist](PHASE10_DEVICE_QA.md), all items initially unverified.
The remaining audit and release artifact inspection are recorded below. No commit,
push, tag, upload, new key or new credential was performed.

## Target migration result and subsequent correction

The **target API 36 configuration blocker is resolved**. No additional Android 16
source compatibility blocker was identified by this inspection and automated regression run.
The subsequent content audit found a separate existing notification privacy defect.
The original finding is retained below, followed by the authorized correction and its
fresh validation. Publication remains blocked by unperformed physical-device QA and
unresolved public-distribution backup-security and Play policy gates. Existing release
signing is available and is not a missing setup gate in this environment.

## Original source blocker (pre-fix audit record)

### HIGH — dose privacy refresh fails open when current data cannot resolve a notification

Locations: `Reminders.kt:217-243`, `domain/NextDose.kt:24-30`,
`data/Backup.kt:184-190`, `domain/ReminderPlan.kt:38-39` (under the main package).

`updatePrivacy` recovers the old notification. It rewrites sensitive title/body/style/
sortKey only inside `NextDose.resolve(...)?.let`. If resolution returns null it still
reposts the old content at line 243.
PRIVATE visibility and a generic publicVersion do not sanitize the notification itself.

A concrete accepted input is a valid imported schedule whose nonblank unique ID is
`s:legacy`. Backup validation allows the colon; the reminder planner creates its normal
occurrence key and considers it outstanding. `NextDose.resolve` requires exactly three
colon-separated pieces and returns null. After a detailed reminder is posted, switching
to Gizli retains its identity-bearing text. Cleanup does not cancel it because it is
still outstanding. `updateSummaries` at lines 187-196 can copy that title into a summary.

A second accepted state is an archived person with an unarchived medication and active
schedule. Restore validates references but not that archive invariant. The planner
filters medication archive status but not person archive status; NextDose rejects the
archived owner. This also allows inappropriate reminders for that restored owner.
Normal UI archive operations close the medication schedules, so this is an import/model
boundary defect rather than the normal archive flow.

Original evidence was a static end-to-end source trace, not an executed reproduction test.
The eight existing privacy regressions exercise real production notification/repository
paths, but do not cover these resolution failures. Earlier Phase 9 certification must
therefore not be treated as an unconditional privacy guarantee.

Smallest proposed correction: cancel or replace with wholly generic content whenever a
dose cannot resolve, before reposting; add real restore-to-refresh regression coverage.
Also align accepted IDs/archive invariants with occurrence resolution and reminder
planning so imported valid data remains actionable and archived owners remain excluded.
The later authorized correction below implements resolver support and fail-closed
presentation without changing accepted backup data or reminder planning semantics.

## Manifest, permissions and component exposure

Inspected source, merged debug/release manifests and the manifest inside the produced AAB.

| Permission | Justification |
| --- | --- |
| CAMERA | User-initiated barcode/DataMatrix scanner. |
| POST_NOTIFICATIONS | Medication, renewal, stock and next-dose notifications. |
| RECEIVE_BOOT_COMPLETED | Restore reminder scheduling after reboot. |
| SCHEDULE_EXACT_ALARM, maxSdk 32 | Exact-alarm capability on Android 12/12L. |
| USE_EXACT_ALARM | Precise medication reminders on newer Android; Play eligibility remains an external gate. |
| Application DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION | AndroidX signature-level receiver protection. |

No INTERNET, network-state, storage, location, contacts, microphone, Bluetooth or
foreground-service permission in the release manifest. No permission diff was introduced.

MainActivity is exported for MAIN/LAUNCHER. Notification confirmation routes through this
activity, not a new exported confirmation component. Reminder/action receivers and the
report FileProvider are non-exported; the provider grants only selected cache report URIs.
Camera metadata, ML Kit discovery/init, GoogleApiActivity, AndroidX startup, Room service,
and DataTransport components are non-exported. ProfileInstallReceiver is exported but
requires the platform DUMP permission. No unprotected internal component was identified.
The debug-only test ComponentActivity is absent from release. DataTransport's transitive
JobService/alarm receiver exist; they are not a parallel app reminder scheduler.

## Secrets, logging, storage and backup

No hardcoded credential/private key/signing material was found in inspected tracked
source/configuration. Signing uses external ignored properties; credential contents were
not printed. The key-generation script contains templates, not an embedded key, and was
not executed. No barcode-to-command/URL execution, WebView, mutable PendingIntent or
cleartext network path was identified. No normal person/medication Logcat call was found.

**MEDIUM hardening finding:** `reminders/ReminderLog.kt:14,20` writes raw exception
messages to Logcat. No actual patient-data log leak was demonstrated, but exception
payloads are not structurally guaranteed identity-free. Proposed follow-up: safe event
codes/allowlisted diagnostic fields instead of raw exception messages. Not fixed here.

**HIGH public-distribution security decision: manual backups are plaintext JSON.**
`BackupCodec.encode` (`data/Backup.kt:45-69`) and `Backups.export` (`:230-233`) use ordinary
UTF-8 serialization, with no encryption, password protection or authenticity check.
Exports include people/names/notes, medication names and dosing schedules, completion/
skipped history, prescription dates, stock values, package GTIN/serial/batch/expiry and
quantities, and application settings. The private before-restore safety copy is also
plaintext. A schema/checksum-style validation is not cryptographic authentication.

The UI explicitly warns that the file contains health information and is unencrypted
(`res/values/strings.xml:250`); a system document picker chooses the destination. This
is not automatically a blocker for informed personal/internal use with trusted storage.
For general distribution it remains a release-security decision gate: document and
approve the threat model/handling and disclosure, or separately design authenticated
encryption. This report does not claim Google Play categorically forbids every plaintext
user-selected export. No encryption or password UI was added.

Room SQLite and MODE_PRIVATE preferences have no application-level encryption; they rely
on Android sandbox/device storage protections. `allowBackup=false`, legacy backup rules,
and both cloud-backup/device-transfer extraction rules exclude root/database/sharedpref/
file/external domains. The design does not intentionally upload medical DB/preferences.
OEM behavior and user-selected document providers still need manual verification.

Restore supports format 1 / schema 2 through 4 with defaults for older prescription and
package fields. It rejects unsupported versions, oversized inputs (10 MiB), deep/duplicate
JSON keys, malformed types, invalid ranges/references and duplicate package identities.
It revalidates before writing, verifies an AtomicFile safety copy, and replaces DB records
inside a Room transaction. Malformed input does not partially replace the DB; existing
rollback tests passed in the prior suite. DB and preferences are separate storage commits,
not a single cross-storage transaction. The accepted model invariant gaps above remain.

## Room, integration, scanner and notifications

Room 2.6.1, schema 4; exported schema files 1-4 are present. Explicit 1→2→3→4 migrations
are registered with no destructive fallback. Schema 3 adds prescription lifecycle fields;
schema 4 adds GTIN/packages and unique GTIN+serial indexing. Migration tests compare retained
people, medications, schedules, logs and stock from older versions; no data-loss migration
path was identified. This audit introduced no DB, backup-format or business-rule diff.

Reviewed onboarding, people/detail, medication editor/detail, Today/history, taken/undo,
settings, treatment/prescription lifecycle, scan/catalog/package/stock, backup and reminder
integration. Transactional completion/undo and package receipt remain the existing write
paths. No additional integration blocker was identified beyond the restored-data issue.

Barcode parsing is bounded and validated; malformed/unknown input has safe manual fallback.
It cannot launch arbitrary URLs or commands. Catalog information does not choose dose or
offer medical advice. Quantity remains user-confirmed. GTIN+serial duplicates are rejected;
new serials represent separate packages; EAN13 without serial is not unique package identity.

One ReminderPlan/AlarmManager engine owns actual reminders and the inexact safety fallback.
No polling scheduler or fake foreground service. Boot/time/timezone/package replacement
restoration remains in the existing receiver. Immutable explicit PendingIntents use distinct
occurrence/action URIs. Locked İçildi routes to app confirmation with current-model/keyguard
checks; retained legacy TAKEN broadcast is inert. No direct locked completion path found.

Renewal and stock Gizli content uses the approved generic wording; detailed mode rebuilds
current model content. Public versions are generic. Next-dose is silent/low importance;
refresh uses only-alert-once. Master OFF/SECRET, Gizli/PRIVATE and detailed/PUBLIC mappings
remain intact. Settings refresh does not mark history or alter medical/person/stock/package/
prescription/treatment data, though normal reminder rebuilding maintains runtime bookkeeping.
The pre-fix unresolved-dose refresh exception is addressed by the correction below.

## UI, dependencies and remaining device coverage

Existing screenshot/accessibility coverage includes light/dark, narrow/320dp, large text,
long names, empty states, semantics and reachable controls. Passing render checks do not
prove TalkBack focus, real touch interaction, OEM notification privacy, Android 16 gesture
navigation/insets, tablet resizing, process recreation or real reminder timing. Every
device checklist item remains unchecked; no physical/emulator run was claimed.

Declared CameraX/ML Kit, Compose, lifecycle, Room and coroutine dependencies serve existing
features. JUnit/Robolectric/ui-test are test-only; ui-test-manifest is debug-only. No concrete
duplicate runtime library, incompatible dependency or required upgrade was established.
This is a source/build audit, not a complete supply-chain vulnerability certification.
Minification and resource shrinking remain disabled intentionally; configured ProGuard
files are inactive. No dependency churn occurred.

## Pre-fix release compilation and artifact inspection (superseded artifact)

Existing external signing configuration was present and passed the release signing gate.
No key/password was created, copied into source or exposed; debug signing was not reused.
The release certificate fingerprint matches the established fingerprint in docs/RELEASE.md.

`tools/env.ps1` then `./gradlew.bat --offline bundleRelease`: **BUILD SUCCESSFUL**, 1m16s,
54 tasks (52 executed, 2 up-to-date). Its built-in lintVital steps ran; the full lint/test/
debug validation was not rerun. Build warning: some native libraries packaged without
stripping; no compile failure.

- Artifact: `app\build\outputs\bundle\release\app-release.aab`
- Type: **release-signed AAB**, QA artifact only, **NOT approved for publication**.
- Size: **25,067,202 bytes**.
- PRE-FIX SHA-256: `4C2F73D956992715BA89230775FD5773232F0EFC329A52D15F9DF5F1A2AADA8A`.
  This is not the corrected release candidate; see the new artifact below.
- Application ID: `com.levanyon.meditakip`; versionCode **3**, versionName **1.2**;
  minSdk **26**, targetSdk/compileSdk **36**.
- Embedded manifest has neither debuggable nor testOnly enabled; release permissions
  match the inventory above. Debug test activity is absent.
- Cached bundletool 1.17.2 validation succeeded. Jarsigner reports `jar verified` (exit 0).
  It also warns about self-signed/untrusted certificate chain, no timestamp, unsigned POSIX
  attributes and JarFile/JarInputStream ordering differences (manifest not first). These
  warnings are recorded, not hidden; bundletool accepts the bundle. Installation/Play
  processing has not been exercised and is not inferred from signature verification.
- All packaged arm64-v8a/x86_64 ELF LOAD segments have 16 KiB alignment. Two 32-bit
  barhopper libraries have 4 KiB alignment. No 64-bit ELF alignment blocker was found;
  this static inspection is not an installation test on a 16 KiB-page device.

## Repository hygiene and publication gates

Expected worktree: targetSdk one-line edit, NextDose.kt, Reminders.kt,
Phase9NotificationTest.kt and these two Phase 10 documents. Build,
bundletool helper and test outputs stay under ignored build/. No tracked/untracked release
artifact, key, credential, local.properties, test output or unrelated temporary file was
identified for commit. .gitignore excludes build/.tools/.gradle/.idea/local.properties and
key/signing files. It does not blanket-ignore .env files; none was found in this audit.
Baseline remains unchanged, 9 ahead / 0 behind local origin; no remote fetch was performed.

Publication gates, collected together:

1. Review the completed dose privacy correction and fresh validation below.
2. Complete and record device/OEM QA, including API 36 and supported-schema upgrades.
3. Resolve the plaintext medical-backup public-distribution security decision.
4. Confirm Google Play USE_EXACT_ALARM eligibility for this app's core functionality;
   its restricted-permission review was not performed by building the bundle.
5. Complete applicable health-app declaration, privacy policy/Data safety disclosures,
   and verify Play app-signing/upload-key registration and account requirements.
6. Check the highest previously distributed/uploaded versionCode before publication;
   code 3 must not be reused for an update if already consumed. Current Play state unknown.

Official policy references: [exact-alarm permissions](https://support.google.com/googleplay/android-developer/answer/16558241),
[health declaration](https://support.google.com/googleplay/android-developer/answer/14738291),
[health content policy](https://support.google.com/googleplay/android-developer/answer/16679511).

Non-blocking findings/limitations: raw exception logging risk; DB/preferences restore is
not cross-storage atomic; unstripped native-library and signature-tool warnings; .env ignore
gap; docs/RELEASE.md:62,88 still says SDK 35/schema 2 and needs later documentation correction.
These were not silently fixed. No other source blocker was established by this inspection.

## Authorized privacy correction — current result

**A) SOURCE CODE / AUTOMATED QA: SOURCE CODE READY FOR PHYSICAL QA.** The identified
stale-dose/summary privacy blocker is corrected and regression-tested. No known remaining
source privacy defect was identified in this correction review. This is not a claim of
completed OEM/device verification.

**B) PUBLICATION: BLOCKED** pending physical-device QA, the plaintext-backup security
decision and the external Play/version/account gates listed above. Signing is available.

### Root cause and implementation

Accepted backup IDs are nonblank/unique strings, not necessarily UUIDs or colon-free.
They remain accepted unchanged. NextDose.resolve previously split the entire occurrence
key into exactly three pieces. It now reads the date between the last two separators,
then matches the entire unchanged occurrence key against current Schedule.day output.
It still rejects malformed, handled, inactive or archived-owner occurrences. This preserves
identifier semantics and restore compatibility without loosening current-model matching.

Reminders.updatePrivacy now resolves a dose before recovering/reposting its notification.
If resolution fails (including a null tag), it cancels that dose and stops processing it.
No old title/body/style/sort key is reused as a fallback in either privacy mode. Successfully
resolved doses continue rebuilding their identity from the current snapshot.

Old summaries are not recovered/reposted during privacy refresh. updateSummaries independently
resolves its candidates against the same current snapshot and constructs identity text from
those occurrences, never from cached notification titles. Unresolved doses contribute nothing.
A group with fewer than two resolved doses loses its summary, including the all-unresolved
case. Gizli summaries remain generic; detailed summaries use current model names. Returning
to detailed mode cannot resurrect a canceled notification shell.

The existing planner's handling of accepted archived-owner combinations is not changed:
such data can still appear in its candidate events, but unresolved notification presentation
is canceled and excluded from summaries on rebuild. No medical records are deleted or repaired
as a side effect. No business/scheduling/backup validation policy was changed in this narrow fix.

Production files changed: domain/NextDose.kt and reminders/Reminders.kt. Test file changed:
data/Phase9NotificationTest.kt. All paths are under the existing main/test package.
The prior app/build.gradle.kts targetSdk 36 change is preserved. Both Phase 10 docs are updated.
No scanner, catalog, package tracking, stock arithmetic, Room/migration, backup serialization,
permissions, UI/screenshot/accessibility source or signing configuration changed.

### Fresh validation after correction

Windows PowerShell with tools/env.ps1 and the bundled JDK; no Java/Gradle processes killed.

- Targeted testDebugUnitTest filters: Phase9NotificationTest **36**, MedicationNextDoseTest
  **4**, ReminderIntegrationTest **13**, BackupTest **12**: **65 passed**, zero failures,
  errors or skips (BUILD SUCCESSFUL, 21s).
- Four added regressions use real Backups.inspect/restore, repository and Reminders.rebuild.
  One covers accepted multi-colon schedule IDs, public/private content and current-data detail
  restoration. Three cover unresolved archived-owner data with zero, one or two remaining
  valid doses: cancellation, summary removal/reconstruction, identity absence, both privacy
  transitions, and unchanged complete repository snapshots/dispatch records.
- Full testDebugUnitTest: **720 passed**, **0 failures, 0 errors, 0 skipped**, **57 suites**,
  counted from JUnit XML; BUILD SUCCESSFUL (1m52s). Existing renewal/stock/SECRET regressions
  remain intact. No test was weakened.
- Existing full-suite grouping included **256 screenshot cases** and **68 dedicated
  AccessibilityTest cases**, all passed. MEDITAKIP_SCREENSHOTS=1 enabled that grouping;
  no separate screenshot/accessibility task or source change was made.
- assembleDebug: **BUILD SUCCESSFUL** (1s).
- lint: **BUILD SUCCESSFUL** (16s), **0 errors / 2 existing warnings**: VectorRaster at
  ic_scan_corners.xml:2 and UnusedResources at phase8.xml:55.
- bundleRelease: **BUILD SUCCESSFUL** (10s), 54 tasks (13 executed, 41 up-to-date), using
  the existing external release signing setup without creating/modifying credentials.
- Existing cached bundletool 1.17.2 validation method: **exit 0**. Jarsigner: **jar verified,
  exit 0**; the same certificate-chain/self-signed/timestamp/POSIX/JarInputStream-ordering
  warnings recorded for the pre-fix artifact remain. No upload/install is implied.

### Corrected artifact (supersedes the pre-fix hash)

- Path: `app\build\outputs\bundle\release\app-release.aab`
- Type/status: **release-signed AAB, NOT PUBLICATION-APPROVED**.
- Size: **25,067,498 bytes**.
- SHA-256: `57AE33D01E9FFFE31F331D9EE8F803B636189CCCC9F7BED954DE4D214D5E884E`.
- Embedded manifest: applicationId `com.levanyon.meditakip`, versionCode **3**, versionName
  **1.2**, minSdk **26**, targetSdk/compileSdk **36**, no debuggable/testOnly enabled.
  Permission inventory unchanged.

The old hash `4C2F73D956992715BA89230775FD5773232F0EFC329A52D15F9DF5F1A2AADA8A`
is historical only and must not be used to identify this corrected candidate.

Final diff review is limited to the expected Gradle edit, two production files, one test
file and two Phase 10 documents. git diff HEAD --check passed. Baseline HEAD remains
f8e2db5917e6e892c0cad23f2736497c05824f11, branch ui/premium-redesign-v2, 9 ahead / 0 behind
the local origin ref. Nothing staged/committed/pushed/tagged/uploaded. All manual checklist
items remain unchecked; plaintext-backup and physical-device gates remain open.
