package io.github.hugoforte.morningboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class ReportTest {
    private static final String TWO_KIDS = "{"
            + "\"days\": [\"MON\"], \"from\": \"7:00\", \"until\": \"8:10\", \"leave\": \"8:00\","
            + "\"email\": {\"to\": [\"a@example.com\"], \"reportAt\": \"7:58\"},"
            + "\"kids\": ["
            + "  {\"name\": \"Axel\", \"steps\": [{\"at\": \"7:00\", \"do\": \"Up\"}, {\"at\": \"7:25\", \"do\": \"Dishwasher\"}]},"
            + "  {\"name\": \"Zoey\", \"steps\": [{\"at\": \"7:00\", \"do\": \"Up\"}, {\"at\": \"7:40\", \"do\": \"Make bed\"}]}]}";

    private static final Routine ROUTINE = Routine.parse(TWO_KIDS);

    private static Set<String> ticks(String... keys) {
        return new HashSet<>(Arrays.asList(keys));
    }

    @Test
    public void aKidWithEveryStepTickedIsDone() {
        assertTrue(Report.allDone(ROUTINE.kids.get(0), 0, ticks("0:0", "0:1")));
    }

    @Test
    public void anotherKidsTicksDoNotCount() {
        assertFalse(Report.allDone(ROUTINE.kids.get(0), 0, ticks("0:0", "1:1")));
    }

    @Test
    public void theFinishedEmailSaysWhoAndWhen() {
        assertEquals("Axel finished the morning at 7:41 AM", Report.finished(ROUTINE.kids.get(0), LocalTime.of(7, 41)).subject);
    }

    @Test
    public void nothingIsReportedWhenEverythingIsTicked() {
        assertNull(Report.unticked(ROUTINE, ticks("0:0", "0:1", "1:0", "1:1")));
    }

    @Test
    public void theReportNamesWhoIsNotDone() {
        assertEquals("Not ticked off yet: Zoey", Report.unticked(ROUTINE, ticks("0:0", "0:1", "1:0")).subject);
    }

    @Test
    public void theReportListsWhatIsUnticked() {
        assertEquals("Axel: all done.\nZoey didn't tick:\n  - Make bed\n",
                Report.unticked(ROUTINE, ticks("0:0", "0:1", "1:0")).body);
    }

    @Test
    public void theReportTimeIsRead() {
        assertEquals(LocalTime.of(7, 58), ROUTINE.email.reportAt);
    }
}
