# MediTakip design system

MediTakip should look like a modern Android 16-era application, not a legacy Material 2 or early Material 3 application. Use strong visual hierarchy, tonal surfaces, expressive but restrained shapes and motion, edge-to-edge layouts, adaptive sizing, generous spacing, accessible touch targets, and responsive layouts. Avoid excessive cards, borders, heavy shadows, cramped screens, fixed-width layouts, and spreadsheet-like UI.

## Foundation

Keep the established green identity and Turkish interface. Use stable Compose Material 3 components; expressive design does not require experimental dependencies. `Theme.kt` owns colors, typography and shapes. `DesignSystem.kt` owns shared dimensions, motion durations, adaptive layout and navigation. `Components.kt` provides reusable screen headings, actions, status indicators, progress and medication/person rows. Changes here must not alter schedule, reminder or persistence behavior.

## Color, shape and hierarchy

- Use background for the page, surfaceContainerLow for grouped content, surfaceContainer for navigation, and surfaceContainerHigh for neutral status. Reserve primaryContainer for the main progress summary and selected states; secondaryContainer signals recorded completion. Use paired on-colors. Never convey status with color alone: retain text and icons.
- Light and dark palettes have explicit container levels. Do not introduce dynamic colors that replace the green identity, decorative gradients, outlined boxes or heavy shadows. Shared surfaces use zero elevation; tonal contrast supplies depth.
- Shape roles: 8dp extra-small details, 12dp small elements, 20dp medium surfaces, 28dp actionable rows/buttons, 36dp hero surfaces/dialogs, and capsules for status. Do not nest cards merely to separate text.
- Typography: headlineLarge for screen titles; titleLarge for section/medication names; headlineSmall for scheduled time; bodyLarge/bodyMedium for content; labelLarge for actions/status; labelMedium/bodySmall for navigation/metadata. Small text stays at least 14sp. Let text wrap and height grow; never reduce font scale or ellipsize essential information to force a fit.

## Layout and accessibility

- Use MediSpace (4/8/12/16/20/24/32dp) for spacing. Interactive targets are at least 48dp; primary actions are at least 56dp. These are minimum heights, not text-clipping fixed heights.
- MediContent fills available width up to an 840dp reading measure and centers on larger screens. This cap is not a fixed screen width. Existing scrolling screens retain their scrolling behavior.
- AdaptivePair measures content and places the second item below the first when they cannot fit side by side. Use it for short heading/status pairs instead of assuming a device width or font size.
- Bottom navigation has four destinations (Bugün, Kişiler, Geçmiş, Ayarlar) with native Material selection semantics; selected tabs use filled icons, others outlined. Tabs share available width; when measured labels cannot fit at enlarged text sizes, the bar scrolls horizontally. Text remains readable and every destination is reachable.
- MainActivity enables edge-to-edge. Scaffold uses safeDrawing insets; the content consumes its scaffold padding. Navigation handles horizontal/bottom safe insets; onboarding applies safeDrawing. Add/edit forms use FormScreen (see below), never a Dialog window. Never add guessed status/navigation-bar padding. Keep Android's three-button navigation contrast scrim.

## Information architecture

Navigation is fixed at four destinations. Medications always belong to a person; there is no top-level İlaçlar destination.

- **Bugün (daily use).** It shows, in order:
  - The Turkish date.
  - The segmented PersonSelector. It scrolls horizontally when there are many people, and each person shows a pending-count badge. The selection is persisted in Preferences.lastPerson and shared with Geçmiş.
  - DaySummary: a progress ring, "x / y tamamlandı" and "Sıradaki: hh:mm • name".
  - The timeline of ScheduledDoseRows with a status rail.

  Exactly one dose is highlighted: the earliest one not yet taken. It gets primaryContainer and a filled İçildi button. Every other pending dose keeps a tonal İçildi button. Overdue doses say "Saati geçti" in text. Marking a dose shows a long Snackbar with Geri Al, and taken rows keep a Geri Al text button. The only management link is "<ad> için ilaçları yönet", which opens that person.
- **Kişiler → Kişi Detay → İlaç Detayı (management).**
  - Kişiler shows PersonRows and an always-visible "Kişi Ekle".
  - Kişi Detay contains only identity, Düzenle, "İlaç Ekle" and MedicationRows. It never shows today's timeline.
  - İlaç Detayı shows, in order: name, person, dose and status; then times and days in one tonal section; then Düzenle; then the quieter "Diğer bilgiler" (dates, note); then "Son 7 gün" as the history matrix; and finally İlacı Sil.
  - Deleting a person happens in the person editor.
- **Geçmiş.** It has the same PersonSelector and a date × (medication, time) matrix built by HistoryMatrices from real schedules and logs. It is read-only and creates no records.
  - Rows are dates, with "Bugün" or the weekday. Each row is a lazy item.
  - The date column stays outside a horizontal scroll that all rows and the sticky header share through one ScrollState.
  - A table narrower than the page keeps its natural width and is centered. A wider one scrolls horizontally and shows a hint.
  - Column widths scale with fontScale (clamped to 1–1.8×). Names use at most 2 lines with ellipsis.
  - Cells show ✓ with the taken time, ○ when not marked, or – when not planned. Every cell has a full TalkBack sentence. Tapping a cell opens HistoryCellDetail.
- **Ayarlar.** Only Bildirim ve Hatırlatmalar, Görünüm and Uygulama Hakkında.
- **Forms (FormScreen).** Forms are drawn in the activity window over the hidden scaffold. The header applies top and horizontal safeDrawing. The body scrolls, and Kaydet is placed *inside* it after the fields. Bottom safeDrawing (navigation bar, gesture area, IME) is applied inside the scroll, so Kaydet can always be scrolled clear of three-button navigation and the keyboard. Do not use Dialog windows or pinned bottom bars for forms, because Dialog-window insets were unreliable on Samsung devices.
  - The medication form shows name, dose, times and days first. Dates, note and "İlaç kullanımda" sit under "Diğer seçenekler".
- **Wording.** Always say "İçildi" (never "Alındı") and "Durduruldu" for inactive. Avoid threatening words.

## Motion

Use short color feedback (160ms) and progress interpolation (240ms), with no bouncing, perpetual animation or decorative page transitions. Use Compose animation APIs and native Material interaction feedback, which follow Android animator duration scale (including disabled animations). Do not introduce manually timed animation loops. State changes and actions must remain understandable without motion.

## Verification for future changes

Run relevant Compose tests with long Turkish text, 2x fonts, a narrow viewport and a larger viewport. Text-bound layout tests must use Robolectric native graphics; its legacy renderer does not provide realistic glyph measurements. Verify both palettes, gesture and three-button navigation, cutouts, landscape, keyboard, disabled animations and TalkBack on a physical device. This foundation does not constitute a redesign of every screen; migrate other layouts only when their screen is in scope.
