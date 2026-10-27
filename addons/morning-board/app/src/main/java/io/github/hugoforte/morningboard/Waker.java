package io.github.hugoforte.morningboard;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * The two alarms of turnTvOn: the TV on at the window's start, back to standby at its end. Set again after every
 * alarm, a boot, a clock change and a pushed routine; cancelled when the routine does not ask for them.
 */
final class Waker {
    static final String WAKE = "io.github.hugoforte.morningboard.WAKE";
    static final String SLEEP = "io.github.hugoforte.morningboard.SLEEP";

    private Waker() {
    }

    static void schedule(Context c) {
        Routine r;
        try {
            r = Store.routine(c);
        } catch (IllegalArgumentException e) {
            r = null;
        }
        AlarmManager am = c.getSystemService(AlarmManager.class);
        PendingIntent wake = alarm(c, WAKE);
        PendingIntent sleep = alarm(c, SLEEP);
        if (r == null || !r.turnTvOn) {
            am.cancel(wake);
            am.cancel(sleep);
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        set(am, r.nextFrom(now), wake);
        set(am, r.nextUntil(now), sleep);
    }

    private static PendingIntent alarm(Context c, String action) {
        return PendingIntent.getBroadcast(c, 0, new Intent(c, AlarmReceiver.class).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void set(AlarmManager am, LocalDateTime at, PendingIntent pi) {
        long ms = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        // Android 12+ may withhold exact alarms; the install script allows them, and a late one is still useful.
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi);
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi);
        }
    }
}
