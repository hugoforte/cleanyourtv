package io.github.hugoforte.morningboard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * After a boot or an update: start watching the screen. The service stops itself when the routine does not ask for
 * it. A boot inside the window opens the board too, since the screen came on before anything was listening.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        boolean boot = Intent.ACTION_BOOT_COMPLETED.equals(action);
        if (!boot && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) return;
        context.startForegroundService(new Intent(context, WatchService.class).putExtra(WatchService.EXTRA_OPEN, boot));
    }
}
