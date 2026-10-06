# Phase 3 — People and Person Detail review

Branch: `ui/premium-redesign-v2`  
Accepted baseline and unchanged HEAD: `5db1a55506e7f7cb8d49edffa2461374dfa99e48`  
Implementation remains uncommitted. Inspection confirmed a clean starting tree.

## Files

Modified:

- `app/src/main/java/com/levanyon/meditakip/ui/People.kt`: screen composition and read-only projections.
- `app/src/main/java/com/levanyon/meditakip/ui/Home.kt`: forwards the existing live date to both screens.
- `app/src/main/java/com/levanyon/meditakip/ui/Components.kt`: optional size/primary treatment for the existing Avatar; defaults retained.
- `app/src/main/res/values/strings.xml`: removes four labels made obsolete by this redesign.
- `app/src/test/java/com/levanyon/meditakip/ui/AccessibilityTest.kt`: approved labels, existing assertions retained.
- `app/src/test/java/com/levanyon/meditakip/ui/ScreenLayoutTest.kt`: approved labels and compact schedule text, existing actions retained.
- `app/src/test/java/com/levanyon/meditakip/ui/UiFlowTest.kt`: approved labels and scrolling to archived people before opening them.

Added:

- `app/src/main/java/com/levanyon/meditakip/ui/PeopleComponents.kt`.
- `app/src/main/res/values/people.xml`.
- `app/src/main/res/drawable/ic_people_back.xml`.
- `app/src/main/res/drawable/ic_people_chevron.xml`.
- `app/src/main/res/drawable/ic_people_plus.xml`.
- `app/src/main/res/drawable/ic_people_people.xml`.
- `app/src/main/res/drawable/ic_people_medication.xml`.
- `app/src/test/java/com/levanyon/meditakip/ui/PeopleTestData.kt`.
- `app/src/test/java/com/levanyon/meditakip/ui/PeopleRedesignTest.kt`.
- `app/src/test/java/com/levanyon/meditakip/ui/PeopleScreenshotTest.kt`.
- This review document.

## Approved Figma nodes

[MediTakip Phase 3](https://www.figma.com/design/XXHVX8smUwa5kVRcsfzm0d/MediTakip-V2-%E2%80%94-Phase-3-People)

| Screen | Light | Dark |
|---|---|---|
| People | 3:38 | 3:151 |
| Person Detail | 3:88 | 3:201 |
| People Empty | 8:104 | 8:197 |
| Person Detail Empty | 8:143 | 8:236 |

Design context and screenshots were inspected for all eight nodes. The five in-scope SVG icons were downloaded and converted with the already-installed Android `Svg2Vector` tool. Their intrinsic dimensions are preserved: back/chevron 20dp, plus 18dp, empty-people 28dp, empty-medication 26dp. Native resources contain no remote asset references.

## Behavior and presentation

1. **People hierarchy:** 20dp page padding, 28dp top padding, 32sp heading, 15sp subtitle, compact Ekle action, 12dp list spacing.
2. **Person card:** 96dp minimum height, 20dp radius, 1dp Phase 1 outline, 16dp padding, 48dp avatar, 14dp body gap, 20dp chevron. Real name, active medication count, and Today status; no fixture data in production.
3. **Today state:** `Schedule.day` supplies doses and `DailyProgress.from` supplies completion. Taken and skipped doses use the same handled definition as Today. Empty days say “Bugün planlanan doz yok”; no false completion or 0/0 progress. Home supplies its date so summaries refresh on date changes and snapshot updates.
4. **Person Detail:** compact back control, 52dp avatar, name/count, Today summary, Add Medication, active medication group, quiet edit action. Existing notes, stopped medications, archives, restore, and report access remain available.
5. **Medication group:** one outlined surface with thin separators; 16sp names, actual times/dose and selected weekdays, 40dp Rx badge. Chips say Aktif or Stok az. Only `StockPlan.low` determines low-stock status. Stopped medications remain in their own section with their existing Durduruldu treatment.
6. **Empty states:** approved text, local decorative icons, working add-person or add-medication callbacks. Person Detail has one Add Medication action and does not duplicate it inside its empty state.
7. **Responsive behavior:** content remains scrollable; long names wrap without ellipsis. `AdaptivePair` moves status/progress text beneath the leading content when needed. Existing centered 840dp reading width and scaffold/navigation insets remain in use.
8. **Dark mode:** uses the existing Phase 1 palette, with dark green surfaces and jade accents. No new raw colors or theme replacements.
9. **Accessibility:** clickable person/medication rows merge their text; medication rows expose a status description; progress exposes its range and summary. Add, edit, and back controls have labels; targets are at least 48dp. Decorative initials/icons do not add redundant announcements. Layout tests inspect text geometry at large font scale.

## Screenshot review

[Local gallery](../app/build/screenshots/phase3/index.html) — 32 PNGs in `app/build/screenshots/phase3` (generated, git-ignored).

Each of these has light and dark renders:

- `peopleMultiple`, `peopleEmpty`, `peopleNarrow`, `peopleLargeFont`.
- `people360`, `people412`, `peopleExpanded`.
- `personDetail`, `personEmpty`, `personLowStock`, `personLongMedication`.
- `personLargeFont`, `personNarrow`, `personExpanded`.
- Additional `personLargeFont-medications` and `personLargeFont-bottom` scroll positions.

Requested light/dark, empty, narrow, low-stock, long-medication, and large-font states were visually inspected against the Figma references. Checks covered hierarchy, spacing, wrapping, border weight, chip size, dark surfaces, and navigation clearance. Screenshots use synthetic fixtures and the real Phase 1 navigation component.

## Validation

PowerShell, existing repository toolchain, no dependencies added:

```powershell
. .\tools\env.ps1
$env:MEDITAKIP_SCREENSHOTS = '1'
.\gradlew.bat --offline assembleDebug testDebugUnitTest lint
```

Final rerun: **BUILD SUCCESSFUL**.

- Debug APK assembled successfully at `app/build/outputs/apk/debug/app-debug.apk`.
- **360 tests passed; 0 failures, 0 errors, 0 skipped.** This includes 288 behavioral/unit checks and 72 screenshot render cases. The new Phase 3 suites contribute 32 behavioral cases and 28 render cases (32 Phase 3 PNGs including extra scroll positions).
- Accessibility: 68 passed; screen layout: 33 passed; design system: 6 passed; Phase 2 Today regression suite: 24 passed; navigation/UI flows: 10 passed.
- `lint`: passed; fresh `lint-results-debug.txt` says **No issues found** (0 errors, 0 warnings). The four unused-label warnings from the intermediate run were resolved by removing obsolete resources, with no suppression.
- `git diff --check`: passed. Branch and accepted HEAD remain unchanged; no commit was created.
- No regressions detected by the full automated suite and scoped visual inspection.

## Deliberate visual differences and limitations

- Existing Phase 1 palette equivalents are retained; container, muted-text, and outline values therefore differ slightly from the Figma sample palette.
- Existing Phase 1 navigation icons, selected treatment, size, and spacing remain unchanged, as requested.
- Back, Ekle, edit, and empty-state actions have at least 48dp touch targets; Add Medication retains the 56dp primary-action minimum. These exceed some Figma control heights.
- Scanning is omitted entirely. Add Medication occupies the full action width.
- Schedule summaries show stored clock times and dose/weekday text rather than guessing “Sabah” or using sample frequencies. Counts and medication order come from application data.
- Notes, report access, stopped medications, and archives remain below the approved core hierarchy when applicable, preserving existing functionality absent from the Figma examples.
- Text and rows grow at large font scale; the existing navigation may scroll horizontally at 2x font size. Tests verify navigation reachability.
- Screenshot tests render review artifacts; they do not perform pixel-baseline comparison. Visual comparison was manual. No physical-device TalkBack or Samsung interaction test was performed in this session.

No business-logic files, manifest, dependencies, schema, or preferences were changed. No barcode/camera work or later redesign phase was started. No known unresolved implementation defects were identified during the reviewed checks. Stop after Phase 3 for user review.
