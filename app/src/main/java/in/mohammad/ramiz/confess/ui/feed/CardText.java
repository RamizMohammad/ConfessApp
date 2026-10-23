package in.mohammad.ramiz.confess.ui.feed;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.TypedValue;
import android.widget.TextView;

import androidx.annotation.NonNull;


/**
 * How confession text reads on cards (Settings → Appearance → Confession text): size, line
 * spacing and font — the parts of the book reader's settings that make sense for a card.
 * Paper and text tone live in {@link in.mohammad.ramiz.confess.ui.components.PaperTone}.
 *
 * The confession screen uses the same choices, a few sizes bigger ({@link #DETAIL_EXTRA}).
 * Always upright: no italic on cards.
 */
public final class CardText {

    public static final int MIN_SIZE = 14, MAX_SIZE = 22, DEFAULT_SIZE = 18;
    public static final int COMPACT = 0, NORMAL = 1, AIRY = 2;
    /** Font ids are {@link in.mohammad.ramiz.confess.ui.components.ReadingFonts}'s. */
    public static final int FONT_SERIF = in.mohammad.ramiz.confess.ui.components.ReadingFonts.NEWSREADER, FONT_SANS = in.mohammad.ramiz.confess.ui.components.ReadingFonts.MANROPE;

    /** The confession screen: this many sp above the card size (18 → 22). */
    public static final int DETAIL_EXTRA = 4;

    /** Line height as a multiple of the text size: compact · normal (the old 27 / 17) · airy. */
    private static final float[] LINE = {1.42f, 1.59f, 1.8f};

    private static final String PREFS = "card_text";

    private CardText() { }

    public static int size(@NonNull Context c) {
        return Math.max(MIN_SIZE, Math.min(MAX_SIZE, prefs(c).getInt("size", DEFAULT_SIZE)));
    }

    public static void setSize(@NonNull Context c, int sp) {
        prefs(c).edit().putInt("size", Math.max(MIN_SIZE, Math.min(MAX_SIZE, sp))).apply();
    }

    public static int spacing(@NonNull Context c) {
        int s = prefs(c).getInt("spacing", NORMAL);
        return s == COMPACT || s == AIRY ? s : NORMAL;
    }

    public static void setSpacing(@NonNull Context c, int spacing) {
        prefs(c).edit().putInt("spacing", spacing).apply();
    }

    public static int font(@NonNull Context c) {
        return in.mohammad.ramiz.confess.ui.components.ReadingFonts.valid(prefs(c).getInt("font", in.mohammad.ramiz.confess.ui.components.ReadingFonts.DEFAULT));
    }

    public static void setFont(@NonNull Context c, int font) {
        prefs(c).edit().putInt("font", font).apply();
    }

    /** Changes since a screen drew with this? */
    public static int stamp(@NonNull Context c) {
        return size(c) * 100 + spacing(c) * 10 + font(c);
    }

    /** Style a confession's text: card size, or the confession screen's bigger size. */
    public static void apply(@NonNull TextView t, boolean detail) {
        Context c = t.getContext();
        int font = font(c);
        float sp = (size(c) + (detail ? DETAIL_EXTRA : 0)) * in.mohammad.ramiz.confess.ui.components.ReadingFonts.scale(font);   // handwriting reads small
        t.setTypeface(in.mohammad.ramiz.confess.ui.components.ReadingFonts.typeface(c, font));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        float px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp * LINE[spacing(c)],
                c.getResources().getDisplayMetrics());
        t.setLineHeight(Math.round(px));
    }

    private static SharedPreferences prefs(@NonNull Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
