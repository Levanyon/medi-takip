# Physical-device verification

Run on Android 8–12 and Android 13+ where available. Record device, Android version and result. These checks are not claimed as executed without a connected device.

- Add two people; edit notes; create medications with multiple times and different weekdays. Reject blank names, invalid times, no weekdays and end dates before start dates.
- Verify Today totals, grouping and large-font scrolling. Mark an item Taken twice: only one completion should exist. Undo, restart and verify persistence.
- Verify start/end dates are inclusive, inactive medications stop appearing, and past history survives edits and deletion.
- Schedule a reminder a few minutes ahead, leave the app, lock the phone and verify notification. Mark Taken before retry and verify retry is suppressed.
- Deny notification permission, disable the channel, and deny exact alarms independently. No crashes; Settings explains current status. Re-enable and verify recovery.
- Reboot before a reminder and unlock the phone. Verify the restored alarm. Change clock and time zone; ensure future reminders follow local time without duplicate completion entries.
- Check a retry crossing midnight. Check multiple medications at the same time.
- Disable all reminders and verify no later notification; re-enable and verify the next event.
- Check system/light/dark themes and accessibility font size. Buttons should remain accessible without clipped text.
- Force-stop and reopen: verify rescheduling. Android does not deliver alarms to a force-stopped app before reopening.
- Install an update signed with the same key; verify Room data remains present.
- Confirm merged manifest has no Internet permission and cloud backup is disabled.
