package io.github.hugoforte.morningboard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.Arrays;
import java.util.List;

/**
 * After a boot, an update or a clock change: set turnTvOn's alarms again, and after a boot or an update start
 * watching the screen. The service stops itself when the routine does not ask for it. A boot inside the window
 * opens the board too, since the screen came on before anything was listening.
 */
public class BootReceiver extends BroadcastReceiver {
    private static final List<String> CLOCK = Arrays.asList(Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED);

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        boolean boot = Intent.ACTION_BOOT_COMPLETED.equals(action);
        boolean replaced = Intent.ACTION_MY_PACKAGE_REPLACED.equals(action);
        if (!boot && !replaced && !CLOCK.contains(action)) return;
        Waker.schedule(context);
        if (boot || replaced) {
            context.startForegroundService(new Intent(context, WatchService.class).putExtra(WatchService.EXTRA_OPEN, boot));
        }
    }
}
