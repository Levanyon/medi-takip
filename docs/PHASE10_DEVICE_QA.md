# Phase 10 device QA — final release and Android 16 / API 36

Status: **MANUAL / NOT YET VERIFIED**. No physical-device or emulator testing was
performed during this migration or the subsequent release audit. Automated Robolectric
results are not API 36 device evidence. The source privacy blocker has a focused correction;
see PHASE10_RELEASE_REVIEW.md for automated validation. Do not treat the bundle as approved
for distribution before the remaining manual and publication gates are completed.

Record device/model, Android build/API, navigation mode, app version/commit, date,
tester, actual result and evidence for each item. Use disposable test medical data.
Do not uninstall or clear the real application to work around upgrade problems.

## Android 16 / API 36

- [ ] Clean launch and process recreation; no crash or compatibility warning.
- [ ] Edge-to-edge: status bar, cutouts, gesture and three-button navigation in light/dark mode.
- [ ] Add/edit forms with keyboard open: headers, fields and save/cancel remain reachable.
- [ ] Predictive back from Today: complete and cancel gestures; correct back-to-home behavior.
- [ ] Predictive back from People and Person detail.
- [ ] Predictive back from Medication detail and Add/Edit Medication; preserve intended navigation/state.
- [ ] Predictive back from Settings and lock-screen settings.
- [ ] Predictive back from scanner, scanned result and medication editor; camera releases correctly.
- [ ] Predictive back from notification confirmation; cancellation never marks a dose taken.
- [ ] Onboarding and dialogs: back gesture/button follows the current step/dismissal rules.
- [ ] Rotation and window resizing preserve form/navigation state without clipping controls.
- [ ] Tablet / sw600dp and larger: portrait/landscape, long names, empty states.
- [ ] Split-screen/windowed mode where supported; resize with keyboard visible.
- [ ] 320dp-equivalent window and 200% font; scroll and reach all actions.
- [ ] TalkBack labels, focus order, selected state, touch targets and contrast in light/dark mode.
- [ ] Due reminder, retry, snooze and fallback behavior with app foreground/background and device idle.
- [ ] Reboot restoration after unlock; manual date/time/timezone changes and midnight rollover.
- [ ] Notification permission denied/revoked, channel disabled and exact-alarm availability handling.
- [ ] Gizli: dose, renewal, stock, summary and ongoing status reveal no medical/person identity.
- [ ] Ayrıntılı: current names/details; switch both ways while notifications are already posted.
- [ ] Master lock-screen OFF: next-dose status is cancelled; dose/stock/renewal notifications request SECRET visibility. Verify shade behavior and system privacy settings.
- [ ] Ongoing OFF with visibility ON: next-dose status remains dismissible; verify OS countdown and snoozed timestamp.
- [ ] Securely locked İçildi: unlock/cancel/relock flows; only explicit unlocked confirmation writes.
- [ ] Completion/undo updates ongoing status and stock once; no duplicate reminders/history.

## Core flows and broader release gates (not executed)

- [ ] Clean install and first-launch onboarding, including permission explanations.
- [ ] Safe upgrade from supported schemas; retain people, medication, prescriptions, history and stock.
- [ ] People/medication add, edit, archive, restore; treatment start/end and prescription dates.
- [ ] Person/medication detail, Today due/upcoming/empty states, taken/undo, history and Settings navigation/back.
- [ ] Upcoming alarm becomes due exactly once; renewal notification uses current prescription data.
- [ ] Scanner permission allow/deny; known/unknown barcode, GS1, malformed input.
- [ ] Duplicate GTIN+serial rejected; same GTIN/new serial accepted; quantity explicitly confirmed.
- [ ] Stock consumption and undo restore the expected quantities; low-stock warning behavior.
- [ ] Create a password-encrypted backup using disposable data; confirm exported text exposes no person/medication names.
- [ ] Valid backup restore and corrupt/invalid restore without partial DB mutation.
- [ ] After a reviewed source correction, restored colon-containing schedule IDs and archived-owner data cannot retain sensitive dose/summary content in Gizli.
- [ ] Light/dark, 200% text, long person/medication names, empty states on phone and tablet.
- [ ] Background/foreground, process recreation and reboot stability.

Existing signing and bundle compilation were verified without installing on a device.
All manual items remain unverified. This checklist does not certify production readiness.

## Portable encrypted backup — MANUAL / NOT YET VERIFIED

Continuation review on 2026-10-12: automated backup and UI coverage is recorded in
PHASE10_RELEASE_REVIEW.md. No item below has been verified on a physical device.

- [ ] Export asks for a password and matching confirmation; fewer than 12 characters and mismatches are rejected.
- [ ] Spaces and Unicode work; show/hide controls, keyboard, rotation, cancellation and large text behave safely.
- [ ] Explain and verify: without the password the portable backup cannot be recovered.
- [ ] Export contains only envelope/crypto metadata and ciphertext, no readable medical/person identity.
- [ ] Restore with the correct password, preview, then explicitly confirm; compare all medical/history/package/stock data.
- [ ] Wrong password displays the generic failure and leaves current data unchanged.
- [ ] Compare people, schedules, completion history, prescriptions, packages, stock and settings before/after each rejected restore.
- [ ] Modified, truncated or corrupt encrypted file displays a safe error and leaves current data unchanged.
- [ ] Supported legacy plaintext schema 2/3/4 backup shows the unencrypted warning and restores after confirmation.
- [ ] Export after a legacy restore creates an encrypted backup, without rewriting the imported original.
- [ ] Copy encrypted backup to another compatible device/install; restore using the password, without a device-bound key.
- [ ] Cancel the picker/password dialog; simulate unavailable/full document provider and confirm no usable partial export remains.
- [ ] Verify device-only Android Keystore AES-GCM before-restore recovery, lost/unavailable key failure, and atomic migration of an existing plaintext safety copy. This is not a portable manual export.
- [ ] Confirm no password is restored after process death; use only disposable QA passwords/data.
