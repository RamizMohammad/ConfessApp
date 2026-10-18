package in.mohammad.ramiz.confess.ui.books.reader;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

/** Reading settings (the "Aa" sheet), kept on this phone for every book. */
public final class ReaderPrefs {

    public static final int THEME_PAPER = 0, THEME_SEPIA = 1, THEME_NIGHT = 2;
    public static final int FONT_SERIF = 0, FONT_SANS = 1;
    public static final int TURN_CURL = PageTurner.CURL, TURN_SLIDE = PageTurner.SLIDE, TURN_INSTANT = PageTurner.INSTANT;
    public static final int MIN_SIZE = 14, MAX_SIZE = 30, DEFAULT_SIZE = 19;
    /** Line height multipliers: compact · normal · airy. */
    static final float[] SPACING = {1.28f, 1.48f, 1.72f};

    private static final String PREFS = "reader";

    public int sizeSp = DEFAULT_SIZE;
    public int spacing = 1;
    public int font = FONT_SERIF;
    public int theme = THEME_PAPER;
    public boolean justify = true;
    public boolean volumeKeys = true;
    /** Faint ruled lines under the text, like letter paper. */
    public boolean lines = true;
    /** How pages turn: curl · slide · instant. */
    public int turn = TURN_CURL;
    /** Screen brightness while reading, 0–100; -1 = the phone's own. */
    public int brightness = -1;

    private ReaderPrefs() { }

    @NonNull
    public static ReaderPrefs load(@NonNull Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        ReaderPrefs r = new ReaderPrefs();
        r.sizeSp = clamp(p.getInt("size", DEFAULT_SIZE), MIN_SIZE, MAX_SIZE);
        r.spacing = clamp(p.getInt("spacing", 1), 0, SPACING.length - 1);
        r.font = clamp(p.getInt("font", FONT_SERIF), 0, 1);
        r.theme = clamp(p.getInt("theme", THEME_PAPER), 0, 2);
        r.justify = p.getBoolean("justify", true);
        r.volumeKeys = p.getBoolean("volume_keys", true);
        r.lines = p.getBoolean("lines", true);
        r.turn = clamp(p.getInt("turn", TURN_CURL), 0, 2);
        int br = p.getInt("brightness", -1);
        r.brightness = br < 0 ? -1 : clamp(br, 0, 100);
        return r;
    }

    public void save(@NonNull Context ctx) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt("size", sizeSp).putInt("spacing", spacing).putInt("font", font).putInt("theme", theme)
                .putBoolean("justify", justify).putBoolean("volume_keys", volumeKeys).putBoolean("lines", lines)
                .putInt("turn", turn).putInt("brightness", brightness).apply();
    }

    float lineSpacing() {
        return SPACING[spacing];
    }

    /** A key for "the layout changed" (theme alone doesn't need new pages). */
    String layoutKey() {
        return sizeSp + "/" + spacing + "/" + font + "/" + justify;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
