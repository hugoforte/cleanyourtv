package io.github.hugoforte.morningboard;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The routine, the mail and Spotify accounts and the day's ticks, in the app's own storage (backups are off). They
 * arrive as routine.json, mail.json and spotify.json, pushed by the install script, each read once and deleted; a
 * file that does not parse leaves what was there and says why on the board.
 */
final class Store {
    static final String ROUTINE = "routine.json";
    static final String MAIL = "mail.json";
    static final String SPOTIFY = "spotify.json";
    static final List<String> PUSHED = Arrays.asList(ROUTINE, MAIL, SPOTIFY);

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("board", Context.MODE_PRIVATE);
    }

    /** Every session of the routine, or null when none was pushed yet. */
    static Schedule schedule(Context c) {
        String json = prefs(c).getString("routine", "");
        return json.isEmpty() ? null : Schedule.parse(json);
    }

    /** The session in play now (Schedule.at), or null when no routine was pushed yet. */
    static Routine routine(Context c) {
        Schedule s = schedule(c);
        return s == null ? null : s.at(LocalDateTime.now());
    }

    /** The account the emails are sent from, or null when no mail.json was pushed. */
    static Mailer.Account mail(Context c) {
        String json = prefs(c).getString("mail", "");
        return json.isEmpty() ? null : Mailer.Account.parse(json);
    }

    /** Why the last pushed file was refused, or null when it was taken. */
    static String problem(Context c) {
        return prefs(c).getString("problem", null);
    }

    /** The Spotify account the morning's music is started with, or null when no spotify.json was pushed. */
    static Spotify.Account spotify(Context c) {
        String json = prefs(c).getString("spotify", "");
        return json.isEmpty() ? null : Spotify.Account.parse(json);
    }

    /** Why the morning's music did not start, or null when it did. */
    static String musicProblem(Context c) {
        return prefs(c).getString("musicProblem", null);
    }

    static void musicResult(Context c, String problem) {
        if (problem == null) prefs(c).edit().remove("musicProblem").apply();
        else prefs(c).edit().putString("musicProblem", problem).apply();
    }

    /** Why the last email could not be sent, or null when it went. */
    static String mailProblem(Context c) {
        return prefs(c).getString("mailProblem", null);
    }

    static void mailResult(Context c, String problem) {
        if (problem == null) prefs(c).edit().remove("mailProblem").apply();
        else prefs(c).edit().putString("mailProblem", problem).apply();
    }

    /** Where a pushed file lands: the app's internal storage, which no other app can read. */
    static File pushed(Context c, String name) {
        return new File(c.getFilesDir(), name);
    }

    /**
     * Takes the pushed files there are, and deletes them. Synchronized: the provider's close listener and its
     * "problem" call can both come for the same file.
     */
    static synchronized void importPushed(Context c) {
        for (String name : PUSHED) {
            File f = pushed(c, name);
            if (!f.isFile()) continue;
            try {
                String json = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
                if (name.equals(ROUTINE)) {
                    Schedule.parse(json);
                    // A new routine is a fresh morning: its ticks, emails and music start over.
                    SharedPreferences.Editor e = prefs(c).edit().putString("routine", json).remove("problem").remove("ticks");
                    for (String key : prefs(c).getAll().keySet()) if (key.startsWith("mailed:")) e.remove(key);
                    e.apply();
                } else if (name.equals(MAIL)) {
                    Mailer.Account.parse(json);
                    prefs(c).edit().putString("mail", json).remove("problem").remove("mailProblem").apply();
                } else {
                    Spotify.Account.parse(json);
                    prefs(c).edit().putString("spotify", json).remove("problem").remove("musicProblem").apply();
                }
                Waker.schedule(c);
            } catch (Exception e) {
                prefs(c).edit().putString("problem", name + " was not taken: " + e.getMessage()).apply();
            } finally {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        }
    }

    /**
     * The steps of session r ticked on that day, as "kid:step" indexes; another day's or session's ticks read as
     * none. Only one session's day is kept: ticking another's lets the last one go.
     */
    static Set<String> ticks(Context c, Routine r, LocalDate day) {
        SharedPreferences p = prefs(c);
        if (!ticksKey(r, day).equals(p.getString("ticksDay", ""))) return new HashSet<>();
        return new HashSet<>(p.getStringSet("ticks", new HashSet<>()));
    }

    static void toggle(Context c, Routine r, LocalDate day, String key) {
        Set<String> ticks = ticks(c, r, day);
        if (!ticks.remove(key)) ticks.add(key);
        prefs(c).edit().putString("ticksDay", ticksKey(r, day)).putStringSet("ticks", ticks).apply();
    }

    private static String ticksKey(Routine r, LocalDate day) {
        return day + "@" + r.id();
    }

    /** Whether firstTime or mark has recorded what for session r on that day. */
    static boolean marked(Context c, Routine r, LocalDate day, String what) {
        return prefs(c).getBoolean(markKey(r, day, what), false);
    }

    /** Sets or clears a mark for session r on that day, as firstTime sets one; gone the day after with the rest. */
    static synchronized void mark(Context c, Routine r, LocalDate day, String what, boolean on) {
        String key = markKey(r, day, what);
        if (on) prefs(c).edit().putBoolean(key, true).apply();
        else prefs(c).edit().remove(key).apply();
    }

    private static String markKey(Routine r, LocalDate day, String what) {
        return "mailed:" + day + ":" + r.id() + ":" + what;
    }

    /** Records that something was done for session r on that day and key, once: false when it already was. */
    static synchronized boolean firstTime(Context c, Routine r, LocalDate day, String what) {
        String key = markKey(r, day, what);
        SharedPreferences p = prefs(c);
        if (p.getBoolean(key, false)) return false;
        SharedPreferences.Editor e = p.edit();
        // Yesterday's marks are no use; keep the file small.
        for (String old : p.getAll().keySet()) if (old.startsWith("mailed:") && !old.startsWith("mailed:" + day)) e.remove(old);
        e.putBoolean(key, true).apply();
        return true;
    }
}
