package in.mohammad.ramiz.confess.ui.me;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.ui.util.Motion;

/**
 * The confession graph (Me page and profiles) — GitHub-style: one column per week (Monday on top),
 * one cell per day, deeper the more you confessed, in the owner's GraphStyle (palette + shape).
 * Month names above, M/W/F on the left,
 * today outlined. Shows as many recent weeks as fit the width; tap a day to hear about it.
 * Columns ease in left to right the first time data arrives.
 */
public class ConfessionGraphView extends View {

    public interface OnDayTap {
        void onDay(@NonNull LocalDate day, int count);
    }

    private final float dp = getResources().getDisplayMetrics().density;
    private final float gap = 3 * dp, minCell = 9 * dp, maxCell = 15 * dp;
    private final float labelW = 14 * dp, monthH = 16 * dp;
    private final float ringPad = 2.5f * dp;               // room for today's outline at the edges

    private final Paint cellPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Path path = new Path();
    @NonNull private GraphStyle style = GraphStyle.DEFAULT;

    @Nullable private LocalDate start;
    private int[] days = new int[0];
    private int cols, shown, first;
    private float cell;
    private int selected = -1;
    private float progress = 1f;
    private boolean entered;
    @Nullable private OnDayTap listener;
    private final String[] weekdayLabels;

    public ConfessionGraphView(Context c) { this(c, null); }

    public ConfessionGraphView(Context c, @Nullable AttributeSet a) {
        super(c, a);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(1.5f * dp);
        textPaint.setColor(ContextCompat.getColor(c, R.color.text_muted));
        textPaint.setTextSize(10 * getResources().getDisplayMetrics().scaledDensity);
        try {
            textPaint.setTypeface(ResourcesCompat.getFont(c, R.font.manrope));
        } catch (Exception ignored) { }
        weekdayLabels = new String[]{c.getString(R.string.graph_mon), c.getString(R.string.graph_wed),
                c.getString(R.string.graph_fri)};
        setClickable(true);
    }

    public void setGraphStyle(@NonNull GraphStyle s) {
        if (s.equals(style)) return;
        style = s;
        invalidate();
    }

    @NonNull
    public GraphStyle getGraphStyle() {
        return style;
    }

    public void setOnDayTap(@Nullable OnDayTap l) {
        listener = l;
    }

    /** days[i] = confessions on start + i (start is a Monday). */
    public void setData(@NonNull LocalDate start, @NonNull int[] days) {
        this.start = start;
        this.days = days;
        cols = (days.length + 6) / 7;
        selected = -1;
        requestLayout();
        invalidate();
        if (!entered && Motion.enabled() && days.length > 0) {
            entered = true;
            ValueAnimator va = ValueAnimator.ofFloat(0f, 1f).setDuration(700);
            va.setInterpolator(new DecelerateInterpolator());
            va.addUpdateListener(a -> { progress = (float) a.getAnimatedValue(); invalidate(); });
            va.start();
        }
    }

    static int level(int n) {
        return n <= 0 ? 0 : n == 1 ? 1 : n == 2 ? 2 : n <= 4 ? 3 : 4;
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        float avail = w - getPaddingLeft() - getPaddingRight() - labelW - 2 * ringPad;
        int c = Math.max(1, cols);
        shown = Math.max(1, Math.min(c, (int) ((avail + gap) / (minCell + gap))));
        cell = Math.min(maxCell, (avail - gap * (shown - 1)) / shown);
        first = c - shown;
        int h = Math.round(getPaddingTop() + monthH + 7 * cell + 6 * gap + ringPad + getPaddingBottom());
        setMeasuredDimension(w, h);
    }

    private float colX(int col) {
        // Right-aligned: the newest week sits at the right edge, like GitHub.
        float used = shown * cell + (shown - 1) * gap;
        float right = getWidth() - getPaddingRight() - ringPad;
        return right - used + (col - first) * (cell + gap);
    }

    private float rowY(int row) {
        return getPaddingTop() + monthH + row * (cell + gap);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (start == null || days.length == 0) return;
        int today = days.length - 1;

        // Month names over the first column of each month (skipped when they'd collide).
        float lastLabelEnd = -1;
        for (int col = first; col < cols; col++) {
            LocalDate monday = start.plusDays(col * 7L);
            LocalDate prev = monday.minusDays(7);
            if (col != first && monday.getMonthValue() == prev.getMonthValue()) continue;
            String m = monday.getMonth().getDisplayName(TextStyle.SHORT, Locale.getDefault());
            float x = colX(col);
            if (x < lastLabelEnd + 4 * dp) continue;
            canvas.drawText(m, x, getPaddingTop() + monthH - 5 * dp, textPaint);
            lastLabelEnd = x + textPaint.measureText(m);
        }
        // M · W · F
        int[] rows = {0, 2, 4};
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        for (int i = 0; i < 3; i++) {
            float cy = rowY(rows[i]) + cell / 2f - (fm.ascent + fm.descent) / 2f;
            canvas.drawText(weekdayLabels[i], colX(first) - labelW, cy, textPaint);
        }

        for (int col = first; col < cols; col++) {
            float x = colX(col);
            // Ease-in: each column fades in a little after the one to its left.
            float t = Math.max(0f, Math.min(1f, progress * (shown + 5) - (col - first)));
            for (int row = 0; row < 7; row++) {
                int i = col * 7 + row;
                if (i >= days.length) break;                       // the rest of this week hasn't happened
                float y = rowY(row);
                r.set(x, y, x + cell, y + cell);
                int color = style.color(level(days[i]));
                cellPaint.setColor(color);
                cellPaint.setAlpha(Math.round(((color >>> 24) & 0xFF) * t));
                drawCell(canvas, r, cellPaint, false);
                if (i == today || i == selected) {
                    ringPaint.setColor(i == selected ? ContextCompat.getColor(getContext(), R.color.text_primary)
                            : style.color(4));
                    ringPaint.setAlpha(Math.round(255 * t));
                    r.inset(-1.5f * dp, -1.5f * dp);
                    drawCell(canvas, r, ringPaint, true);
                }
            }
        }
    }

    /** One cell in the style's shape, filling (or outlining) the box. */
    private void drawCell(@NonNull Canvas c, @NonNull RectF box, @NonNull Paint p, boolean ring) {
        drawShape(c, box, p, style.shape, ring, path);
    }

    /** A cell of {@code shape} in {@code box} — also used by the style sheet's swatches. */
    static void drawShape(@NonNull Canvas c, @NonNull RectF box, @NonNull Paint p, @NonNull GraphStyle.Shape shape,
                          boolean ring, @NonNull Path path) {
        float w = box.width();
        switch (shape) {
            case SQUARE:
                c.drawRoundRect(box, w * 0.08f, w * 0.08f, p);
                break;
            case CIRCLE:
                c.drawOval(box, p);
                break;
            case DIAMOND: {
                float cx = box.centerX(), cy = box.centerY(), h = w / 2f * (ring ? 1.08f : 1.12f);
                path.reset();
                path.moveTo(cx, cy - h);
                path.lineTo(cx + h, cy);
                path.lineTo(cx, cy + h);
                path.lineTo(cx - h, cy);
                path.close();
                c.drawPath(path, p);
                break;
            }
            case HEART: {
                // Two lobes and a point, sized to the box.
                float l = box.left, t = box.top, s = w;
                path.reset();
                path.moveTo(l + s * 0.5f, t + s * 0.92f);
                path.cubicTo(l + s * 0.08f, t + s * 0.62f, l - s * 0.02f, t + s * 0.30f, l + s * 0.18f, t + s * 0.14f);
                path.cubicTo(l + s * 0.32f, t + s * 0.02f, l + s * 0.46f, t + s * 0.08f, l + s * 0.5f, t + s * 0.24f);
                path.cubicTo(l + s * 0.54f, t + s * 0.08f, l + s * 0.68f, t + s * 0.02f, l + s * 0.82f, t + s * 0.14f);
                path.cubicTo(l + s * 1.02f, t + s * 0.30f, l + s * 0.92f, t + s * 0.62f, l + s * 0.5f, t + s * 0.92f);
                path.close();
                c.drawPath(path, p);
                break;
            }
            default:
                c.drawRoundRect(box, w * 0.28f, w * 0.28f, p);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN) return true;
        if (e.getAction() != MotionEvent.ACTION_UP || start == null) return super.onTouchEvent(e);
        int col = first + (int) Math.floor((e.getX() - colX(first)) / (cell + gap));
        int row = (int) Math.floor((e.getY() - rowY(0)) / (cell + gap));
        if (col < first || col >= cols || row < 0 || row > 6) return true;
        int i = col * 7 + row;
        if (i >= days.length) return true;
        selected = i;
        invalidate();
        performClick();
        if (listener != null) listener.onDay(start.plusDays(i), days[i]);
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
