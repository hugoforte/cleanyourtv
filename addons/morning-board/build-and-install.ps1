# Windows version of build-and-install.sh: build Morning Board, install it on the TV, push a routine if given one,
# start the screen watcher and open the board.
#
# Usage: addons\morning-board\build-and-install.ps1 -TvHost 192.168.1.50 [-Routine my-routine.json] [-Mail mail.json]
#        addons\morning-board\build-and-install.ps1 -TvHost 192.168.1.50 -Uninstall
param(
    [string]$TvHost = $env:TV_HOST,
    [string]$Routine,
    # The account the emails go from: {"host", "port", "user", "password"}. Keep it out of every repo.
    [string]$Mail,
    [switch]$Uninstall
)
$ErrorActionPreference = 'Stop'
if (-not $TvHost) { throw 'pass -TvHost <tv ip> or set $env:TV_HOST' }
foreach ($f in @($Routine, $Mail)) { if ($f -and -not (Test-Path $f -PathType Leaf)) { throw "no such file: $f" } }
$env:TV_HOST = $TvHost
$here = $PSScriptRoot
$tools = Join-Path (Split-Path (Split-Path $here -Parent) -Parent) 'tools'
$py = Join-Path $tools 'venv\Scripts\python.exe'
if (-not (Test-Path $py)) { throw 'run: python -m venv tools\venv; tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt' }
$pkg = 'io.github.hugoforte.morningboard'
function Tv([string]$cmd) { & $py -I (Join-Path $tools 'tv.py') $cmd; if ($LASTEXITCODE) { throw "tv.py failed: $cmd" } }
# turnTvOn's standby service, as Sleep Timer's install script enables its own.
$svc = "$pkg/$pkg.SleepService"

# No double quotes inside these commands: Windows PowerShell 5.1 mangles them on the way to python, and the
# values have no spaces.
if ($Uninstall) {
    Tv ('s=$(settings get secure enabled_accessibility_services); s=$(echo :$s: | sed s#:SVC:#:#); s=${s#:}; s=${s%:}; if [ -z $s ] || [ $s = null ]; then settings delete secure enabled_accessibility_services; else settings put secure enabled_accessibility_services $s; fi; pm uninstall PKG'.Replace('SVC', $svc).Replace('PKG', $pkg))
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
# tv.py exits 0 whatever the command did, so the output says whether the install took.
$out = (& $py -I (Join-Path $tools 'tv.py') 'pm install -r /data/local/tmp/morningboard.apk; rm -f /data/local/tmp/morningboard.apk') -join "`n"
Write-Host $out
if ("$out" -notmatch 'Success') { throw 'pm install failed (an APK signed on another machine needs -Uninstall first)' }
# Android 10 and later let an app open a screen from the background only when it may draw over other apps;
# Android 12 and later withhold exact alarms unless allowed.
Tv "appops set $pkg SYSTEM_ALERT_WINDOW allow; appops set $pkg SCHEDULE_EXACT_ALARM allow 2>/dev/null; true"
# The media-session access for music: it does nothing unless the routine has music.
Tv "cmd notification allow_listener $pkg/$pkg.MediaListener"
# The accessibility service, for turnTvOn's standby and the music's Play button: it does nothing unless the routine
# asks for one. Android binds a service with the capabilities it had when it was turned on, so it is turned off and
# on again on every install, as the launcher's own script does.
Tv ('s=$(settings get secure enabled_accessibility_services); case :$s: in *:SVC:*) ;; *) if [ -z $s ] || [ $s = null ]; then s=SVC; else s=$s:SVC; fi;; esac; settings put secure enabled_accessibility_services ''''; sleep 1; settings put secure enabled_accessibility_services $s; settings put secure accessibility_enabled 1'.Replace('SVC', $svc))

foreach ($push in @(@{ File = $Routine; Name = 'routine.json' }, @{ File = $Mail; Name = 'mail.json' })) {
    if (-not $push.File) { continue }
    # The app's storage is out of the shell's reach, so the file goes in through the app's provider.
    $stage = "/data/local/tmp/morningboard-$($push.Name)"
    & $py -I (Join-Path $tools 'tv.py') --push (Resolve-Path $push.File).Path $stage | Out-Null
    if ($LASTEXITCODE) { throw "sending $($push.Name) failed; run this again" }
    Tv "content write --uri content://$pkg.config/$($push.Name) < $stage; rm -f $stage"
    $answer = (& $py -I (Join-Path $tools 'tv.py') "content call --uri content://$pkg.config --method problem") -join "`n"
    if ("$answer" -notmatch 'problem=none') { throw "the TV refused $($push.Name) and kept what it had: $answer" }
    Write-Host "$($push.Name) taken."
}
# Starting the watcher once takes the app out of the stopped state, without which Android withholds BOOT_COMPLETED.
# It stops itself again when the routine does not ask for showOnWake.
Tv "am start-foreground-service -n $pkg/.WatchService"
Tv "am start -n $pkg/.BoardActivity"
Write-Host 'Installed. The board is open on the TV; press Home and answer Show card for the card.'
