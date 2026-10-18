package in.mohammad.ramiz.confess.ui.feed;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import in.mohammad.ramiz.confess.data.model.Post;

/**
 * Bursts: one writer's confessions posted close together — each within 20 minutes of the one
 * before, or with nobody else posting in between — are one group, however many there are.
 *
 * The server decides (posts.burst_id, migration 0017). With bursts on, the feed sends one card
 * per burst — its newest confession, with {@code burst: {id, count}} — and the burst itself is
 * fetched a set at a time, oldest first, as the reader swipes ({@link BurstStore}).
 *
 * How a burst looks is the reader's choice (asked the first time one shows up, with a little
 * animation of each; Settings → Appearance changes it):
 *   SLIDER   — swipe through them in one card
 *   STACK    — up to 3: the slider; more: one card "u1 made 217 confessions — read them" that
 *              opens them full screen, oldest first
 *   SEPARATE — every confession its own card (the feed is asked without bursts)
 */
public final class Bursts {

    public static final int SLIDER = 0, STACK = 1, SEPARATE = 2;

    /** Up to this many, a burst is always a slider (unless SEPARATE). */
    public static final int SMALL = 3;

    private static final String PREFS = "bursts";

    private Bursts() { }

    // ── The reader's choice ───────────────────────────────────────────────

    public static int mode(@NonNull Context ctx) {
        int m = prefs(ctx).getInt("mode", SLIDER);
        return m == STACK || m == SEPARATE ? m : SLIDER;
    }

    public static void setMode(@NonNull Context ctx, int mode) {
        prefs(ctx).edit().putInt("mode", mode).putBoolean("asked", true).apply();
    }

    /** Has the chooser been shown (or a choice made in Settings)? */
    public static boolean asked(@NonNull Context ctx) {
        return prefs(ctx).getBoolean("asked", false);
    }

    public static void markAsked(@NonNull Context ctx) {
        prefs(ctx).edit().putBoolean("asked", true).apply();
    }

    private static SharedPreferences prefs(@NonNull Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ── A burst card in the feed ──────────────────────────────────────────

    public static final class Group {
        /** The burst's id on the server ("burst:" ids are only for list diffing). */
        @NonNull public final String id;
        /** Everyone / Following — the burst is fetched with the same filter as the feed. */
        @NonNull public final String scope;
        /** The newest confession — the card the feed sent. */
        @NonNull public final Post head;
        /** How many the burst has (as the feed counted them). */
        public final int total;

        Group(@NonNull String id, @NonNull String scope, @NonNull Post head, int total) {
            this.id = id;
            this.scope = scope;
            this.head = head;
            this.total = total;
        }

        @NonNull
        public String alias() {
            return head.author != null && head.author.alias != null ? head.author.alias : "";
        }

        /** Its confessions as far as they're loaded (shared with the reader). */
        @NonNull
        public BurstStore.Entry entry() {
            return BurstStore.get(id, scope, total);
        }
    }

    /** The feed card as a burst, or null when it stands on its own. */
    @Nullable
    public static Group of(@NonNull Post p, @NonNull String scope) {
        if (p.burst == null || p.burst.id == null || p.burst.count <= 1) return null;
        return new Group(p.burst.id, scope, p, p.burst.count);
    }

    public static boolean asStack(@NonNull Group g, int mode) {
        return mode == STACK && g.total > SMALL;
    }
}
