package io.github.hugoforte.morningboard;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * The routine's alarms: turnTvOn's two (the TV on at the window's start, back to standby at its end) and the email
 * report. Set again after every alarm, a boot, a clock change and a pushed file; cancelled when the routine does not
 * ask for them.
 */
final class Waker {
    static final String WAKE = "io.github.hugoforte.morningboard.WAKE";
    static final String SLEEP = "io.github.hugoforte.morningboard.SLEEP";
    /** The email of what is still unticked, at the routine's email.reportAt. */
    static final String REPORT = "io.github.hugoforte.morningboard.REPORT";
    /** The morning's music paused, at the routine's music.until. */
    static final String MUSIC_STOP = "io.github.hugoforte.morningboard.MUSIC_STOP";

    private Waker() {
    }

    /** Each alarm at the soonest time any session asks for it; the receiver acts for the session in play then. */
    static void schedule(Context c) {
        Schedule s;
        try {
            s = Store.schedule(c);
        } catch (IllegalArgumentException e) {
            s = null;
        }
        AlarmManager am = c.getSystemService(AlarmManager.class);
        LocalDateTime now = LocalDateTime.now();
        set(am, c, WAKE, s == null ? null : s.soonest(r -> r.turnTvOn ? r.nextFrom(now) : null));
        set(am, c, SLEEP, s == null ? null : s.soonest(r -> r.turnTvOn ? r.nextUntil(now) : null));
        set(am, c, REPORT, s == null ? null : s.soonest(r -> r.email != null ? r.nextReport(now) : null));
        set(am, c, MUSIC_STOP, s == null ? null : s.soonest(r -> r.music != null ? r.nextMusicStop(now) : null));
    }

    /** Sets the alarm for action at at, or cancels it when at is null. */
    private static void set(AlarmManager am, Context c, String action, LocalDateTime at) {
        PendingIntent pi = alarm(c, action);
        if (at == null) am.cancel(pi);
        else set(am, at, pi);
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
