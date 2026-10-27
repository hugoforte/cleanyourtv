package io.github.hugoforte.morningboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;

public class RoutineTest {
    // 2026-10-26 is a Monday.
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 26);
    private static final LocalDate FRIDAY = MONDAY.plusDays(4);

    private static final String SCHOOL_DAYS = "{"
            + "\"days\": [\"MON\", \"TUE\", \"WED\", \"THU\", \"FRI\"],"
            + "\"from\": \"6:30\", \"until\": \"8:10\", \"leave\": \"8:00\", \"showOnWake\": true,"
            + "\"kids\": [{\"name\": \"Sam\","
            + "  \"steps\": [{\"at\": \"7:00\", \"do\": \"Breakfast\"}, {\"at\": \"6:30\", \"do\": \"Up, dressed\"},"
            + "              {\"at\": \"7:50\", \"do\": \"Shoes, jacket\"}],"
            + "  \"extras\": {\"TUE\": [\"PE kit\"]}}]}";

    private static Routine routine() {
        return Routine.parse(SCHOOL_DAYS);
    }

    private static LocalDateTime at(LocalDate day, int hour, int minute) {
        return day.atTime(hour, minute);
    }

    private static String refusal(String json) {
        return assertThrows(IllegalArgumentException.class, () -> Routine.parse(json)).getMessage();
    }

    @Test
    public void theShippedExampleIsARoutine() throws Exception {
        // Unit tests run in the app module's folder.
        String json = new String(Files.readAllBytes(Paths.get("../example-routine.json")), StandardCharsets.UTF_8);
        assertEquals(2, Routine.parse(json).kids.size());
    }

    @Test
    public void stepsAreSortedByTime() {
        assertEquals(LocalTime.of(6, 30), routine().kids.get(0).steps.get(0).at);
    }

    @Test
    public void extrasAreReadPerDay() {
        assertEquals(Arrays.asList("PE kit"), routine().kids.get(0).extrasOn(DayOfWeek.TUESDAY));
    }

    @Test
    public void aDayWithNoExtrasHasNone() {
        assertTrue(routine().kids.get(0).extrasOn(DayOfWeek.MONDAY).isEmpty());
    }

    @Test
    public void theWindowOpensAtFrom() {
        assertTrue(routine().inWindow(at(MONDAY, 6, 30)));
    }

    @Test
    public void theWindowIsClosedAtUntil() {
        assertFalse(routine().inWindow(at(MONDAY, 8, 10)));
    }

    @Test
    public void theWindowIsClosedOnADayItDoesNotRun() {
        assertFalse(routine().inWindow(at(MONDAY.minusDays(1), 7, 0)));
    }

    @Test
    public void theBoardShowsTodayBeforeTheWindowCloses() {
        assertEquals(MONDAY, routine().showing(at(MONDAY, 5, 0)));
    }

    @Test
    public void afterFridaysWindowTheBoardShowsMonday() {
        assertEquals(MONDAY.plusWeeks(1), routine().showing(at(FRIDAY, 9, 0)));
    }

    @Test
    public void theCurrentStepIsTheLastOneStarted() {
        assertEquals(1, Routine.current(routine().kids.get(0), LocalTime.of(7, 49)));
    }

    @Test
    public void beforeTheFirstStepNoneIsCurrent() {
        assertEquals(-1, Routine.current(routine().kids.get(0), LocalTime.of(6, 29)));
    }

    @Test
    public void minutesToLeaveRoundUp() {
        assertEquals(1, routine().minutesToLeave(LocalTime.of(7, 59, 1)));
    }

    @Test
    public void minutesToLeaveAreZeroAtTheTime() {
        assertEquals(0, routine().minutesToLeave(LocalTime.of(8, 0)));
    }

    @Test
    public void showOnWakeIsOffUnlessAsked() {
        assertFalse(Routine.parse(SCHOOL_DAYS.replace("\"showOnWake\": true,", "")).showOnWake);
    }

    @Test
    public void anUnknownDayIsRefusedByName() {
        assertEquals("\"FUNDAY\" is not a day; write MON, TUE … SUN", refusal(SCHOOL_DAYS.replace("\"MON\"", "\"FUNDAY\"")));
    }

    @Test
    public void aTimeThatIsNotOneIsRefusedWithItsField() {
        assertEquals("\"leave\": \"8am\" is not a time like 7:05", refusal(SCHOOL_DAYS.replace("\"8:00\"", "\"8am\"")));
    }

    @Test
    public void aWindowThatEndsBeforeItStartsIsRefused() {
        assertEquals("\"from\" must be before \"until\"", refusal(SCHOOL_DAYS.replace("\"8:10\"", "\"6:00\"")));
    }

    @Test
    public void aLeaveTimeAfterTheWindowIsRefused() {
        assertEquals("\"leave\" must be between \"from\" and \"until\"", refusal(SCHOOL_DAYS.replace("\"8:00\"", "\"8:30\"")));
    }

    @Test
    public void aLeaveTimeBeforeTheWindowIsRefused() {
        assertEquals("\"leave\" must be between \"from\" and \"until\"", refusal(SCHOOL_DAYS.replace("\"8:00\"", "\"6:00\"")));
    }

    @Test
    public void aStepAtTheEndOfTheWindowIsRefused() {
        assertEquals("Sam's step at 8:10 is outside \"from\" to \"until\"",
                refusal(SCHOOL_DAYS.replace("\"at\": \"7:50\"", "\"at\": \"8:10\"")));
    }

    @Test
    public void twoStepsAtOneTimeAreRefused() {
        assertEquals("Sam has two steps at 7:00; give each its own time",
                refusal(SCHOOL_DAYS.replace("\"at\": \"7:50\"", "\"at\": \"7:00\"")));
    }

    @Test
    public void aShowOnWakeThatIsNotTrueOrFalseIsRefused() {
        refusal(SCHOOL_DAYS.replace("\"showOnWake\": true", "\"showOnWake\": \"yes\""));
    }

    @Test
    public void fiveKidsAreRefused() {
        String kid = SCHOOL_DAYS.substring(SCHOOL_DAYS.indexOf("{\"name\""), SCHOOL_DAYS.length() - 2);
        String five = SCHOOL_DAYS.substring(0, SCHOOL_DAYS.indexOf("\"kids\"")) + "\"kids\": ["
                + String.join(",", kid, kid, kid, kid, kid) + "]}";
        assertEquals("\"kids\" needs 1 to 4 entries, not 5", refusal(five));
    }

    @Test
    public void theCountdownSaysWhenItIsTimeToGo() {
        assertEquals("Time to go!", Routine.leaveText(0));
    }

    @Test
    public void aLongCountdownIsInHoursAndMinutes() {
        assertEquals("Leave in 1 h 30 min", Routine.leaveText(90));
    }

    @Test
    public void aRoutineWithNoKidsIsRefused() {
        assertEquals("\"kids\" needs 1 to 4 entries, not 0",
                refusal(SCHOOL_DAYS.substring(0, SCHOOL_DAYS.indexOf("\"kids\"")) + "\"kids\": []}"));
    }
}
