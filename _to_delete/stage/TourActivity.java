package in.mohammad.ramiz.confess.ui.tour;

import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.ColorUtils;

import java.util.ArrayList;
import java.util.List;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.core.Haptics;
import in.mohammad.ramiz.confess.databinding.ActivityTourBinding;
import in.mohammad.ramiz.confess.ui.base.BaseActivity;
import in.mohammad.ramiz.confess.ui.util.EdgeInsets;

/**
 * The feature tour.
 *
 * Words sit top-left: a NEW tag, the title (its words rise in one after another), a line, and
 * "Find it" — the path to the feature in the app, lighting up step by step as if tapped through.
 * Below them, a live sample of the feature inside one shape that morphs from page to page.
 *
 * Moving between pages is physical: the page follows your finger (words, sample, shape and a
 * soft glow each at their own depth), and lets go on a spring. Pages arrive layered — the
 * sample comes into focus out of a blur while the words slide in one line at a time.
 * Phone animations off → everything simply appears.
 *
 * MODE_FULL: every page (new installs, Settings → Take the tour).
 * MODE_NEW: what's new since the version last seen, with "See the full tour" at the end.
 */
public class TourActivity extends BaseActivity {

    public static final String EXTRA_MODE = "mode";
    public static final int MODE_FULL = 0, MODE_NEW = 1;

    private static final String STATE_INDEX = "index";

    /** A damped spring: 1 − e^(−7t)·cos(ωt). Bigger ω = a touch more bounce. */
    static final class Spring implements TimeInterpolator {
        private final float w;
        Spring(float w) { this.w = w; }
        @Override public float getInterpolation(float t) {
            if (t >= 1f) return 1f;
            return (float) (1 - Math.exp(-7 * t) * Math.cos(w * t));
        }
    }

    private ActivityTourBinding b;
    private List<Tour.Page> pages;
    private int index = 0;
    private int mode;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<ValueAnimator> running = new ArrayList<>();
    private ValueAnimator floatAnim;
    @Nullable private TourDemos.Demo demo;

    // Dragging between pages.
    private float downX, downY, dragX;
    private boolean dragging, transitioning, trackable;
    @Nullable private VelocityTracker velocity;
    private int slop;
    private int tone;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityTourBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        EdgeInsets.padTop(b.topBar);
        EdgeInsets.padBottom(b.bottom, false);
        slop = ViewConfiguration.get(this).getScaledTouchSlop() * 2;

        mode = getIntent().getIntExtra(EXTRA_MODE, MODE_FULL);
        pages = mode == MODE_NEW ? Tour.whatsNew(Tour.seenVersion(this)) : Tour.full();
        if (pages.isEmpty()) pages = Tour.full();
        if (savedInstanceState != null) index = Math.min(savedInstanceState.getInt(STATE_INDEX, 0), pages.size() - 1);

        b.skip.setOnClickListener(v -> finishTour());
        b.next.setOnClickListener(v -> {
            Haptics.tick(this);
            if (index < pages.size() - 1) go(index + 1);
            else finishTour();
        });
        b.fullTour.setOnClickListener(v -> {
            Haptics.tick(this);
            mode = MODE_FULL;
            pages = Tour.full();
            buildDots();
            go(0);
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (index > 0) go(index - 1); else finishTour();
            }
        });

        // Samples change size as they play (the bar measures, bursts fan out): the frame keeps hugging them.
        b.demo.addOnLayoutChangeListener((v, l, t, r, bt, ol, ot, or, ob) -> {
            if (v.getVisibility() != View.VISIBLE || (r - l == or - ol && bt - t == ob - ot)) return;
            morphAround(b.demo, true, tone, b.morph.isMorphing());
        });

        buildDots();
        show(index, 0);
        startFloating();
    }

    // ── Moving between pages ─────────────────────────────────────────────────

    private void go(int to) {
        if (to < 0 || to >= pages.size()) { settle(); return; }
        if (to == index) { show(index, 0); return; }
        int dir = to > index ? 1 : -1;
        index = to;
        if (!motion()) { show(index, 0); return; }
        transitioning = true;
        paintDots();

        // Out: words slide away, the sample drifts off and blurs; the shape stays to morph.
        float w = b.getRoot().getWidth();
        View content = content();
        b.textBlock.animate().cancel();
        b.textBlock.animate().translationX(-dir * w * 0.3f).alpha(0f).setDuration(190)
                .setInterpolator(new AccelerateInterpolator(1.4f)).start();
        content.animate().cancel();
        content.animate().translationX(-dir * w * 0.45f).alpha(0f).scaleX(0.92f).scaleY(0.92f)
                .setDuration(210).setInterpolator(new AccelerateInterpolator(1.4f))
                .withEndAction(() -> { transitioning = false; show(index, dir); }).start();
        blur(content, 0f, dp(14), 210, 0);
        b.morph.animate().translationX(0f).setDuration(210).start();
    }

    /** Back to rest after a drag that didn't turn the page. */
    private void settle() {
        Spring spring = new Spring(9f);
        b.textBlock.animate().translationX(0f).alpha(1f).setDuration(520).setInterpolator(spring).start();
        View content = content();
        content.animate().translationX(0f).scaleX(1f).scaleY(1f).alpha(1f).setDuration(560).setInterpolator(spring).start();
        b.morph.animate().translationX(0f).setDuration(560).setInterpolator(spring).start();
        b.glow.animate().translationX(glowX(index)).setDuration(700).setInterpolator(spring).start();
    }

    /** While dragging, each layer follows the finger at its own depth. */
    private void drag(float dx) {
        float w = Math.max(1, b.getRoot().getWidth());
        boolean edge = (dx < 0 && index == pages.size() - 1) || (dx > 0 && index == 0);
        if (edge) dx *= 0.3f;                                         // rubber band at the ends
        dragX = dx;
        float f = Math.min(1f, Math.abs(dx) / w);
        b.textBlock.setTranslationX(dx * 0.6f);
        b.textBlock.setAlpha(1f - f * 0.9f);
        View content = content();
        content.setTranslationX(dx);
        content.setScaleX(1f - f * 0.08f);
        content.setScaleY(1f - f * 0.08f);
        b.morph.setTranslationX(dx * 0.35f);
        b.glow.setTranslationX(glowX(index) + dx * 0.15f);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (!motionTouchAllowed(ev)) return super.dispatchTouchEvent(ev);
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = ev.getRawX();
                downY = ev.getRawY();
                dragging = false;
                if (velocity != null) velocity.recycle();
                velocity = VelocityTracker.obtain();
                velocity.addMovement(ev);
                break;
            case MotionEvent.ACTION_MOVE: {
                if (velocity != null) velocity.addMovement(ev);
                float dx = ev.getRawX() - downX, dy = ev.getRawY() - downY;
                if (!dragging && !transitioning && Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) * 1.3f) {
                    dragging = true;
                    downX = ev.getRawX() - Math.signum(dx) * 1;               // no jump at the start
                    MotionEvent cancel = MotionEvent.obtain(ev);             // samples don't also get a tap
                    cancel.setAction(MotionEvent.ACTION_CANCEL);
                    super.dispatchTouchEvent(cancel);
                    cancel.recycle();
                }
                if (dragging) {
                    drag(ev.getRawX() - downX);
                    return true;
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragging) {
                    dragging = false;
                    float v = 0;
                    if (velocity != null) {
                        velocity.addMovement(ev);
                        velocity.computeCurrentVelocity(1000);
                        v = velocity.getXVelocity();
                    }
                    float w = b.getRoot().getWidth();
                    int to = dragX < 0 ? index + 1 : index - 1;
                    boolean far = Math.abs(dragX) > w * 0.22f;
                    boolean flung = Math.abs(v) > dp(500) && Math.signum(v) == Math.signum(dragX);
                    if (ev.getActionMasked() == MotionEvent.ACTION_UP && (far || flung) && to >= 0 && to < pages.size()) {
                        Haptics.tick(this);
                        go(to);
                    } else {
                        settle();
                    }
                    return true;
                }
                break;
            default:
                break;
        }
        return super.dispatchTouchEvent(ev);
    }

    /** Drags only start on the page itself, never on the buttons at the bottom. */
    private boolean motionTouchAllowed(MotionEvent ev) {
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
            int[] loc = new int[2];
            b.bottom.getLocationOnScreen(loc);
            trackable = ev.getRawY() < loc[1];
        }
        return trackable;
    }

    // ── A page ───────────────────────────────────────────────────────────────

    /** Lay out page i; {@code dir} = the direction it arrives from (0 = none). */
    private void show(int i, int dir) {
        handler.removeCallbacksAndMessages(null);
        for (ValueAnimator a : running) a.cancel();
        running.clear();
        Tour.Page p = pages.get(i);
        boolean motion = motion();
        String title = getString(p.title);

        b.step.setText(getString(R.string.tour_step, i + 1, pages.size()));
        b.page.setContentDescription(getString(R.string.cd_tour_page, i + 1, pages.size()));
        b.icon.setImageResource(p.icon);
        int size = dp(p.brand ? 72 : 48);
        b.icon.getLayoutParams().width = size;
        b.icon.getLayoutParams().height = size;
        b.icon.requestLayout();
        b.icon.setImageTintList(p.brand ? null : ColorStateList.valueOf(ContextCompat.getColor(this, R.color.amber)));
        b.badge.setVisibility(p.isNew() ? View.VISIBLE : View.GONE);
        b.title.setContentDescription(title);
        b.title.setText(title);
        b.body.setText(p.body);

        // Where to find it.
        int whereRes = TourDemos.where(p.title);
        List<TextView> steps = buildWhere(whereRes == 0 ? null : getResources().getStringArray(whereRes));

        // The live sample, or the icon (with points) on pages without one.
        if (demo != null) demo.stop();
        b.demo.removeAllViews();
        demo = TourDemos.create(this, p.title);
        boolean hasDemo = demo != null;
        if (hasDemo) b.demo.addView(demo.view);
        b.demo.setVisibility(hasDemo ? View.VISIBLE : View.GONE);
        b.iconDisc.setVisibility(hasDemo ? View.GONE : View.VISIBLE);
        b.points.removeAllViews();
        if (!hasDemo) for (int res : p.points) b.points.addView(point(getString(res)));
        View content = content();

        boolean last = i == pages.size() - 1;
        b.next.setText(last ? R.string.tour_done : R.string.tour_next);
        b.next.setIconResource(last ? 0 : R.drawable.ic_arrow_right);
        b.skip.setVisibility(last ? View.INVISIBLE : View.VISIBLE);
        b.fullTour.setVisibility(last && mode == MODE_NEW ? View.VISIBLE : View.GONE);
        paintDots();

        // Once laid out: the shape morphs to hug the new icon / sample, the glow drifts.
        tone = p.brand
                ? ColorUtils.blendARGB(ContextCompat.getColor(this, R.color.surface_raised), ContextCompat.getColor(this, R.color.amber), 0.14f)
                : ContextCompat.getColor(this, R.color.surface_raised);
        b.stage.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                b.stage.getViewTreeObserver().removeOnPreDrawListener(this);
                morphAround(content, hasDemo, tone, motion);
                float gx = glowX(index), gy = glowY(index);
                if (motion) b.glow.animate().translationX(gx).translationY(gy).setDuration(900).setInterpolator(new Spring(6f)).start();
                else { b.glow.setTranslationX(gx); b.glow.setTranslationY(gy); }
                return true;
            }
        });

        // Reset what the drag / exit moved.
        b.textBlock.animate().cancel();
        b.textBlock.setTranslationX(0f);
        b.textBlock.setAlpha(1f);
        content.animate().cancel();
        content.setRenderEffect(null);
        b.morph.setTranslationX(0f);

        if (!motion) {
            content.setAlpha(1f);
            content.setScaleX(1f);
            content.setScaleY(1f);
            content.setTranslationX(0f);
            for (TextView s : steps) light(s, true, false);
            return;
        }

        // In: each line of words slides in on a spring, one after another…
        Spring spring = new Spring(8f);
        float from = (dir == 0 ? 1 : dir) * dp(56);
        int k = 0;
        for (int c = 0; c < b.textBlock.getChildCount(); c++) {
            View v = b.textBlock.getChildAt(c);
            if (v.getVisibility() != View.VISIBLE) continue;
            v.animate().cancel();
            v.setTranslationX(from);
            v.setAlpha(0f);
            v.animate().translationX(0f).alpha(1f).setStartDelay(50L * k++).setDuration(620)
                    .setInterpolator(spring).start();
        }
        for (int c = 0; c < b.points.getChildCount(); c++) {
            View v = b.points.getChildAt(c);
            v.setAlpha(0f);
            v.setTranslationY(dp(10));
            v.animate().alpha(1f).translationY(0f).setStartDelay(260 + 90L * c).setDuration(420).setInterpolator(spring).start();
        }
        riseWords(title);

        // …the sample comes into focus out of a blur as the shape lands around it…
        content.setTranslationX((dir == 0 ? 0 : dir) * b.getRoot().getWidth() * 0.4f);
        content.setAlpha(0f);
        content.setScaleX(1.05f);
        content.setScaleY(1.05f);
        content.animate().translationX(0f).alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(90).setDuration(720)
                .setInterpolator(spring).start();
        blur(content, dp(16), 0f, 460, 90);

        // …and the path to the feature lights up, step by step.
        if (!steps.isEmpty()) handler.postDelayed(() -> walk(steps, 0), 700);
    }

    /** The title's words rise in, one after another (opacity per word; no reflow). */
    private void riseWords(String title) {
        String[] words = title.split(" ");
        int[] starts = new int[words.length];
        for (int i = 0, at = 0; i < words.length; i++) { starts[i] = at; at += words[i].length() + 1; }
        int ink = ContextCompat.getColor(this, R.color.text_primary);
        SpannableString s = new SpannableString(title);
        ValueAnimator a = ValueAnimator.ofFloat(0f, words.length + 1f);
        a.setDuration(160L * words.length + 260);
        a.addUpdateListener(x -> {
            float t = (float) x.getAnimatedValue();
            for (Object o : s.getSpans(0, s.length(), ForegroundColorSpan.class)) s.removeSpan(o);
            for (int i = 0; i < words.length; i++) {
                float alpha = Math.max(0f, Math.min(1f, t - i));
                s.setSpan(new ForegroundColorSpan(ColorUtils.setAlphaComponent(ink, Math.round(alpha * 255))),
                        starts[i], starts[i] + words[i].length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            b.title.setText(s);
        });
        running.add(a);
        a.start();
    }

    // ── Find it ──────────────────────────────────────────────────────────────

    private List<TextView> buildWhere(@Nullable String[] path) {
        List<TextView> steps = new ArrayList<>();
        b.where.removeAllViews();
        b.whereRow.setVisibility(path == null ? View.GONE : View.VISIBLE);
        if (path == null) return steps;
        for (int i = 0; i < path.length; i++) {
            if (i > 0) {
                TextView arrow = new TextView(this);
                arrow.setText("›");
                arrow.setTextColor(ContextCompat.getColor(this, R.color.text_muted));
                arrow.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                arrow.setTypeface(ResourcesCompat.getFont(this, R.font.manrope));
                arrow.setPadding(dp(1), dp(5), dp(1), dp(5));     // same height as a step, so it sits centred
                arrow.setGravity(Gravity.CENTER);
                b.where.addView(arrow);
            }
            TextView s = new TextView(this);
            s.setText(path[i]);
            s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            s.setTypeface(ResourcesCompat.getFont(this, R.font.manrope));
            s.setPadding(dp(10), dp(5), dp(10), dp(5));
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(12));
            s.setBackground(bg);
            light(s, false, false);
            b.where.addView(s);
            steps.add(s);
        }
        return steps;
    }

    /** Lights the path one step at a time, like tapping through to it; then rests, and again. */
    private void walk(List<TextView> steps, int at) {
        if (at >= steps.size()) {
            handler.postDelayed(() -> {
                for (TextView s : steps) light(s, false, false);
                handler.postDelayed(() -> walk(steps, 0), 500);
            }, 2600);
            return;
        }
        for (int i = 0; i < at; i++) light(steps.get(i), true, false);
        light(steps.get(at), true, true);
        handler.postDelayed(() -> walk(steps, at + 1), 520);
    }

    private void light(TextView s, boolean on, boolean current) {
        int amber = ContextCompat.getColor(this, R.color.amber);
        GradientDrawable bg = (GradientDrawable) s.getBackground();
        if (current) {
            bg.setColor(amber);
            s.setTextColor(ContextCompat.getColor(this, R.color.paper_ink));
            if (motion()) {
                s.setScaleX(0.9f);
                s.setScaleY(0.9f);
                s.animate().scaleX(1f).scaleY(1f).setDuration(380).setInterpolator(new Spring(11f)).start();
            }
        } else if (on) {
            bg.setColor(ColorUtils.setAlphaComponent(amber, 0x2E));
            s.setTextColor(amber);
        } else {
            bg.setColor(ContextCompat.getColor(this, R.color.surface_high));
            s.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private View content() {
        return b.demo.getVisibility() == View.VISIBLE ? b.demo : b.iconDisc;
    }

    /** The shape hugs the icon (a circle) or the sample (a rounded frame a little larger than it). */
    private void morphAround(View content, boolean frame, int tone, boolean animate) {
        int[] s = new int[2], m = new int[2];
        b.stage.getLocationInWindow(s);
        b.morph.getLocationInWindow(m);
        float l = s[0] - m[0] + content.getLeft(), t = s[1] - m[1] + content.getTop();
        RectF r = new RectF(l, t, l + content.getWidth(), t + content.getHeight());
        float radius;
        if (frame) {
            r.inset(-dp(16), -dp(16));
            radius = dp(30);
        } else {
            float cx = r.centerX(), cy = r.centerY(), half = dp(70);
            r.set(cx - half, cy - half, cx + half, cy + half);
            radius = half;
        }
        b.morph.morphTo(r, radius, tone, animate);
    }

    /** The glow wanders from left to right as you go through the tour, a little up and down. */
    private float glowX(int i) {
        float span = b.getRoot().getWidth() - b.glow.getWidth() * 0.5f;
        float f = pages.size() <= 1 ? 0f : (float) i / (pages.size() - 1);
        return -b.glow.getWidth() * 0.25f + span * f;
    }

    private float glowY(int i) {
        return (i % 2 == 0 ? 0.1f : 0.35f) * b.stageRoot.getHeight();
    }

    private void blur(View v, float from, float to, long ms, long delay) {
        ValueAnimator a = ValueAnimator.ofFloat(from, to);
        a.setDuration(ms);
        a.setStartDelay(delay);
        a.addUpdateListener(x -> {
            float r = (float) x.getAnimatedValue();
            v.setRenderEffect(r < 0.5f ? null : RenderEffect.createBlurEffect(r, r, Shader.TileMode.DECAL));
        });
        running.add(a);
        a.start();
    }

    /** One point: an amber dot and a line of text. */
    private View point(@NonNull String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextAppearance(R.style.TextAppearance_Confess_BodyMedium);
        t.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        t.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.dot_amber, 0, 0, 0);
        t.setCompoundDrawablePadding(dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        t.setLayoutParams(lp);
        return t;
    }

    private static boolean motion() {
        return ValueAnimator.areAnimatorsEnabled();
    }

    // ── Dots ─────────────────────────────────────────────────────────────────

    private void buildDots() {
        b.dots.removeAllViews();
        for (int i = 0; i < pages.size(); i++) {
            View d = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(6), dp(6));
            lp.setMarginStart(dp(3));
            lp.setMarginEnd(dp(3));
            d.setLayoutParams(lp);
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(3));
            d.setBackground(g);
            b.dots.addView(d);
        }
    }

    /** The current page's dot stretches into an amber pill. */
    private void paintDots() {
        int amber = ContextCompat.getColor(this, R.color.amber);
        int off = ContextCompat.getColor(this, R.color.hairline);
        for (int i = 0; i < b.dots.getChildCount(); i++) {
            View d = b.dots.getChildAt(i);
            boolean on = i == index;
            ((GradientDrawable) d.getBackground()).setColor(on ? amber : off);
            int target = dp(on ? 18 : 6);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) d.getLayoutParams();
            if (lp.width == target) continue;
            if (!motion()) {
                lp.width = target;
                d.setLayoutParams(lp);
                continue;
            }
            ValueAnimator a = ValueAnimator.ofInt(lp.width, target);
            a.setDuration(420);
            a.setInterpolator(new Spring(9f));
            a.addUpdateListener(x -> {
                lp.width = Math.max(dp(4), (int) x.getAnimatedValue());
                d.setLayoutParams(lp);
            });
            a.start();
        }
    }

    /** The icon floats gently up and down while you read. */
    private void startFloating() {
        if (!motion()) return;
        floatAnim = ValueAnimator.ofFloat(-dp(4), dp(4));
        floatAnim.setDuration(1800);
        floatAnim.setRepeatMode(ValueAnimator.REVERSE);
        floatAnim.setRepeatCount(ValueAnimator.INFINITE);
        floatAnim.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        floatAnim.addUpdateListener(a -> b.icon.setTranslationY((float) a.getAnimatedValue()));
        floatAnim.start();
    }

    private void finishTour() {
        Tour.markSeen(this);
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (demo != null && demo.sound != null) demo.sound.stop();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(STATE_INDEX, index);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        for (ValueAnimator a : running) a.cancel();
        if (floatAnim != null) floatAnim.cancel();
        if (demo != null) demo.stop();
        if (velocity != null) velocity.recycle();
        super.onDestroy();
    }
}
