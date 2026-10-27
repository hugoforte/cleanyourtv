package io.github.hugoforte.morningboard;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;

import java.time.LocalDateTime;

/**
 * Opens the board when the screen turns on inside the routine's window, for a routine with showOnWake. SCREEN_ON is
 * only delivered to a receiver registered at run time, so this foreground service keeps one registered; with no
 * routine, or showOnWake off, it stops itself.
 */
public class WatchService extends Service {
    /** Open the board now if the window is open: a boot, whose screen came on before the receiver was registered. */
    static final String EXTRA_OPEN = "open";
    private static final String CHANNEL = "watch";

    private final BroadcastReceiver screenOn = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            openInWindow();
        }
    };

    private void openInWindow() {
        Routine r = wanted();
        if (r != null && r.inWindow(LocalDateTime.now())) {
            startActivity(new Intent(this, BoardActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, getString(R.string.watching),
                NotificationManager.IMPORTANCE_MIN));
        Notification notification = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_today)
                .setContentTitle(getString(R.string.watching))
                .build();
        startForeground(1, notification);
        registerReceiver(screenOn, new IntentFilter(Intent.ACTION_SCREEN_ON));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (wanted() == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (intent != null && intent.getBooleanExtra(EXTRA_OPEN, false)) openInWindow();
        return START_STICKY;
    }

    /** The routine, when it asks to open on wake. */
    private Routine wanted() {
        try {
            Routine r = Store.routine(this);
            return r != null && r.showOnWake ? r : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(screenOn);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
