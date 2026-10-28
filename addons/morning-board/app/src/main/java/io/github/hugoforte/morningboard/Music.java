package io.github.hugoforte.morningboard;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The morning's music: once a morning, when the screen comes on inside the window, open the music app (so its
 * Spotify Connect device comes up), wait for the device, set the TV's starting volume, start the routine's playlist
 * on it through Spotify's Web API, and bring the board back on top while it plays; then ramp the volume up until
 * someone ticks a step. At music.until, pause it. A failure is kept for the board to show, like a failed email.
 */
final class Music {
    private static final String TAG = "MorningBoard";
    /** After the screen comes on, a TV's on-timer switches to its input; the music app opens after that. */
    private static final long OPEN_MS = 1_500;
    /** How long a cold-started music app may take to show up as a Connect device. */
    private static final long DEVICE_WAIT_MS = 30_000;
    private static final int PLAY_ATTEMPTS = 12;
    /** Long enough for Spotify to have applied its own volume once playback starts. */
    private static final long VOLUME_AFTER_MS = 3_000;
    private static final int MAX_VOLUME_STEPS = 110;
    private static final long VOLUME_STEP_MS = 250;
    private static final ExecutorService NET = Executors.newSingleThreadExecutor();
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
        startingUntil = SystemClock.elapsedRealtime() + OPEN_MS + DEVICE_WAIT_MS;
        NET.execute(() -> {
            try {
                Spotify.Account account = Store.spotify(app);
                if (account == null) throw new IllegalStateException("no spotify.json was pushed, so no music can start");
                SystemClock.sleep(OPEN_MS);
                app.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(r.music.uri)).setPackage(r.music.app)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                Spotify spotify = new Spotify(account);
                String device = null;
                long giveUp = SystemClock.elapsedRealtime() + DEVICE_WAIT_MS;
                while (device == null && SystemClock.elapsedRealtime() < giveUp) {
                    device = spotify.device(r.music.device);
                    if (device == null) SystemClock.sleep(2_000);
                }
                if (device == null) {
                    throw new IllegalStateException("Spotify never showed the device \"" + r.music.device + "\"");
                }
                playOnFreshDevice(spotify, device, r.music.uri);
                // Spotify applies the volume it last had on the device as playback starts; the morning's comes after.
                if (r.music.startVolume >= 0) {
                    SystemClock.sleep(VOLUME_AFTER_MS);
                    setVolume(app, r.music.startVolume);
                }
                Store.musicResult(app, null);
                if (r.music.rampEveryMinutes > 0) MAIN.postDelayed(new Ramp(app), r.music.rampEveryMinutes * 60_000L);
            } catch (Exception e) {
                Log.w(TAG, "morning music not started", e);
                Store.musicResult(app, "Music not started: " + e.getMessage());
            } finally {
                startingUntil = 0;
                app.startActivity(new Intent(app, BoardActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
        });
    }

    /**
     * The wake-up ramp: every rampEveryMinutes, a ding and the TV's volume rampStep higher, up to maxVolume, until
     * someone ticks a step or the music's time is up. It reads the routine afresh each time, so a new one takes over.
     */
    private static final class Ramp implements Runnable {
        private final Context app;

        Ramp(Context app) {
            this.app = app;
        }

        @Override
        public void run() {
            Routine r;
            try {
                r = Store.routine(app);
            } catch (IllegalArgumentException e) {
                return;
            }
            LocalDateTime now = LocalDateTime.now();
            if (r == null || r.music == null || r.music.rampEveryMinutes <= 0 || !r.inWindow(now)
                    || !now.toLocalTime().isBefore(r.music.until) || !Store.ticks(app, now.toLocalDate()).isEmpty()) {
                return;
            }
            Chime.play();
            int to = r.music.raised(volume(app));
            NET.execute(() -> setVolume(app, to));
            MAIN.postDelayed(this, r.music.rampEveryMinutes * 60_000L);
        }
    }

    /** The TV's volume, in percent of the music stream's range. */
    static int volume(Context c) {
        AudioManager audio = c.getSystemService(AudioManager.class);
        return Math.round(audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100f
                / audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
    }

    /**
     * Brings the TV's volume to percent one step at a time. A Sony BRAVIA moves its volume a single step for each
     * change it is asked for, however far the asked-for level is, as its remote's volume button does; so the board
     * steps until it gets there. Blocks while it steps: call it off the main thread.
     */
    static void setVolume(Context c, int percent) {
        AudioManager audio = c.getSystemService(AudioManager.class);
        int target = Math.round(percent * audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) / 100f);
        for (int i = 0; i < MAX_VOLUME_STEPS; i++) {
            int now = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
            if (now == target) return;
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                    now < target ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER, 0);
            SystemClock.sleep(VOLUME_STEP_MS);
        }
        Log.w(TAG, "the volume did not reach " + percent + "%; it is " + volume(c) + "%");
    }

    /**
     * Starts the playlist on a device that has only just shown up: Spotify lists a newly opened app's device a few
     * seconds before it will take a command, answering 404 or 502 until then (on the Bravia, 502 at 7:00 on
     * 2026-10-28), so the play is asked again for as long as the answer says try later.
     */
    private static void playOnFreshDevice(Spotify spotify, String device, String uri) throws IOException {
        for (int attempt = 1; ; attempt++) {
            try {
                spotify.play(device, uri);
                return;
            } catch (IOException e) {
                if (attempt >= PLAY_ATTEMPTS || !Spotify.tryLater(e)) throw e;
                SystemClock.sleep(3_000);
            }
        }
    }

    /** Pauses the account's playback, at music.until. done runs once that has been asked. */
    static void stop(Context c, Runnable done) {
        Context app = c.getApplicationContext();
        NET.execute(() -> {
            try {
                Spotify.Account account = Store.spotify(app);
                if (account != null) new Spotify(account).pause();
            } catch (Exception e) {
                Log.w(TAG, "morning music not paused", e);
            } finally {
                done.run();
            }
        });
    }
}
