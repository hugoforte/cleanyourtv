package io.github.hugoforte.morningboard;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/**
 * The routine and the day's ticks, in the app's own storage (backups are off). The routine arrives as routine.json
 * pushed by the install script, which is read once and deleted; a file that does not parse leaves the routine as it
 * was and says why on the board.
 */
final class Store {
    static final String PUSHED = "routine.json";

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("board", Context.MODE_PRIVATE);
    }

    /** The routine, or null when none was pushed yet. */
    static Routine routine(Context c) {
        String json = prefs(c).getString("routine", "");
        return json.isEmpty() ? null : Routine.parse(json);
    }

    /** Why the last pushed routine.json was refused, or null when it was taken. */
    static String problem(Context c) {
        return prefs(c).getString("problem", null);
    }

    /** Takes a pushed routine.json if there is one, and deletes it. True when there was one. */
    static boolean importPushed(Context c) {
        File dir = c.getExternalFilesDir(null);
        File f = dir == null ? null : new File(dir, PUSHED);
        if (f == null || !f.isFile()) return false;
        try {
            String json = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            Routine.parse(json);
            prefs(c).edit().putString("routine", json).remove("problem").remove("ticks").apply();
        } catch (Exception e) {
            prefs(c).edit().putString("problem", "routine.json was not taken: " + e.getMessage()).apply();
        } finally {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
        return true;
    }

    /** The steps ticked on that day, as "kid:step" indexes; another day's ticks read as none. */
    static Set<String> ticks(Context c, LocalDate day) {
        SharedPreferences p = prefs(c);
        if (!day.toString().equals(p.getString("ticksDay", ""))) return new HashSet<>();
        return new HashSet<>(p.getStringSet("ticks", new HashSet<>()));
    }

    static void toggle(Context c, LocalDate day, String key) {
        Set<String> ticks = ticks(c, day);
        if (!ticks.remove(key)) ticks.add(key);
        prefs(c).edit().putString("ticksDay", day.toString()).putStringSet("ticks", ticks).apply();
    }
}
