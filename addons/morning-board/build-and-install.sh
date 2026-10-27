#!/usr/bin/env bash
# Build Morning Board, install it on the TV, push a routine if given one, start the screen watcher and open the board.
#   TV_HOST=<tv ip> addons/morning-board/build-and-install.sh [routine.json [mail.json]]
#   TV_HOST=<tv ip> addons/morning-board/build-and-install.sh --uninstall
# mail.json is the account the emails go from ({"host", "port", "user", "password"}); keep it out of every repo.
set -euo pipefail
ROUTINE="${1:-}"
MAIL="${2:-}"
: "${TV_HOST:?set TV_HOST to the TV IP address}"
absolute() {
  [ -f "$1" ] || { echo "no such file: $1" >&2; exit 1; }
  local p
  p="$(cd "$(dirname "$1")" && pwd)/$(basename "$1")"
  if command -v cygpath >/dev/null 2>&1; then p="$(cygpath -m "$p")"; fi
  echo "$p"
}
if [ -n "$ROUTINE" ] && [ "$ROUTINE" != "--uninstall" ]; then ROUTINE=$(absolute "$ROUTINE"); fi
if [ -n "$MAIL" ]; then MAIL=$(absolute "$MAIL"); fi
cd "$(dirname "$0")"
TOOLS="$(cd ../../tools && pwd)"
PY=$TOOLS/venv/bin/python
[ -x "$PY" ] || PY=$TOOLS/venv/Scripts/python.exe
export MSYS_NO_PATHCONV=1
PKG=io.github.hugoforte.morningboard
# Script paths stay relative: with MSYS_NO_PATHCONV, Git Bash would hand Windows Python a /c/... path.
tv() { "$PY" -I ../../tools/tv.py "$@"; }
# turnTvOn's standby service, as Sleep Timer's install script enables its own.
SVC=$PKG/$PKG.SleepService

if [ "$ROUTINE" = "--uninstall" ]; then
  tv "s=\$(settings get secure enabled_accessibility_services); s=\$(echo :\$s: | sed s#:$SVC:#:#); s=\${s#:}; s=\${s%:}; if [ -z \$s ] || [ \$s = null ]; then settings delete secure enabled_accessibility_services; else settings put secure enabled_accessibility_services \$s; fi; pm uninstall $PKG"
  exit 0
fi

[ -f local.properties ] || { [ -n "${ANDROID_HOME:-}" ] && echo "sdk.dir=${ANDROID_HOME//\\//}" > local.properties; } || true
if [ -n "${JAVA_HOME:-}" ]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
./gradlew assembleRelease -q --console=plain

"$PY" -I ../../tools/b64push.py app/build/outputs/apk/release/app-release.apk /data/local/tmp/morningboard.apk
OUT=$(tv 'pm install -r /data/local/tmp/morningboard.apk; rm -f /data/local/tmp/morningboard.apk')
echo "$OUT"
case "$OUT" in *Success*) ;; *) echo "ERROR: pm install failed" >&2; exit 1 ;; esac
# Android 10 and later let an app open a screen from the background only when it may draw over other apps;
# Android 12 and later withhold exact alarms unless allowed.
tv "appops set $PKG SYSTEM_ALERT_WINDOW allow; appops set $PKG SCHEDULE_EXACT_ALARM allow 2>/dev/null; true"
# The standby service for turnTvOn: it does nothing unless the routine asks for it.
tv "s=\$(settings get secure enabled_accessibility_services); case :\$s: in *:$SVC:*) ;; *) if [ -z \$s ] || [ \$s = null ]; then s=$SVC; else s=\$s:$SVC; fi; settings put secure enabled_accessibility_services \$s;; esac; settings put secure accessibility_enabled 1"

# The app's storage is out of the shell's reach, so each file goes in through the app's provider.
push() {
  local stage=/data/local/tmp/morningboard-$2
  tv --push "$1" "$stage" >/dev/null
  tv "content write --uri content://$PKG.config/$2 < $stage; rm -f $stage"
  ANSWER=$(tv "content call --uri content://$PKG.config --method problem")
  case "$ANSWER" in *problem=none*) echo "$2 taken." ;; *) echo "ERROR: the TV refused $2 and kept what it had: $ANSWER" >&2; exit 1 ;; esac
}
if [ -n "$ROUTINE" ]; then push "$ROUTINE" routine.json; fi
if [ -n "$MAIL" ]; then push "$MAIL" mail.json; fi
# Starting the watcher once takes the app out of the stopped state, without which Android withholds BOOT_COMPLETED.
# It stops itself again when the routine does not ask for showOnWake.
tv "am start-foreground-service -n $PKG/.WatchService"
tv "am start -n $PKG/.BoardActivity >/dev/null; echo opened"
echo "Installed. The board is open on the TV; press Home and answer Show card for the card."
