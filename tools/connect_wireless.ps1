# Connects the PC to an Android phone over Wi-Fi (Wireless debugging, Android 11+).
#
# On the phone first:
#   Settings > Developer options > Wireless debugging: ON
#   then tap "Pair device with pairing code" and LEAVE THAT WINDOW OPEN.
#
# Run (PowerShell):
#   powershell -ExecutionPolicy Bypass -File tools\connect_wireless.ps1
#
# The script finds the phone's address by itself. You only type the 6-digit code.
# Pairing is needed once per PC. Next time, with Wireless debugging on, just run it
# again: it skips pairing if the phone is already paired and only connects.

$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) { Write-Host "adb not found at $adb"; exit 1 }

function Find-Service($kind) {
    $out = & $adb mdns services 2>&1 | Out-String
    $m = [regex]::Match($out, "_adb-tls-$kind\._tcp\.?\s+(\d{1,3}(?:\.\d{1,3}){3}:\d+)")
    if ($m.Success) { return $m.Groups[1].Value }
    return $null
}

# 0) Drop stale entries (they show "offline" after the cable is unplugged or the port changes).
foreach ($line in (& $adb devices)) {
    if ($line -match '^(\d{1,3}(?:\.\d{1,3}){3}:\d+)\s+offline') { & $adb disconnect $Matches[1] | Out-Null }
}

# 1) Already paired? Then only connect.
$connect = Find-Service "connect"

# 2) Pair (once)
if (-not $connect) {
    Write-Host "Looking for the pairing window on the phone..."
    $pair = $null
    for ($i = 0; $i -lt 15 -and -not $pair; $i++) {
        $pair = Find-Service "pairing"
        if (-not $pair) { Start-Sleep -Seconds 2 }
    }
    if (-not $pair) { $pair = Read-Host "Pairing address shown on the phone (IP:port)" }
    $code = Read-Host "6-digit pairing code shown on the phone"
    & $adb pair $pair $code
    Start-Sleep -Seconds 2
    $connect = Find-Service "connect"
}

# 3) Connect
if (-not $connect) { $connect = Read-Host "Main address on the Wireless debugging screen (IP:port)" }
& $adb connect $connect
Write-Host ""
& $adb devices
