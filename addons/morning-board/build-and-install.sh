#!/usr/bin/env bash
# Build Morning Board, install it on the TV, push a routine if given one, start the screen watcher and open the board.
#   TV_HOST=<tv ip> addons/morning-board/build-and-install.sh [routine.json]
#   TV_HOST=<tv ip> addons/morning-board/build-and-install.sh --uninstall
set -euo pipefail
ROUTINE="${1:-}"
: "${TV_HOST:?set TV_HOST to the TV IP address}"
if [ -n "$ROUTINE" ] && [ "$ROUTINE" != "--uninstall" ]; then
  [ -f "$ROUTINE" ] || { echo "no such file: $ROUTINE" >&2; exit 1; }
  ROUTINE="$(cd "$(dirname "$ROUTINE")" && pwd)/$(basename "$ROUTINE")"
  if command -v cygpath >/dev/null 2>&1; then ROUTINE="$(cygpath -m "$ROUTINE")"; fi
fi
cd "$(dirname "$0")"
TOOLS="$(cd ../../tools && pwd)"
PY=$TOOLS/venv/bin/python
[ -x "$PY" ] || PY=$TOOLS/venv/Scripts/python.exe
export MSYS_NO_PATHCONV=1
PKG=io.github.hugoforte.morningboard
# Script paths stay relative: with MSYS_NO_PATHCONV, Git Bash would hand Windows Python a /c/... path.
tv() { "$PY" -I ../../tools/tv.py "$@"; }

if [ "$ROUTINE" = "--uninstall" ]; then
  tv "pm uninstall $PKG"
  exit 0
fi

[ -f local.properties ] || { [ -n "${ANDROID_HOME:-}" ] && echo "sdk.dir=${ANDROID_HOME//\\//}" > local.properties; } || true
if [ -n "${JAVA_HOME:-}" ]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
./gradlew assembleRelease -q --console=plain

"$PY" -I ../../tools/b64push.py app/build/outputs/apk/release/app-release.apk /data/local/tmp/morningboard.apk
OUT=$(tv 'pm install -r /data/local/tmp/morningboard.apk; rm -f /data/local/tmp/morningboard.apk')
echo "$OUT"
case "$OUT" in *Success*) ;; *) echo "ERROR: pm install failed" >&2; exit 1 ;; esac
# Android 10 and later let an app open a screen from the background only when it may draw over other apps.
tv "appops set $PKG SYSTEM_ALERT_WINDOW allow"

# The app's storage is out of the shell's reach, so the file goes in through the app's provider.
if [ -n "$ROUTINE" ]; then
  STAGE=/data/local/tmp/morningboard-routine.json
  tv --push "$ROUTINE" "$STAGE" >/dev/null
  tv "content write --uri content://$PKG.config/routine.json < $STAGE; rm -f $STAGE"
  ANSWER=$(tv "content call --uri content://$PKG.config --method problem")
  case "$ANSWER" in *problem=none*) echo "Routine taken." ;; *) echo "ERROR: the TV refused the routine and kept the one it had: $ANSWER" >&2; exit 1 ;; esac
fi
# Starting the watcher once takes the app out of the stopped state, without which Android withholds BOOT_COMPLETED.
# It stops itself again when the routine does not ask for showOnWake.
tv "am start-foreground-service -n $PKG/.WatchService"
tv "am start -n $PKG/.BoardActivity >/dev/null; echo opened"
echo "Installed. The board is open on the TV; press Home and answer Show card for the card."
