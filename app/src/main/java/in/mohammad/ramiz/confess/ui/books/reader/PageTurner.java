package in.mohammad.ramiz.confess.ui.books.reader;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.PathInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * The reader's pages, turned like paper.
 *
 * Holds the page on screen and its two neighbours (views made by a RecyclerView.Adapter, so
 * the reader's adapter is reused as is) and turns between them under the finger:
 *
 *   CURL    — the corner you hold lifts and folds over: the back of the sheet shows the print
 *             faintly through, the page underneath is shaded along the fold, the flap casts a
 *             shadow. Grab low for the bottom corner, high for the top one.
 *   SLIDE   — the page slides off to the left and uncovers the next one (the previous page
 *             slides back in over this one).
 *   INSTANT — a quick fade, no motion.
 *
 * The page follows the finger, a flick finishes the turn, a small drag springs back, and a new
 * touch or tap during a turn finishes it at once, so pages can be flipped as fast as you tap.
 * Pages that aren't on screen are laid out off to the side, so only the page you see gets
 * touches (the end-of-chapter page has buttons); they're drawn in place while a turn shows them.
 */
public final class PageTurner extends ViewGroup {

    public static final int CURL = 0, SLIDE = 1, INSTANT = 2;

    public interface Listener {
        /** A turn landed on a new position. (Not called for {@link #setPosition}.) */
        void onSettled(int position);

        /** The reader started dragging a page. */
        void onDragStart();

        /** A tap no page view used. */
        void onTap(float x, float y);
    }

    /** A page view made by the adapter and the position it shows (-1: not in use). */
    private static final class Slot {
        final RecyclerView.ViewHolder holder;
        final int type;
        int pos = -1;

        Slot(@NonNull RecyclerView.ViewHolder holder, int type) {
            this.holder = holder;
            this.type = type;
        }

        View view() {
            return holder.itemView;
        }
    }

    @Nullable private RecyclerView.Adapter<RecyclerView.ViewHolder> adapter;
    @Nullable private Listener listener;
    private final List<Slot> slots = new ArrayList<>();
    private int position = 0;
    private int style = CURL;
    @androidx.annotation.ColorInt private int paper = Color.WHITE;

    // The turn in progress. dir +1: this page turns away to show the next; -1: the previous page
    // turns back over this one. f: how far the turning page is turned, 0 flat … 1 gone.
    private int dir = 0;
    private float f = 0f;
    private float cornerY, baseY;
    @Nullable private ValueAnimator anim;

    // Touch
    private final int slop, flingV;
    private float downX, downY;
    private long downTime;
    private boolean dragging, moved;
    @Nullable private VelocityTracker vt;

    // Drawing
    private final Path rect = new Path(), cut = new Path(), flap = new Path();
    private final Matrix reflect = new Matrix();
    private final float[] m9 = new float[9];
    private final Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint flapShadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float edgeW;

    private final RecyclerView.AdapterDataObserver observer = new RecyclerView.AdapterDataObserver() {
        @Override
        public void onChanged() {
            rebind();
        }

        @Override
        public void onItemRangeChanged(int start, int count) {
            rebind();
        }

        @Override
        public void onItemRangeInserted(int start, int count) {
            rebind();
        }

        @Override
        public void onItemRangeRemoved(int start, int count) {
            rebind();
        }

        @Override
        public void onItemRangeMoved(int from, int to, int count) {
            rebind();
        }
    };

    public PageTurner(@NonNull Context context) {
        this(context, null);
    }

    public PageTurner(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        float d = getResources().getDisplayMetrics().density;
        slop = ViewConfiguration.get(context).getScaledTouchSlop();
        flingV = Math.round(450 * d);
        edgeW = 24 * d;
        flapShadow.setColor(paper);
        flapShadow.setShadowLayer(16 * d, 0, 0, 0x55000000);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    // ── Setup ─────────────────────────────────────────────────────────────

    public void setAdapter(@NonNull RecyclerView.Adapter<RecyclerView.ViewHolder> a) {
        if (adapter != null) adapter.unregisterAdapterDataObserver(observer);
        removeAllViews();
        slots.clear();
        adapter = a;
        a.registerAdapterDataObserver(observer);
        rebind();
    }

    public void setListener(@Nullable Listener l) {
        listener = l;
    }

    public void setTurnStyle(int s) {
        style = s == SLIDE || s == INSTANT ? s : CURL;
        invalidate();
    }

    /** The page colour: the back of a curled sheet is drawn in it. */
    public void setPaperColor(@androidx.annotation.ColorInt int color) {
        paper = color;
        flapShadow.setColor(color);
        invalidate();
    }

    public int position() {
        return position;
    }

    public boolean isTurning() {
        return dragging || anim != null;
    }

    /** Jump to a position without a turn (opening a chapter, the scrubber). */
    public void setPosition(int p) {
        stopAnim();
        dir = 0;
        f = 0;
        dragging = false;
        position = p;
        rebind();
    }

    /** Turn one page (a tap, a key, a button). {@code y} picks the corner: high = top corner. */
    public boolean turn(int d, float y) {
        if (dragging) return false;
        if (anim != null) anim.end();                       // flipping fast: finish the last turn first
        if (!canTurn(d) || getWidth() == 0) return false;
        float h = getHeight();
        dir = d;
        cornerY = y < h * 0.33f ? 0 : h;
        baseY = cornerY;
        f = d > 0 ? 0f : 1f;
        animateTo(true, true);
        return true;
    }

    private boolean canTurn(int d) {
        int n = adapter == null ? 0 : adapter.getItemCount();
        int t = position + d;
        return t >= 0 && t < n;
    }

    // ── Pages ─────────────────────────────────────────────────────────────

    /** Bind the page on screen and its neighbours, reusing views that already show them. */
    private void rebind() {
        RecyclerView.Adapter<RecyclerView.ViewHolder> a = adapter;
        int n = a == null ? 0 : a.getItemCount();
        if (n == 0) {
            for (Slot s : slots) s.pos = -1;
            position = 0;
            invalidate();
            return;
        }
        position = Math.max(0, Math.min(position, n - 1));
        int[] want = {position, position + 1, position - 1};
        Slot[] chosen = new Slot[want.length];
        List<Slot> free = new ArrayList<>(slots);
        for (int i = 0; i < want.length; i++) {
            int p = want[i];
            if (p < 0 || p >= n) continue;
            int type = a.getItemViewType(p);
            for (Slot s : free) {
                if (s.pos == p && s.type == type) {
                    chosen[i] = s;
                    break;
                }
            }
            if (chosen[i] != null) free.remove(chosen[i]);
        }
        for (int i = 0; i < want.length; i++) {
            int p = want[i];
            if (p < 0 || p >= n || chosen[i] != null) continue;
            int type = a.getItemViewType(p);
            Slot pick = null;
            for (Slot s : free) {
                if (s.type == type) {
                    pick = s;
                    break;
                }
            }
            if (pick == null) {
                pick = new Slot(a.onCreateViewHolder(this, type), type);
                slots.add(pick);
                addView(pick.view());
            } else {
                free.remove(pick);
            }
            chosen[i] = pick;
        }
        for (Slot s : free) s.pos = -1;
        for (int i = 0; i < want.length; i++) {
            if (chosen[i] == null) continue;
            chosen[i].pos = want[i];
            a.onBindViewHolder(chosen[i].holder, want[i]);
        }
        place();
        invalidate();
    }

    @Nullable
    private View viewAt(int p) {
        for (Slot s : slots) if (s.pos == p && p >= 0) return s.view();
        return null;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec), h = MeasureSpec.getSize(heightSpec);
        setMeasuredDimension(w, h);
        int ws = MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY);
        int hs = MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY);
        for (int i = 0; i < getChildCount(); i++) getChildAt(i).measure(ws, hs);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        place();
    }

    /** The page on screen at 0,0; the rest off to the side (no touches, drawn only during a turn). */
    private void place() {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        int ws = MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY);
        int hs = MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY);
        int off = 2 * w + 64;
        for (Slot s : slots) {
            View v = s.view();
            v.measure(ws, hs);
            int x = s.pos == position ? 0 : off;
            v.layout(x, 0, x + w, h);
        }
    }

    // ── Touch ─────────────────────────────────────────────────────────────

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        return track(e);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        track(e);
        return true;
    }

    private boolean track(@NonNull MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (anim != null) anim.end();               // a page still moving: let it land
                downX = e.getX();
                downY = e.getY();
                downTime = e.getEventTime();
                dragging = false;
                moved = false;
                if (vt == null) vt = VelocityTracker.obtain();
                vt.clear();
                vt.addMovement(e);
                return false;
            case MotionEvent.ACTION_MOVE: {
                if (vt != null) vt.addMovement(e);
                float dx = e.getX() - downX, dy = e.getY() - downY;
                if (!dragging) {
                    if (Math.hypot(dx, dy) > slop) moved = true;
                    if (Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) * 1.2f) {
                        int d = dx < 0 ? 1 : -1;
                        if (canTurn(d) && getWidth() > 0) {
                            dragging = true;
                            dir = d;
                            float h = getHeight();
                            cornerY = downY < h * 0.33f ? 0 : h;
                            downX = e.getX();                       // the turn starts from here: no jump
                            downY = e.getY();
                            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                            if (listener != null) listener.onDragStart();
                        }
                    }
                }
                if (dragging) {
                    drag(e.getX(), e.getY());
                    return true;
                }
                return false;
            }
            case MotionEvent.ACTION_UP:
                if (dragging) {
                    float vx = 0;
                    if (vt != null) {
                        vt.addMovement(e);
                        vt.computeCurrentVelocity(1000);
                        vx = vt.getXVelocity();
                    }
                    dragging = false;
                    release(vx);
                    return true;
                }
                if (!moved && e.getEventTime() - downTime < ViewConfiguration.getLongPressTimeout()
                        && listener != null) {
                    listener.onTap(e.getX(), e.getY());
                }
                return false;
            case MotionEvent.ACTION_CANCEL:
                if (dragging) {
                    dragging = false;
                    release(0);
                }
                return false;
            default:
                return dragging;
        }
    }

    private void drag(float x, float y) {
        float w = getWidth(), h = getHeight();
        if (w == 0) return;
        float p = (x - downX) / w;
        if (style == INSTANT) {
            f = dir > 0 ? 0f : 1f;                                   // nothing moves until it's let go
        } else {
            float k = style == CURL ? 1.5f : 1f;                     // a curl needs twice the travel
            f = dir > 0 ? clamp01(-p * k) : clamp01(1f - p * k);
        }
        float by = cornerY + (y - downY) * 0.8f;
        baseY = cornerY > 0 ? clamp(by, h * 0.45f, h) : clamp(by, 0, h * 0.55f);
        invalidate();
    }

    private void release(float vx) {
        boolean commit;
        if (style == INSTANT) {
            commit = dir > 0 ? vx < flingV : vx > -flingV;           // any swipe that isn't flicked back
        } else if (dir > 0) {
            commit = vx < -flingV || (f > 0.28f && vx < flingV);
        } else {
            commit = vx > flingV || (f < 0.72f && vx > -flingV);
        }
        animateTo(commit, false);
    }

    private void animateTo(boolean commit, boolean whole) {
        stopAnim();
        final float target = commit == (dir > 0) ? 1f : 0f;
        final float f0 = f, b0 = baseY;
        long full = style == CURL ? 520 : style == SLIDE ? 360 : 170;
        long dur = whole || style == INSTANT ? full
                : Math.max(120, Math.round(full * (0.3f + 0.7f * Math.abs(target - f0))));
        final boolean[] cancelled = {false};
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(dur);
        a.setInterpolator(whole ? new PathInterpolator(0.35f, 0f, 0.2f, 1f) : new DecelerateInterpolator(1.5f));
        a.addUpdateListener(v -> {
            float u = (float) v.getAnimatedValue();
            f = f0 + (target - f0) * u;
            baseY = b0 + (cornerY - b0) * u;
            invalidate();
        });
        a.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled[0] = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (anim == animation) anim = null;
                if (!cancelled[0]) finish(commit);
            }
        });
        anim = a;
        a.start();
    }

    private void stopAnim() {
        ValueAnimator a = anim;
        anim = null;
        if (a != null) a.cancel();
    }

    private void finish(boolean commit) {
        int d = dir;
        dir = 0;
        f = 0;
        if (commit && d != 0) {
            position += d;
            rebind();
            if (listener != null) listener.onSettled(position);
        } else {
            invalidate();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        stopAnim();
        if (vt != null) {
            vt.recycle();
            vt = null;
        }
        super.onDetachedFromWindow();
    }

    // ── Drawing ───────────────────────────────────────────────────────────

    @Override
    protected void dispatchDraw(@NonNull Canvas c) {
        View cur = viewAt(position);
        View turning = dir > 0 ? cur : dir < 0 ? viewAt(position - 1) : null;
        View under = dir > 0 ? viewAt(position + 1) : dir < 0 ? cur : null;
        if (turning == null || under == null) {
            if (cur != null) drawPage(c, cur);
            return;
        }
        if (style == SLIDE) drawSlide(c, turning, under);
        else if (style == INSTANT) drawFade(c, turning, under);
        else drawCurl(c, turning, under);
    }

    /** Draw a page at 0,0 wherever it's laid out. */
    private void drawPage(@NonNull Canvas c, @NonNull View v) {
        c.save();
        c.translate(-v.getLeft(), -v.getTop());
        drawChild(c, v, getDrawingTime());
        c.restore();
    }

    private void drawSlide(@NonNull Canvas c, @NonNull View turning, @NonNull View under) {
        float w = getWidth(), h = getHeight();
        drawPage(c, under);
        c.drawColor(Color.argb(Math.round(56 * (1 - f)), 0, 0, 0));      // in the shadow until uncovered
        float x = -f * w, edge = x + w;
        shade.setShader(new LinearGradient(edge, 0, edge + edgeW, 0,
                Color.argb(Math.round(80 * (1 - f)), 0, 0, 0), 0, Shader.TileMode.CLAMP));
        c.drawRect(edge, 0, edge + edgeW, h, shade);
        c.save();
        c.translate(x, 0);
        drawPage(c, turning);
        c.restore();
    }

    private void drawFade(@NonNull Canvas c, @NonNull View turning, @NonNull View under) {
        drawPage(c, under);
        int alpha = Math.round(255 * (1 - f));
        if (alpha <= 0) return;
        c.saveLayerAlpha(0, 0, getWidth(), getHeight(), alpha);
        drawPage(c, turning);
        c.restore();
    }

    /**
     * The page curl. The sheet is fixed along its left edge; the corner C is lifted to T. The fold
     * is the line half-way between them (perpendicular to CT): beyond it the page is gone and the
     * next one shows; the part that was there is mirrored across the fold, back side up.
     */
    private void drawCurl(@NonNull Canvas c, @NonNull View turning, @NonNull View under) {
        float w = getWidth(), h = getHeight();
        if (f <= 0.001f) {
            drawPage(c, turning);
            return;
        }
        if (f >= 0.999f) {
            drawPage(c, under);
            return;
        }
        float cx = w, cy = cornerY;
        boolean bottom = cornerY > 0;
        float tx = w - 2f * w * f;
        float lift = h * 0.06f * (float) Math.sin(Math.PI * f);      // the corner rises mid-turn
        float ty = bottom ? Math.min(cy, baseY - lift) : Math.max(cy, baseY + lift);
        // The spine holds the sheet: the corner can't get further than a page width from it.
        float sx = 0, sy = cy, ex = tx - sx, ey = ty - sy;
        float reach = (float) Math.hypot(ex, ey);
        if (reach > w) {
            tx = sx + ex * w / reach;
            ty = sy + ey * w / reach;
        }
        float vx = cx - tx, vy = cy - ty;
        float len = (float) Math.hypot(vx, vy);
        if (len < 1f) {
            drawPage(c, turning);
            return;
        }
        float nx = vx / len, ny = vy / len;                         // across the fold, toward the corner
        float px = -ny, py = nx;                                    // along the fold
        float mx = (tx + cx) / 2f, my = (ty + cy) / 2f;             // a point on the fold
        float big = 3f * (w + h);

        // Everything beyond the fold, on the corner's side.
        cut.rewind();
        cut.moveTo(mx + px * big, my + py * big);
        cut.lineTo(mx - px * big, my - py * big);
        cut.lineTo(mx - px * big + nx * big, my - py * big + ny * big);
        cut.lineTo(mx + px * big + nx * big, my + py * big + ny * big);
        cut.close();

        // Mirror across the fold: p' = p − 2((p − m)·n)n.
        float k = 2f * (mx * nx + my * ny);
        m9[Matrix.MSCALE_X] = 1 - 2 * nx * nx;
        m9[Matrix.MSKEW_X] = -2 * nx * ny;
        m9[Matrix.MTRANS_X] = k * nx;
        m9[Matrix.MSKEW_Y] = -2 * nx * ny;
        m9[Matrix.MSCALE_Y] = 1 - 2 * ny * ny;
        m9[Matrix.MTRANS_Y] = k * ny;
        m9[Matrix.MPERSP_0] = 0;
        m9[Matrix.MPERSP_1] = 0;
        m9[Matrix.MPERSP_2] = 1;
        reflect.setValues(m9);

        // 1. The next page where the corner lifted away, in the fold's shadow.
        drawPage(c, under);
        c.save();
        c.clipPath(cut);
        float sw = Math.min(len * 0.5f, w * 0.3f) + 1f;
        shade.setShader(new LinearGradient(mx, my, mx + nx * sw, my + ny * sw,
                0x55000000, 0, Shader.TileMode.CLAMP));
        c.drawPaint(shade);
        c.restore();

        // 2. This page, short of its corner.
        c.save();
        c.clipOutPath(cut);
        drawPage(c, turning);

        // 3. The folded corner: its shadow on the page, then the back of the sheet with the print
        //    showing through faintly (mirrored), darker into the fold.
        rect.rewind();
        rect.addRect(0, 0, w, h, Path.Direction.CW);
        rect.transform(reflect, flap);
        c.drawPath(flap, flapShadow);
        c.concat(reflect);
        c.clipRect(0, 0, w, h);
        drawPage(c, turning);
        c.drawColor(ColorUtils.setAlphaComponent(paper, 0xE6));
        shade.setShader(new LinearGradient(mx, my, cx, cy,
                new int[]{0x44000000, 0x0A000000, 0x00000000, 0x1E000000},
                new float[]{0f, 0.2f, 0.62f, 1f}, Shader.TileMode.CLAMP));
        c.drawPaint(shade);
        c.restore();
    }

    // ── Accessibility ─────────────────────────────────────────────────────

    @Override
    public CharSequence getAccessibilityClassName() {
        return "androidx.viewpager.widget.ViewPager";
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setScrollable(true);
        if (canTurn(1)) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
        if (canTurn(-1)) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
    }

    @Override
    public boolean performAccessibilityAction(int action, @Nullable Bundle args) {
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) return turn(1, getHeight());
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) return turn(-1, getHeight());
        return super.performAccessibilityAction(action, args);
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
