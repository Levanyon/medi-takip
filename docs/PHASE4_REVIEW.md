# Phase 4 — Medication Detail + Add / Edit

Branch: `ui/premium-redesign-v2`. Accepted HEAD: `f010a8855a7d1b74001513ce7b12826baa835202`.

The branch and HEAD were verified before editing; the initial working tree was clean. All changes remain uncommitted.

## 1. Exact files changed

Modified:

- `app/src/main/java/com/levanyon/meditakip/ui/Editors.kt`
- `app/src/main/java/com/levanyon/meditakip/ui/Home.kt`
- `app/src/main/java/com/levanyon/meditakip/ui/Medications.kt`
- `app/src/main/java/com/levanyon/meditakip/ui/ScreenComponents.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/com/levanyon/meditakip/ui/ScreenLayoutTest.kt`
- `app/src/test/java/com/levanyon/meditakip/ui/UiFlowTest.kt`

Added:

- `app/src/main/java/com/levanyon/meditakip/ui/MedicationComponents.kt`
- `app/src/main/res/values/medications.xml`
- `app/src/main/res/drawable/ic_medication_back.xml`
- `app/src/main/res/drawable/ic_medication_calendar.xml`
- `app/src/main/res/drawable/ic_medication_clock.xml`
- `app/src/main/res/drawable/ic_medication_edit.xml`
- `app/src/main/res/drawable/ic_medication_note.xml`
- `app/src/main/res/drawable/ic_medication_pill.xml`
- `app/src/main/res/drawable/ic_medication_plus.xml`
- `app/src/main/res/drawable/ic_medication_stock.xml`
- `app/src/main/res/drawable/ic_medication_time.xml`
- `app/src/test/java/com/levanyon/meditakip/ui/MedicationNextDoseTest.kt`
- `app/src/test/java/com/levanyon/meditakip/ui/MedicationRedesignTest.kt`
- `app/src/test/java/com/levanyon/meditakip/ui/MedicationScreenshotTest.kt`
- `docs/PHASE4_REVIEW.md`

Generated, git-ignored artifacts: `app/build/screenshots/phase4/`, Gradle reports/APK, and `build/phase4-assets/` (downloaded SVG sources and a temporary SDK converter). No dependency was added. Four obsolete labels were removed after their UI was replaced; no feature was deleted.

## 2. Figma nodes

Source: [Phase 4 — Medications](https://www.figma.com/design/XXHVX8smUwa5kVRcsfzm0d/MediTakip-V2-%E2%80%94-Phase-3-People?node-id=19-105).

| Screen | Light | Dark |
| --- | --- | --- |
| Medication Detail | `19:105` | `19:191` |
| Add Medication | `19:277` | `19:363` |
| Edit Medication | `19:449` | `19:535` |

Design context and screenshots were retrieved for these six nodes. Figma SVG icons were converted with the installed Android SDK's `Svg2Vector`; production resources contain no temporary asset URLs. Scanned Prefill (`19:621`, `19:716`) is not implemented. There is no scan button, camera permission, scanner dependency, lookup, catalog, or barcode navigation.

## 3–5. Detail, Add, and Edit

Detail uses a restrained identity hero, a primary-container dose panel, grouped schedule/date/stock information, and a note surface. Back and edit are available in the header. Existing labeled edit, stock addition, archive/restore, recent history, and correction access are retained. Name, person, dose, note, dates, and state come from the snapshot and latest schedule revision.

Add and Edit continue to share `MedicationEditor`, its existing state, validation, and save callback. The new header, information groups, schedule section, time chips, and existing status chip are composed from Phase 1 tokens/components. The form container gains an optional header slot; other forms retain their existing header.

Add retains the real defaults: blank name/dose/note, one `08:00` time, every day, today's start date, no end date, active, and optional stock disabled. Figma's sample medication, dose, stock, and dates are not inserted into production data.

Edit populates name, dose, note, all times, weekday bitmask, start/end, active state, stock enabled, quantity, per-dose amount, and threshold. The existing Home → ViewModel save/update path is unchanged. Home only passes its existing live clock to Detail. Schedule revision behavior remains unchanged.

## 6. Schedule

Time editing/addition uses the existing native picker, sorting, and deduplication. Removal remains available with its own 48dp control. The day summary expands to the existing every-day/individual-weekday selection; its bitmask behavior is preserved. No frequency engine or alternative recurrence model is introduced.

Existing date-picker restrictions remain: an existing past start is preserved, while newly selected dates cannot precede today; end must not precede start. Clearing the existing end date still stores `null`.

## 7. Stock

Stock uses unchanged `StockPlan.isLow`, `daysLeft`, `format`, parsing, and validation. The warning chip appears only when `isLow` returns true. Quantity, threshold, per-dose consumption, optional tracking, and add-stock callbacks remain functional. Disabling stock preserves its stored amounts.

The Figma example's warning at stock 18/threshold 5 is not reproduced: actual stock state determines the warning. No unit such as “tablet” is inferred from a free-text dose.

## 8. Next dose

`medicationNextDose` is a UI selection helper over `Schedule.day`, not a new occurrence generator. It filters to the current active revision and ignores archived medications/people. Taken and skipped occurrences are excluded using the existing `handled` property.

The earliest outstanding dose today is retained even when its time has passed; its label becomes “Bekleyen doz”. Once today is handled, the next valid weekday is selected, with a date shown for future days. Search starts at the actual schedule/effective start and includes a full weekly recurrence, supporting far-future starts. Ended, inactive, archived, or otherwise unavailable schedules display “Yaklaşan doz yok”. No fake time is substituted.

## 9–11. Responsive, dark mode, accessibility

The shared form scrolls vertically and retains safe-area/keyboard inset handling. Time chips wrap. Long medication names are now multiline editable fields; long names/notes in Detail wrap. Tablet layouts use the existing centered 840dp reading column. The main form has no horizontal scrolling.

Light/dark themes use Phase 1 semantic colors, including surface borders, primary-container panels, and stock warnings. Decorative icons have no content descriptions; their tint follows the theme.

Back/edit/save and time controls are labeled. Interactive targets are at least 48dp. Next-dose information is merged into a readable summary; stock exposes state semantics. Name/dose fields expose invalid state, and the validation summary is an assertive live region. Existing accessibility tests cover 1.0, 1.3, 1.5, and 2.0 font scales at 320dp; new tests include both themes, multiline content, and additional widths.

## 12–13. Omitted fields and exact model gaps

| Approved concept | Existing persistence | Phase 4 decision |
| --- | --- | --- |
| Treatment start | `MedicationScheduleEntity.start: Long` (epoch day) | Retain existing functional date |
| Treatment end | `MedicationScheduleEntity.end: Long?` | Retain existing functional date |
| No end date / indefinite | `end == null` | Retain existing no-end-date behavior; no new flag |
| Prescription date | No field | Omit |
| Prescription renewal date | No field | Omit |
| Renewal reminder lead time | No field | Omit |
| Independent renewal reminder date | No field | Omit |

The future prescription section will need persisted prescription/renewal dates and either a persisted reminder date or defined lead-time data, plus its dedicated scheduling behavior. None is created here.

Schedule dates are revisioned scheduling bounds, not a separate treatment-episode/lifecycle entity. If V2 requires independent treatment lifecycle state, that is a future model decision. The current UI does not claim that such an entity exists. No Room schema, migration, repository, backup format, or user data changed.

## 14. Screenshots and visual review

58 native-rendered PNGs are under `app/build/screenshots/phase4/`. Both light and dark variants are generated for:

- `detail`, `detailLowStock`, `detailLongName`, `detailNoNext`, `detailLargeFont`
- `add`, `edit`, `addNarrow`, `addLargeFont`, `editLargeFont`, `add360`, `edit412`, `editTablet`
- Each form additionally has `-period` and `-save` scroll positions.

These are synthetic fixtures rendered through the real Compose UI. Screenshot tests generate review artifacts and check control bounds/target sizes; they do not claim pixel-baseline comparisons. The requested views are manually inspected against the Figma screenshots.

Required adaptations to retain existing behavior and accessibility: native labeled text fields and date/time pickers; weekday selection instead of inventing a frequency setting; removable times; per-dose stock consumption and tracking toggle; active toggle and revision guidance; date/stock fields stacked for long labels; 48dp controls; existing history/archive/restore actions below the core hierarchy. Existing Phase 1 palette/type roles remain authoritative where their values differ from the Figma sample. Unsupported prescription/renewal content is omitted rather than disabled or faked.

## 15–17. Validation

Final fresh run: **BUILD SUCCESSFUL**.

- **404 tests passed; 0 failures, 0 errors, 0 skipped.** This includes 306 behavioral/unit checks and 98 screenshot render cases across the complete project.
- Phase 4 adds 4 next-dose selection tests, 14 UI/retention/validation tests, and 26 screenshot cases (58 PNGs including scroll positions).
- Existing accessibility matrix: 68 passed. Screen layout: 33 passed. Navigation/UI flows: 10 passed. All other existing unit and regression suites passed.
- Debug build: passed. APK: `app/build/outputs/apk/debug/app-debug.apk`.
- Lint: **No issues found** (0 errors, 0 warnings), `app/build/reports/lint-results-debug.txt`.
- `git diff --check`: passed.
- Protected-path diff check: no changes to data/domain/reminder sources, Room schemas, manifest, or dependencies. The only Home change is the existing clock passed into Medication Detail.

```powershell
. .\tools\env.ps1
$env:MEDITAKIP_SCREENSHOTS = '1'
.\gradlew.bat --offline assembleDebug testDebugUnitTest lint
```

## 18–19. Regressions and limitations

The navigation assertion was updated to verify the new header edit action and scroll to the retained labeled edit action. Tests for hidden advanced fields were replaced with assertions for the now-visible persisted date section; stock/date validation checks remain. Existing tests were not disabled or weakened. Intermediate unused-resource/naming warnings and a stock-section indentation lint error were fixed without suppressions. No regressions or unresolved implementation defects were detected in final automated checks and scoped visual review.

No physical-device TalkBack or IME/device interaction run is claimed. Native Robolectric rendering and automated semantics/layout checks do not replace that testing. The protected domain/data/reminder/history logic and unrelated redesign phases remain untouched.

Stop after Phase 4 for review. No commit is created.
