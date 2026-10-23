package in.mohammad.ramiz.confess.ui.components;

import android.content.Context;
import android.graphics.Typeface;

import androidx.annotation.FontRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.res.ResourcesCompat;

import in.mohammad.ramiz.confess.R;

/**
 * The fonts people can read confessions and books in (Settings → Appearance → Confession text,
 * and the reader's "Aa" sheet / Settings → Reading). Ids are what's saved, so they never change
 * meaning: 0 and 1 were the original serif / sans choices.
 *
 * All open-licence (SIL OFL) from Google Fonts. The new ones are bundled as their regular weight,
 * Latin only (res/font/*_regular.ttf); other scripts fall back to the phone's own font.
 */
public final class ReadingFonts {

    public static final int NEWSREADER = 0, MANROPE = 1, LITERATA = 2, LORA = 3,
            ATKINSON = 4, NUNITO = 5, CAVEAT = 6, TYPEWRITER = 7;

    public static final int DEFAULT = MANROPE;

    /** The order the picker shows them. */
    public static final int[] ORDER = {MANROPE, NEWSREADER, LITERATA, LORA, ATKINSON, NUNITO, CAVEAT, TYPEWRITER};

    private ReadingFonts() { }

    /** Anything unknown (an old or broken saved value) reads as the default. */
    public static int valid(int id) {
        return id >= NEWSREADER && id <= TYPEWRITER ? id : DEFAULT;
    }

    @FontRes
    public static int font(int id) {
        switch (valid(id)) {
            case NEWSREADER: return R.font.newsreader;
            case LITERATA: return R.font.literata_regular;
            case LORA: return R.font.lora_regular;
            case ATKINSON: return R.font.atkinson_regular;
            case NUNITO: return R.font.nunito_regular;
            case CAVEAT: return R.font.caveat_regular;
            case TYPEWRITER: return R.font.jetbrainsmono;
            default: return R.font.manrope;
        }
    }

    @StringRes
    public static int name(int id) {
        switch (valid(id)) {
            case NEWSREADER: return R.string.font_newsreader;
            case LITERATA: return R.string.font_literata;
            case LORA: return R.string.font_lora;
            case ATKINSON: return R.string.font_atkinson;
            case NUNITO: return R.string.font_nunito;
            case CAVEAT: return R.string.font_caveat;
            case TYPEWRITER: return R.string.font_typewriter;
            default: return R.string.font_manrope;
        }
    }

    /** A short line on what it's like. */
    @StringRes
    public static int about(int id) {
        switch (valid(id)) {
            case NEWSREADER: return R.string.font_newsreader_sub;
            case LITERATA: return R.string.font_literata_sub;
            case LORA: return R.string.font_lora_sub;
            case ATKINSON: return R.string.font_atkinson_sub;
            case NUNITO: return R.string.font_nunito_sub;
            case CAVEAT: return R.string.font_caveat_sub;
            case TYPEWRITER: return R.string.font_typewriter_sub;
            default: return R.string.font_manrope_sub;
        }
    }

    /**
     * Some faces look smaller than others at the same size (handwriting has a tiny x-height),
     * so they're scaled to read about as big as the rest.
     */
    public static float scale(int id) {
        switch (valid(id)) {
            case CAVEAT: return 1.22f;
            case TYPEWRITER: return 0.92f;
            default: return 1f;
        }
    }

    /** Upright, regular weight. */
    @Nullable
    public static Typeface typeface(@NonNull Context c, int id) {
        Typeface face = ResourcesCompat.getFont(c, font(id));
        return Typeface.create(face, Typeface.NORMAL);
    }
}
