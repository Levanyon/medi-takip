# Phase 8 — barcode, reference catalog and package receipts

Base: `977b3fc5ae7f2388ec04fdcf626beaa37aab7672`, branch `ui/premium-redesign-v2`.
The starting working tree was clean. No Phase 9, release work, commit or push is included.

## Inspection and scanner technology

Inspected Room v3, repository transactions, stock completion/undo math, shared MedicationEditor,
MedicationDetail, PersonDetail/Home navigation, Phase 6 permission launchers, Gradle and the approved
Figma screens before implementation. Stock remains a per-medication unit balance, not a count of boxes.

Official stable dependencies verified on 2026-10-09:

- `androidx.camera:camera-camera2:1.6.2`
- `androidx.camera:camera-lifecycle:1.6.2`
- `androidx.camera:camera-view:1.6.2`
- `com.google.mlkit:barcode-scanning:17.3.0` (bundled model)

Sources: [CameraX release table](https://developer.android.com/jetpack/androidx/releases/camera),
[ML Kit Android barcode scanning](https://developers.google.com/ml-kit/vision/barcode-scanning/android).
CameraX AAR metadata requires compileSdk 36 and AGP 8.9.1. Only compileSdk changes from 35 to 36;
the existing AGP, Kotlin, minSdk 26, targetSdk 35 and application version remain unchanged.
Google Code Scanner is not used. CameraX supplies the custom preview and lifecycle; direct ML Kit
image analysis enables DATA_MATRIX and EAN_13 only. No camera image/video is persisted.

Camera permission is composed/requested only inside the explicit Person Detail scan flow. First use
shows the MediTakip rationale; denial offers retry, permanent denial opens this app's settings.
Settings return/resume rechecks permission. No-camera devices and camera errors retain manual input.
The manifest adds optional camera/autofocus features and CAMERA only; transitive INTERNET and
ACCESS_NETWORK_STATE permissions are removed because the bundled flow needs no runtime network.
No location, storage or contact permission is introduced. Notification/exact alarm handling is unchanged.

ImageAnalysis uses KEEP_ONLY_LATEST and closes each ImageProxy after ML Kit completes, including errors.
Only this screen's use cases are bound/unbound. Lifecycle binding stops the camera in background;
disposal unbinds preview/analysis, switches off torch, closes the scanner and stops its executor.
Disposed asynchronous callbacks cannot navigate. Torch is enabled only when cameraInfo reports support.
An atomic per-session gate accepts one valid result. Raw result, parse-date anchor, receipt UUID,
acknowledgements and editor route are saved across recreation. No physical camera is used in tests.

## Approved design

[Figma file](https://www.figma.com/design/XXHVX8smUwa5kVRcsfzm0d)
nodes inspected with design context and screenshots:

- Permission: `12:124`, `12:143`
- Scan: `12:162`, `12:181`
- Result: `12:200`, `12:230`
- Scanned prefill: `19:621`, `19:716`
- Person Detail action placement: `3:88` (Medication Actions `12:104`)

Figma back, camera, scan corners, flash, check, barcode and prefill icons were downloaded and converted
with the installed Android Svg2Vector converter. No remote asset URLs occur in production resources.
Existing theme tokens/components and shared MedicationEditor are reused. Scan sits beside Add on normal
phones and stacks at large text/narrow widths. Scanner screens scroll and buttons are at least 48dp.
The approved preview is 470dp at normal font size, expanded at larger sizes for readable instructions.
The result adds real GTIN/serial/lot/expiry and safety confirmation controls required by this phase.
The prefill banner shows the real identity and the safety note remains “Doz bilgisi barkoddan belirlenmez.”
Only the product name is prefilled; the user enters dose and explicitly confirms the existing form's
schedule/treatment dates. No catalog field generates a prescription, quantity, schedule or recommendation.

## Parser rules

MedicineBarcode is standalone Kotlin. GTIN is ASCII digits with a validated GS1 check digit.
EAN-13 is zero-padded to 14; GTIN-14 leading zeroes and nonzero packaging indicators are preserved.
All-zero, malformed, non-product payloads, duplicate/unsupported AIs, missing GTIN, overlong fields and
invalid dates fail safely. A decoded GS1 product is not automatically called a medicine.

Supported AIs: 01 (14 digits), 21 (serial, 1–20 characters), 17 (YYMMDD), 10 (lot, 1–20 characters).
Supports `]d2`, initial ASCII GS, ASCII GS separators, Turkish 01→21→17→10 order with required separators,
other valid orders, absent optional fields and parenthesized manual HRI input. Variable fields terminate
at GS (or a parenthesized AI in manual HRI), never by guessing that embedded `17` or `10` starts a field.
An unseparated short terminal serial is consequently treated as a serial; hidden fields cannot be recovered.
Unknown AIs are deliberately rejected rather than discarded. Serial/lot support is printable ASCII;
parentheses are reserved for manual HRI boundaries.

Expiry parsing uses strict LocalDate/YearMonth validation and a 49-years-back/50-years-forward century
window anchored when the scan session starts. Day 00 retains month precision (`YYYY-MM`), displays “gün
belirtilmemiş”, and compares expiry using that month's end. Invalid days never roll into another month.
An expired package requires acknowledgement before saving; missing expiry displays a box-check message.
Expiry is package data, never copied into treatment end or prescription renewal.

References: [GS1 AIs](https://www.gs1.org/gs1-application-identifiers),
[AI definitions](https://ref.gs1.org/ai/?lang=en),
[GS1 month-end semantics](https://ref.gs1.org/standards/genspecs/15.0.0/).
Month-only support is for legacy/product codes; it does not assert that a code complies with current
regulated-healthcare labelling rules, which require a valid day from January 2025.

## Catalog source, version and limits

Source: [Tip-Atlasi-Projesi/ilaclardb](https://github.com/Tip-Atlasi-Projesi/ilaclardb).
Pinned commit: `6211e27aeb1c48f77a7d2c3c0858ecec60a8505b` (2026-07-26).
Snapshot date: 2026-10-09. Exact source: `data/json/ilac.json.zip` at that commit.
License: CC0-1.0, verified from the pinned LICENSE and included as `assets/catalog/LICENSE.txt`.
The upstream README names a different path; the actual archive was located using the pinned Git tree.

The import keeps only GTIN and product name. No clinical descriptions, dosage text, e-commerce scraping,
or unsupported claims of official authority are included. This is an explicitly labelled community
reference catalog. The user must compare the product identity with the physical box.

`tools/import-catalog.py` reproducibly downloads that pinned archive using Python's standard library,
validates check digits, canonicalizes GTIN, deduplicates identical names and excludes conflicting names.
22,087 input rows yield 21,848 products; 149 invalid rows are excluded, with zero conflicting GTINs.
The remaining row-count difference is identical duplicate rows. The bundled JSON is about 1.8 MB.

Archive SHA-256: `47f13231578fb938e1c10286802d721f9899690ea6635f108055653a408a0ec8`.
Snapshot SHA-256: `dd8535ec8327a78568fff8973f410cebdca5307912ed5406b32c95ce347a5e9f`.
The manifest records provenance, counts, license, date and checksums. Runtime lookup checks integrity,
version and unique canonical keys, loads on Dispatchers.IO, and has no network path. Unknown codes and
unavailable/corrupt catalogs permit manual continuation with the scanned GTIN; names are never invented.
MedicationCatalog and the preview are injectable in tests.

## Room v3 → v4, receipts and stock

Explicit registered MIGRATION_3_4 adds nullable medications.gtin and a packages table with indexes.
No existing column or row is rewritten. People, medication state, stock, schedules/treatment dates,
logs, renewal data, dispatches and preferences are preserved. No destructive fallback is added.
Room's generated `4.json` is included; previous schema exports are unchanged.

MedicationPackageEntity stores receipt ID, medicationId FK, canonical GTIN, nullable serial/lot/expiry,
added timestamp and quantityAdded. It records an acquisition, not a dispensing/allocation model.
Active/consumed state is not invented: existing stock is an aggregate and doses cannot truthfully be
assigned to a box. Month/exact precision is retained in the ISO expiry string.

GTIN+serial is globally unique through a database unique index and transaction-level checks. Same code
with a new serial is a new package; the same physical package cannot be added for another person or
medication. Receipt UUID also rejects double submission, including after recreation. A fresh EAN-only
scan has no serial uniqueness guarantee and requires an explicit “not already added” confirmation.
SQLite permits multiple null serials, reflecting that product codes cannot identify individual boxes.

If GTIN already belongs to a medication for the selected person, the user chooses the existing medication;
there is no auto-created duplicate treatment. Multiple matches are never guessed. Archived matches point
to restore; matching uses stored GTIN only, never fuzzy names. Previously manual medications with no GTIN
cannot be automatically matched. New medication + package receipt + stock addition commit atomically.

Existing tracked medications require an explicit unit amount (0 records metadata only). Disabled tracking
allows metadata only, explains this, and directs the user to Edit to enable stock. A new scanned medication
uses the existing optional stock form: the entered initial unit balance is added exactly once and recorded
on the receipt. No quantity is inferred from a product name. Nonfinite/negative additions are rejected.
Dose completion, undo, skip, per-dose conversion, thresholds and low-stock calculations are unchanged.

## Backup

Envelope format stays 1; schemaVersion becomes 4. Medication GTIN and package receipts are included;
bundled catalog rows are excluded. Schema 2/3 import continues with null GTIN/empty package defaults,
retaining the established prescription defaults for schema 2. Schema 4 strictly validates field types,
references, canonical codes, unique serial identities, expiry precision and finite nonnegative quantity.
Restore clears/inserts in FK-safe order inside the existing transaction and safety-copy flow.
Restoration never re-adds quantityAdded to stock: the saved medication balance is authoritative.

## Verification

Targeted development checks cover parser/GTIN/separators/expiry, exact and unavailable catalog,
duplicate prevention, new serials, atomic creation, dose-stock regressions, migration preservation,
old/new backups, actual permission launcher/settings return, fake-camera navigation/debounce,
saved-state restoration and scanned prefill. Native screenshot cases cover permission, scanner,
result and prefill in light/dark at 390dp, 320dp/200% font and 1200dp (24 cases, 48 images).
All screenshots/logs/tool intermediates remain under ignored build directories.

Initial targeted issues: Gradle's Windows transform cache rename was locked; stopping the stale daemon
and using a fresh process resolved it without changing project logic. A JUnit test accidentally returned
assertThrows' result instead of Unit; corrected. A new test expected an obsolete dose helper string;
it now asserts the actual empty editable value. No existing behavioral assertion was weakened.

Final validation on 2026-10-09 used `tools/env.ps1`, `MEDITAKIP_SCREENSHOTS=1` and exactly one
`./gradlew.bat --offline assembleDebug testDebugUnitTest lint` invocation. It ended BUILD FAILED:
674 tests ran, 673 passed, one failed, with zero errors and zero skipped tests. The failure was
`ScreenLayoutTest.archivedPersonDetailOffersOnlyRestoreAndShowsArchivedMedications` at line 256.
Phase 8 had appended `scan` after `restore` in PersonDetail, redirecting existing trailing restore
lambdas to the scan callback. Moving `scan` before `restore` preserves that existing call behavior;
the regression test and assertions remain unchanged. The correction was subsequently compiled and
verified by the focused post-fix validation below; the full suite was not rerun.

All 24 ScannerScreenshotTest cases passed and generated 48 images, including light/dark, 390dp,
320dp at 200% font and 1200dp. Their checks cover reachable actions/back controls and minimum 48dp
touch targets. All 68 AccessibilityTest cases passed; ScreenLayoutTest passed 32 of 33 cases.
These automated checks are not a physical-device or TalkBack audit.

That initial task graph stopped at testDebugUnitTest before assembleDebug and lint completed.
The later post-fix pass below successfully produced the debug APK and current lint reports.
`git diff HEAD --check` passed. Screenshots, APKs, reports and the local tool environment are ignored,
with no tracked files under app/build, build or .tools. Room's generated v4 schema is present.
The merged debug manifest contains CAMERA, the existing reminder permissions and AndroidX's internal
receiver permission; it has no location, storage, contacts or network permission. Camera permission
is requested only within the explicit scan route. The catalog snapshot checksum matches its manifest.
No commit, push or Phase 9 work was performed.

### Post-fix validation (2026-10-09)

Used the bundled `tools/env.ps1` environment with `MEDITAKIP_SCREENSHOTS` unset. The initial offline
attempt compiled the correction and assembled Debug, but lint could not resolve the uncached existing
ML Kit transitive dependency `androidx.exifinterface:exifinterface:1.0.0`; targeted tests had not run.
A network-enabled retry resolved that dependency without changing dependency declarations. Report
inspection then found the exact People method filter omitted its parameterized cases. The final
offline pass used a method-suffix wildcard and verified all four intended cases:

- `ScreenLayoutTest.archivedPersonDetailOffersOnlyRestoreAndShowsArchivedMedications`: 1 passed.
- `PeopleRedesignTest.detailEmptyKeepsAddEditBackWithoutFakeProgress*`: 2 passed (`dark=false/true`).
- `ScannerFlowTest.personDetailOffersScanOnlyForActivePerson`: 1 passed.

The final command was:

```powershell
./gradlew.bat --offline assembleDebug testDebugUnitTest --tests "com.levanyon.meditakip.ui.ScreenLayoutTest.archivedPersonDetailOffersOnlyRestoreAndShowsArchivedMedications" --tests "com.levanyon.meditakip.ui.PeopleRedesignTest.detailEmptyKeepsAddEditBackWithoutFakeProgress*" --tests "com.levanyon.meditakip.ui.ScannerFlowTest.personDetailOffersScanOnlyForActivePerson" lint
```

Result: BUILD SUCCESSFUL; 4 passed, 0 failures/errors/skips. `assembleDebug` and `lint` passed in the
same final invocation. Lint reports 0 errors and 10 warnings: target API, seven dependency-update
notices, the scanner corner vector dimensions, and an unused scanner string. These remain nonblocking;
no unrelated cleanup or dependency upgrade was made. `git diff HEAD --check` passed.

No production or test code changed during post-fix validation; only this review was updated.
No full-suite rerun or screenshot regeneration occurred. The original 673 passing tests plus the
focused regression validation leave no known unresolved Phase 8 defect. Physical-device QA and the
documented product limitations below remain. Git is on `ui/premium-redesign-v2`, ahead of its remote
by 7 existing commits, with 17 modified tracked files and 26 untracked files; nothing is staged.

## Known limits

- Physical-device camera focus, torch, background/recreation and offline ML Kit delivery still require
  device QA; Robolectric verifies injected flows and Android permission launchers, not optical decoding.
- Community catalog coverage and correctness are limited by the pinned upstream snapshot. No authenticity,
  prescription validity or clinical suitability is inferred from a match.
- Missing separators cannot be reconstructed safely; only the supported AI subset is accepted.
- EAN/product-only scans cannot prevent reuse of the same box across independent scan sessions.
- Package receipts do not allocate consumption, automatically expire stock, or adjust existing history.
- There is no catalog update download, box transfer, or package editing subsystem in this phase.
- Existing platform deprecation warnings in tests remain; no warning suppression is introduced.
