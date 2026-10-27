package io.github.hugoforte.morningboard;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;

import java.time.LocalDateTime;

/**
 * The morning's music: once a morning, when the screen comes on inside the window, open the music app on the
 * routine's link (a spotify:playlist:… for Spotify), press its focused Play button through SleepService, check the
 * player is playing and press again if not, then bring the board back on top while it plays. At music.until, pause
 * it. Spotify's TV app ignores a media session's playFromUri from an app it does not know, so the button is pressed
 * instead. The player's state is read through its media session, which needs the board's notification listener
 * allowed (the install script does it).
 */
final class Music {
    private static final String TAG = "MorningBoard";
    /** After the screen comes on, a TV's on-timer switches to its input; the music app opens after that. */
    private static final long OPEN_MS = 1_500;
    /** Long enough for the music app to load the playlist page with its Play button focused. */
    private static final long[] PRESS_MS = {5_000, 9_000};
    private static final long BACK_MS = 2_000;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** Until when (elapsed realtime) the music is being started, which the board's reopening waits for. */
    private static volatile long startingUntil;

    private Music() {
    }

    static boolean starting() {
        return SystemClock.elapsedRealtime() < startingUntil;
    }

    /** Starts the morning's music once a morning, if the routine has some and the window is open. */
    static void morning(Context c, Routine r) {
        LocalDateTime now = LocalDateTime.now();
        if (r.music == null || !r.inWindow(now) || !now.toLocalTime().isBefore(r.music.until)) return;
        if (!Store.firstTime(c, now.toLocalDate(), "music")) return;
        Context app = c.getApplicationContext();
        startingUntil = SystemClock.elapsedRealtime() + OPEN_MS + PRESS_MS[PRESS_MS.length - 1] + BACK_MS;
        MAIN.postDelayed(() -> {
            try {
                app.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(r.music.uri)).setPackage(r.music.app)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (RuntimeException e) {
                Log.w(TAG, "could not open " + r.music.app, e);
                startingUntil = 0;
                return;
            }
            for (int i = 0; i < PRESS_MS.length; i++) {
                boolean last = i == PRESS_MS.length - 1;
                MAIN.postDelayed(() -> {
                    if (!playing(app, r.music.app) && !SleepService.press(r.music.app)) {
                        Log.w(TAG, "no Play button to press in " + r.music.app + "; is the board's accessibility service on?");
                    }
                    if (last) {
                        MAIN.postDelayed(() -> {
                            startingUntil = 0;
                            app.startActivity(new Intent(app, BoardActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                        }, BACK_MS);
                    }
                }, PRESS_MS[i]);
            }
        }, OPEN_MS);
    }

    /** Pauses the music app's player, at music.until. */
    static void stop(Context c, Routine r) {
        if (r.music == null) return;
        MediaController player = player(c.getApplicationContext(), r.music.app);
        if (player != null) {
            player.getTransportControls().pause();
        } else {
            AudioManager audio = c.getSystemService(AudioManager.class);
            audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE));
            audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE));
        }
    }

    private static boolean playing(Context c, String pkg) {
        MediaController player = player(c, pkg);
        PlaybackState state = player == null ? null : player.getPlaybackState();
        return state != null && state.getState() == PlaybackState.STATE_PLAYING;
    }

    private static MediaController player(Context c, String pkg) {
        try {
            MediaSessionManager sessions = c.getSystemService(MediaSessionManager.class);
            for (MediaController m : sessions.getActiveSessions(new ComponentName(c, MediaListener.class))) {
                if (pkg.equals(m.getPackageName())) return m;
            }
        } catch (SecurityException e) {
            Log.w(TAG, "the board's notification listener is not allowed", e);
        }
        return null;
    }
}
