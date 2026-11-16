package io.github.hugoforte.morningboard;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * The pushed routine.json: one session (a routine as Routine reads it), or {"sessions": [...]} for several a day, a
 * school morning and an afternoon. One session is in play at a time, the one whose window is open or else the one
 * that opens next; the board, the music and the emails work on that one. Plain Java, tested on the JVM.
 */
final class Schedule {
    final List<Routine> sessions;

    private Schedule(List<Routine> sessions) {
        this.sessions = sessions;
    }

    /** Reads routine.json; the message of what it throws says which session and field is wrong. */
    static Schedule parse(String json) {
        try {
            JSONObject o = new JSONObject(json);
            if (!o.has("sessions")) return new Schedule(Collections.singletonList(Routine.parse(o)));
            if (o.length() != 1) {
                throw new IllegalArgumentException("with \"sessions\", everything goes inside a session");
            }
            JSONArray s = o.getJSONArray("sessions");
            if (s.length() < 1) throw new IllegalArgumentException("\"sessions\" is empty");
            List<Routine> sessions = new ArrayList<>();
            for (int i = 0; i < s.length(); i++) {
                Routine r;
                try {
                    r = Routine.parse(s.getJSONObject(i));
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("session " + (i + 1) + ": " + e.getMessage(), e);
                }
                for (Routine earlier : sessions) {
                    if (r.overlaps(earlier)) {
                        throw new IllegalArgumentException("the sessions from " + earlier.id() + " and from " + r.id()
                                + " overlap on a day both run");
                    }
                }
                sessions.add(r);
            }
            return new Schedule(Collections.unmodifiableList(sessions));
        } catch (JSONException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    /** The session in play at t: the one whose window is open, else the one that opens soonest after t. */
    Routine at(LocalDateTime t) {
        Routine next = null;
        LocalDateTime opens = null;
        for (Routine r : sessions) {
            if (r.inWindow(t)) return r;
            LocalDateTime o = r.nextFrom(t);
            if (opens == null || o.isBefore(opens)) {
                next = r;
                opens = o;
            }
        }
        return next;
    }

    /** Whether any session opens the board when the screen comes on, which is when the screen watcher runs. */
    boolean showOnWake() {
        for (Routine r : sessions) if (r.showOnWake) return true;
        return false;
    }

    /**
     * The soonest of next over the sessions that have the thing it is about, for an alarm that serves them all;
     * null when none has it. next returns null for a session without it.
     */
    LocalDateTime soonest(Function<Routine, LocalDateTime> next) {
        LocalDateTime soonest = null;
        for (Routine r : sessions) {
            LocalDateTime at = next.apply(r);
            if (at != null && (soonest == null || at.isBefore(soonest))) soonest = at;
        }
        return soonest;
    }
}
