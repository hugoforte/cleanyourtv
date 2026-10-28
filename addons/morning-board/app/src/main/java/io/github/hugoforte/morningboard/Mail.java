package io.github.hugoforte.morningboard;

import android.content.Context;
import android.util.Log;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * When the emails go: once a kid ticks everything inside the window, and at the report time if anything is still
 * unticked. Each goes once a morning. Sending happens off the main thread; a failure is kept for the board to show.
 */
final class Mail {
    private static final String TAG = "MorningBoard";
    private static final ExecutorService NET = Executors.newSingleThreadExecutor();

    private Mail() {
    }

    /** After a tick: if kid k has just finished inside the window, tell the parents. */
    static void afterTick(Context c, Routine r, int k, LocalDate day) {
        LocalDateTime now = LocalDateTime.now();
        if (r.email == null || !r.inWindow(now) || !day.equals(now.toLocalDate())) return;
        Routine.Kid kid = r.kids.get(k);
        if (!Report.allDone(kid, k, Store.ticks(c, day)) || !Store.firstTime(c, day, "done:" + k)) return;
        send(c, r, Report.finished(kid, now.toLocalTime()), null);
    }

    /** At the report time: what is still unticked, if anything. done runs once the send has finished. */
    static void report(Context c, Runnable done) {
        Routine r;
        try {
            r = Store.routine(c);
        } catch (IllegalArgumentException e) {
            r = null;
        }
        LocalDateTime now = LocalDateTime.now();
        // An alarm delivered late, after a TV in deep standby woke, is no report of this morning.
        if (r == null || r.email == null || !r.inWindow(now) || now.toLocalTime().isBefore(r.email.reportAt)) {
            done.run();
            return;
        }
        Set<String> ticks = Store.ticks(c, now.toLocalDate());
        Report.Message m = Report.unticked(r, ticks);
        if (m == null || !Store.firstTime(c, now.toLocalDate(), "report")) {
            done.run();
            return;
        }
        send(c, r, m, done);
    }

    private static void send(Context c, Routine r, Report.Message m, Runnable done) {
        Context app = c.getApplicationContext();
        NET.execute(() -> {
            try {
                Mailer.Account account = Store.mail(app);
                if (account == null) throw new IllegalStateException("no mail.json was pushed, so no email can go");
                new Mailer().send(account, r.email.to, m.subject, m.body);
                Store.mailResult(app, null);
            } catch (Exception e) {
                Log.w(TAG, "email not sent: " + m.subject, e);
                Store.mailResult(app, "Email not sent (" + m.subject + "): " + e.getMessage());
            } finally {
                if (done != null) done.run();
            }
        });
    }
}
