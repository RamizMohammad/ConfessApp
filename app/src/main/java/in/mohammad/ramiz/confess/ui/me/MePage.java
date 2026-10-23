package in.mohammad.ramiz.confess.ui.me;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.databinding.FragmentMeBinding;

/**
 * The Me page, the reader's way (Settings → Appearance → Me page): the sections under the
 * header in any order, and any of them hidden. Settings is not one of them — it's the only way
 * into Settings, so it always stays, last, with "Arrange this page" under it.
 */
public final class MePage {

    public static final String PEOPLE = "people", LETTERS = "letters", DRAFTS = "drafts",
            FELT = "felt", RESPONSES = "responses";
    public static final List<String> DEFAULT_ORDER =
            Collections.unmodifiableList(Arrays.asList(PEOPLE, LETTERS, DRAFTS, FELT, RESPONSES));

    private static final String PREFS = "me_page";

    private MePage() { }

    // ── The reader's choices ──────────────────────────────────────────────

    /** Every section once; sections added in a later version join at the end. */
    @NonNull
    public static List<String> order(@NonNull Context c) {
        String raw = prefs(c).getString("order", null);
        List<String> out = new ArrayList<>();
        if (raw != null) {
            for (String k : raw.split(",")) if (DEFAULT_ORDER.contains(k) && !out.contains(k)) out.add(k);
        }
        for (String k : DEFAULT_ORDER) if (!out.contains(k)) out.add(k);
        return out;
    }

    public static void setOrder(@NonNull Context c, @NonNull List<String> order) {
        prefs(c).edit().putString("order", String.join(",", order)).apply();
    }

    public static boolean shown(@NonNull Context c, @NonNull String key) {
        return !hidden(c).contains(key);
    }

    public static void setShown(@NonNull Context c, @NonNull String key, boolean shown) {
        Set<String> h = new HashSet<>(hidden(c));
        if (shown) h.remove(key); else h.add(key);
        prefs(c).edit().putStringSet("hidden", h).apply();
    }

    /** Back to the original page: every section, original order. */
    public static void reset(@NonNull Context c) {
        prefs(c).edit().clear().apply();
    }

    @NonNull
    private static Set<String> hidden(@NonNull Context c) {
        return prefs(c).getStringSet("hidden", Collections.emptySet());
    }

    // ── Per section ───────────────────────────────────────────────────────

    @DrawableRes
    public static int icon(@NonNull String key) {
        switch (key) {
            case LETTERS: return R.drawable.ic_letters;
            case DRAFTS: return R.drawable.ic_pencil;
            case FELT: return R.drawable.ic_heart;
            case RESPONSES: return R.drawable.ic_message;
            default: return R.drawable.ic_users;
        }
    }

    @StringRes
    public static int name(@NonNull String key) {
        switch (key) {
            case LETTERS: return R.string.me_card_letters;
            case DRAFTS: return R.string.me_card_drafts;
            case FELT: return R.string.me_card_felt;
            case RESPONSES: return R.string.me_card_responses;
            default: return R.string.mepage_people;
        }
    }

    @NonNull
    private static View view(@NonNull FragmentMeBinding b, @NonNull String key) {
        switch (key) {
            case LETTERS: return b.cardLetters;
            case DRAFTS: return b.cardDrafts;
            case FELT: return b.cardFelt;
            case RESPONSES: return b.cardResponses;
            default: return b.sectionPeople;
        }
    }

    // ── Laying out the page ───────────────────────────────────────────────

    /**
     * Sections in the reader's order (hidden ones gone), then Settings and the arrange link.
     * The edit buttons under the header stay first. The first visible section gets the roomier
     * gap under those buttons; the rest sit closer together, as before.
     */
    public static void apply(@NonNull Context c, @NonNull FragmentMeBinding b) {
        ViewGroup content = b.content;
        List<View> tail = new ArrayList<>();
        for (String key : order(c)) tail.add(view(b, key));
        tail.add(b.cardSettings);
        tail.add(b.meArrange);
        String state = String.join(",", order(c)) + "|" + new java.util.TreeSet<>(hidden(c));
        boolean inPlace = state.equals(content.getTag(R.id.me_page_state));
        if (inPlace) return;                              // nothing changed: don't touch the page
        content.setTag(R.id.me_page_state, state);
        for (View v : tail) content.removeView(v);
        int roomy = c.getResources().getDimensionPixelSize(R.dimen.space_lg);
        int close = c.getResources().getDimensionPixelSize(R.dimen.space_12);
        boolean first = true;
        for (String key : order(c)) {
            View v = view(b, key);
            boolean show = shown(c, key);
            v.setVisibility(show ? View.VISIBLE : View.GONE);
            setTop(v, first && show ? roomy : close);
            if (show) first = false;
            content.addView(v);
        }
        setTop(b.cardSettings, first ? roomy : close);
        content.addView(b.cardSettings);
        content.addView(b.meArrange);
    }

    private static void setTop(@NonNull View v, int px) {
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp instanceof LinearLayout.LayoutParams) {
            ((LinearLayout.LayoutParams) lp).topMargin = px;
            v.setLayoutParams(lp);
        }
    }

    private static SharedPreferences prefs(@NonNull Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
