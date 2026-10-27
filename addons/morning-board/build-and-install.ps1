# Windows version of build-and-install.sh: build Morning Board, install it on the TV, push a routine if given one,
# start the screen watcher and open the board.
#
# Usage: addons\morning-board\build-and-install.ps1 -TvHost 192.168.1.50 [-Routine my-routine.json]
#        addons\morning-board\build-and-install.ps1 -TvHost 192.168.1.50 -Uninstall
param(
    [string]$TvHost = $env:TV_HOST,
    [string]$Routine,
    [switch]$Uninstall
)
$ErrorActionPreference = 'Stop'
if (-not $TvHost) { throw 'pass -TvHost <tv ip> or set $env:TV_HOST' }
if ($Routine -and -not (Test-Path $Routine -PathType Leaf)) { throw "no such file: $Routine" }
$env:TV_HOST = $TvHost
$here = $PSScriptRoot
$tools = Join-Path (Split-Path (Split-Path $here -Parent) -Parent) 'tools'
$py = Join-Path $tools 'venv\Scripts\python.exe'
if (-not (Test-Path $py)) { throw 'run: python -m venv tools\venv; tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt' }
$pkg = 'io.github.hugoforte.morningboard'
function Tv([string]$cmd) { & $py -I (Join-Path $tools 'tv.py') $cmd; if ($LASTEXITCODE) { throw "tv.py failed: $cmd" } }

if ($Uninstall) {
    Tv "pm uninstall $pkg"
    exit 0
}

Push-Location $here
# javac notes go to stderr, which Windows PowerShell 5.1 turns into errors under 'Stop'.
$ErrorActionPreference = 'Continue'
try {
    & .\gradlew.bat assembleRelease -q
} finally {
    Pop-Location
}
if ($LASTEXITCODE) { throw 'build failed (JDK 17 and Android SDK 34 needed; see launcher\README)' }
$ErrorActionPreference = 'Stop'
& $py -I (Join-Path $tools 'b64push.py') (Join-Path $here 'app\build\outputs\apk\release\app-release.apk') /data/local/tmp/morningboard.apk | Out-Null
if ($LASTEXITCODE) { throw 'sending the APK failed; run this again' }
Tv 'pm install -r /data/local/tmp/morningboard.apk; rm -f /data/local/tmp/morningboard.apk'
# Android 10 and later let an app open a screen from the background only when it may draw over other apps.
Tv "appops set $pkg SYSTEM_ALERT_WINDOW allow"

if ($Routine) {
    # The app's storage is out of the shell's reach, so the file goes in through the app's provider.
    $stage = '/data/local/tmp/morningboard-routine.json'
    & $py -I (Join-Path $tools 'tv.py') --push (Resolve-Path $Routine).Path $stage | Out-Null
    if ($LASTEXITCODE) { throw 'sending the routine failed; run this again' }
    Tv "content write --uri content://$pkg.config/routine.json < $stage; rm -f $stage"
}
# Starting the watcher once takes the app out of the stopped state, without which Android withholds BOOT_COMPLETED.
# It stops itself again when the routine does not ask for showOnWake.
Tv "am start-foreground-service -n $pkg/.WatchService"
Tv "am start -n $pkg/.BoardActivity"
Write-Host 'Installed. The board is open on the TV; press Home and answer Show card for the card.'
