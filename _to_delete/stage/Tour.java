package in.mohammad.ramiz.confess.ui.tour;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import in.mohammad.ramiz.confess.BuildConfig;
import in.mohammad.ramiz.confess.R;

/**
 * The feature tour: what Confess can do, one page each (TourActivity).
 *
 * Shown once on Home:
 *   never seen (a new install — and everyone updating to 5.4.1, when the tour arrived) → every page
 *   seen in an older version → "What's new": only pages added since, then "See the full tour"
 * and any time from Settings → Take the tour.
 *
 * Adding a feature later: add a Page with {@code since} = the versionCode it ships in. It's
 * marked NEW in that version and shows in that version's "What's new".
 */
public final class Tour {

    /** One page: icon, title, a line, up to three points; NEW when it arrived in this version. */
    public static final class Page {
        @DrawableRes public final int icon;
        @StringRes public final int title, body;
        @StringRes public final int[] points;
        /** versionCode this feature shipped in. */
        public final int since;
        /** Letty in full colour instead of a tinted line icon. */
        public final boolean brand;

        Page(@DrawableRes int icon, @StringRes int title, @StringRes int body, int since, boolean brand, @StringRes int... points) {
            this.icon = icon;
            this.title = title;
            this.body = body;
            this.since = since;
            this.brand = brand;
            this.points = points;
        }

        public boolean isNew() {
            return since >= BuildConfig.VERSION_CODE;
        }
    }

    /** The version the tour first shipped in; features from before count as "always there". */
    private static final int BASE = 22;

    static final Page WELCOME = new Page(R.drawable.logo_letty, R.string.tour_welcome_title, R.string.tour_welcome_body,
            0, true, R.string.tour_welcome_p1, R.string.tour_welcome_p2);
    static final Page WHATS_NEW = new Page(R.drawable.logo_letty, R.string.tour_whatsnew_title, R.string.tour_whatsnew_body,
            0, true);
    static final Page END = new Page(R.drawable.logo_letty, R.string.tour_end_title, R.string.tour_end_body,
            0, true, R.string.tour_end_p1);

    /** The features, in the order the full tour shows them. */
    static final List<Page> FEATURES = Collections.unmodifiableList(Arrays.asList(
            new Page(R.drawable.ic_check_check, R.string.tour_kinds_title, R.string.tour_kinds_body, BASE, false,
                    R.string.tour_kinds_p1, R.string.tour_kinds_p2, R.string.tour_kinds_p3),
            new Page(R.drawable.ic_gif, R.string.tour_gif_title, R.string.tour_gif_body, 23, false,
                    R.string.tour_gif_p1, R.string.tour_gif_p2, R.string.tour_gif_p3),
            new Page(R.drawable.ic_heart, R.string.tour_feel_title, R.string.tour_feel_body, BASE, false,
                    R.string.tour_feel_p1, R.string.tour_feel_p2, R.string.tour_feel_p3),
            new Page(R.drawable.ic_clock, R.string.tour_stories_title, R.string.tour_stories_body, BASE, false,
                    R.string.tour_stories_p1, R.string.tour_stories_p2),
            new Page(R.drawable.ic_users, R.string.tour_follow_title, R.string.tour_follow_body, BASE, false,
                    R.string.tour_follow_p1, R.string.tour_follow_p2),
            new Page(R.drawable.ic_book_open, R.string.tour_books_title, R.string.tour_books_body, BASE, false,
                    R.string.tour_books_p1, R.string.tour_books_p2, R.string.tour_books_p3),
            new Page(R.drawable.ic_layers, R.string.tour_bursts_title, R.string.tour_bursts_body, BASE, false,
                    R.string.tour_bursts_p1, R.string.tour_bursts_p2),
            new Page(R.drawable.ic_eye_off, R.string.tour_gentle_title, R.string.tour_gentle_body, BASE, false,
                    R.string.tour_gentle_p1, R.string.tour_gentle_p2),
            new Page(R.drawable.ic_sliders, R.string.tour_yours_title, R.string.tour_yours_body, 23, false,
                    R.string.tour_yours_p1, R.string.tour_yours_p2, R.string.tour_yours_p3),
            new Page(R.drawable.ic_music, R.string.tour_sounds_title, R.string.tour_sounds_body, 23, false,
                    R.string.tour_sounds_p1, R.string.tour_sounds_p2),
            new Page(R.drawable.ic_vibrate, R.string.tour_private_title, R.string.tour_private_body, 23, false,
                    R.string.tour_private_p1, R.string.tour_private_p2, R.string.tour_private_p3)));

    private static final String PREFS = "tour";
    private static final String KEY_SEEN = "seen_version";

    private Tour() { }

    /** Every page: welcome, the features, the end. */
    @NonNull
    static List<Page> full() {
        List<Page> out = new ArrayList<>();
        out.add(WELCOME);
        out.addAll(FEATURES);
        out.add(END);
        return out;
    }

    /** Only what arrived after {@code seenVersion}; empty when nothing did. */
    @NonNull
    static List<Page> whatsNew(int seenVersion) {
        List<Page> fresh = new ArrayList<>();
        for (Page p : FEATURES) if (p.since > seenVersion) fresh.add(p);
        if (fresh.isEmpty()) return fresh;
        List<Page> out = new ArrayList<>();
        out.add(WHATS_NEW);
        out.addAll(fresh);
        out.add(END);
        return out;
    }

    /** The version whose tour was last seen (0 = never). */
    static int seenVersion(@NonNull Context c) {
        return prefs(c).getInt(KEY_SEEN, 0);
    }

    static void markSeen(@NonNull Context c) {
        prefs(c).edit().putInt(KEY_SEEN, BuildConfig.VERSION_CODE).apply();
    }

    /**
     * On Home: show the tour if this version's hasn't been seen. True when it opened (the caller
     * then leaves other prompts for later).
     */
    public static boolean maybeShow(@NonNull Activity from) {
        int seen = seenVersion(from);
        if (seen >= BuildConfig.VERSION_CODE) return false;
        if (seen > 0 && whatsNew(seen).isEmpty()) {
            markSeen(from);                              // an update with nothing new to show
            return false;
        }
        from.startActivity(new Intent(from, TourActivity.class)
                .putExtra(TourActivity.EXTRA_MODE, seen == 0 ? TourActivity.MODE_FULL : TourActivity.MODE_NEW));
        from.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        return true;
    }

    /** Settings → Take the tour. */
    public static void open(@NonNull Context from) {
        from.startActivity(new Intent(from, TourActivity.class).putExtra(TourActivity.EXTRA_MODE, TourActivity.MODE_FULL));
    }

    private static SharedPreferences prefs(@NonNull Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
