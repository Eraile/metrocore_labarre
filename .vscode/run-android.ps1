<#
    Build + install + lancement de metrocore - La Barre.

    Usage :
        .\.vscode\run-android.ps1 -Target emulator
        .\.vscode\run-android.ps1 -Target device

    Appele par F5 (voir .vscode/launch.json) et par Ctrl+Shift+B (.vscode/tasks.json).
#>
[CmdletBinding()]
param(
    [ValidateSet('emulator', 'device')]
    [string]$Target = 'emulator',

    [ValidateSet('debug', 'release')]
    [string]$BuildType = 'debug'
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

function Fail($message) {
    Write-Host ''
    Write-Host "  $message" -ForegroundColor Red
    Write-Host ''
    exit 1
}

function Step($message) {
    Write-Host ''
    Write-Host "==> $message" -ForegroundColor Cyan
}

# --- Localiser le SDK Android -------------------------------------------------

$sdk = $null
$localProps = Join-Path $root 'local.properties'
if (Test-Path $localProps) {
    $line = Select-String -Path $localProps -Pattern '^\s*sdk\.dir\s*=\s*(.+)$' | Select-Object -First 1
    if ($line) {
        # Format .properties : les ':' et '\' sont echappes par un backslash.
        $sdk = $line.Matches[0].Groups[1].Value.Trim().Replace('\:', ':').Replace('\\', '\')
    }
}
if (-not $sdk -and $env:ANDROID_HOME) { $sdk = $env:ANDROID_HOME }
if (-not $sdk -and $env:ANDROID_SDK_ROOT) { $sdk = $env:ANDROID_SDK_ROOT }
if (-not $sdk -or -not (Test-Path $sdk)) {
    Fail "SDK Android introuvable. Renseigne sdk.dir dans local.properties ou la variable ANDROID_HOME."
}

$adb = Join-Path $sdk 'platform-tools\adb.exe'
$emulatorExe = Join-Path $sdk 'emulator\emulator.exe'
if (-not (Test-Path $adb)) { Fail "adb introuvable : $adb" }

# --- Inventaire des appareils connectes ---------------------------------------

function Get-Devices {
    # Lignes "serial<TAB>state" ; on ne garde que celles reellement pretes.
    $out = & $adb devices
    $result = @()
    foreach ($line in $out) {
        if ($line -match '^(\S+)\s+device$') {
            $result += $Matches[1]
        }
    }
    return $result
}

function Get-Target {
    $devices = Get-Devices
    if ($Target -eq 'emulator') {
        return ($devices | Where-Object { $_ -like 'emulator-*' } | Select-Object -First 1)
    }
    return ($devices | Where-Object { $_ -notlike 'emulator-*' } | Select-Object -First 1)
}

$serial = Get-Target

# --- Demarrer l'emulateur si besoin -------------------------------------------

if (-not $serial -and $Target -eq 'emulator') {
    if (-not (Test-Path $emulatorExe)) { Fail "emulator.exe introuvable : $emulatorExe" }

    # @() force un tableau : avec un seul AVD, le pipeline renverrait une chaine
    # et $avds[0] indexerait son premier caractere.
    $avds = @(& $emulatorExe -list-avds | Where-Object { $_ -and $_.Trim() })
    if (-not $avds) {
        Fail "Aucun AVD configure. Cree-en un via Android Studio (Device Manager) ou avdmanager."
    }
    $avd = $avds[0].Trim()

    Step "Demarrage de l'emulateur '$avd' (premier lancement : ca peut prendre 1-2 min)"
    Start-Process -FilePath $emulatorExe -ArgumentList @('-avd', $avd) -WindowStyle Minimized | Out-Null

    # Surtout pas `adb wait-for-device` : des qu'un telephone est branche en plus,
    # adb refuse toute commande sans -s ("more than one device"). On attend donc que
    # l'emulateur apparaisse nommement, puis on l'interroge par son serial.
    $deadline = (Get-Date).AddMinutes(4)
    $booted = ''
    while ((Get-Date) -lt $deadline) {
        $serial = Get-Target
        if ($serial) {
            # wait-for-device rendrait la main bien avant la fin du boot.
            $booted = (& $adb -s $serial shell getprop sys.boot_completed 2>$null | Out-String).Trim()
            if ($booted -eq '1') { break }
        }
        Start-Sleep -Seconds 2
    }
    if ($booted -ne '1') { Fail "L'emulateur n'a pas fini de demarrer dans le temps imparti." }
}

if (-not $serial) {
    if ($Target -eq 'device') {
        Fail "Aucun appareil physique detecte. Branche-le en USB, active le debogage USB, et autorise l'ordinateur sur le telephone."
    }
    Fail "Aucun emulateur detecte."
}

Write-Host ''
Write-Host "  Cible : $serial" -ForegroundColor Green

# --- Build + install ----------------------------------------------------------

# AGP installe sur l'appareil designe par ANDROID_SERIAL.
$env:ANDROID_SERIAL = $serial

$task = 'installDebug'
if ($BuildType -eq 'release') { $task = 'installRelease' }

Step "Build & install ($task)"
& (Join-Path $root 'gradlew.bat') $task
if ($LASTEXITCODE -ne 0) { Fail "Le build a echoue (voir les erreurs ci-dessus)." }

# --- Lancement ----------------------------------------------------------------

Step 'Lancement de metrocore - La Barre'
& $adb -s $serial shell am start -n 'dev.metrocore.navbar/.MainActivity' | Out-Null

# Raccourci pratique : ouvre directement la page ou activer le service.
$enabled = (& $adb -s $serial shell settings get secure enabled_accessibility_services | Out-String)
if ($enabled -notmatch 'dev\.metrocore\.navbar') {
    Write-Host ''
    Write-Host "  Le service d'accessibilite n'est pas encore active sur cette cible." -ForegroundColor Yellow
    Write-Host "  Ouvre les reglages systeme depuis l'app, puis active 'metrocore - La Barre'." -ForegroundColor Yellow
}

Write-Host ''
Write-Host '  OK.' -ForegroundColor Green
Write-Host ''
