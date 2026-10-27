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
    /** Switch the TV on at "from" on the days it runs, and back to standby at "until" if the board is still up. */
    final boolean turnTvOn;
    /** Who hears how the morning went, and when the report of what is still unticked goes; null for no email. */
    final Email email;
    /** What plays when the board comes up in the morning, and until when; null for no music. */
    final Music music;
    final List<Kid> kids;

    static final class Music {
        final String uri;
        /** The package that plays it: Spotify's TV app unless the routine says otherwise. */
        final String app;
        final LocalTime until;

        Music(String uri, String app, LocalTime until) {
            this.uri = uri;
            this.app = app;
            this.until = until;
        }
    }

    static final class Email {
        final List<String> to;
        final LocalTime reportAt;

        Email(List<String> to, LocalTime reportAt) {
            this.to = to;
            this.reportAt = reportAt;
        }
    }

    private Routine(Set<DayOfWeek> days, LocalTime from, LocalTime until, LocalTime leave, boolean showOnWake,
                    boolean turnTvOn, Email email, Music music, List<Kid> kids) {
        this.days = days;
        this.from = from;
        this.until = until;
        this.leave = leave;
        this.showOnWake = showOnWake;
        this.turnTvOn = turnTvOn;
        this.email = email;
        this.music = music;
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
            boolean turnTvOn = o.has("turnTvOn") && o.getBoolean("turnTvOn");
            Email email = o.has("email") ? email(o.getJSONObject("email"), from, until, leave) : null;
            Music music = o.has("music") ? music(o.getJSONObject("music"), from, until) : null;
            return new Routine(days, from, until, leave, showOnWake, turnTvOn, email, music,
                    Collections.unmodifiableList(kids));
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

    private static Email email(JSONObject o, LocalTime from, LocalTime until, LocalTime leave) throws JSONException {
        JSONArray t = o.getJSONArray("to");
        List<String> to = new ArrayList<>();
        for (int i = 0; i < t.length(); i++) {
            String address = t.getString(i).trim();
            if (!address.matches("[^@\\s<>]+@[^@\\s<>]+\\.[^@\\s<>]+")) {
                throw new IllegalArgumentException("\"" + address + "\" in \"email\".\"to\" is not an email address");
            }
            to.add(address);
        }
        if (to.isEmpty()) throw new IllegalArgumentException("\"email\".\"to\" names nobody");
        LocalTime reportAt = o.has("reportAt") ? time(o, "reportAt") : leave;
        if (reportAt.isBefore(from) || !reportAt.isBefore(until)) {
            throw new IllegalArgumentException("\"email\".\"reportAt\" must be from \"from\" to before \"until\"");
        }
        return new Email(Collections.unmodifiableList(to), reportAt);
    }

    private static Music music(JSONObject o, LocalTime from, LocalTime until) throws JSONException {
        String uri = o.getString("uri").trim();
        if (!uri.matches("[a-z][a-z0-9+.-]*:\\S+")) throw new IllegalArgumentException("\"music\".\"uri\" is not a URI: \"" + uri + "\"");
        String app = o.optString("app", "com.spotify.tv.android").trim();
        LocalTime stop = time(o, "until");
        if (!stop.isAfter(from) || stop.isAfter(until)) {
            throw new IllegalArgumentException("\"music\".\"until\" must be after \"from\" and no later than \"until\"");
        }
        return new Music(uri, app, stop);
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

    /** The next time after t that the window opens: when turnTvOn switches the TV on. */
    LocalDateTime nextFrom(LocalDateTime t) {
        return next(t, from);
    }

    /** The next time after t that the window closes: when turnTvOn puts the TV back to standby. */
    LocalDateTime nextUntil(LocalDateTime t) {
        return next(t, until);
    }

    /** The next report of what is still unticked, after t; only for a routine with email. */
    LocalDateTime nextReport(LocalDateTime t) {
        return next(t, email.reportAt);
    }

    /** The next time after t the morning's music stops; only for a routine with music. */
    LocalDateTime nextMusicStop(LocalDateTime t) {
        return next(t, music.until);
    }

    private LocalDateTime next(LocalDateTime t, LocalTime time) {
        LocalDateTime at = t.toLocalDate().atTime(time);
        while (!at.isAfter(t) || !days.contains(at.getDayOfWeek())) at = at.plusDays(1);
        return at;
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
