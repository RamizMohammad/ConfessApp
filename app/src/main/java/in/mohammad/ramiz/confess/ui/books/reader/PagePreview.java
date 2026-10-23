package in.mohammad.ramiz.confess.ui.books.reader;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.text.LineBreaker;
import android.text.Layout;
import android.text.StaticLayout;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.core.Haptics;

/**
 * A small sample page at the top of the reading sheet: drawn with the same {@link ReaderStyle}
 * as the reader (theme, paper / text tone, font, size, spacing, ruled lines, justify), so every
 * change shows at once — handy from Settings → Reading, where there's no book behind the sheet.
 * Tap it to see the chosen page turn (curl · slide · instant) between two sample pages.
 */
public class PagePreview extends View {

    private final RectF box = new RectF();
    private final Paint paper = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float pad, radius;
    private final String[] pages;
    private int page = 0;
    @Nullable private ReaderPrefs prefs;
    @Nullable private ReaderStyle style;
    @Nullable private StaticLayout layout;
    private boolean turning;

    public PagePreview(@NonNull Context context) {
        this(context, null);
    }

    public PagePreview(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        float d = getResources().getDisplayMetrics().density;
        pad = 18 * d;
        radius = 14 * d;
        pages = new String[]{context.getString(R.string.reader_preview_page1), context.getString(R.string.reader_preview_page2)};
        setClickable(true);
        setFocusable(true);
        setContentDescription(context.getString(R.string.cd_reader_preview));
        setOnClickListener(v -> turn());
    }

    /** Re-draw with these settings (call after every change). */
    public void update(@NonNull ReaderPrefs p) {
        prefs = p;
        style = new ReaderStyle(getContext(), p, 0, 0);
        layout = null;
        invalidate();
    }

    /** Show the chosen page turn once (also used right after picking a turn style). */
    public void turn() {
        if (prefs == null || turning) return;
        Haptics.tick(getContext());
        if (!android.animation.ValueAnimator.areAnimatorsEnabled()) {
            nextPage();
            return;
        }
        turning = true;
        if (prefs.turn == ReaderPrefs.TURN_INSTANT) {        // a quick fade, no motion
            animate().alpha(0f).setDuration(90).withEndAction(() -> {
                nextPage();
                animate().alpha(1f).setDuration(110).withEndAction(() -> turning = false).start();
            }).start();
        } else if (prefs.turn == ReaderPrefs.TURN_SLIDE) {
            animate().translationX(-getWidth() * 0.6f).alpha(0f).setDuration(180)
                    .setInterpolator(new AccelerateInterpolator())
                    .withEndAction(() -> {
                        nextPage();
                        setTranslationX(getWidth() * 0.6f);
                        animate().translationX(0f).alpha(1f).setDuration(220)
                                .setInterpolator(new DecelerateInterpolator())
                                .withEndAction(() -> turning = false).start();
                    }).start();
        } else {                                      // curl: the page folds over from the right edge
            setPivotX(0f);
            setPivotY(getHeight() / 2f);
            setCameraDistance(8000 * getResources().getDisplayMetrics().density);
            animate().rotationY(-80f).alpha(0.4f).setDuration(240)
                    .setInterpolator(new AccelerateInterpolator())
                    .withEndAction(() -> {
                        nextPage();
                        setRotationY(0f);
                        setAlpha(0f);
                        animate().alpha(1f).setDuration(160)
                                .withEndAction(() -> turning = false).start();
                    }).start();
        }
    }

    private void nextPage() {
        page = (page + 1) % pages.length;
        layout = null;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        layout = null;
    }

    @Override
    protected void onDraw(@NonNull Canvas c) {
        super.onDraw(c);
        if (style == null) return;
        box.set(0, 0, getWidth(), getHeight());
        paper.setColor(style.background);
        c.drawRoundRect(box, radius, radius, paper);

        // Running label, as on a real page.
        float labelBase = pad + style.label.getTextSize();
        c.drawText(getContext().getString(R.string.reader_preview_label), pad, labelBase, style.label);

        int textWidth = Math.max(1, Math.round(getWidth() - 2 * pad));
        if (layout == null) {
            StaticLayout.Builder b = StaticLayout.Builder.obtain(pages[page], 0, pages[page].length(), style.body, textWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, style.lineSpacing)
                    .setIncludePad(false);
            if (style.justify) b.setJustificationMode(LineBreaker.JUSTIFICATION_MODE_INTER_WORD);
            layout = b.build();
        }
        float top = labelBase + pad * 0.8f;
        float bottom = getHeight() - pad;
        c.save();
        c.clipRect(pad, top, getWidth() - pad, bottom);
        c.translate(pad, top);
        for (int i = 0; i < layout.getLineCount(); i++) {
            if (top + layout.getLineTop(i) > bottom) break;
            if (style.rule != null) {
                float y = layout.getLineBaseline(i) + style.body.getTextSize() * 0.28f;
                c.drawLine(0, y, textWidth, y, style.rule);
            }
        }
        layout.draw(c);
        c.restore();
    }
}
