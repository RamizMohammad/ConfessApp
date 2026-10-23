package in.mohammad.ramiz.confess.ui.components;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.PathInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;

import in.mohammad.ramiz.confess.R;

/**
 * A segmented choice whose amber pill glides to the option you pick instead of jumping.
 *
 * The buttons themselves are bare (Widget.Confess.Button.Segment: no fill, no outline); this
 * group draws one rounded hairline track around them all and an amber pill inside it, and
 * slides the pill from the old choice to the new one. Each label
 * fades from grey to ink by how much of the pill is under it, so the text follows the glide.
 * With the phone's animations off it simply moves.
 *
 * With app:selectionRequired="false" nothing may be chosen (the composer's letter · poll · GIF):
 * tapping the chosen option again lets the pill fade away, and the next pick fades it in there.
 */
public class GlideToggleGroup extends MaterialButtonToggleGroup {

    private static final long GLIDE_MS = 300;

    private final Paint pill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF trackRect = new RectF();
    private final RectF now = new RectF();
    private final RectF from = new RectF();
    private final RectF to = new RectF();
    private final float inset;
    private final int on, off;
    @Nullable private ValueAnimator glide;
    /** 0–1: the pill fades in / out when there's nothing chosen. */
    private float pillAlpha = 1f;

    public GlideToggleGroup(@NonNull Context context) {
        this(context, null);
    }

    public GlideToggleGroup(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, com.google.android.material.R.attr.materialButtonToggleGroupStyle);
    }

    public GlideToggleGroup(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setWillNotDraw(false);                               // onDraw runs before the buttons draw
        pill.setColor(ContextCompat.getColor(context, R.color.amber));
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(getResources().getDimension(R.dimen.hairline));
        track.setColor(ContextCompat.getColor(context, R.color.hairline));
        inset = getResources().getDisplayMetrics().density * 3;
        on = ContextCompat.getColor(context, R.color.paper_ink);
        off = ContextCompat.getColor(context, R.color.text_secondary);
        addOnButtonCheckedListener((group, id, checked) -> {
            if (checked) moveTo(id, isLaidOut());
            else if (getCheckedButtonId() == View.NO_ID) fadeAway(isLaidOut());
        });
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (glide != null && glide.isRunning()) return;
        if (getCheckedButtonId() == View.NO_ID) {
            now.setEmpty();
            tintLabels();
        } else {
            moveTo(getCheckedButtonId(), false);
        }
    }

    /** Nothing chosen any more: the pill fades out where it is. */
    private void fadeAway(boolean animate) {
        if (glide != null) glide.cancel();
        if (now.isEmpty()) return;
        if (!animate || !ValueAnimator.areAnimatorsEnabled()) {
            now.setEmpty();
            pillAlpha = 1f;
            tintLabels();
            invalidate();
            return;
        }
        glide = ValueAnimator.ofFloat(1f, 0f);
        glide.setDuration(180);
        glide.addUpdateListener(a -> {
            pillAlpha = (float) a.getAnimatedValue();
            tintLabels();
            invalidate();
        });
        glide.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                if (pillAlpha <= 0.01f) {
                    now.setEmpty();
                    pillAlpha = 1f;
                    tintLabels();
                    invalidate();
                }
            }
        });
        glide.start();
    }

    private void moveTo(int id, boolean animate) {
        View target = id == View.NO_ID ? null : findViewById(id);
        if (target == null || target.getWidth() == 0) return;
        to.set(target.getLeft() + inset, target.getTop() + inset, target.getRight() - inset, target.getBottom() - inset);
        if (glide != null) glide.cancel();
        if (now.isEmpty() && animate && ValueAnimator.areAnimatorsEnabled()) {
            // Nothing was chosen: the pill fades in on the new choice, growing a little.
            now.set(to);
            glide = ValueAnimator.ofFloat(0f, 1f);
            glide.setDuration(200);
            glide.addUpdateListener(a -> {
                float f = (float) a.getAnimatedValue();
                pillAlpha = f;
                float inset = (1f - f) * to.height() * 0.12f;
                now.set(to.left + inset, to.top + inset, to.right - inset, to.bottom - inset);
                tintLabels();
                invalidate();
            });
            glide.start();
            return;
        }
        pillAlpha = 1f;
        if (!animate || now.isEmpty() || !ValueAnimator.areAnimatorsEnabled()) {
            now.set(to);
            tintLabels();
            invalidate();
            return;
        }
        from.set(now);
        glide = ValueAnimator.ofFloat(0f, 1f);
        glide.setDuration(GLIDE_MS);
        glide.setInterpolator(new PathInterpolator(0.3f, 0f, 0.1f, 1f));
        glide.addUpdateListener(a -> {
            float f = (float) a.getAnimatedValue();
            // A little stretch on the way: the leading edge runs ahead of the trailing one.
            float lead = Math.min(1f, f * 1.25f);
            boolean right = to.centerX() >= from.centerX();
            now.left = lerp(from.left, to.left, right ? f : lead);
            now.right = lerp(from.right, to.right, right ? lead : f);
            now.top = lerp(from.top, to.top, f);
            now.bottom = lerp(from.bottom, to.bottom, f);
            tintLabels();
            invalidate();
        });
        glide.start();
    }

    /** Ink where the pill is, grey where it isn't — blended by overlap while it glides. */
    private void tintLabels() {
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (!(v instanceof MaterialButton) || v.getWidth() == 0) continue;
            float overlap = Math.max(0f, Math.min(now.right, v.getRight()) - Math.max(now.left, v.getLeft()));
            float share = now.isEmpty() ? 0f : Math.min(1f, overlap / Math.max(1f, now.width())) * pillAlpha;
            ((MaterialButton) v).setTextColor(ColorUtils.blendARGB(off, on, share));
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        float half = track.getStrokeWidth() / 2f;
        trackRect.set(half, half, getWidth() - half, getHeight() - half);
        float tr = trackRect.height() / 2f;
        canvas.drawRoundRect(trackRect, tr, tr, track);
        if (now.isEmpty()) return;
        float r = now.height() / 2f;
        pill.setAlpha(Math.round(255 * pillAlpha));
        canvas.drawRoundRect(now, r, r, pill);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (glide != null) glide.cancel();
        super.onDetachedFromWindow();
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
