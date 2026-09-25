# Creates MediTakip's release signing key ONCE, outside the repository.
#
#   Run it yourself in PowerShell from the repository root:   .\tools\new-release-key.ps1
#
# - Asks for a password twice (hidden input); it never appears on screen, in the command line,
#   in PowerShell history or in the repository.
# - Writes two files to %USERPROFILE%\AndroidKeys\meditakip\ (created if needed):
#     meditakip-release.p12     the key (PKCS12, RSA 4096, alias "meditakip", valid 30 years)
#     keystore.properties       what Gradle reads to sign release builds (contains the password)
# - Refuses to overwrite an existing key: replacing it would make future updates uninstallable.
# - Prints the certificate SHA-256 fingerprint to record in docs\RELEASE.md.
# Afterwards back up BOTH the key file and the password (see docs\RELEASE.md, "Back up the key").

param([string]$KeyDir = (Join-Path $env:USERPROFILE "AndroidKeys\meditakip"))

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$Alias = "meditakip"
$PasswordVariable = "MEDITAKIP_KEYSTORE_PASSWORD"

function Find-Keytool {
    $repo = Split-Path -Parent $PSScriptRoot
    $jdk = Get-ChildItem -Path (Join-Path $repo ".tools\jdk") -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
    $candidates = @()
    if ($jdk) { $candidates += Join-Path $jdk.FullName "bin\keytool.exe" }
    if ($env:JAVA_HOME) { $candidates += Join-Path $env:JAVA_HOME "bin\keytool.exe" }
    foreach ($c in $candidates) { if (Test-Path $c) { return $c } }
    throw "keytool.exe not found. Expected the project JDK under .tools\jdk (see README.md)."
}

# java.util.Properties reads files as ISO-8859-1 and treats '\' as an escape character.
# Escape so that any password (including Turkish letters) round-trips exactly.
function ConvertTo-PropertiesValue([string]$Value) {
    $sb = New-Object System.Text.StringBuilder
    for ($i = 0; $i -lt $Value.Length; $i++) {
        $ch = $Value[$i]; $code = [int]$ch
        if ($ch -eq '\') { [void]$sb.Append('\\') }
        elseif ($i -eq 0 -and $ch -eq ' ') { [void]$sb.Append('\ ') }
        elseif ($code -lt 32 -or $code -gt 126) { [void]$sb.Append(('\u{0:x4}' -f $code)) }
        else { [void]$sb.Append($ch) }
    }
    $sb.ToString()
}

# JDK 17's PKCS12 key protection only accepts printable ASCII passwords.
function Test-AsciiPassword([string]$Value) {
    foreach ($ch in $Value.ToCharArray()) { if ([int]$ch -lt 32 -or [int]$ch -gt 126) { return $false } }
    return $true
}

function ConvertFrom-Secure([Security.SecureString]$Secure) {
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Secure)
    try { [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) } finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
}

function New-MediTakipKey([string]$Directory, [Security.SecureString]$Password) {
    $keyFile = Join-Path $Directory "meditakip-release.p12"
    $propsFile = Join-Path $Directory "keystore.properties"
    if ((Test-Path $keyFile) -or (Test-Path $propsFile)) {
        throw "A key already exists in $Directory. It was NOT changed. Never replace a release key that has been used to install the app."
    }
    New-Item -ItemType Directory -Force -Path $Directory | Out-Null
    $keytool = Find-Keytool
    $plain = ConvertFrom-Secure $Password
    if (-not (Test-AsciiPassword $plain)) { $plain = $null; throw "Password must be printable ASCII (PKCS12 limitation). Nothing was created." }
    try {
        # keytool reads the password from an environment variable, never from the command line.
        Set-Item -Path "env:$PasswordVariable" -Value $plain
        & $keytool -genkeypair -keystore $keyFile -storetype PKCS12 -alias $Alias -keyalg RSA -keysize 4096 -validity 10950 `
            -dname "CN=MediTakip, O=MediTakip, C=TR" -storepass:env $PasswordVariable -keypass:env $PasswordVariable -noprompt
        if ($LASTEXITCODE -ne 0 -or -not (Test-Path $keyFile)) { throw "keytool could not create the key." }
        $escaped = ConvertTo-PropertiesValue $plain
        $lines = @(
            "# MediTakip release signing. KEEP PRIVATE. Never copy into the repository.",
            "storeFile=meditakip-release.p12",
            "storePassword=$escaped",
            "keyAlias=$Alias",
            "keyPassword=$escaped"
        )
        [IO.File]::WriteAllLines($propsFile, $lines, [Text.Encoding]::ASCII)
        $listing = & $keytool -list -v -keystore $keyFile -storetype PKCS12 -alias $Alias -storepass:env $PasswordVariable
        $fingerprint = ($listing | Select-String "SHA256:").ToString().Trim()
    } finally {
        Remove-Item -Path "env:$PasswordVariable" -ErrorAction SilentlyContinue
        $plain = $null
    }
    [pscustomobject]@{ KeyFile = $keyFile; PropertiesFile = $propsFile; Fingerprint = $fingerprint }
}

# Dot-sourcing (". .\tools\new-release-key.ps1") only loads the functions, e.g. for testing.
if ($MyInvocation.InvocationName -ne '.') {
    Write-Host "MediTakip release key will be created in: $KeyDir"
    Write-Host "Choose a strong password: at least 12 characters, English letters/digits/symbols only. You will need it for every release."
    $first = Read-Host "Password" -AsSecureString
    $second = Read-Host "Password again" -AsSecureString
    $a = ConvertFrom-Secure $first; $b = ConvertFrom-Secure $second
    $same = ($a -ceq $b); $long = ($a.Length -ge 12); $ascii = Test-AsciiPassword $a; $a = $null; $b = $null
    if (-not $same) { throw "The passwords do not match. Nothing was created." }
    if (-not $long) { throw "The password must be at least 12 characters. Nothing was created." }
    if (-not $ascii) { throw "Use only English letters, digits and symbols (no Turkish letters such as s-cedilla, g-breve, dotless i, o/u-umlaut, c-cedilla). The key format (PKCS12) rejects them. Nothing was created." }
    $result = New-MediTakipKey -Directory $KeyDir -Password $first
    Write-Host ""
    Write-Host "Created: $($result.KeyFile)"
    Write-Host "Created: $($result.PropertiesFile)"
    Write-Host "Certificate $($result.Fingerprint)"
    Write-Host ""
    Write-Host "NEXT: back up the key file and the password in two separate safe places (docs\RELEASE.md)."
    Write-Host "Then tell Claude Code that the key exists; it will build and verify the release APK."
}
