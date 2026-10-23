package in.mohammad.ramiz.confess.ui.home;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.databinding.ViewBottomBarBinding;

/**
 * The home bottom bar, the reader's way (Settings → Appearance → Bottom bar):
 *
 *   order       — the four tabs in any order; two sit left of the Confess button, two right
 *                 (Confess itself never moves)
 *   labels      — names under the icons: always · only under the selected tab · never
 *   animations  — the selected tab's icon draws itself in (avd_tab_*) and pops; off when the
 *                 reader turns it off or the phone has animations off
 *
 * Used by Home and by the live preview in Appearance (same view_bottom_bar layout).
 */
public final class NavBar {

    public static final String FEED = "feed", SEARCH = "search", BOOKS = "books", ME = "me";
    public static final List<String> DEFAULT_ORDER = Collections.unmodifiableList(Arrays.asList(FEED, SEARCH, BOOKS, ME));

    public static final int LABELS_ALWAYS = 0, LABELS_SELECTED = 1, LABELS_NEVER = 2;

    private static final String PREFS = "nav_bar";

    private NavBar() { }

    // ── The reader's choices ──────────────────────────────────────────────

    /** Always the four tabs, each once; anything odd saved falls back to the original order. */
    @NonNull
    public static List<String> order(@NonNull Context c) {
        String raw = prefs(c).getString("order", null);
        if (raw == null) return new ArrayList<>(DEFAULT_ORDER);
        List<String> list = new ArrayList<>(Arrays.asList(raw.split(",")));
        if (list.size() != DEFAULT_ORDER.size() || !list.containsAll(DEFAULT_ORDER)) return new ArrayList<>(DEFAULT_ORDER);
        return list;
    }

    public static void setOrder(@NonNull Context c, @NonNull List<String> order) {
        if (order.size() != DEFAULT_ORDER.size() || !order.containsAll(DEFAULT_ORDER)) return;
        prefs(c).edit().putString("order", String.join(",", order)).apply();
    }

    public static int labels(@NonNull Context c) {
        int m = prefs(c).getInt("labels", LABELS_ALWAYS);
        return m == LABELS_SELECTED || m == LABELS_NEVER ? m : LABELS_ALWAYS;
    }

    public static void setLabels(@NonNull Context c, int mode) {
        prefs(c).edit().putInt("labels", mode).apply();
    }

    /** The reader's switch (on by default). */
    public static boolean animationsOn(@NonNull Context c) {
        return prefs(c).getBoolean("animate", true);
    }

    public static void setAnimationsOn(@NonNull Context c, boolean on) {
        prefs(c).edit().putBoolean("animate", on).apply();
    }

    /** Should icons animate right now: the reader's switch and the phone's animation setting. */
    public static boolean animate(@NonNull Context c) {
        return animationsOn(c) && ValueAnimator.areAnimatorsEnabled();
    }

    // ── Per tab ───────────────────────────────────────────────────────────

    @DrawableRes
    public static int icon(@NonNull String tab) {
        switch (tab) {
            case SEARCH: return R.drawable.ic_search;
            case BOOKS: return R.drawable.ic_book;
            case ME: return R.drawable.ic_user;
            default: return R.drawable.ic_letters;
        }
    }

    @DrawableRes
    private static int animated(@NonNull String tab) {
        switch (tab) {
            case SEARCH: return R.drawable.avd_tab_search;
            case BOOKS: return R.drawable.avd_tab_books;
            case ME: return R.drawable.avd_tab_me;
            default: return R.drawable.avd_tab_feed;
        }
    }

    @StringRes
    public static int name(@NonNull String tab) {
        switch (tab) {
            case SEARCH: return R.string.tab_search;
            case BOOKS: return R.string.tab_books;
            case ME: return R.string.tab_me;
            default: return R.string.tab_feed;
        }
    }

    @NonNull
    public static View tab(@NonNull ViewBottomBarBinding b, @NonNull String tab) {
        switch (tab) {
            case SEARCH: return b.tabSearch;
            case BOOKS: return b.tabBooks;
            case ME: return b.tabMe;
            default: return b.tabFeed;
        }
    }

    @NonNull
    private static ImageView iconView(@NonNull ViewBottomBarBinding b, @NonNull String tab) {
        switch (tab) {
            case SEARCH: return b.iconSearch;
            case BOOKS: return b.iconBooks;
            case ME: return b.iconMe;
            default: return b.iconFeed;
        }
    }

    @NonNull
    private static TextView labelView(@NonNull ViewBottomBarBinding b, @NonNull String tab) {
        switch (tab) {
            case SEARCH: return b.labelSearch;
            case BOOKS: return b.labelBooks;
            case ME: return b.labelMe;
            default: return b.labelFeed;
        }
    }

    // ── Painting the bar ──────────────────────────────────────────────────

    /** Lay the tabs out in this order: two, the Confess button, two. */
    public static void arrange(@NonNull ViewBottomBarBinding b, @NonNull List<String> order) {
        ViewGroup bar = (ViewGroup) b.getRoot();
        View[] slots = {tab(b, order.get(0)), tab(b, order.get(1)), b.composeSlot, tab(b, order.get(2)), tab(b, order.get(3))};
        boolean same = bar.getChildCount() == slots.length;
        for (int i = 0; same && i < slots.length; i++) same = bar.getChildAt(i) == slots[i];
        if (same) return;
        bar.removeAllViews();
        for (View v : slots) bar.addView(v);              // each keeps its own layout params
    }

    /**
     * Colours, labels and selected state for {@code selected} (null = none, e.g. Search never
     * stays selected). {@code animate}: this is a tab change the reader just made.
     */
    public static void render(@NonNull Context c, @NonNull ViewBottomBarBinding b, @Nullable String selected, boolean animate) {
        boolean motion = animate && animate(c);
        int labels = labels(c);
        if (motion && labels == LABELS_SELECTED) {
            TransitionManager.beginDelayedTransition((ViewGroup) b.getRoot(), new AutoTransition().setDuration(180));
        }
        int on = ContextCompat.getColor(c, R.color.amber);
        int off = ContextCompat.getColor(c, R.color.text_muted);
        for (String key : DEFAULT_ORDER) {
            boolean sel = key.equals(selected);
            ImageView icon = iconView(b, key);
            TextView label = labelView(b, key);
            icon.setColorFilter(sel ? on : off);
            label.setTextColor(sel ? on : off);
            tab(b, key).setSelected(sel);
            label.setVisibility(labels == LABELS_ALWAYS || (labels == LABELS_SELECTED && sel) ? View.VISIBLE : View.GONE);
            if (sel && motion) {
                play(c, icon, key);
            } else {
                icon.animate().cancel();
                icon.setScaleX(1f);
                icon.setScaleY(1f);
                icon.setImageResource(icon(key));
            }
        }
        b.labelCompose.setVisibility(labels == LABELS_ALWAYS ? View.VISIBLE : View.GONE);
    }

    /** A tab that opens a screen instead of staying selected (Search): just the flourish. */
    public static void bump(@NonNull Context c, @NonNull ViewBottomBarBinding b, @NonNull String tab) {
        if (animate(c)) play(c, iconView(b, tab), tab);
    }

    private static void play(@NonNull Context c, @NonNull ImageView icon, @NonNull String tab) {
        Drawable d = ContextCompat.getDrawable(c, animated(tab));
        if (d == null) return;
        d = d.mutate();
        icon.setImageDrawable(d);                          // the view's colour filter carries over
        if (d instanceof Animatable) ((Animatable) d).start();
        icon.animate().cancel();
        icon.setScaleX(0.72f);
        icon.setScaleY(0.72f);
        icon.animate().scaleX(1f).scaleY(1f).setDuration(360)
                .setInterpolator(new OvershootInterpolator(2.6f)).start();
    }

    private static SharedPreferences prefs(@NonNull Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
