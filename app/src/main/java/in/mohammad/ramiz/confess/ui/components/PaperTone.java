package in.mohammad.ramiz.confess.ui.components;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.button.MaterialButton;

import in.mohammad.ramiz.confess.R;

/**
 * Paper + text tone for everything printed on "paper": confession cards, the composer's note,
 * the chapter editor and book pages (Paper / Sepia).
 *
 *   Paper tone  0 = the usual warm white paper … 100 = deep charcoal for reading in the dark.
 *   Text tone   Auto (default) = whichever of dark ink / light cream reads best on the paper,
 *               so text flips to light by itself as the paper gets dark;
 *               or a fixed tone 0 = dark ink … 100 = light cream.
 *
 * Kept on this phone; Settings → Appearance and the reader's Aa sheet both change it.
 */
public final class PaperTone {

    public static final int MAX = 100;
    /** Text tone value meaning "pick for me". */
    public static final int AUTO = -1;
    /** The dark end of the paper slider. */
    @ColorInt private static final int NIGHT = 0xFF3E3C39;
    /** Light text for dark paper (warm, not pure white — gentler at night). */
    @ColorInt private static final int CREAM = 0xFFEFE9DF;

    private static final String PREFS = "appearance";
    private static int paperCached = -1;
    private static int textCached = Integer.MIN_VALUE;

    private PaperTone() { }

    // ── Settings ──────────────────────────────────────────────────────────

    public static int get(@NonNull Context ctx) {
        if (paperCached < 0) paperCached = clamp(prefs(ctx).getInt("paper_tone", 0));
        return paperCached;
    }

    public static void set(@NonNull Context ctx, int value) {
        paperCached = clamp(value);
        prefs(ctx).edit().putInt("paper_tone", paperCached).apply();
    }

    /** {@link #AUTO} or 0–100. */
    public static int text(@NonNull Context ctx) {
        if (textCached == Integer.MIN_VALUE) {
            int v = prefs(ctx).getInt("text_tone", AUTO);
            textCached = v == AUTO ? AUTO : clamp(v);
        }
        return textCached;
    }

    public static void setText(@NonNull Context ctx, int value) {
        textCached = value == AUTO ? AUTO : clamp(value);
        prefs(ctx).edit().putInt("text_tone", textCached).apply();
    }

    /** Settings changed since a screen drew with `stamp`? (paper and text in one number) */
    public static int stamp(@NonNull Context ctx) {
        return get(ctx) * 1000 + text(ctx) + 1;
    }

    // ── Colours ───────────────────────────────────────────────────────────

    /** A paper colour dimmed toward charcoal by `tone`. */
    @ColorInt
    public static int tint(@ColorInt int paper, int tone) {
        return ColorUtils.blendARGB(paper, NIGHT, clamp(tone) / (float) MAX);
    }

    @ColorInt
    public static int tint(@NonNull Context ctx, @ColorInt int paper) {
        return tint(paper, get(ctx));
    }

    /** The confession paper at the current tone. */
    @ColorInt
    public static int paper(@NonNull Context ctx) {
        return tint(ctx, ContextCompat.getColor(ctx, R.color.paper));
    }

    /**
     * Text on `background`: the theme's own dark ink, light cream, or a fixed mix of the two.
     * Auto picks whichever has more contrast.
     */
    @ColorInt
    public static int ink(@ColorInt int darkInk, @ColorInt int background, int textTone) {
        if (textTone == AUTO) {
            return ColorUtils.calculateContrast(darkInk, opaque(background))
                    >= ColorUtils.calculateContrast(CREAM, opaque(background)) ? darkInk : CREAM;
        }
        return ColorUtils.blendARGB(darkInk, CREAM, clamp(textTone) / (float) MAX);
    }

    @ColorInt
    public static int ink(@NonNull Context ctx, @ColorInt int darkInk, @ColorInt int background) {
        return ink(darkInk, background, text(ctx));
    }

    /** Text on the confession paper. */
    @ColorInt
    public static int ink(@NonNull Context ctx) {
        return ink(ctx, ContextCompat.getColor(ctx, R.color.paper_ink), paper(ctx));
    }

    /** Metadata on paper (65% of the ink, like paper_meta). */
    @ColorInt
    public static int meta(@NonNull Context ctx) {
        return ColorUtils.setAlphaComponent(ink(ctx), 0xA6);
    }

    /** Rules / dividers on paper (15% of the ink, like paper_rule). */
    @ColorInt
    public static int rule(@NonNull Context ctx) {
        return ColorUtils.setAlphaComponent(ink(ctx), 0x26);
    }

    // ── Applying to views ─────────────────────────────────────────────────

    /**
     * Recolour the text and icons of a paper surface. Anything drawn in paper_ink becomes the
     * current ink, anything in paper_meta the current meta colour. Colours set for meaning
     * (amber for "felt", brick for errors…) are left alone. Safe to call on every bind.
     */
    public static void apply(@NonNull View root) {
        Context ctx = root.getContext();
        int baseInk = ContextCompat.getColor(ctx, R.color.paper_ink);
        int baseMeta = ContextCompat.getColor(ctx, R.color.paper_meta);
        walk(root, baseInk, baseMeta, ink(ctx), meta(ctx));
    }

    private static void walk(View v, int baseInk, int baseMeta, int ink, int meta) {
        if (v instanceof TextView) {
            TextView t = (TextView) v;
            Integer c = swap(t, R.id.tone_text, t.getCurrentTextColor(), baseInk, baseMeta, ink, meta);
            if (c != null) t.setTextColor(c);
            Integer h = swap(t, R.id.tone_hint, t.getCurrentHintTextColor(), baseInk, baseMeta, ink, meta);
            if (h != null) t.setHintTextColor(h);
            ColorStateList dt = t.getCompoundDrawableTintList();
            if (dt != null) {
                Integer d = swap(t, R.id.tone_drawable, dt.getDefaultColor(), baseInk, baseMeta, ink, meta);
                if (d != null) t.setCompoundDrawableTintList(ColorStateList.valueOf(d));
            }
            if (t instanceof MaterialButton) {
                MaterialButton m = (MaterialButton) t;
                ColorStateList it = m.getIconTint();
                if (it != null) {
                    Integer i = swap(m, R.id.tone_icon, it.getDefaultColor(), baseInk, baseMeta, ink, meta);
                    if (i != null) m.setIconTint(ColorStateList.valueOf(i));
                }
            }
        } else if (v instanceof ImageView) {
            ImageView iv = (ImageView) v;
            ColorStateList it = iv.getImageTintList();
            if (it != null) {
                Integer i = swap(iv, R.id.tone_icon, it.getDefaultColor(), baseInk, baseMeta, ink, meta);
                if (i != null) iv.setImageTintList(ColorStateList.valueOf(i));
            }
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) walk(g.getChildAt(i), baseInk, baseMeta, ink, meta);
        }
    }

    /**
     * New colour for one property, or null to leave it. The first time we see a property we
     * remember whether it was ink or meta; later we only replace it while it still shows that
     * role's colour (the base or the tone we applied last), never a colour code put there for meaning.
     */
    @Nullable
    private static Integer swap(View v, int key, int current, int baseInk, int baseMeta, int ink, int meta) {
        Object tag = v.getTag(key);
        int[] st;
        if (tag instanceof int[]) {
            st = (int[]) tag;
        } else {
            int role = current == baseInk ? 1 : current == baseMeta ? 2 : 0;
            st = new int[]{role, current};
            v.setTag(key, st);
        }
        if (st[0] == 0) return null;
        int base = st[0] == 1 ? baseInk : baseMeta;
        if (current != base && current != st[1]) return null;      // someone coloured it on purpose
        int next = st[0] == 1 ? ink : meta;
        st[1] = next;
        return current == next ? null : next;
    }

    @ColorInt
    private static int opaque(@ColorInt int c) {
        return ColorUtils.setAlphaComponent(c, 0xFF);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(MAX, v));
    }

    private static android.content.SharedPreferences prefs(@NonNull Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

}
