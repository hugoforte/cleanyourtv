# Build the YouTube Channel card for one channel and install it on the TV.
#
# Usage: addons\youtube-channel\build-and-install.ps1 -TvHost 192.168.1.50 -ChannelId UC... -Name "Rosa's Lounge"
#            -Picture rosa.jpg
#        addons\youtube-channel\build-and-install.ps1 -TvHost 192.168.1.50 -Uninstall
param(
    [string]$TvHost = $env:TV_HOST,
    # The channel's ID, the UC... in youtube.com/channel/<id>.
    [string]$ChannelId,
    # The card's label: up to 24 letters, digits, spaces and . & + ' - (docs/specs/card.md).
    [string]$Name,
    # The tile's picture, a square PNG or JPEG such as the channel's own avatar.
    [string]$Picture,
    [switch]$Uninstall
)
$ErrorActionPreference = 'Stop'
if (-not $TvHost) { throw 'pass -TvHost <tv ip> or set $env:TV_HOST' }
$env:TV_HOST = $TvHost
$here = $PSScriptRoot
$tools = Join-Path (Split-Path (Split-Path $here -Parent) -Parent) 'tools'
$py = Join-Path $tools 'venv\Scripts\python.exe'
if (-not (Test-Path $py)) { throw 'run: python -m venv tools\venv; tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt' }
$pkg = 'io.github.hugoforte.youtubechannel'

if ($Uninstall) {
    & $py -I (Join-Path $tools 'tv.py') "pm uninstall $pkg"
    exit 0
}
if ($ChannelId -notmatch '^UC[A-Za-z0-9_-]{22}$') { throw "-ChannelId must be a channel ID (UC and 22 more characters), not '$ChannelId'" }
if ($Name -notmatch "^[A-Za-z0-9][A-Za-z0-9 .&+'-]{0,22}[A-Za-z0-9.&+'-]?$") { throw "-Name '$Name' is not a card label (docs/specs/card.md)" }
if (-not $Picture -or -not (Test-Path $Picture -PathType Leaf)) { throw "pass -Picture <a square png or jpg>" }
$ext = [IO.Path]::GetExtension($Picture).ToLowerInvariant()
if ($ext -eq '.jpeg') { $ext = '.jpg' }
if ($ext -notin '.png', '.jpg') { throw "-Picture must be a .png or .jpg, not $ext" }

# The channel's resources, out of git (.gitignore): its name, ID, card and picture.
$res = Join-Path $here 'app\src\channel\res'
if (Test-Path $res) { Remove-Item -Recurse -Force $res }
foreach ($d in 'values', 'xml', 'drawable-nodpi') { New-Item -ItemType Directory -Force (Join-Path $res $d) | Out-Null }
$xmlName = [Security.SecurityElement]::Escape($Name)
# A string resource takes an apostrophe as \' and not as the &apos; an attribute takes.
$resName = $Name.Replace('&', '&amp;').Replace("'", "\'")
$utf8 = New-Object Text.UTF8Encoding $false
[IO.File]::WriteAllText((Join-Path $res 'values\channel.xml'), @"
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">$resName</string>
    <string name="channel_id" translatable="false">$ChannelId</string>
</resources>
"@, $utf8)
[IO.File]::WriteAllText((Join-Path $res 'xml\module_card.xml'), @"
<?xml version="1.0" encoding="utf-8"?>
<card spec="1" label="$xmlName" caption="Live or past shows" accent="#C0392B" />
"@, $utf8)
Copy-Item $Picture (Join-Path $res "drawable-nodpi\channel_picture$ext")

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
& $py -I (Join-Path $tools 'b64push.py') (Join-Path $here 'app\build\outputs\apk\release\app-release.apk') /data/local/tmp/youtubechannel.apk | Out-Null
if ($LASTEXITCODE) { throw 'sending the APK failed; run this again' }
# tv.py exits 0 whatever the command did, so the output says whether the install took.
$out = (& $py -I (Join-Path $tools 'tv.py') 'pm install -r /data/local/tmp/youtubechannel.apk; rm -f /data/local/tmp/youtubechannel.apk') -join "`n"
Write-Host $out
if ("$out" -notmatch 'Success') { throw 'pm install failed (an APK signed on another machine needs -Uninstall first)' }
Write-Host 'Installed. Press Home on the TV and answer Show card for the card.'
