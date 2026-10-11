package in.mohammad.ramiz.confess.ui.stories;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import in.mohammad.ramiz.confess.R;

/** The five story backgrounds (server `style` 0–4), all from the app palette. */
public final class StoryStyles {

    public static final int COUNT = 5;

    private static final int[] BG = {R.color.story_night, R.color.paper, R.color.amber, R.color.sage, R.color.brick};
    private static final int[] FG = {R.color.paper, R.color.paper_ink, R.color.paper_ink, R.color.paper_ink, R.color.paper};

    private StoryStyles() { }

    private static int clamp(int style) {
        return style < 0 || style >= COUNT ? 0 : style;
    }

    @ColorInt
    public static int background(@NonNull Context c, int style) {
        return ContextCompat.getColor(c, BG[clamp(style)]);
    }

    @ColorInt
    public static int text(@NonNull Context c, int style) {
        return ContextCompat.getColor(c, FG[clamp(style)]);
    }

    /** A soft diagonal wash of the colour (lighter top-left → deeper bottom-right). */
    @NonNull
    public static GradientDrawable card(@NonNull Context c, int style, float radiusPx) {
        int base = background(c, style);
        int light = ColorUtils.blendARGB(base, 0xFFFFFFFF, 0.10f);
        int deep = ColorUtils.blendARGB(base, 0xFF000000, 0.22f);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{light, base, deep});
        g.setCornerRadius(radiusPx);
        return g;
    }
}
