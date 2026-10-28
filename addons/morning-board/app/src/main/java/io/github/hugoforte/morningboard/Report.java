package io.github.hugoforte.morningboard;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** The two emails: a kid who has ticked everything, and the report of what is still unticked. Plain Java, tested. */
final class Report {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a");

    static final class Message {
        final String subject;
        final String body;

        Message(String subject, String body) {
            this.subject = subject;
            this.body = body;
        }
    }

    private Report() {
    }

    /** Whether kid k has ticked every step; ticks are "kid:step" indexes. */
    static boolean allDone(Routine.Kid kid, int k, Set<String> ticks) {
        for (int s = 0; s < kid.steps.size(); s++) if (!ticks.contains(k + ":" + s)) return false;
        return true;
    }

    static Message finished(Routine.Kid kid, LocalTime at) {
        return new Message(kid.name + " finished the morning at " + at.format(TIME),
                kid.name + " ticked off all " + kid.steps.size() + " steps on the morning board by " + at.format(TIME) + ".\n");
    }

    /** What is still unticked, by kid; null when everything is done. */
    static Message unticked(Routine routine, Set<String> ticks) {
        List<String> late = new ArrayList<>();
        StringBuilder body = new StringBuilder();
        for (int k = 0; k < routine.kids.size(); k++) {
            Routine.Kid kid = routine.kids.get(k);
            List<String> open = new ArrayList<>();
            for (int s = 0; s < kid.steps.size(); s++) {
                if (!ticks.contains(k + ":" + s)) open.add(kid.steps.get(s).what);
            }
            if (open.isEmpty()) {
                body.append(kid.name).append(": all done.\n");
            } else {
                late.add(kid.name);
                body.append(kid.name).append(" didn't tick:\n");
                for (String what : open) body.append("  - ").append(what).append('\n');
            }
        }
        if (late.isEmpty()) return null;
        return new Message("Not ticked off yet: " + String.join(" and ", late), body.toString());
    }
}
