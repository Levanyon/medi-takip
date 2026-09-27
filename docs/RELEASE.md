# MediTakip — Windows APK release and safe updates

Current release candidate: **1.2 (versionCode 3)**. Physical Samsung verification and 1–2 weeks of parallel use remain required. See [DEVICE-TESTS.md](DEVICE-TESTS.md).

The real app is `com.levanyon.meditakip`. Debug is a separate app, `com.levanyon.meditakip.debug`, labelled MediTakip (debug). Debug cannot update the real app or access its private database.

**Keep the existing release key. Never create a replacement to fix a signing error. Never uninstall MediTakip or clear storage to install an update.** Uninstalling removes local records. A different signing identity prevents an in-place update.

## Key facts

| | |
|---|---|
| Key file | `%USERPROFILE%\AndroidKeys\meditakip\meditakip-release.p12` (PKCS12, RSA 4096, valid 30 years) |
| Alias | `meditakip` |
| Gradle settings | `%USERPROFILE%\AndroidKeys\meditakip\keystore.properties` (contains the password; never in the repository) |
| Certificate SHA-256 | `84:E8:0A:AC:3A:16:C7:9B:01:F5:8B:28:23:5C:6F:71:DF:E7:AC:68:96:F6:72:F8:83:F9:9D:06:87:E9:81:7F` |
| apksigner form | `84e80aac3a16c79b01f58b28235c6f71dfe7ac6896f672f883f99d0687e9817f` |
| Certificate DN | `CN=MediTakip, O=MediTakip, C=TR` |
| Created / backed up | 2026-09-25 (two places, confirmed by the developer) |

The SHA-256 fingerprint is public information. It identifies the key and is used to check backups and APKs.

## Existing key and its backups

The key in the table already exists outside this repository. No key/password was created, changed or copied during Phase 4/6. Gradle resolves signing configuration from MEDITAKIP_SIGNING, then the external properties file above, then the ignored legacy root properties file. No unsigned/debug-signed release fallback exists.

The user should retain **two encrypted copies outside the repository**, for example two encrypted removable drives kept separately; store passwords separately in an offline password manager or another secure location. The existing `.p12` and its corresponding configuration are the files to protect. Do not put them in Git, chat, e-mail or an unencrypted synced folder. The agent does not copy these files. Restore the same key from those copies if the working copy is lost; do not generate a new identity.

To check a backup copy of the key (for example on the USB drive), replace the path and enter the password when asked. The `SHA256:` line must match the table:

```powershell
. .\tools\env.ps1
keytool -list -v -keystore E:\meditakip\meditakip-release.p12 -storetype PKCS12 -alias meditakip
```

`.gitignore` excludes `.p12`, `.jks`, `.keystore`, signing properties and AndroidKeys. Certificate fingerprints are public identifiers, not private-key material. Matching the documented fingerprint is necessary for update compatibility; the phone's installed package/version must also be checked before installation.

## Build and verify in PowerShell

From the repository root:

```powershell
. .\tools\env.ps1
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleRelease --offline
$bt = Join-Path $env:ANDROID_HOME 'build-tools\35.0.0'
& "$bt\apksigner.bat" verify --verbose --print-certs app\build\outputs\apk\debug\app-debug.apk
& "$bt\apksigner.bat" verify --verbose --print-certs app\build\outputs\apk\release\app-release.apk
```

If ANDROID_HOME is not set, use `.tools\android-sdk\build-tools\35.0.0` for `$bt`. Release SHA-256 must exactly match the table. Debug must verify too, but uses the separate Android debug certificate. A failed check blocks installation.

Actual Gradle outputs:
- Debug: `app\build\outputs\apk\debug\app-debug.apk`
- Signed release: `app\build\outputs\apk\release\app-release.apk`

You may copy the verified release APK as `MediTakip-release.apk` for distribution; renaming does not alter its signature. Build outputs stay out of Git.

R8 (minification and resource shrinking) is **off** for 1.2; see DECISIONS D6.2. It can only be turned on after a minified APK has been installed on a safe test device and these were checked: Room open/migration, İçildi/Geri Al, a notification's İçildi and 10 dk ertele, reminder after reboot, JSON backup round-trip and PDF sharing. `app/proguard-rules.pro` is kept for that attempt. If R8 is ever on, keep the matching `app\build\outputs\mapping\release\mapping.txt` privately with each released APK for crash diagnosis.

## Android Studio on Windows

1. Open the existing project; use JDK 17 and SDK 35, then sync.
2. For debug, select the debug build variant and **Build > Build Bundle(s) / APK(s) > Build APK(s)** (the equivalent APK menu in newer Studio).
3. For release, run `assembleRelease` in Studio's PowerShell terminal using the existing signing configuration above. Alternatively choose **Generate Signed Bundle / APK > APK**, select the **existing** external `.p12`, enter its existing credentials locally, and choose release. Never select Create new.
4. Verify the actual output with apksigner before copying it. The command-line paths above are guaranteed for the Gradle tasks; the signing wizard may use the output folder selected in that wizard.

## Safe update on the physical phone

1. Open the installed MediTakip. In **Ayarlar > Yedekleme > Yedek Oluştur**, save a manual JSON backup to local storage and retain a separate safe copy. If upgrading a version without export, preserve the installation/data and test the update on a safe device first; do not uninstall to work around it.
2. Keep the existing app installed. Build the release APK, verify its existing signing fingerprint and ensure its versionCode is higher than the installed release.
3. Connect the unlocked phone to Windows by USB, choose File transfer, and copy the signed APK to Downloads.
4. On the phone, open Files > Downloads > APK. If prompted, temporarily allow installation from this Files app, then select **Update/Install** over the existing MediTakip. Revoke the Files installation permission afterward if desired.
5. **Never uninstall first.** On conflict or downgrade errors stop and verify package, certificate and version; do not clear data, force a downgrade or change keys.
6. Open MediTakip. Verify people, medications, history, stock, reminders and the backup screen. Confirm old history remains unchanged.
7. Run the test reminder from Hatırlatma Durumu, then an ordinary scheduled reminder with the phone locked.
8. Follow DEVICE-TESTS.md and keep the family's existing reminder method in parallel for **1–2 weeks** before relying on this app alone.

Optional USB debugging update (authorize the Windows computer on the phone):

```powershell
& '.tools\android-sdk\platform-tools\adb.exe' install -r app\build\outputs\apk\release\app-release.apk
```

No installation, uninstall or real-data modification was performed during this development session. An earlier one-time debug-to-release switch is historical; its uninstall instruction must never be reused for current updates.

## Version policy

`versionCode` is an integer increased by one for each distributed release; never reuse or decrease it. `versionName` is user-facing: 1.1/code 2 → 1.2/code 3 for these backup/report and reliability milestones. Patch fixes may use 1.2.1 with code 4; a subsequent feature release increases the minor version. Room schema version is independent: still 2, with explicit migration 1 → 2. Update CHANGELOG.md and the Turkish Settings version label together.