package io.github.hugoforte.morningboard;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The morning routine from the pushed routine.json: which days it runs, its window, when everyone leaves, and each
 * kid's steps and day-by-day extras. Plain Java, so the schedule is tested on the JVM.
 */
final class Routine {
    static final int MAX_KIDS = 4;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("H:mm");

    static final class Step {
        final LocalTime at;
        final String what;

        Step(LocalTime at, String what) {
            this.at = at;
            this.what = what;
        }
    }

    static final class Kid {
        final String name;
        final List<Step> steps;
        final Map<DayOfWeek, List<String>> extras;

        Kid(String name, List<Step> steps, Map<DayOfWeek, List<String>> extras) {
            this.name = name;
            this.steps = steps;
            this.extras = extras;
        }

        List<String> extrasOn(DayOfWeek day) {
            List<String> e = extras.get(day);
            return e == null ? Collections.emptyList() : e;
        }
    }

    final Set<DayOfWeek> days;
    final LocalTime from;
    final LocalTime until;
    final LocalTime leave;
    final boolean showOnWake;
    final List<Kid> kids;

    private Routine(Set<DayOfWeek> days, LocalTime from, LocalTime until, LocalTime leave, boolean showOnWake,
                    List<Kid> kids) {
        this.days = days;
        this.from = from;
        this.until = until;
        this.leave = leave;
        this.showOnWake = showOnWake;
        this.kids = kids;
    }

    /** Reads routine.json; the message of what it throws says which field is wrong, for the board to show. */
    static Routine parse(String json) {
        try {
            JSONObject o = new JSONObject(json);
            Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
            JSONArray d = o.getJSONArray("days");
            for (int i = 0; i < d.length(); i++) days.add(day(d.getString(i)));
            if (days.isEmpty()) throw new IllegalArgumentException("\"days\" names no day");
            LocalTime from = time(o, "from");
            LocalTime until = time(o, "until");
            LocalTime leave = time(o, "leave");
            if (!from.isBefore(until)) throw new IllegalArgumentException("\"from\" must be before \"until\"");
            if (leave.isBefore(from) || leave.isAfter(until)) {
                throw new IllegalArgumentException("\"leave\" must be between \"from\" and \"until\"");
            }
            JSONArray k = o.getJSONArray("kids");
            if (k.length() < 1 || k.length() > MAX_KIDS) {
                throw new IllegalArgumentException("\"kids\" needs 1 to " + MAX_KIDS + " entries, not " + k.length());
            }
            List<Kid> kids = new ArrayList<>();
            for (int i = 0; i < k.length(); i++) kids.add(kid(k.getJSONObject(i), from, until));
            boolean showOnWake = o.has("showOnWake") && o.getBoolean("showOnWake");
            return new Routine(days, from, until, leave, showOnWake, Collections.unmodifiableList(kids));
        } catch (JSONException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    private static Kid kid(JSONObject o, LocalTime from, LocalTime until) throws JSONException {
        String name = o.getString("name").trim();
        if (name.isEmpty()) throw new IllegalArgumentException("a kid has no \"name\"");
        JSONArray s = o.getJSONArray("steps");
        List<Step> steps = new ArrayList<>();
        for (int i = 0; i < s.length(); i++) {
            JSONObject step = s.getJSONObject(i);
            LocalTime at = time(step, "at");
            if (at.isBefore(from) || !at.isBefore(until)) {
                throw new IllegalArgumentException(name + "'s step at " + at.format(TIME) + " is outside \"from\" to \"until\"");
            }
            steps.add(new Step(at, step.getString("do")));
        }
        if (steps.isEmpty()) throw new IllegalArgumentException(name + " has no \"steps\"");
        steps.sort(Comparator.comparing(x -> x.at));
        for (int i = 1; i < steps.size(); i++) {
            if (steps.get(i).at.equals(steps.get(i - 1).at)) {
                throw new IllegalArgumentException(name + " has two steps at " + steps.get(i).at.format(TIME)
                        + "; give each its own time");
            }
        }
        Map<DayOfWeek, List<String>> extras = new EnumMap<>(DayOfWeek.class);
        JSONObject e = o.optJSONObject("extras");
        if (e != null) {
            for (Iterator<String> it = e.keys(); it.hasNext(); ) {
                String key = it.next();
                JSONArray items = e.getJSONArray(key);
                List<String> list = new ArrayList<>();
                for (int i = 0; i < items.length(); i++) list.add(items.getString(i));
                extras.put(day(key), Collections.unmodifiableList(list));
            }
        }
        return new Kid(name, Collections.unmodifiableList(steps), extras);
    }

    private static DayOfWeek day(String name) {
        String n = name.trim().toUpperCase(Locale.ROOT);
        for (DayOfWeek d : DayOfWeek.values()) {
            if (n.length() >= 3 && d.name().startsWith(n)) return d;
        }
        throw new IllegalArgumentException("\"" + name + "\" is not a day; write MON, TUE … SUN");
    }

    private static LocalTime time(JSONObject o, String key) throws JSONException {
        String value = o.getString(key);
        try {
            return LocalTime.parse(value.trim(), TIME);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("\"" + key + "\": \"" + value + "\" is not a time like 7:05");
        }
    }

    boolean inWindow(LocalDateTime t) {
        LocalTime time = t.toLocalTime();
        return days.contains(t.getDayOfWeek()) && !time.isBefore(from) && time.isBefore(until);
    }

    /** The morning the board shows at t: today until the window closes on a day it runs, else the next day it runs. */
    LocalDate showing(LocalDateTime t) {
        LocalDate day = t.toLocalDate();
        if (days.contains(day.getDayOfWeek()) && t.toLocalTime().isBefore(until)) return day;
        do {
            day = day.plusDays(1);
        } while (!days.contains(day.getDayOfWeek()));
        return day;
    }

    /** The step under way at t: the last one whose time has come, or -1 before the first. */
    static int current(Kid kid, LocalTime t) {
        int now = -1;
        for (int i = 0; i < kid.steps.size(); i++) {
            if (!kid.steps.get(i).at.isAfter(t)) now = i;
        }
        return now;
    }

    /** Whole minutes left before leaving, rounded up so "1 min" lasts until the minute is over; 0 or less once it is time. */
    long minutesToLeave(LocalTime t) {
        long seconds = Duration.between(t, leave).getSeconds();
        return seconds <= 0 ? seconds / 60 : (seconds + 59) / 60;
    }

    /** The countdown's words for minutesToLeave. */
    static String leaveText(long minutes) {
        if (minutes <= 0) return "Time to go!";
        if (minutes < 60) return "Leave in " + minutes + " min";
        return "Leave in " + minutes / 60 + " h " + minutes % 60 + " min";
    }
}
