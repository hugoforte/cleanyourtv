package io.github.hugoforte.morningboard;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

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

    /**
     * When to bring the board back after the screen comes on. A TV's own on-timer switches to its input a second or
     * so after waking, over the board; a Sony BRAVIA's does it again some 20 seconds in, once the clock it syncs on
     * waking reaches the timer. So the board comes back several times in the first minute.
     */
    private static final long[] AGAIN_MS = {3_000, 8_000, 15_000, 25_000, 40_000, 60_000};

    private final Handler main = new Handler(Looper.getMainLooper());

    private final BroadcastReceiver screenOn = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            openInWindow();
            for (long ms : AGAIN_MS) main.postDelayed(WatchService.this::openInWindow, ms);
            Routine r = wanted();
            if (r != null) Music.morning(WatchService.this, r);
        }
    };

    private void openInWindow() {
        Routine r = wanted();
        // While the music is being started its app is in front on purpose; Music brings the board back after.
        if (r != null && r.inWindow(LocalDateTime.now()) && !BoardActivity.showing() && !Music.starting()) {
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
        main.removeCallbacksAndMessages(null);
        unregisterReceiver(screenOn);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
