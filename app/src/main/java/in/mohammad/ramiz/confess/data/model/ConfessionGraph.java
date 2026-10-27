package in.mohammad.ramiz.confess.data.model;

import androidx.annotation.Nullable;

/**
 * GET users/me/graph (mine, with my streak) or users/{alias}/graph (theirs, if they show it) —
 * confessions per day for the GitHub-style graph.
 * days[i] = confessions on start + i; whole weeks, Monday first, ending today (server's time zone).
 */
public class ConfessionGraph {
    public String start;          // yyyy-MM-dd, a Monday
    public String end;            // yyyy-MM-dd, today
    public int[] days;
    public int total;
    public int activeDays;
    /** Only on my own graph (others never see my streak). */
    @Nullable public Streak streak;
    /** The owner's look: "" = default, else "g1:<palette>.<shape>" (see ui.me.GraphStyle). */
    @Nullable public String style;
    /** Whether others can see it (my own graph only matters). */
    @com.google.gson.annotations.SerializedName("public") public boolean isPublic = true;
    /** Someone else's graph that they keep to themselves: nothing else is sent. */
    public boolean hidden;

    /** Days in a row the app was opened. days = 0 once a day was missed; today = today already counts. */
    public static class Streak {
        public int days;
        public int best;
        public boolean today;
    }
}
