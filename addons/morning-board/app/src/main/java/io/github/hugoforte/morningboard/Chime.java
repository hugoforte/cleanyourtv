package io.github.hugoforte.morningboard;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

/**
 * The board's sounds, made here rather than shipped as files: a short two-note bell for a new step coming due (and
 * each step of the wake-up ramp), and a happy run up a chord for a box ticked.
 */
final class Chime {
    private static final int RATE = 44_100;

    private Chime() {
    }

    /** The bell as 16-bit mono samples: E6 then C6, each struck and dying away. Plain Java, tested. */
    static short[] samples() {
        double[] notes = {1318.5, 1046.5};
        int each = RATE * 6 / 10;
        short[] out = new short[each * notes.length];
        for (int n = 0; n < notes.length; n++) {
            for (int i = 0; i < each; i++) {
                double t = (double) i / RATE;
                double bell = Math.sin(2 * Math.PI * notes[n] * t) + 0.3 * Math.sin(2 * Math.PI * notes[n] * 2.76 * t);
                out[n * each + i] = (short) (Math.exp(-5 * t) * bell * 0.35 * Short.MAX_VALUE / 1.3);
            }
        }
        return out;
    }

    /** A happy little run up the major chord, C E G C, for a box ticked. Plain Java, tested. */
    static short[] happy() {
        double[] notes = {1046.5, 1318.5, 1568.0, 2093.0};
        int each = RATE * 12 / 100;
        int last = RATE * 5 / 10;
        short[] out = new short[each * (notes.length - 1) + last];
        for (int n = 0; n < notes.length; n++) {
            int length = n == notes.length - 1 ? last : each;
            for (int i = 0; i < length; i++) {
                double t = (double) i / RATE;
                double tone = Math.sin(2 * Math.PI * notes[n] * t) + 0.25 * Math.sin(2 * Math.PI * notes[n] * 2 * t);
                out[n * each + i] = (short) (Math.exp(-6 * t) * tone * 0.35 * Short.MAX_VALUE / 1.25);
            }
        }
        return out;
    }

    static void play() {
        play(samples());
    }

    static void playHappy() {
        play(happy());
    }

    private static void play(short[] s) {
        AudioTrack track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(new AudioFormat.Builder().setSampleRate(RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(s.length * 2)
                .build();
        track.write(s, 0, s.length);
        track.setNotificationMarkerPosition(s.length);
        track.setPlaybackPositionUpdateListener(new AudioTrack.OnPlaybackPositionUpdateListener() {
            @Override
            public void onMarkerReached(AudioTrack t) {
                t.release();
            }

            @Override
            public void onPeriodicNotification(AudioTrack t) {
            }
        });
        track.play();
    }
}
