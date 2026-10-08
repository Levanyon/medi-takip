# Phase 5 — History & Settings review

Scope: Kotlin/Jetpack Compose UI only, on `ui/premium-redesign-v2`, based on accepted HEAD `59813a9b1cfd2aad98b6de0c0b8e006813767d56`. Initial working tree was clean. No commit created. No later phase started.

1. **Exact files changed**

   Modified:

   ```text
   app/src/main/java/com/levanyon/meditakip/ui/BackupUi.kt
   app/src/main/java/com/levanyon/meditakip/ui/History.kt
   app/src/main/java/com/levanyon/meditakip/ui/Settings.kt
   app/src/main/res/values/strings.xml
   app/src/test/java/com/levanyon/meditakip/ui/AccessibilityTest.kt
   app/src/test/java/com/levanyon/meditakip/ui/BackupUiTest.kt
   app/src/test/java/com/levanyon/meditakip/ui/HistoryCorrectionTest.kt
   app/src/test/java/com/levanyon/meditakip/ui/ScreenLayoutTest.kt
   app/src/test/java/com/levanyon/meditakip/ui/SkipDialogFlowTest.kt
   app/src/test/java/com/levanyon/meditakip/ui/UiFlowTest.kt
   ```

   Added:

   ```text
   app/src/main/java/com/levanyon/meditakip/ui/HistoryComponents.kt
   app/src/main/java/com/levanyon/meditakip/ui/HistoryScreen.kt
   app/src/main/java/com/levanyon/meditakip/ui/RestoreConfirmationDialog.kt
   app/src/main/java/com/levanyon/meditakip/ui/SettingsComponents.kt
   app/src/main/res/drawable/ic_phase5_backup.xml
   app/src/main/res/drawable/ic_phase5_bell.xml
   app/src/main/res/drawable/ic_phase5_chevron.xml
   app/src/main/res/drawable/ic_phase5_clock.xml
   app/src/main/res/drawable/ic_phase5_filter.xml
   app/src/main/res/drawable/ic_phase5_history.xml
   app/src/main/res/drawable/ic_phase5_info.xml
   app/src/main/res/drawable/ic_phase5_lock.xml
   app/src/main/res/drawable/ic_phase5_moon.xml
   app/src/main/res/drawable/ic_phase5_restore.xml
   app/src/main/res/drawable/ic_phase5_warning.xml
   app/src/main/res/values/phase5.xml
   app/src/test/java/com/levanyon/meditakip/ui/HistoryFiltersTest.kt
   app/src/test/java/com/levanyon/meditakip/ui/Phase5RedesignTest.kt
   app/src/test/java/com/levanyon/meditakip/ui/Phase5ScreenshotTest.kt
   app/src/test/java/com/levanyon/meditakip/ui/Phase5TestData.kt
   app/src/test/java/com/levanyon/meditakip/ui/SettingsFileFlowTest.kt
   docs/PHASE5_REVIEW.md
   ```

   Generated PNGs, build logs, downloaded SVG sources, and the temporary Android SDK conversion helper are under ignored build directories. No dependency, manifest, persistence, schema, repository, domain, reminder, or other screen business logic changes.

2. **Figma nodes implemented**

   Source: [MediTakip V2, Phase 5 — History & Settings](https://www.figma.com/design/XXHVX8smUwa5kVRcsfzm0d/MediTakip-V2-%E2%80%94-Phase-3-People).

   | View | Light | Dark |
   | --- | --- | --- |
   | History | 25:105 | 25:195 |
   | History empty | 25:285 | 25:329 |
   | History filter sheet | 27:104 | 27:130 |
   | Settings | 25:373 | 25:483 |
   | Permission attention | 25:593 | 25:710 |
   | Restore dialog | 26:104 | 26:118 |

   Figma SVGs were downloaded and converted with the already-installed Android SDK `Svg2Vector` converter. Their original dimensions are retained: 20dp icons, 18dp chevron, 30dp empty-history icon. Native theme tints provide light/dark colors. Existing Phase 1 navigation remains shared with the rest of the app.

3. **History hierarchy** — Geçmiş, “İlaç kullanım kayıtların”, filter action, real person chips, compact recent-period summary, date groups. A secondary “Planlanan dozlar” action retains the existing matrix and unmarked-dose correction functionality.

4. **History grouping** — Newest date first, then scheduled minute, person name, and stable occurrence key. The displayed medication comes from the recorded schedule revision; person names come from current people data. Rows open the existing detail/correction dialog, which includes actual taken time and notes. Logged dates are resolved with `Schedule.day`; no parallel scheduling rules were introduced.

5. **History summary** — Uses unchanged `DailyProgress.from` over `Schedule.day` for today and the preceding six calendar days, limited by the selected person. It counts handled doses, including skipped doses. Therefore the truthful label is “completed / total doz işaretlendi”, with the existing integer percentage and fraction. The summary always says “Son 7 gün”, independently of list date/status filters. No synthetic metrics or stored changes.

6. **Person filtering** — Tümü plus actual active and archived people. IDs drive filtering; names are never sample constants in production. Individual selections retain the existing selection callback; Tümü is local UI state and adds no persistence semantics. Long chips scroll and keep their full accessibility labels.

7. **Filter sheet** — Native modal bottom sheet. Date: Son 7 gün / 30 gün / Tümü. Status: Tümü / İçildi / Atlandı, using existing TAKEN/SKIPPED values. Choices are drafts until “Filtreyi uygula”; dismissal discards drafts. Reopening shows applied choices. Filtering never writes history.

8. **Empty state** — “Henüz geçmiş kaydı yok” / “İlaçları işaretledikçe kullanım geçmişin burada görünür.” A distinct no-match message avoids implying older or filtered records do not exist. Without people, the existing route to Kişiler remains available.

9. **Settings hierarchy** — Ayarlar / “MediTakip deneyimini yönet”; Hatırlatmalar, Görünüm, Veri ve yedekleme, Gizlilik ve bilgi. Reusable grouped rows and sections use Phase 1 shapes, surfaces, border, spacing, and typography with scoped Figma text sizes, following the existing People pattern.

10. **Notification state** — Actual Android access and the existing application toggle determine the row status. The detail dialog retains the app toggle, permission action, reminder-status screen, retry toggle, and 15/30/60/120-minute choices. Permission requests require an explicit action and use `rememberPermissionAccess`.

11. **Exact-alarm state** — Actual access drives the row and attention card. “Aç” calls existing exact-alarm access. Returning from Android settings refreshes through the existing lifecycle handling. With access already granted, the row opens the existing reminder-status screen. No new permissions or engine changes.

12. **Theme** — Tema displays the current persisted System/Light/Dark choice. The dialog preserves all three choices and delegates to the existing settings callback. No persistence changes.

13. **Backup** — “Yedek oluştur” opens the retained last-backup information, local-storage guidance, unencrypted-backup disclosure, due reminder, and safety-copy action. Its create action uses the original local JSON CreateDocument contract and export function. Busy actions remain disabled.

14. **Restore** — “Yedekten geri yükle” uses the original local OpenDocument contract and `inspectBackup`. Validated preview, busy/error handling, safety copy, transactional restore, serialization, schema, and revalidation are unchanged. Selecting a file never auto-restores.

15. **Restore dialog** — Warning styling, restore icon, explicit data-replacement explanation, “Vazgeç” and “Geri yükle”. The existing backup date, counts, record range, and safety-copy explanation remain visible in scrollable content. Cancel/back never confirms. The warning says the operation changes current data rather than claiming recovery is impossible, because the existing implementation keeps a safety copy.

16. **Privacy/info** — Local data wording agrees with the offline implementation and manifest. The detail disclosure explicitly says exported backups are unencrypted. About/version comes from installed package metadata, including the debug suffix; no placeholder version or security guarantee.

17. **Responsive behavior** — Covered at 320, 360, 390, 412, and 1200dp; font scale 2.0 for both content and Android dialog windows. Chips scroll or wrap. Rows grow vertically; values/statuses stack at narrow widths. Dialog actions wrap and content scrolls. Shared navigation remains outside content scrolling and can scroll horizontally at large font scale; tests check Ayarlar remains reachable.

18. **Dark mode** — All Phase 5 variants have native dark renders. Existing Phase 1 colors remain authoritative; there is no second palette or theme.

19. **Accessibility** — At least 48dp interactive targets, meaningful row text and filter descriptions, selectable chip semantics, date/section headings, progress semantics, and text states in addition to color. Decorative icons are silent. Existing 68-case accessibility suite is retained, alongside Phase 5 render semantics checks. Modal filter interactions are additionally verified through accessibility actions because Robolectric pointer coordinates for the separate sheet window were unreliable; no production callbacks were bypassed.

20. **Screenshots generated and reviewed** — 76 native PNGs under `app/build/screenshots/phase5`, from 38 light/dark render cases, including extra scroll positions. Required views inspected against Figma:

   | Required view | Files in that directory |
   | --- | --- |
   | History light/dark | history-light.png, history-dark.png |
   | Empty light/dark | historyEmpty-light.png, historyEmpty-dark.png |
   | Long names | historyLongNames-light.png, historyLongNames-dark.png |
   | Narrow | historyNarrow-light.png, historyNarrow-dark.png |
   | Large font | historyLargeFont-light.png, historyLargeFont-dark.png; bottom captures |
   | Filter light/dark | filter-light.png, filter-dark.png |
   | Settings light/dark | settings-light.png, settings-dark.png |
   | Permission attention | settingsAttention-light.png, settingsAttention-dark.png |
   | Settings narrow | settingsNarrow-light.png, settingsNarrow-dark.png |
   | Settings large font | settingsLargeFont-light.png, settingsLargeFont-dark.png; info captures |
   | Restore light/dark | restore-light.png, restore-dark.png |

   Additional filter/restore large-font and 360/412/tablet cases are generated. These are review artifacts with layout/semantics assertions, not pixel-baseline tests. The native bottom-sheet window backdrop renders black in Robolectric captures; its content is reviewed, but device-level scrim compositing is not claimed.

21. **Tests/results** — Final full run: **466 passed, 0 failures, 0 errors, 0 skipped** (330 behavioral/unit cases and 136 screenshot render cases across the project). Added 8 history-query tests, 13 redesign interaction tests, 3 actual document-picker flow tests, and 38 screenshot/accessibility cases. Existing accessibility: 68 passed; screen layout: 33 passed; navigation/UI flow: 10 passed. Existing matrix/correction tests now target the retained planned-dose screen with their behavioral assertions unchanged; Home navigation tests verify the new route. Restore cancellation now asserts “Vazgeç”. Full log: `build/phase5-final.log`; XML/HTML results: `app/build/test-results/testDebugUnitTest` and `app/build/reports/tests/testDebugUnitTest`.

22. **Debug build** — `assembleDebug` passed. APK: `app/build/outputs/apk/debug/app-debug.apk`.

23. **Lint** — `lint` passed, with “No issues found” (0 errors, 0 warnings). Superseded labels reuse the existing resource IDs; no warnings were suppressed.

24. **Intentional visual deviations** — Existing Phase 1 palette and navigation are retained. Interactive controls are at least 48dp rather than Figma's smaller example sizes. The summary uses the existing handled-dose metric and truthful wording. Dates include the year for unambiguous older history. Unmarked-dose management remains a secondary path. Settings advanced controls stay in detail dialogs. The restore dialog is taller to retain backup metadata and safety-copy disclosure; its warning reflects the actual safety-copy behavior. Narrow and large-font rows/actions reflow rather than clip. The filter sheet uses native modal drag-handle placement.

25. **Regressions** — Protected-path diff is empty. Initial full runs exposed a People restore-message timeout while that test passed unchanged in isolation. The new screenshot tests had changed global Android font scale without cleanup. After adding font-scale cleanup, the full suite passed, including the unchanged People restore assertion. No production People code or assertions were changed to hide the failure. Final build command: **BUILD SUCCESSFUL**. `git diff --check` passed.

26. **Unresolved issues/limits** — No known failing automated checks or production defects remain. No physical-device TalkBack, OS permission-settings round trip, or pointer/scrim verification is claimed. Robolectric sheet-window backdrop/pointer limitations are described above. No out-of-scope phase started; changes remain uncommitted for review.

Validation command (native Windows PowerShell):

```powershell
. .\tools\env.ps1
$env:MEDITAKIP_SCREENSHOTS = '1'
.\gradlew.bat --offline assembleDebug testDebugUnitTest lint --continue --console=plain
```
