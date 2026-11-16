package io.github.hugoforte.morningboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class ScheduleTest {
    // 2026-10-26 is a Monday.
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 26);
    private static final LocalDate FRIDAY = MONDAY.plusDays(4);

    private static final String MORNING = "{"
            + "\"days\": [\"MON\", \"TUE\", \"WED\", \"THU\", \"FRI\"],"
            + "\"from\": \"7:00\", \"until\": \"8:10\", \"leave\": \"8:00\", \"showOnWake\": true,"
            + "\"email\": {\"to\": [\"a@example.com\"], \"reportAt\": \"7:58\"},"
            + "\"music\": {\"uri\": \"spotify:playlist:x\", \"device\": \"TV\", \"until\": \"8:00\"},"
            + "\"kids\": [{\"name\": \"Zoey\", \"steps\": [{\"at\": \"7:00\", \"do\": \"Check and empty the dishwasher\"}]}]}";

    private static final String AFTERNOON = "{"
            + "\"days\": [\"MON\", \"TUE\", \"WED\", \"THU\", \"FRI\"],"
            + "\"from\": \"16:00\", \"until\": \"17:00\","
            + "\"email\": {\"to\": [\"a@example.com\"], \"reportAt\": \"16:50\"},"
            + "\"kids\": [{\"name\": \"Axel\", \"steps\": [{\"at\": \"16:00\", \"do\": \"Load the dishwasher\"}]}]}";

    private static Schedule both() {
        return Schedule.parse("{\"sessions\": [" + MORNING + ", " + AFTERNOON + "]}");
    }

    private static String refusal(String json) {
        return assertThrows(IllegalArgumentException.class, () -> Schedule.parse(json)).getMessage();
    }

    @Test
    public void aRoutineWithoutSessionsIsOneSession() {
        assertEquals(1, Schedule.parse(MORNING).sessions.size());
    }

    @Test
    public void sessionsAreReadInOrder() {
        assertEquals(LocalTime.of(16, 0), both().sessions.get(1).from);
    }

    @Test
    public void theMorningIsInPlayDuringItsWindow() {
        assertEquals(LocalTime.of(7, 0), both().at(MONDAY.atTime(7, 30)).from);
    }

    @Test
    public void onceTheMorningIsOverTheAfternoonIsInPlay() {
        assertEquals(LocalTime.of(16, 0), both().at(MONDAY.atTime(8, 10)).from);
    }

    @Test
    public void theAfternoonIsInPlayDuringItsWindow() {
        assertEquals(LocalTime.of(16, 0), both().at(MONDAY.atTime(16, 30)).from);
    }

    @Test
    public void afterTheAfternoonTheNextMorningIsInPlay() {
        Routine r = both().at(MONDAY.atTime(17, 0));
        assertEquals(LocalTime.of(7, 0), r.from);
        assertEquals(MONDAY.plusDays(1), r.showing(MONDAY.atTime(17, 0)));
    }

    @Test
    public void afterFridaysAfternoonMondaysMorningIsInPlay() {
        Routine r = both().at(FRIDAY.atTime(18, 0));
        assertEquals(MONDAY.plusWeeks(1), r.showing(FRIDAY.atTime(18, 0)));
        assertEquals(LocalTime.of(7, 0), r.from);
    }

    @Test
    public void theReportAlarmIsTheSoonestOfEitherSession() {
        LocalDateTime noon = MONDAY.atTime(12, 0);
        assertEquals(MONDAY.atTime(16, 50), both().soonest(r -> r.email != null ? r.nextReport(noon) : null));
    }

    @Test
    public void onlyASessionWithMusicStopsIt() {
        LocalDateTime nine = MONDAY.atTime(9, 0);
        assertEquals(MONDAY.plusDays(1).atTime(8, 0), both().soonest(r -> r.music != null ? r.nextMusicStop(nine) : null));
    }

    @Test
    public void noSessionAskingMeansNoAlarm() {
        assertNull(both().soonest(r -> r.turnTvOn ? r.nextFrom(MONDAY.atTime(9, 0)) : null));
    }

    @Test
    public void theAfternoonHasNoMusic() {
        assertNull(both().sessions.get(1).music);
    }

    @Test
    public void oneSessionWithShowOnWakeKeepsTheWatcher() {
        assertTrue(both().showOnWake());
    }

    @Test
    public void noSessionWithShowOnWakeStopsTheWatcher() {
        assertFalse(Schedule.parse(AFTERNOON).showOnWake());
    }

    @Test
    public void sessionsAreKeptApartByTheirStart() {
        assertFalse(both().sessions.get(0).id().equals(both().sessions.get(1).id()));
    }

    @Test
    public void aSessionIsNamedByWhenItStarts() {
        assertEquals("afternoon", both().sessions.get(1).part());
    }

    @Test
    public void aSessionWithoutLeaveHasNoCountdown() {
        assertNull(both().sessions.get(1).leave);
    }

    @Test
    public void anEmailWithoutLeaveNeedsAReportTime() {
        assertEquals("session 2: \"email\".\"reportAt\" is needed when there is no \"leave\"",
                refusal("{\"sessions\": [" + MORNING + ", " + AFTERNOON.replace(", \"reportAt\": \"16:50\"", "") + "]}"));
    }

    @Test
    public void overlappingSessionsAreRefused() {
        assertEquals("the sessions from 7:00 and from 7:30 overlap on a day both run",
                refusal("{\"sessions\": [" + MORNING + ", " + AFTERNOON.replace("\"16:00\"", "\"7:30\"")
                        .replace("\"16:50\"", "\"7:40\"") + "]}"));
    }

    @Test
    public void sessionsOnDifferentDaysMayShareHours() {
        String saturday = MORNING.replace("\"MON\", \"TUE\", \"WED\", \"THU\", \"FRI\"", "\"SAT\"");
        assertEquals(2, Schedule.parse("{\"sessions\": [" + MORNING + ", " + saturday + "]}").sessions.size());
    }

    @Test
    public void aRefusedSessionIsNamedByNumber() {
        assertEquals("session 2: \"from\" must be before \"until\"",
                refusal("{\"sessions\": [" + MORNING + ", " + AFTERNOON.replace("\"17:00\"", "\"15:00\"") + "]}"));
    }

    @Test
    public void fieldsBesideSessionsAreRefused() {
        assertEquals("with \"sessions\", everything goes inside a session",
                refusal("{\"showOnWake\": true, \"sessions\": [" + MORNING + "]}"));
    }

    @Test
    public void noSessionsAreRefused() {
        assertEquals("\"sessions\" is empty", refusal("{\"sessions\": []}"));
    }
}
