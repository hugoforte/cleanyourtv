package io.github.hugoforte.morningboard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

/**
 * The routine's alarms. WAKE switches the screen on and opens the board; SLEEP puts the TV back to standby, but only
 * while the board is what is on screen, so a show someone started is left playing; REPORT emails what is still
 * unticked. Each sets the next alarms.
 */
public class AlarmReceiver extends BroadcastReceiver {
    private static final long WAKE_HOLD_MS = 10_000;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Waker.WAKE.equals(action)) {
            PowerManager pm = context.getSystemService(PowerManager.class);
            // Deprecated for phones, but on a TV the only way an app can turn the screen on; held only long
            // enough for the board's own turnScreenOn to take over.
            @SuppressWarnings("deprecation")
            PowerManager.WakeLock lock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK
                    | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE, "morningboard:wake");
            lock.acquire(WAKE_HOLD_MS);
            context.startActivity(new Intent(context, BoardActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } else if (Waker.SLEEP.equals(action)) {
            if (BoardActivity.showing()) SleepService.sleep();
        } else if (Waker.MUSIC_STOP.equals(action)) {
            // The pause is network work: keep the broadcast alive until it is done.
            PendingResult pending = goAsync();
            Music.stop(context, pending::finish);
        } else if (Waker.REPORT.equals(action)) {
            // The send is network work: keep the broadcast alive until it is done.
            PendingResult pending = goAsync();
            Mail.report(context, pending::finish);
        }
        Waker.schedule(context);
    }
}
