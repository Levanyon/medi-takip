# MediTakip build environment for native Windows PowerShell.
# Usage (from the repository root):   . .\tools\env.ps1   then   .\gradlew.bat assembleDebug
#
# The JDK, Gradle distribution and Android SDK live in the git-ignored .tools folder,
# so nothing is installed system-wide. Dot-sourcing sets the variables for this shell only.

$root = Split-Path -Parent $PSScriptRoot
$jdk = Get-ChildItem -Path (Join-Path $root ".tools\jdk") -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $jdk) { Write-Error "JDK not found under .tools\jdk. See README.md for toolchain setup."; return }

$env:JAVA_HOME = $jdk.FullName
$env:GRADLE_USER_HOME = Join-Path $root ".tools\gradle-home"
$sdk = Join-Path $root ".tools\android-sdk"
if (Test-Path $sdk) { $env:ANDROID_HOME = $sdk }
if (-not ($env:Path -split ';' | Where-Object { $_ -eq (Join-Path $env:JAVA_HOME "bin") })) {
    $env:Path = (Join-Path $env:JAVA_HOME "bin") + ";" + $env:Path
}

Write-Host "MediTakip env ready: JAVA_HOME=$env:JAVA_HOME"
Write-Host "Tip: add --offline to Gradle commands; the wrapper distribution is already cached."
