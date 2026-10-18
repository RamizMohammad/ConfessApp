package in.mohammad.ramiz.confess.ui.feed;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.ui.components.PaperTone;

/**
 * A tiny looping film of how bursts look, for the chooser: a phone-sized window with paper
 * cards in it.
 *   SLIDER   — three cards from one writer slide past one by one, dots keeping count
 *   STACK    — a stacked card with "7" on it is tapped, and the pages open one after another
 *   SEPARATE — their cards scroll by one under another, the way the feed is now
 * Drawn in code (no video file), in the reader's own paper tone; it stops when off screen and
 * holds a still frame when animations are turned off on the phone.
 */
public final class BurstPreviewView extends View {

    private static final long LOOP_MS = 4200;

    private int mode = Bursts.SLIDER;
    private float t = 0f;                                 // 0…1 through the loop
    @Nullable private ValueAnimator loop;

    private final Paint frame = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paper = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint accent = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final float d;

    public BurstPreviewView(@NonNull Context context) {
        this(context, null);
    }

    public BurstPreviewView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        d = getResources().getDisplayMetrics().density;
        frame.setColor(ContextCompat.getColor(context, R.color.surface_high));
        int p = PaperTone.paper(context);
        paper.setColor(p);
        int i = PaperTone.ink(context);
        ink.setColor(ColorUtils.setAlphaComponent(i, 120));
        accent.setColor(ContextCompat.getColor(context, R.color.amber));
        dim.setColor(ColorUtils.setAlphaComponent(ContextCompat.getColor(context, R.color.text_secondary), 90));
        shadow.setColor(ColorUtils.blendARGB(p, 0xFF000000, 0.18f));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setMode(int m) {
        mode = m;
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!ValueAnimator.areAnimatorsEnabled()) {
            t = 0.3f;                                     // a telling still frame
            return;
        }
        loop = ValueAnimator.ofFloat(0f, 1f);
        loop.setDuration(LOOP_MS);
        loop.setRepeatCount(ValueAnimator.INFINITE);
        loop.setInterpolator(new LinearInterpolator());
        loop.addUpdateListener(a -> {
            t = (float) a.getAnimatedValue();
            invalidate();
        });
        loop.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (loop != null) loop.cancel();
        loop = null;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onMeasure(int w, int h) {
        int width = MeasureSpec.getSize(w);
        int height = Math.round(108 * d);
        setMeasuredDimension(width, resolveSize(height, h));
    }

    @Override
    protected void onDraw(@NonNull Canvas c) {
        float w = getWidth(), h = getHeight();
        // The "phone": a rounded window, cards inside it.
        float fw = Math.min(w, 132 * d);
        float fx = (w - fw) / 2f;
        r.set(fx, 0, fx + fw, h);
        c.drawRoundRect(r, 14 * d, 14 * d, frame);
        c.save();
        c.clipRect(fx + 6 * d, 6 * d, fx + fw - 6 * d, h - 6 * d);
        float left = fx + 10 * d, right = fx + fw - 10 * d;
        switch (mode) {
            case Bursts.STACK: drawStack(c, left, right, h); break;
            case Bursts.SEPARATE: drawSeparate(c, left, right, h); break;
            default: drawSlider(c, left, right, h);
        }
        c.restore();
    }

    /** Three pages: hold, slide, hold, slide, hold, back to the first. */
    private void drawSlider(@NonNull Canvas c, float left, float right, float h) {
        float cw = right - left;
        float pos = sliderPosition(t);                   // 0 … 2 pages
        float top = 14 * d, bottom = h - 26 * d;
        for (int i = 0; i < 3; i++) {
            float x = left + (i - pos) * (cw + 8 * d);
            if (x > right || x + cw < left) continue;
            card(c, x, top, x + cw, bottom, i == 0 ? 3 : i == 1 ? 2 : 4, true);
        }
        // dots
        float cy = h - 15 * d, gap = 9 * d, cx0 = (left + right) / 2f - gap;
        int on = Math.round(pos);
        for (int i = 0; i < 3; i++) c.drawCircle(cx0 + i * gap, cy, 2.6f * d, i == on ? accent : dim);
    }

    private static float sliderPosition(float t) {
        // 0–.22 hold · .22–.34 slide · .34–.56 hold · .56–.68 slide · .68–.9 hold · .9–1 back
        if (t < 0.22f) return 0;
        if (t < 0.34f) return ease((t - 0.22f) / 0.12f);
        if (t < 0.56f) return 1;
        if (t < 0.68f) return 1 + ease((t - 0.56f) / 0.12f);
        if (t < 0.90f) return 2;
        return 2 - 2 * ease((t - 0.90f) / 0.10f);
    }

    /** The stack is tapped (a ring), then its pages open one after another. */
    private void drawStack(@NonNull Canvas c, float left, float right, float h) {
        float top = 16 * d, bottom = h - 22 * d;
        float open = t < 0.35f ? 0 : t < 0.5f ? ease((t - 0.35f) / 0.15f) : t < 0.9f ? 1 : 1 - ease((t - 0.9f) / 0.1f);
        // The sheets underneath fall away as it opens.
        float drop = 1 - open;
        r.set(left + 14 * d, top + 12 * d * drop, right - 14 * d, bottom + 12 * d * drop);
        c.drawRoundRect(r, 8 * d, 8 * d, shadow);
        r.set(left + 7 * d, top + 6 * d * drop, right - 7 * d, bottom + 6 * d * drop);
        c.drawRoundRect(r, 8 * d, 8 * d, shadow);
        float grow = 8 * d * open;
        card(c, left - grow, top - grow, right + grow, bottom + grow, 0, true);
        if (open < 0.5f) {
            // "u1 made 7" + the count badge
            float a = 1 - open * 2;
            Paint p = new Paint(ink);
            p.setAlpha(Math.round(p.getAlpha() * a));
            float y = top + 30 * d;
            c.drawRoundRect(left + 10 * d, y, left + (right - left) * 0.72f, y + 6 * d, 3 * d, 3 * d, p);
            Paint badge = new Paint(accent);
            badge.setAlpha(Math.round(255 * a));
            c.drawCircle(right - 14 * d, top + 14 * d, 8 * d, badge);
            // the tap
            if (t > 0.18f && t < 0.38f) {
                float k = (t - 0.18f) / 0.2f;
                Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
                ring.setStyle(Paint.Style.STROKE);
                ring.setStrokeWidth(2 * d);
                ring.setColor(accent.getColor());
                ring.setAlpha(Math.round(220 * (1 - k)));
                c.drawCircle((left + right) / 2f, (top + bottom) / 2f + 8 * d, 6 * d + 14 * d * k, ring);
            }
        } else {
            // Opened: page lines, with a counter of segments up top.
            int pageNo = t < 0.65f ? 0 : t < 0.8f ? 1 : 2;
            float sx = left + 4 * d, sw = (right - left - 8 * d - 12 * d) / 4f;
            for (int i = 0; i < 4; i++) {
                c.drawRoundRect(sx + i * (sw + 4 * d), top - 4 * d, sx + i * (sw + 4 * d) + sw, top - 2 * d, d, d,
                        i <= pageNo ? accent : dim);
            }
            lines(c, left + 8 * d, right - 8 * d, top + 12 * d, 3 + pageNo);
        }
    }

    /** Cards one under another, scrolling up slowly. */
    private void drawSeparate(@NonNull Canvas c, float left, float right, float h) {
        float ch = 40 * d, gap = 8 * d;
        float shift = (t * 3f % 1f) * (ch + gap);
        for (int i = 0; i < 4; i++) {
            float y = 8 * d + i * (ch + gap) - shift;
            card(c, left, y, right, y + ch, 2, false);
        }
    }

    private void card(@NonNull Canvas c, float l, float t, float rr, float b, int lineCount, boolean avatar) {
        r.set(l, t, rr, b);
        c.drawRoundRect(r, 8 * d, 8 * d, paper);
        float x = l + 8 * d, y = t + 8 * d;
        c.drawCircle(x + 4 * d, y + 4 * d, 4 * d, accent);              // the writer's aura
        c.drawRoundRect(x + 12 * d, y + 2 * d, x + 40 * d, y + 6 * d, 2 * d, 2 * d, ink);
        if (lineCount > 0) lines(c, l + 8 * d, rr - 8 * d, y + 16 * d, lineCount);
    }

    private void lines(@NonNull Canvas c, float l, float rr, float y, int n) {
        for (int i = 0; i < n; i++) {
            float end = i == n - 1 ? l + (rr - l) * 0.6f : rr;
            float yy = y + i * 8 * d;
            if (yy > getHeight()) break;
            c.drawRoundRect(l, yy, end, yy + 3.5f * d, 2 * d, 2 * d, ink);
        }
    }

    private static float ease(float x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }
}
