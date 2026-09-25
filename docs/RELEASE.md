# MediTakip — signing, building and installing a release

The phone runs the **release** build, signed with MediTakip's own release key. Android only installs an update over an existing app if it is signed with the **same key**. If the key or its password is lost, the next version cannot be installed over the app. The only way out is to uninstall, which **deletes all data** on the phone. Treat the key like the house keys.

Debug builds are a separate app (`com.levanyon.meditakip.debug`, shown as "MediTakip (debug)") signed with the Android debug key. They are only for testing and never replace the real app.

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

## 1. Create the key (once)

In PowerShell, from the repository root:

```
.\tools\new-release-key.ps1
```

- Choose a password of **at least 12 characters, using only English letters, digits and symbols**. The PKCS12 key format in JDK 17 rejects Turkish letters (ş, ğ, ı, İ, ö, ü, ç).
- The script refuses to run if a key already exists. It never overwrites one.
- It prints the certificate SHA-256 fingerprint. Record it in the table above.

## 2. Back up the key (immediately, and after any change)

Keep **two copies in two different places**, and keep the password **separately** from the key file:

1. **An encrypted USB stick kept at home.** Encrypt the stick with BitLocker To Go (right-click the drive → *Turn on BitLocker*) and copy the whole `%USERPROFILE%\AndroidKeys\meditakip\` folder onto it.
2. **A password manager** (Bitwarden, 1Password, KeePass…). Store the password as an entry and attach `meditakip-release.p12` as a file. If you use personal cloud storage instead, put the key inside a 7-Zip archive with AES-256 encryption and a different strong password.
3. Optional: write the password on paper and keep it at home, away from the USB stick.

Never put the key or password in the repository, e-mail, chat apps, the desktop, or an unencrypted synced folder (e.g. OneDrive).

**Check a backup** (for example after copying to the USB stick), replacing the path with the backup copy:

```
. .\tools\env.ps1
keytool -list -v -keystore E:\meditakip\meditakip-release.p12 -storetype PKCS12 -alias meditakip
```

Enter the password. The `SHA256:` line must match the fingerprint in the table above.

## 3. Build the release APK

```
. .\tools\env.ps1
.\gradlew.bat assembleRelease --offline
```

- Output: `app\build\outputs\apk\release\app-release.apk`
- If no key is configured, the build **stops** with "No release signing key found". It never produces an unsigned APK.
- To use a key from another location for one build, set `$env:MEDITAKIP_SIGNING` to that `keystore.properties` path.

Verify the signature before installing:

```
$bt = Get-ChildItem .tools\android-sdk\build-tools -Directory | Select-Object -Last 1
& "$($bt.FullName)\apksigner.bat" verify --print-certs app\build\outputs\apk\release\app-release.apk
```

The `SHA-256 digest` must equal the fingerprint in the table above (apksigner prints it without colons).

## 4. Install on the phone

**First release install (one time, 2026-09):** the phone still has the old debug-signed app. Uninstall it first (this deletes its test data), then install `app-release.apk`. This was agreed because the app was not in real use yet.

**Every later update:** install `app-release.apk` over the existing app **without uninstalling**. Data is kept because the key is the same. Before an update, export a backup once Phase 4 exists (see `docs/ROADMAP.md`).

Copy the APK to the phone's *Download* folder and open it, or use USB debugging:

```
& ".tools\android-sdk\platform-tools\adb.exe" install -r app\build\outputs\apk\release\app-release.apk
```

If Android reports that the package conflicts with an existing one, the APK was signed with a different key. **Do not uninstall** unless losing the data is acceptable. Check the fingerprint first.

## 5. If the key is lost

Restore it from a backup (section 2) and check the fingerprint. If no backup exists, a new key can be created, but the app must then be uninstalled and reinstalled, and all data on the phone is lost unless a Phase 4 backup file exists.
