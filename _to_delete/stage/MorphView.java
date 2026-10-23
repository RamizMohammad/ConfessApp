package in.mohammad.ramiz.confess.ui.tour;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import in.mohammad.ramiz.confess.R;

/**
 * The tour's one shape that never leaves: a soft panel that morphs from page to page — the
 * welcome's circle stretches into the frame around a card, shrinks into the ring row, grows
 * around the phone… — like PowerPoint's Morph, so each page visibly carries on from the last.
 */
public class MorphView extends View {

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF cur = new RectF();
    private final ArgbEvaluator argb = new ArgbEvaluator();
    private float radius;
    private int color;
    private boolean has;
    @Nullable private ValueAnimator anim;

    public MorphView(@NonNull Context c) {
        this(c, null);
    }

    public MorphView(@NonNull Context c, @Nullable AttributeSet attrs) {
        super(c, attrs);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, c.getResources().getDisplayMetrics()));
        edge.setColor(ContextCompat.getColor(c, R.color.hairline));
    }

    /** Glide to {@code to} (in this view's coordinates) with the given corner radius and colour. */
    public void morphTo(@NonNull RectF to, float r, int c, boolean animate) {
        if (anim != null) anim.cancel();
        if (!animate || !ValueAnimator.areAnimatorsEnabled()) {
            cur.set(to);
            radius = r;
            color = c;
            has = true;
            invalidate();
            return;
        }
        RectF from = has ? new RectF(cur) : new RectF(to.centerX(), to.centerY(), to.centerX(), to.centerY());
        float r0 = has ? radius : 0f;
        int c0 = has ? color : c;
        has = true;
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(760);
        anim.setInterpolator(new TourActivity.Spring(7f));            // lands with a soft settle
        anim.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            cur.set(lerp(from.left, to.left, t), lerp(from.top, to.top, t),
                    lerp(from.right, to.right, t), lerp(from.bottom, to.bottom, t));
            radius = Math.min(lerp(r0, r, t), Math.min(cur.width(), cur.height()) / 2f);
            color = (int) argb.evaluate(t, c0, c);
            invalidate();
        });
        anim.start();
    }

    public boolean isMorphing() {
        return anim != null && anim.isRunning();
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (!has || cur.isEmpty()) return;
        fill.setColor(color);
        canvas.drawRoundRect(cur, radius, radius, fill);
        canvas.drawRoundRect(cur, radius, radius, edge);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (anim != null) anim.cancel();
        super.onDetachedFromWindow();
    }
}
