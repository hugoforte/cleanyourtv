# Morning Board

What to do next on a school morning, on the TV: a large clock and how long until you leave, then a column per kid with
the morning's steps and their times. The step under way is highlighted, and the kids tick steps off with the remote.
For the [Clean Your TV](../../README.md) launcher it declares a [module card](../../docs/modules.md): once you
answer **Show card** its tile reads "Morning" with an amber glow. On any other Android TV home it is an ordinary app.

![Morning Board on a TV](screenshot.jpg)

| On the board | With the remote |
|---|---|
| The clock, and "Leave in 23 min", then "Time to go!" | |
| A column per kid (up to four): their name, how many steps are done, and the steps | ◀ ▶ moves between kids, ▲ ▼ between steps |
| The step whose time has come, in amber; earlier ones dimmed | OK ticks a step off, or back on |
| That day's extras under each column ("Today: PE kit") | |

Ticks last for the morning and are gone the next. Outside the routine's window the board shows the next morning's
plan, with nothing to tick.

## The routine

One JSON file, pushed from a computer (`example-routine.json` is a full one):

```json
{
  "days": ["MON", "TUE", "WED", "THU", "FRI"],
  "from": "6:30", "until": "8:10", "leave": "8:00",
  "showOnWake": true,
  "kids": [
    { "name": "Sam",
      "steps": [{ "at": "6:45", "do": "Up, dressed" }, { "at": "7:00", "do": "Breakfast" }],
      "extras": { "TUE": ["PE kit"] } }
  ]
}
```

| Field | Meaning |
|---|---|
| `days` | The days the routine runs: `MON` … `SUN` |
| `from`, `until` | The window, in 24-hour `H:mm` |
| `leave` | When everyone leaves, inside the window; the countdown runs to it |
| `showOnWake` | `true` opens the board by itself when the TV is switched on, or boots, inside the window. Off when left out |
| `kids` | One to four, each a `name`, its `steps` (`at` inside the window, one step a time, and `do`) and optional `extras` by day |

A file that breaks any of these is refused: the install script stops with the reason, the board shows it, and the
routine already on the TV stays. While the window is open the board keeps the screen on, so the screensaver does not
cover it. Keep your own routine outside the repo: it names your children.

## Build and install

JDK 17 and Android SDK platform 34 with build-tools 34.0.0, as for the launcher, and the `tools/venv` from the main
README. The `gradlew` scripts reuse the launcher's pinned Gradle wrapper with this folder as the project.

```bash
TV_HOST=<tv ip> addons/morning-board/build-and-install.sh [routine.json]
```

```powershell
addons\morning-board\build-and-install.ps1 -TvHost <tv ip> [-Routine routine.json]
```

Run it again with a new routine to change it; `--uninstall` (`-Uninstall`) removes the app. To build only:
`cd addons/morning-board && ./gradlew assembleRelease`; the JVM tests run with `./gradlew testReleaseUnitTest`.

## Permissions

| Permission | Why |
|---|---|
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | Only for `showOnWake`: a service that hears the screen turn on. It stops itself when the routine does not ask for it. |
| `RECEIVE_BOOT_COMPLETED` | Starts that service again after a reboot, and opens the board when the TV boots inside the window. |
| `SYSTEM_ALERT_WINDOW` | Android 10 and later let an app open its screen from the background only with it; the install script grants it. Not needed on Android 9. |

No network, no accounts, no libraries. The routine and the ticks stay in the app's own storage (backups are off).
`ConfigProvider` takes a pushed file and answers whether it was taken, and only the ADB shell can use it (it requires
`WRITE_SECURE_SETTINGS`, as Smart Home's does).

## Tested on

Not yet on a TV: the JVM tests cover the schedule (the window, which morning is shown, the step under way, the
countdown) and the routine's refusals.

## Maintainer

[@hugoforte](https://github.com/hugoforte)
