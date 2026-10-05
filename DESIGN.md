# MediTakip design system

MediTakip follows Quiet Luxury Healthcare: calm petrol and jade, warm neutral surfaces, readable system typography and restrained depth. Use clear hierarchy, edge-to-edge layouts, adaptive sizing and accessible touch targets. Avoid decorative gradients, glass effects, heavy shadows and pill-shaped controls everywhere. Premium redesign Phase 1 establishes shared foundations only; the Today screen redesign requires separate review and approval.

## Foundation

Keep the established green identity and Turkish interface. Use stable Compose Material 3 components without new dependencies. `Theme.kt` owns colors and typography and applies `MediShapes`. `DesignSystem.kt` owns shared spacing, radius, border and elevation tokens, motion durations, adaptive layout and navigation. `Components.kt` and `ScreenComponents.kt` provide shared components. Changes here must not alter schedule, reminder or persistence behavior.

## Color, shape and hierarchy

- Use background for pages, surfaceContainerLow for grouped content/dialogs/navigation, surfaceContainer for quiet icon controls, and surfaceContainerHigh for neutral status. PrimaryContainer identifies selected/completed states; secondaryContainer supports secondary actions and avatars. Amber is for warnings and red for errors/destructive states. Retain text and icons alongside status colors.
- Both palettes explicitly define surface levels, tint and inverse snackbar roles. Most surfaces and buttons are flat; dialogs and navigation use only 2dp shadow. `mediSurfaceBorder()` supplies a subtle 1dp outlineVariant border for navigation, dialogs and empty states. Decorative borders are not relied on for accessible status contrast.
- `MediRadius`: detail 10dp, control 14dp, card 20dp, prominent 24dp, dialog 28dp; mapped to `MediShapes` from extraSmall through extraLarge. Avatars remain circular. Do not nest cards merely to separate text.
- Typography uses Android SansSerif: screen headings 32/38sp, section headings 18/24sp, card titles 20/26sp, scheduled times 23/30sp with tabular numerals, body 16/24sp, labels 14/20sp, buttons 16/22sp (size/line height). Small text stays at least 14sp. Let text wrap and height grow; never reduce font scale or ellipsize essential information to force a fit.

| Role | Light | Dark |
|---|---|---|
| Primary / onPrimary | `#0B5A4C` / `#FFFFFF` | `#8FD7BE` / `#053A30` |
| Primary container / content | `#D6EEE6` / `#073C33` | `#174A3F` / `#D5F3E7` |
| Background / content | `#F5F7F3` / `#18231F` | `#0D1512` / `#E0E9E4` |
| Surface | `#FAFBF8` | `#111B17` |
| Containers low / normal / high | `#F1F4EF` / `#E9EEE9` / `#E2E8E3` | `#17211D` / `#1D2924` / `#25322C` |
| Secondary container / content | `#E3ECE7` / `#2D443B` | `#2A4037` / `#D6E9E0` |
| Muted text | `#53615B` | `#BCCAC3` |
| Warning / warning container | `#8A6338` / `#F4E7D4` | `#E4C18F` / `#503D25` |
| Error / error container | `#BA4B4B` / `#FFDAD8` | `#FFB4AB` / `#93000A` |

## Layout and accessibility

- Retain MediSpace (4/8/12/16/20/24/32/40dp) for spacing; 40dp is reserved for spacious feature sections. Interactive targets are at least 48dp; primary and secondary actions are at least 56dp. These are minimum heights, not text-clipping fixed heights. Icons use MediSize: 18dp status, 24dp controls, 32dp feature icons.
- MediContent fills available width up to an 840dp reading measure and centers on larger screens. This cap is not a fixed screen width. Existing scrolling screens retain their scrolling behavior.
- AdaptivePair measures content and places the second item below the first when they cannot fit side by side. Use it for short heading/status pairs instead of assuming a device width or font size.
- Bottom navigation retains four destinations (Bugün, Kişiler, Geçmiş, Ayarlar) with native Material selection semantics; selected tabs use filled icons, others outlined. The contained shell respects bottom/horizontal safe insets. Tabs share available width; measured label width triggers horizontal scrolling when necessary and minimum bar height grows with the label line height. The reusable component is also tested with five destinations without adding a production destination.
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
  - "Kişiyi Arşivle" is in the person editor; "İlacı Arşivle" is at the end of İlaç Detayı. The confirmation uses the primary color, because archiving is reversible.
  - Archived people are listed under "Arşivdekiler" in Kişiler, and archived medications under "Arşivlenen ilaçlar" in Kişi Detay. "Arşivden Çıkar" restores a person on their own, and a medication only when its person is active. After a person is restored, a snackbar says how many of their medications are still archived.
- **Geçmiş.** It has the same PersonSelector and a date × (medication, time) matrix built by HistoryMatrices from real schedules and logs. It is read-only and creates no records.
  - Rows are dates, with "Bugün" or the weekday. Each row is a lazy item.
  - The date column stays outside a horizontal scroll that all rows and the sticky header share through one ScrollState.
  - A table narrower than the page keeps its natural width and is centered. A wider one scrolls horizontally and shows a hint.
  - Column widths scale with fontScale (clamped to 1–1.8×). Names use at most 2 lines with ellipsis.
  - Cells show ✓ with the taken time, ○ when not marked, or – when not planned. Every cell has a full TalkBack sentence. Tapping a cell opens HistoryCellDetail.
- **Ayarlar.** Only Bildirim ve Hatırlatmalar, Görünüm and Uygulama Hakkında.
- **Forms (FormScreen).** Forms are drawn in the activity window over the hidden scaffold. The header applies top and horizontal safeDrawing. The body scrolls, and Kaydet is placed *inside* it after the fields. Bottom safeDrawing (navigation bar, gesture area, IME) is applied inside the scroll, so Kaydet can always be scrolled clear of three-button navigation and the keyboard. Do not use Dialog windows or pinned bottom bars for forms, because Dialog-window insets were unreliable on Samsung devices.
  - The medication form shows name, dose, times and days first. Dates, note and "İlaç kullanımda" sit under "Diğer seçenekler".
- **Wording.** Follow the glossary in `CLAUDE.md`. The button is "İçildi". The not-marked history state is "Alındı olarak işaretlenmedi". Inactive is "Durduruldu". Removing something is "Arşivle" or "Arşivden Çıkar", never "Sil". Avoid threatening words.

## Turkish glossary

This is the single source for UI, notification and report wording.

| Concept | Use exactly | Never |
|---|---|---|
| Mark a dose as taken (button, notification action) | **İçildi** | Alındı, Aldım, Tamam |
| Taken state | **✓ İçildi • 08:04** | |
| Undo a mark | **Geri Al** | Sil, İptal |
| History: dose not marked | **Alındı olarak işaretlenmedi** (short legend: İşaretlenmedi) | Unutuldu, Kaçırıldı, İçilmedi |
| Follow-up notification text | **henüz alındı olarak işaretlenmedi** | |
| Skipped dose (Phase 3) | **Atla** (action), **Atlandı** (state) | |
| Scheduled time | **Planlanan** | |
| Medication in use or stopped | **Kullanımda**, **Durduruldu** | Aktif, Pasif |
| Remove a person or medication | **Arşivle**; state **Arşivde**; reverse **Arşivden Çıkar** | Sil |
| Next dose | **Sıradaki** | |
| Past due and not marked (today) | **Saati geçti** | Gecikti, Kaçırıldı |
| Snooze | **10 dk ertele**; state **Ertelendi** | |
| Reminder health | **Hatırlatma Durumu**; action **Düzelt** | |
| Save, cancel | **Kaydet**, **İptal** | |

- Never give or imply medical advice.
- Never judge the user: no "kaçırdınız", and no warnings about health consequences.
- A number is followed by the singular noun ("2 ilaç").

## Motion

Use short color feedback (160ms) and progress interpolation (240ms), with no bouncing, perpetual animation or decorative page transitions. Use Compose animation APIs and native Material interaction feedback, which follow Android animator duration scale (including disabled animations). Do not introduce manually timed animation loops. State changes and actions must remain understandable without motion.

## Verification for future changes

Run relevant Compose tests with long Turkish text, 2x fonts, a narrow viewport and a larger viewport. Text-bound layout tests must use Robolectric native graphics; its legacy renderer does not provide realistic glyph measurements. Verify both palettes, gesture and three-button navigation, cutouts, landscape, keyboard, disabled animations and TalkBack on a physical device. This foundation does not constitute a redesign of every screen; migrate other layouts only when their screen is in scope.
