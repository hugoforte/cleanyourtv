package io.github.hugoforte.morningboard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** After a boot or an update: start watching the screen. The service stops itself when the routine does not ask for it. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        context.startForegroundService(new Intent(context, WatchService.class));
    }
}
