package in.mohammad.ramiz.confess.ui.components;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.util.Random;

import in.mohammad.ramiz.confess.R;

/**
 * Draws a {@link Banner}: a deep two-tone wash in the aura's colours, the pattern on top in its
 * glow colour, a soft dark band at the top (status bar / labels stay readable) and, on profiles,
 * a fade into the page at the bottom so the avatar sits on it without a hard edge.
 *
 * Every shape comes from a fixed seed per pattern, placed in fractions of the size — the same
 * banner looks the same on every phone. With {@link #setAnimated} the stars twinkle, rain falls,
 * waves roll… slowly, and only while it's on screen and the phone allows animations.
 */
public class BannerView extends View {

    private static final long LOOP_MS = 14_000;

    @NonNull private Banner banner = Banner.of(Banner.GLOW, Aura.AURA_04);
    private float corner;
    private boolean fade = true;
    /** Where the fade into the page starts, as a fraction of the height. */
    private float fadeFrom = 0.5f;
    private boolean animated;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wash = new Paint();
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint topScrim = new Paint();
    private final Paint bottomFade = new Paint();
    private final Paint veil = new Paint();
    private final Path path = new Path(), path2 = new Path(), clip = new Path();
    private final Matrix m = new Matrix();
    @Nullable private RadialGradient glowShader;
    private final float dp;
    private final int ink;

    /** Pattern pieces, in fractions of the view: x, y, size (dp), alpha, phase. */
    private float[] ex = new float[0], ey = new float[0], es = new float[0], ea = new float[0], ep = new float[0];
    private int[] linkA = new int[0], linkB = new int[0];

    @Nullable private ValueAnimator clock;
    private float t;

    public BannerView(Context c) {
        this(c, null);
    }

    public BannerView(Context c, @Nullable AttributeSet a) {
        super(c, a);
        dp = getResources().getDisplayMetrics().density;
        ink = ContextCompat.getColor(c, R.color.ink);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        fill.setStyle(Paint.Style.FILL);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        build();
    }

    // ── API ──────────────────────────────────────────────────────────────────

    public void setBanner(@NonNull Banner b) {
        if (b.equals(banner)) return;
        boolean newShapes = b.pattern != banner.pattern;
        banner = b;
        if (newShapes) {
            build();
            stop();
            start();
        }
        shaders();
        invalidate();
    }

    @NonNull
    public Banner getBanner() {
        return banner;
    }

    /** Rounded corners (thumbnails, the picker's preview). */
    public void setCornerRadius(float px) {
        corner = px;
        invalidate();
    }

    /** Fade the bottom into the page (profiles). Off for thumbnails. */
    public void setFadeToInk(boolean on) {
        fade = on;
        shaders();
        invalidate();
    }

    /**
     * A banner behind a whole header (avatar, name, about…) fades late, so the pattern shows
     * behind all of it and only melts into the page at the very bottom.
     */
    public void setFadeStart(float fraction) {
        fadeFrom = Math.max(0f, Math.min(0.95f, fraction));
        shaders();
        invalidate();
    }

    public void setAnimated(boolean on) {
        animated = on;
        if (on) start(); else stop();
    }

    // ── Clock ────────────────────────────────────────────────────────────────

    private void start() {
        if (!animated || clock != null || !isAttachedToWindow() || !ValueAnimator.areAnimatorsEnabled()) return;
        if (banner.pattern == Banner.RULED) return;          // paper stays still
        clock = ValueAnimator.ofFloat(0f, 1f);
        clock.setDuration(LOOP_MS);
        clock.setRepeatCount(ValueAnimator.INFINITE);
        clock.setInterpolator(new LinearInterpolator());
        clock.addUpdateListener(a -> {
            t = (float) a.getAnimatedValue();
            invalidate();
        });
        clock.start();
    }

    private void stop() {
        if (clock != null) {
            clock.cancel();
            clock = null;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        start();
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    @Override
    public void onVisibilityAggregated(boolean visible) {
        super.onVisibilityAggregated(visible);
        if (visible) start(); else stop();
    }

    // ── Shapes ───────────────────────────────────────────────────────────────

    private int count() {
        switch (banner.pattern) {
            case Banner.STARS: return 120;
            case Banner.WAVES: return 5;
            case Banner.RULED: return 3;
            case Banner.LETTERS: return 16;
            case Banner.MOON: return 60;
            case Banner.RAIN: return 90;
            case Banner.CONSTELLATION: return 16;
            case Banner.DUNES: return 4;
            case Banner.BOKEH: return 20;
            default: return 0;
        }
    }

    private void build() {
        int n = count();
        Random r = new Random(7919L * (banner.pattern + 1));
        ex = new float[n];
        ey = new float[n];
        es = new float[n];
        ea = new float[n];
        ep = new float[n];
        for (int i = 0; i < n; i++) {
            ex[i] = r.nextFloat();
            ey[i] = r.nextFloat();
            es[i] = r.nextFloat();
            ea[i] = r.nextFloat();
            ep[i] = r.nextFloat();
        }
        if (banner.pattern == Banner.CONSTELLATION) {
            for (int i = 0; i < n; i++) {                    // keep them off the very edges and the fade
                ex[i] = 0.05f + ex[i] * 0.9f;
                ey[i] = 0.12f + ey[i] * 0.6f;
            }
            links(n);
        }
    }

    /** Each star to its nearest neighbour after it (a drawing, not a mesh). Width ≈ 2.4 × height. */
    private void links(int n) {
        int[] a = new int[n], b = new int[n];
        int k = 0;
        for (int i = 0; i < n; i++) {
            int best = -1;
            float bd = Float.MAX_VALUE;
            for (int j = 0; j < n; j++) {
                if (j == i) continue;
                float dx = (ex[i] - ex[j]) * 2.4f, dy = ey[i] - ey[j];
                float d = dx * dx + dy * dy;
                if (d < bd) {
                    bd = d;
                    best = j;
                }
            }
            if (best >= 0 && bd < 0.12f) {
                a[k] = i;
                b[k] = best;
                k++;
            }
        }
        linkA = java.util.Arrays.copyOf(a, k);
        linkB = java.util.Arrays.copyOf(b, k);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        shaders();
    }

    private void shaders() {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        Aura a = banner.colour;
        wash.setShader(new LinearGradient(0, 0, w * 0.6f, h * 1.4f,
                new int[]{shade(a.mid, 0.62f), shade(a.mid, 0.38f), a.base}, new float[]{0f, 0.45f, 1f},
                Shader.TileMode.CLAMP));
        glowShader = new RadialGradient(0, 0, 1f, new int[]{alpha(a.glow, 150), alpha(a.glow, 40), alpha(a.glow, 0)},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP);
        glowPaint.setShader(glowShader);
        veil.setShader(new LinearGradient(0, h * 0.45f, 0, h, new int[]{alpha(ink, 0), alpha(ink, 70), alpha(ink, 0)},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        topScrim.setShader(new LinearGradient(0, 0, 0, h * 0.38f, alpha(ink, 120), alpha(ink, 0), Shader.TileMode.CLAMP));
        bottomFade.setShader(fade
                ? new LinearGradient(0, h * fadeFrom, 0, h, new int[]{alpha(ink, 0), alpha(ink, 140), ink},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP)
                : new LinearGradient(0, h * 0.5f, 0, h, alpha(ink, 0), alpha(ink, 90), Shader.TileMode.CLAMP));
    }

    // ── Drawing ──────────────────────────────────────────────────────────────

    @Override
    protected void onDraw(@NonNull Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        if (wash.getShader() == null) shaders();
        int save = c.save();
        if (corner > 0) {
            clip.reset();
            clip.addRoundRect(0, 0, w, h, corner, corner, Path.Direction.CW);
            c.clipPath(clip);
        }
        c.drawRect(0, 0, w, h, wash);
        float tau = (float) (Math.PI * 2);
        Aura a = banner.colour;
        switch (banner.pattern) {
            case Banner.STARS: glow(c, w * 0.75f, h * 0.2f, w * 0.55f); stars(c, w, h, ex.length, 1f); sparkles(c, w, h); break;
            case Banner.WAVES: glow(c, w * 0.2f, h * 0.15f, w * 0.6f); waves(c, w, h, tau); break;
            case Banner.RULED: glow(c, w * 0.85f, h * 0.1f, w * 0.5f); ruled(c, w, h); break;
            case Banner.LETTERS: glow(c, w * 0.3f, h * 0.3f, w * 0.6f); letters(c, w, h, tau); break;
            case Banner.MOON: moon(c, w, h); break;
            case Banner.RAIN: glow(c, w * 0.5f, 0, w * 0.7f); rain(c, w, h); break;
            case Banner.CONSTELLATION: glow(c, w * 0.65f, h * 0.35f, w * 0.5f); constellation(c, w, h, tau); break;
            case Banner.DUNES: dunes(c, w, h, tau); break;
            case Banner.BOKEH: bokeh(c, w, h, tau); break;
            default:                                         // GLOW: two soft lights drifting
                glow(c, w * (0.25f + 0.06f * (float) Math.sin(tau * t)), h * (0.4f + 0.1f * (float) Math.cos(tau * t)), w * 0.7f);
                glow(c, w * (0.82f - 0.05f * (float) Math.sin(tau * t)), h * 0.2f, w * 0.45f);
                arcs(c, w, h, a);
        }
        c.drawRect(0, 0, w, h * 0.38f, topScrim);
        if (fade && fadeFrom > 0.6f) c.drawRect(0, 0, w, h, veil);
        c.drawRect(0, h * (fade ? fadeFrom : 0.5f), w, h, bottomFade);
        c.restoreToCount(save);
    }

    private void glow(Canvas c, float x, float y, float radius) {
        if (glowShader == null) return;
        m.setScale(radius, radius);
        m.postTranslate(x, y);
        glowShader.setLocalMatrix(m);
        c.drawCircle(x, y, radius, glowPaint);
    }

    /** Faint rings around the first light (GLOW). */
    private void arcs(Canvas c, int w, int h, Aura a) {
        line.setStrokeWidth(dp);
        for (int i = 1; i <= 4; i++) {
            line.setColor(alpha(a.glow, 46 - i * 8));
            c.drawCircle(w * 0.25f, h * 0.4f, h * 0.28f * i, line);
        }
    }

    private float twinkle(int i, float speed) {
        if (clock == null) return 1f;
        return 0.55f + 0.45f * (float) Math.sin(Math.PI * 2 * (t * speed + ep[i]));
    }

    private void stars(Canvas c, int w, int h, int n, float scale) {
        int g = banner.colour.glow;
        for (int i = 0; i < n; i++) {
            float r = (0.5f + es[i] * 1.2f) * dp * scale;
            fill.setColor(alpha(lighten(g, 0.5f), (int) ((60 + ea[i] * 190) * twinkle(i, 3))));
            c.drawCircle(ex[i] * w, ey[i] * h * 0.9f, r, fill);
        }
    }

    private void sparkles(Canvas c, int w, int h) {
        int g = lighten(banner.colour.glow, 0.6f);
        for (int i = 0; i < 5; i++) {
            float x = ex[i] * w, y = (0.1f + ey[i] * 0.5f) * h, s = (4f + es[i] * 4f) * dp * twinkle(i, 2);
            fill.setColor(alpha(g, 230));
            path.reset();
            path.moveTo(x, y - s);
            path.quadTo(x, y, x + s, y);
            path.quadTo(x, y, x, y + s);
            path.quadTo(x, y, x - s, y);
            path.quadTo(x, y, x, y - s);
            path.close();
            c.drawPath(path, fill);
        }
    }

    private void waves(Canvas c, int w, int h, float tau) {
        Aura a = banner.colour;
        float step = Math.max(4f, 6f * dp);
        for (int i = 0; i < ex.length; i++) {
            float base = h * (0.3f + i * 0.13f), amp = h * (0.05f + 0.03f * es[i]);
            float k = tau * (0.8f + ex[i] * 0.9f) / w;
            float ph = tau * ep[i] + tau * t * (i % 2 == 0 ? 1 : -1);
            path.reset();
            path.moveTo(0, h);
            for (float x = 0; x <= w + step; x += step) {
                path.lineTo(x, base + amp * (float) Math.sin(k * x + ph));
            }
            path.lineTo(w, h);
            path.close();
            fill.setColor(alpha(mix(a.glow, a.mid, i / 4f), 34 + i * 9));
            c.drawPath(path, fill);
        }
    }

    /** Letter paper: ruled lines, a margin, three lines of "handwriting" and a wax seal. */
    private void ruled(Canvas c, int w, int h) {
        Aura a = banner.colour;
        float gap = 20 * dp;
        line.setStrokeWidth(dp);
        line.setColor(alpha(a.glow, 34));
        for (float y = 30 * dp; y < h; y += gap) c.drawLine(0, y, w, y, line);
        float mx = w * 0.12f;
        line.setColor(alpha(a.glow, 90));
        c.drawLine(mx, 0, mx, h, line);
        c.drawLine(mx + 3 * dp, 0, mx + 3 * dp, h, line);
        line.setStrokeWidth(1.6f * dp);
        line.setColor(alpha(lighten(a.glow, 0.3f), 120));
        for (int i = 0; i < ex.length; i++) {
            float y = 30 * dp + gap * (i + 1) - 4 * dp, end = mx + 14 * dp + w * (0.25f + 0.35f * ex[i]);
            path.reset();
            path.moveTo(mx + 14 * dp, y);
            for (float x = mx + 14 * dp; x < end; x += 5 * dp) {
                path.lineTo(x, y - (float) Math.abs(Math.sin(x / (3.2f * dp) + ep[i] * 6)) * 5 * dp);
            }
            c.drawPath(path, line);
        }
        float sx = w * 0.86f, sy = h * 0.34f, sr = 17 * dp;
        fill.setColor(alpha(a.mid, 235));
        c.drawCircle(sx, sy, sr, fill);
        line.setStrokeWidth(1.4f * dp);
        line.setColor(alpha(a.glow, 170));
        c.drawCircle(sx, sy, sr * 0.68f, line);
        heart(c, sx, sy, sr * 0.34f, alpha(lighten(a.glow, 0.2f), 220));
    }

    private void heart(Canvas c, float x, float y, float s, int colour) {
        path.reset();
        path.moveTo(x, y + s);
        path.cubicTo(x - s * 1.6f, y - s * 0.2f, x - s * 0.6f, y - s * 1.4f, x, y - s * 0.4f);
        path.cubicTo(x + s * 0.6f, y - s * 1.4f, x + s * 1.6f, y - s * 0.2f, x, y + s);
        path.close();
        fill.setColor(colour);
        c.drawPath(path, fill);
    }

    private void letters(Canvas c, int w, int h, float tau) {
        Aura a = banner.colour;
        line.setStrokeWidth(1.5f * dp);
        for (int i = 0; i < ex.length; i++) {
            float s = (18 + es[i] * 18) * dp, hh = s * 0.66f;
            float drift = clock == null ? 0f : 3 * dp * (float) Math.sin(tau * (2 * t + ep[i]));
            c.save();
            c.translate(ex[i] * w, ey[i] * h * 0.85f + drift);
            c.rotate(-30 + ep[i] * 60);
            line.setColor(alpha(lighten(a.glow, 0.2f), (int) (50 + ea[i] * 110)));
            c.drawRoundRect(-s / 2, -hh / 2, s / 2, hh / 2, 2 * dp, 2 * dp, line);
            path.reset();
            path.moveTo(-s / 2, -hh / 2);
            path.lineTo(0, hh * 0.08f);
            path.lineTo(s / 2, -hh / 2);
            c.drawPath(path, line);
            c.restore();
        }
    }

    private void moon(Canvas c, int w, int h) {
        Aura a = banner.colour;
        float cx = w * 0.78f, cy = h * 0.32f, r = h * 0.17f;
        glow(c, cx, cy, r * 4.2f);
        stars(c, w, h, ex.length, 0.9f);
        path.reset();
        path.addCircle(cx, cy, r, Path.Direction.CW);
        path2.reset();
        path2.addCircle(cx + r * 0.45f, cy - r * 0.2f, r * 0.9f, Path.Direction.CW);
        path.op(path2, Path.Op.DIFFERENCE);
        fill.setColor(alpha(lighten(a.glow, 0.45f), 245));
        c.drawPath(path, fill);
        hills(c, w, h, 0.66f, mix(a.mid, a.base, 0.5f), 0.05f);
        hills(c, w, h, 0.76f, shade(a.base, 0.8f), 0.6f);
    }

    private void hills(Canvas c, int w, int h, float top, int colour, float shift) {
        path.reset();
        path.moveTo(0, h);
        path.lineTo(0, h * (top + 0.06f));
        path.cubicTo(w * (0.25f + shift * 0.2f), h * (top - 0.08f), w * 0.45f, h * (top + 0.12f), w * 0.68f, h * top);
        path.cubicTo(w * 0.82f, h * (top - 0.05f), w * 0.92f, h * (top + 0.02f), w, h * (top - 0.02f));
        path.lineTo(w, h);
        path.close();
        fill.setColor(colour);
        c.drawPath(path, fill);
    }

    private void rain(Canvas c, int w, int h) {
        int g = lighten(banner.colour.glow, 0.3f);
        line.setStrokeWidth(1.2f * dp);
        for (int i = 0; i < ex.length; i++) {
            float len = (10 + es[i] * 16) * dp, speed = 2f + ep[i] * 2f;
            float y = ((ey[i] + t * speed) % 1f) * (h + len) - len;
            float x = ex[i] * (w + h * 0.3f) - (y + len) * 0.28f;
            line.setColor(alpha(g, (int) (40 + ea[i] * 110)));
            c.drawLine(x, y, x + len * 0.28f, y + len, line);
        }
    }

    private void constellation(Canvas c, int w, int h, float tau) {
        int g = lighten(banner.colour.glow, 0.4f);
        line.setStrokeWidth(dp);
        line.setColor(alpha(g, 70));
        for (int k = 0; k < linkA.length; k++) {
            int i = linkA[k], j = linkB[k];
            c.drawLine(ex[i] * w, ey[i] * h, ex[j] * w, ey[j] * h, line);
        }
        for (int i = 0; i < ex.length; i++) {
            float r = (1.6f + es[i] * 1.6f) * dp, tw = twinkle(i, 2);
            fill.setColor(alpha(g, (int) (40 * tw)));
            c.drawCircle(ex[i] * w, ey[i] * h, r * 3.2f, fill);
            fill.setColor(alpha(g, (int) (160 + 90 * tw)));
            c.drawCircle(ex[i] * w, ey[i] * h, r, fill);
        }
    }

    private void dunes(Canvas c, int w, int h, float tau) {
        Aura a = banner.colour;
        glow(c, w * 0.7f, h * 0.18f, w * 0.5f);
        for (int i = 0; i < ex.length; i++) {
            float top = h * (0.3f + 0.14f * i), sway = clock == null ? 0f : 0.03f * (float) Math.sin(tau * (t + i * 0.25f));
            path.reset();
            path.moveTo(0, h);
            path.lineTo(0, top + h * 0.08f * (ex[i] - 0.5f));
            path.cubicTo(w * (0.3f + sway), top - h * (0.1f + 0.06f * es[i]), w * (0.6f - sway), top + h * 0.12f,
                    w, top - h * 0.04f * ep[i]);
            path.lineTo(w, h);
            path.close();
            fill.setColor(mix(alpha(a.glow, 120), a.base, i / 3f));
            c.drawPath(path, fill);
        }
    }

    private void bokeh(Canvas c, int w, int h, float tau) {
        int g = banner.colour.glow;
        glow(c, w * 0.5f, h * 0.3f, w * 0.6f);
        line.setStrokeWidth(dp);
        for (int i = 0; i < ex.length; i++) {
            float r = (8 + es[i] * 36) * dp;
            float dx = clock == null ? 0f : 6 * dp * (float) Math.cos(tau * (t + ep[i]));
            float dy = clock == null ? 0f : 6 * dp * (float) Math.sin(tau * (t + ep[i]));
            int al = (int) (16 + ea[i] * 44);
            fill.setColor(alpha(g, al));
            c.drawCircle(ex[i] * w + dx, ey[i] * h * 0.85f + dy, r, fill);
            line.setColor(alpha(g, al + 30));
            c.drawCircle(ex[i] * w + dx, ey[i] * h * 0.85f + dy, r, line);
        }
    }

    // ── Colour ───────────────────────────────────────────────────────────────

    private static int alpha(int c, int a) {
        return (Math.max(0, Math.min(255, a)) << 24) | (c & 0x00FFFFFF);
    }

    /** Darker by {@code f} (0 = black, 1 = unchanged); keeps alpha. */
    private static int shade(int c, float f) {
        return Color.argb(Color.alpha(c), (int) (Color.red(c) * f), (int) (Color.green(c) * f), (int) (Color.blue(c) * f));
    }

    private static int lighten(int c, float f) {
        return mix(c, 0xFFFFFFFF, f);
    }

    private static int mix(int a, int b, float f) {
        float g = 1f - f;
        return Color.argb((int) (Color.alpha(a) * g + Color.alpha(b) * f), (int) (Color.red(a) * g + Color.red(b) * f),
                (int) (Color.green(a) * g + Color.green(b) * f), (int) (Color.blue(a) * g + Color.blue(b) * f));
    }
}
