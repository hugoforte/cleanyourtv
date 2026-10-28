package io.github.hugoforte.morningboard;

import android.app.Activity;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Set;

/**
 * The tile and module card open this, and so does the screen turning on in the morning with showOnWake. A clock and
 * how long until leaving, then a column per kid with the morning's steps: the one under way is highlighted, and OK
 * ticks a step off. Outside the window it shows the next morning's plan, whose steps can be ticked ahead.
 */
public class BoardActivity extends Activity {
    private static final long REFRESH_MS = 15_000;
    private static final int BACKGROUND = 0xFF0D1117;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFF8B949E;
    private static final int NOW = 0xFFFFB74D;
    private static final int DONE = 0xFF7EE787;

    private final Handler main = new Handler(Looper.getMainLooper());
    private TextView clock;
    private TextView leave;
    private TextView date;
    private LinearLayout columns;
    private DateTimeFormatter time;
    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            build();
            main.postDelayed(this, REFRESH_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Opened by turnTvOn's alarm, the board brings the screen up with it.
        if (Build.VERSION.SDK_INT >= 27) {
            setTurnScreenOn(true);
            setShowWhenLocked(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        }
        time =DateTimeFormatter.ofPattern(android.text.format.DateFormat.is24HourFormat(this) ? "H:mm" : "h:mm");
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(BACKGROUND);
        page.setPadding(px(48), px(28), px(48), px(24));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        clock = text(48, TEXT);
        top.addView(clock, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        leave = text(30, NOW);
        leave.setGravity(Gravity.END);
        top.addView(leave);
        page.addView(top);
        date = text(18, MUTED);
        date.setPadding(0, 0, 0, px(12));
        page.addView(date);

        columns = new LinearLayout(this);
        page.addView(columns, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(page);
    }

    @Override
    protected void onResume() {
        super.onResume();
        showing = true;
        Store.importPushed(this);
        main.post(refresh);
    }

    /** Each kid's step under way at the last rebuild, while the board has been up; null before the first. */
    private int[] lastSteps;

    /** A chime when any kid's next step comes due while the board is up; none when it first opens. */
    private void chimeOnNewStep(Routine routine, LocalTime now) {
        if (now == null) {
            lastSteps = null;
            return;
        }
        int[] steps = new int[routine.kids.size()];
        boolean moved = false;
        for (int k = 0; k < steps.length; k++) {
            steps[k] = Routine.current(routine.kids.get(k), now);
            if (lastSteps != null && lastSteps.length == steps.length && steps[k] > lastSteps[k]) moved = true;
        }
        lastSteps = steps;
        if (moved) Chime.play();
    }

    @Override
    protected void onPause() {
        lastSteps = null;
        showing = false;
        main.removeCallbacks(refresh);
        super.onPause();
    }

    private static volatile boolean showing;

    /** Whether the board is the screen in front, which is when turnTvOn may put the TV back to standby. */
    static boolean showing() {
        return showing;
    }

    /** Rebuilds the board from the clock and the ticks; focus stays on the step it was on. */
    private void build() {
        LocalDateTime now = LocalDateTime.now();
        clock.setText(now.toLocalTime().format(time));
        View focused = columns.findFocus();
        Object focusTag = focused == null ? null : focused.getTag();
        columns.removeAllViews();

        Routine routine;
        String problem = Store.problem(this);
        if (problem == null) problem = Store.mailProblem(this);
        if (problem == null) problem = Store.musicProblem(this);
        try {
            routine = Store.routine(this);
        } catch (IllegalArgumentException e) {
            routine = null;
            problem = "The routine on the TV is no longer valid: " + e.getMessage() + ". Push it again.";
        }
        if (routine == null || problem != null) {
            leave.setText("");
            date.setText(problem != null ? problem
                    : "No routine yet. Push a routine.json with the install script; see the add-on's README.");
            if (routine == null) {
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                return;
            }
        }

        LocalDate day = routine.showing(now);
        boolean today = day.equals(now.toLocalDate()) && routine.inWindow(now);
        // The TV's screensaver would cover the board a minute in; it stays up through the morning, and only then.
        if (today) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (problem == null) {
            date.setText(today ? now.toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))
                    : "Next morning: " + day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)));
        }
        leave.setText(today ? Routine.leaveText(routine.minutesToLeave(now.toLocalTime()))
                : "From " + routine.from.format(time));

        chimeOnNewStep(routine, today ? now.toLocalTime() : null);
        Set<String> ticks = Store.ticks(this, day);
        for (int k = 0; k < routine.kids.size(); k++) {
            columns.addView(column(routine, routine.kids.get(k), k, day, today, now.toLocalTime(), ticks),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        }
        View again = focusTag == null ? null : columns.findViewWithTag(focusTag);
        if (again != null) again.requestFocus();
    }

    /** A kid's column, scrolling when its steps are more than the screen holds; focus scrolls it along. */
    private View column(Routine routine, Routine.Kid kid, int k, LocalDate day, boolean today, LocalTime now,
                        Set<String> ticks) {
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(px(8), 0, px(16), 0);
        scroll.addView(col);

        int done = 0;
        for (int s = 0; s < kid.steps.size(); s++) if (ticks.contains(k + ":" + s)) done++;

        // The greeting is the reward: the name until every step is ticked, then the greeting.
        TextView name = text(26, TEXT);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setText(done == kid.steps.size() ? routine.greet(kid) : kid.name);
        col.addView(name);
        TextView progress = text(18, done == kid.steps.size() ? DONE : MUTED);
        progress.setText(done == kid.steps.size() ? "All done ✓" : done + " of " + kid.steps.size() + " done");
        progress.setPadding(0, 0, 0, px(8));
        col.addView(progress);

        int current = today ? Routine.current(kid, now) : -1;
        for (int s = 0; s < kid.steps.size(); s++) {
            col.addView(step(kid.steps.get(s), k + ":" + s, day, ticks.contains(k + ":" + s), s == current,
                    today && s < current));
        }

        List<String> extras = kid.extrasOn(day.getDayOfWeek());
        if (!extras.isEmpty()) {
            TextView bring = text(20, NOW);
            bring.setText((today ? "Today: " : "Bring: ") + TextUtils.join(", ", extras));
            bring.setPadding(px(12), px(12), 0, 0);
            col.addView(bring);
        }
        return scroll;
    }

    /** A step with its box: OK ticks it for the morning shown, so a bag packed the night before can be ticked then. */
    private TextView step(Routine.Step step, String key, LocalDate day, boolean ticked, boolean current, boolean past) {
        TextView row = text(20, ticked ? DONE : current ? NOW : past ? MUTED : TEXT);
        row.setText(step.at.format(time) + "   " + step.what);
        row.setCompoundDrawablesRelativeWithIntrinsicBounds(ticked ? R.drawable.box_on : R.drawable.box_off, 0, 0, 0);
        row.setCompoundDrawablePadding(px(14));
        row.setGravity(Gravity.CENTER_VERTICAL);
        if (ticked) row.setPaintFlags(row.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        if (current && !ticked) row.setTypeface(Typeface.DEFAULT_BOLD);
        row.setMaxLines(2);
        row.setEllipsize(TextUtils.TruncateAt.END);
        row.setPadding(px(12), px(6), px(12), px(6));
        row.setTag(key);
        row.setFocusable(true);
        row.setBackgroundResource(R.drawable.row);
        row.setOnClickListener(v -> {
            Store.toggle(this, day, key);
            if (Store.ticks(this, day).contains(key)) Chime.playHappy();
            Routine r = Store.routine(this);
            if (r != null) Mail.afterTick(this, r, Integer.parseInt(key.substring(0, key.indexOf(':'))), day);
            build();
        });
        return row;
    }

    private TextView text(float sp, int color) {
        TextView t = new TextView(this);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    private int px(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
