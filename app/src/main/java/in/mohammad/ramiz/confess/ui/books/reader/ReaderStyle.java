package in.mohammad.ramiz.confess.ui.books.reader;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.util.TypedValue;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.ColorUtils;

import in.mohammad.ramiz.confess.R;

/**
 * Everything a page needs to look like a page: colours for the chosen theme, the paints
 * (body / chapter heading / running head) and the page margins. Built from {@link ReaderPrefs}.
 */
final class ReaderStyle {

    @ColorInt final int background, ink, meta, accent;
    final boolean dark;
    final TextPaint body = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    final TextPaint heading = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    final TextPaint label = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    final TextPaint running = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    /** Ruled lines under the text (null = off). */
    @androidx.annotation.Nullable final Paint rule;
    final float lineSpacing;
    final boolean justify;
    /** Page margins (px); top / bottom include the system bars so text never sits under them. */
    int sidePx, topPx, bottomPx, headBaselinePx, footBaselineFromBottomPx;

    ReaderStyle(@NonNull Context ctx, @NonNull ReaderPrefs p, int statusBarPx, int navBarPx) {
        int bg, fg, mt;
        switch (p.theme) {
            case ReaderPrefs.THEME_SEPIA:
                bg = R.color.reader_sepia_bg; fg = R.color.reader_sepia_ink; mt = R.color.reader_sepia_meta; break;
            case ReaderPrefs.THEME_NIGHT:
                bg = R.color.reader_night_bg; fg = R.color.reader_night_ink; mt = R.color.reader_night_meta; break;
            default:
                bg = R.color.reader_paper_bg; fg = R.color.reader_paper_ink; mt = R.color.reader_paper_meta; break;
        }
        boolean night = p.theme == ReaderPrefs.THEME_NIGHT;
        if (night) {
            background = ContextCompat.getColor(ctx, bg);
            ink = ContextCompat.getColor(ctx, fg);
            meta = ContextCompat.getColor(ctx, mt);
        } else {
            // Paper tone dims Paper / Sepia pages toward charcoal; text tone (Auto: flips to light
            // cream once the page gets dark) keeps them readable.
            background = in.mohammad.ramiz.confess.ui.components.PaperTone.tint(ctx, ContextCompat.getColor(ctx, bg));
            ink = in.mohammad.ramiz.confess.ui.components.PaperTone.ink(ctx, ContextCompat.getColor(ctx, fg), background);
            meta = ColorUtils.blendARGB(background, ink, 0.55f);
        }
        dark = ColorUtils.calculateLuminance(background) < 0.25;   // light status-bar icons, amber accent
        accent = ContextCompat.getColor(ctx, dark ? R.color.amber : R.color.reader_accent);
        lineSpacing = p.lineSpacing();
        justify = p.justify;
        if (p.lines) {
            rule = new Paint(Paint.ANTI_ALIAS_FLAG);
            // Same weight as the composer's letter paper (paper_rule = 15% ink); a touch lighter at night.
            rule.setColor(ColorUtils.setAlphaComponent(ink, dark ? 30 : 38));
            rule.setStrokeWidth(Math.max(1f, ctx.getResources().getDisplayMetrics().density));
        } else {
            rule = null;
        }

        float size = sp(ctx, p.sizeSp);
        Typeface bodyFace = in.mohammad.ramiz.confess.ui.components.ReadingFonts.typeface(ctx, p.font);
        Typeface headFace = ResourcesCompat.getFont(ctx, R.font.newsreader_headline);
        Typeface mono = ResourcesCompat.getFont(ctx, R.font.jetbrainsmono);

        body.setTypeface(bodyFace);
        body.setTextSize(size * in.mohammad.ramiz.confess.ui.components.ReadingFonts.scale(p.font));   // handwriting reads small
        body.setColor(ink);
        // Night: slightly softer ink, like e-ink night mode.
        if (dark) body.setColor(ColorUtils.setAlphaComponent(ink, 235));

        heading.setTypeface(headFace);
        heading.setTextSize(size * 1.55f);
        heading.setColor(ink);

        label.setTypeface(mono);
        label.setTextSize(Math.max(sp(ctx, 10), size * 0.6f));
        label.setLetterSpacing(0.22f);
        label.setColor(accent);

        running.setTypeface(mono);
        running.setTextSize(sp(ctx, 10));
        running.setLetterSpacing(0.08f);
        running.setColor(meta);

        sidePx = dp(ctx, 28);
        headBaselinePx = statusBarPx + dp(ctx, 26);
        topPx = statusBarPx + dp(ctx, 46);
        footBaselineFromBottomPx = navBarPx + dp(ctx, 18);
        bottomPx = navBarPx + dp(ctx, 44);
    }

    static float sp(Context c, float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, c.getResources().getDisplayMetrics());
    }

    static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }
}
