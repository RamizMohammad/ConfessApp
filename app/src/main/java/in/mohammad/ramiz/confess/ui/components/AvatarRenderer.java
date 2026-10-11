package in.mohammad.ramiz.confess.ui.components;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.LruCache;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import in.mohammad.ramiz.confess.ui.components.AvatarSpec.Part;

/**
 * Draws an {@link AvatarSpec} — a flat, friendly illustrated face on an aura backdrop.
 * All geometry lives in a 100 × 100 box and is scaled, so it's crisp at 28 dp and at 180 dp.
 * Bitmaps are cached by (spec, size).
 */
public final class AvatarRenderer {

    // ── Palettes (index = option number; keep counts in sync with AvatarSpec.Part) ──

    public static final int[] SKIN = {
            0xFFFFE0C7, 0xFFF6CBA5, 0xFFE8B184, 0xFFD39A68, 0xFFB57A4E, 0xFF94603B, 0xFF6F452A, 0xFF4E301E};
    public static final int[] HAIR = {
            0xFF1E1B1A, 0xFF3B2A20, 0xFF6B4423, 0xFFA5652D, 0xFFD9B26F,
            0xFFEDE0C4, 0xFF9C9AA0, 0xFFC0485A, 0xFF4A74C0, 0xFF8A63C9};
    public static final int[] OUTFIT = {
            0xFFE8A04C, 0xFF8FB39A, 0xFFD2553F, 0xFF6C4F8C, 0xFF2F4A6D,
            0xFF2A2832, 0xFFEDE6DA, 0xFF3E8E8A, 0xFFD98BA3, 0xFFC9A227};

    private static final int INK = 0xFF1B1A20;
    private static final int LIP = 0xFF9E3B3B;
    private static final int TONGUE = 0xFFE07A8A;

    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(6 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
    };

    private AvatarRenderer() { }

    /** Square bitmap of the avatar (backdrop included; callers clip it to a circle). */
    @NonNull
    public static Bitmap bitmap(@NonNull AvatarSpec spec, int px) {
        px = Math.max(16, px);
        String key = spec.encode() + "@" + px;
        Bitmap cached = CACHE.get(key);
        if (cached != null && !cached.isRecycled()) return cached;
        Bitmap bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.scale(px / 100f, px / 100f);
        new Painter(spec).draw(c);
        CACHE.put(key, bmp);
        return bmp;
    }

    // ── The drawing ──────────────────────────────────────────────────────────

    private static final class Painter {
        final AvatarSpec s;
        final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        final int skin, skinShade, hair, hairShade, outfit;

        Painter(AvatarSpec s) {
            this.s = s;
            fill.setStyle(Paint.Style.FILL);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeCap(Paint.Cap.ROUND);
            stroke.setStrokeJoin(Paint.Join.ROUND);
            skin = SKIN[s.get(Part.SKIN)];
            skinShade = ColorUtils.blendARGB(skin, Color.BLACK, 0.14f);
            hair = HAIR[s.get(Part.HAIR_COLOR)];
            hairShade = ColorUtils.blendARGB(hair, Color.BLACK, 0.25f);
            outfit = OUTFIT[s.get(Part.OUTFIT)];
        }

        void draw(Canvas c) {
            backdrop(c);
            int save = c.save();
            c.scale(1.12f, 1.12f, 50f, 58f);                               // fill the circle like a portrait
            hairBack(c);
            body(c);
            // Ears + head
            fill(skinShade);
            c.drawCircle(29.5f, 50f, 4.6f, fill);
            c.drawCircle(70.5f, 50f, 4.6f, fill);
            fill(skin);
            c.drawOval(new RectF(29f, 23f, 71f, 72f), fill);
            blush(c);
            beard(c);
            nose(c);
            mouth(c);
            eyes(c);
            brows(c);
            hairFront(c);
            hat(c);
            glasses(c);
            c.restoreToCount(save);
        }

        /** Beanie / cap sit on the head: tall styles are drawn as the short cut underneath. */
        int hairStyle() {
            int h = s.get(Part.HAIR);
            int hat = s.get(Part.HAT);
            boolean covering = hat == 1 || hat == 2;
            if (covering && (h == 4 || h == 8)) return 2;
            if (hat == 1 && h == 5) return 6;                               // bun under a beanie → long-ish
            return h;
        }

        void fill(@ColorInt int color) {
            fill.setShader(null);
            fill.setColor(color);
        }

        void stroke(@ColorInt int color, float width) {
            stroke.setColor(color);
            stroke.setStrokeWidth(width);
        }

        void backdrop(Canvas c) {
            Aura a = Aura.values()[s.get(Part.BACKDROP) % Aura.values().length];
            fill.setShader(new RadialGradient(50 + a.focusX * 50, 50 + a.focusY * 50, 62,
                    new int[]{a.glow, a.mid, a.base}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, 100, 100, fill);
            fill.setShader(null);
        }

        void body(Canvas c) {
            fill(skinShade);
            c.drawRect(43.5f, 62f, 56.5f, 82f, fill);                        // neck
            fill(outfit);
            Path p = new Path();
            p.moveTo(12, 101);
            p.cubicTo(12, 85, 28, 77, 50, 77);
            p.cubicTo(72, 77, 88, 85, 88, 101);
            p.close();
            c.drawPath(p, fill);
            fill(skinShade);                                                // collar
            Path v = new Path();
            v.moveTo(43.5f, 77);
            v.lineTo(50, 85);
            v.lineTo(56.5f, 77);
            v.close();
            c.drawPath(v, fill);
            fill(ColorUtils.blendARGB(outfit, Color.BLACK, 0.18f));
            Path seam = new Path();                                         // soft shoulder shading
            seam.moveTo(12, 101);
            seam.cubicTo(12, 92, 16, 87, 22, 84);
            seam.cubicTo(20, 90, 20, 96, 21, 101);
            seam.close();
            c.drawPath(seam, fill);
            Path seam2 = new Path();
            seam2.moveTo(88, 101);
            seam2.cubicTo(88, 92, 84, 87, 78, 84);
            seam2.cubicTo(80, 90, 80, 96, 79, 101);
            seam2.close();
            c.drawPath(seam2, fill);
        }

        void blush(Canvas c) {
            fill(ColorUtils.setAlphaComponent(0xFFFF7A8A, s.get(Part.SKIN) >= 4 ? 30 : 44));
            c.drawCircle(37.5f, 59f, 3.6f, fill);
            c.drawCircle(62.5f, 59f, 3.6f, fill);
        }

        void nose(Canvas c) {
            stroke(skinShade, 1.4f);
            Path p = new Path();
            p.moveTo(48.4f, 57.2f);
            p.quadTo(50f, 59.2f, 51.6f, 57.2f);
            c.drawPath(p, stroke);
        }

        // ── Eyes / brows / mouth ─────────────────────────────────────────────

        void eyes(Canvas c) {
            int style = s.get(Part.EYES);
            switch (style) {
                case 1: roundEye(c, 41); roundEye(c, 59); break;
                case 2: happyEye(c, 41); happyEye(c, 59); break;
                case 3: sleepyEye(c, 41); sleepyEye(c, 59); break;
                case 4: dotEye(c, 41, 2.7f); happyEye(c, 59); break;
                case 5: lashEye(c, 41, -1); lashEye(c, 59, 1); break;
                default: dotEye(c, 41, 2.7f); dotEye(c, 59, 2.7f); break;
            }
        }

        void dotEye(Canvas c, float x, float r) {
            fill(INK);
            c.drawCircle(x, 50.5f, r, fill);
            fill(Color.WHITE);
            c.drawCircle(x + r * 0.35f, 50.5f - r * 0.4f, r * 0.33f, fill);
        }

        void roundEye(Canvas c, float x) {
            fill(Color.WHITE);
            c.drawOval(new RectF(x - 4.4f, 46f, x + 4.4f, 55f), fill);
            dotEye(c, x, 2.5f);
        }

        void happyEye(Canvas c, float x) {
            stroke(INK, 1.9f);
            Path p = new Path();
            p.moveTo(x - 3.6f, 51.5f);
            p.quadTo(x, 47f, x + 3.6f, 51.5f);
            c.drawPath(p, stroke);
        }

        void sleepyEye(Canvas c, float x) {
            stroke(INK, 1.8f);
            Path p = new Path();
            p.moveTo(x - 3.6f, 50.5f);
            p.quadTo(x, 53.5f, x + 3.6f, 50.5f);
            c.drawPath(p, stroke);
        }

        void lashEye(Canvas c, float x, int side) {
            dotEye(c, x, 2.9f);
            stroke(INK, 1.3f);
            float ox = x + side * 2.6f;
            c.drawLine(ox, 48.6f, ox + side * 2.2f, 46.6f, stroke);
            c.drawLine(ox - side * 0.6f, 47.6f, ox + side * 0.6f, 45.4f, stroke);
        }

        void brows(Canvas c) {
            int style = s.get(Part.BROWS);
            int col = ColorUtils.blendARGB(hairShade, INK, 0.25f);
            float w = style == 1 ? 2.7f : 1.7f;
            stroke(col, w);
            if (style == 3) {                                               // serious
                c.drawLine(37f, 42.2f, 45f, 44.2f, stroke);
                c.drawLine(55f, 44.2f, 63f, 42.2f, stroke);
                return;
            }
            float y = style == 2 ? 40.5f : 43.5f;
            float lift = style == 2 ? 3.2f : 2.2f;
            Path p = new Path();
            p.moveTo(37f, y);
            p.quadTo(41f, y - lift, 45f, y);
            p.moveTo(55f, y);
            p.quadTo(59f, y - lift, 63f, y);
            c.drawPath(p, stroke);
        }

        void mouth(Canvas c) {
            Path p = new Path();
            switch (s.get(Part.MOUTH)) {
                case 1: {                                                   // grin
                    p.moveTo(43.5f, 61.5f);
                    p.quadTo(50f, 70.5f, 56.5f, 61.5f);
                    p.close();
                    fill(LIP);
                    c.drawPath(p, fill);
                    fill(Color.WHITE);
                    c.drawRect(45.5f, 61.5f, 54.5f, 63.4f, fill);
                    break;
                }
                case 2:                                                     // flat
                    stroke(INK, 1.8f);
                    c.drawLine(45.6f, 64f, 54.4f, 64f, stroke);
                    break;
                case 3:                                                     // "o"
                    fill(LIP);
                    c.drawOval(new RectF(47.4f, 61.2f, 52.6f, 67.4f), fill);
                    break;
                case 4:                                                     // smirk
                    stroke(INK, 1.8f);
                    p.moveTo(45f, 63.6f);
                    p.quadTo(51.5f, 66f, 56f, 61.8f);
                    c.drawPath(p, stroke);
                    break;
                case 5: {                                                   // tongue out
                    p.moveTo(43.8f, 61.8f);
                    p.quadTo(50f, 69.5f, 56.2f, 61.8f);
                    p.close();
                    fill(LIP);
                    c.drawPath(p, fill);
                    fill(TONGUE);
                    c.drawOval(new RectF(47.2f, 63.6f, 52.8f, 69.6f), fill);
                    break;
                }
                default:                                                    // smile
                    stroke(INK, 1.8f);
                    p.moveTo(44.2f, 62f);
                    p.quadTo(50f, 67.6f, 55.8f, 62f);
                    c.drawPath(p, stroke);
                    break;
            }
        }

        // ── Beard ────────────────────────────────────────────────────────────

        void beard(Canvas c) {
            int style = s.get(Part.BEARD);
            if (style == 0) return;
            if (style == 1) {                                               // stubble
                fill(ColorUtils.setAlphaComponent(hair, 70));
                Path p = new Path();
                p.moveTo(29.5f, 52);
                p.cubicTo(30, 70, 40, 72.5f, 50, 72.5f);
                p.cubicTo(60, 72.5f, 70, 70, 70.5f, 52);
                p.cubicTo(68, 63, 60, 67, 50, 67);
                p.cubicTo(40, 67, 32, 63, 29.5f, 52);
                p.close();
                c.drawPath(p, fill);
                return;
            }
            fill(hair);
            if (style == 4) {                                               // full beard
                Path p = new Path();
                p.moveTo(29, 50);
                p.cubicTo(28.5f, 72, 40, 79, 50, 79);
                p.cubicTo(60, 79, 71.5f, 72, 71, 50);
                p.cubicTo(69, 58, 65, 62, 61, 64.5f);
                p.cubicTo(57, 61.5f, 43, 61.5f, 39, 64.5f);
                p.cubicTo(35, 62, 31, 58, 29, 50);
                p.close();
                c.drawPath(p, fill);
            }
            if (style == 3) {                                               // goatee
                Path g = new Path();
                g.moveTo(45.6f, 66.6f);
                g.quadTo(50, 68.8f, 54.4f, 66.6f);
                g.lineTo(53.4f, 72.4f);
                g.quadTo(50, 74.6f, 46.6f, 72.4f);
                g.close();
                c.drawPath(g, fill);
            }
            Path m = new Path();                                            // mustache (2, 3, 4)
            m.moveTo(42.6f, 61f);
            m.cubicTo(45.6f, 58.4f, 48.8f, 58.8f, 50f, 60.1f);
            m.cubicTo(51.2f, 58.8f, 54.4f, 58.4f, 57.4f, 61f);
            m.cubicTo(54.4f, 62.8f, 51.4f, 62f, 50f, 61.3f);
            m.cubicTo(48.6f, 62f, 45.6f, 62.8f, 42.6f, 61f);
            m.close();
            c.drawPath(m, fill);
        }

        // ── Hair ─────────────────────────────────────────────────────────────

        void hairBack(Canvas c) {
            int style = hairStyle();
            fill(hairShade);
            Path p = new Path();
            switch (style) {
                case 5:                                                     // bun
                    fill(hair);
                    c.drawCircle(50, 17, 8.5f, fill);
                    break;
                case 6:                                                     // long
                    p.moveTo(26, 44);
                    p.cubicTo(24, 23, 38, 16, 50, 16);
                    p.cubicTo(62, 16, 76, 23, 74, 44);
                    p.lineTo(77, 82);
                    p.cubicTo(70, 88, 30, 88, 23, 82);
                    p.close();
                    c.drawPath(p, fill);
                    break;
                case 7:                                                     // bob
                    p.moveTo(25, 44);
                    p.cubicTo(24, 21, 38, 15, 50, 15);
                    p.cubicTo(62, 15, 76, 21, 75, 44);
                    p.lineTo(75.5f, 67);
                    p.cubicTo(70, 71, 30, 71, 24.5f, 67);
                    p.close();
                    c.drawPath(p, fill);
                    break;
                case 9:                                                     // afro
                    fill(hair);
                    c.drawCircle(50, 39, 31, fill);
                    break;
                default:
                    break;
            }
        }

        void hairFront(Canvas c) {
            int style = hairStyle();
            fill(hair);
            Path p = new Path();
            switch (style) {
                case 1:                                                     // buzz
                    fill(ColorUtils.setAlphaComponent(hair, 215));
                    p.moveTo(29.2f, 43);
                    p.cubicTo(29, 27, 39, 22, 50, 22);
                    p.cubicTo(61, 22, 71, 27, 70.8f, 43);
                    p.cubicTo(66, 35.5f, 58, 33, 50, 33);
                    p.cubicTo(42, 33, 34, 35.5f, 29.2f, 43);
                    break;
                case 2:                                                     // short
                    p.moveTo(27.5f, 46);
                    p.cubicTo(26, 25, 38, 18, 50, 18);
                    p.cubicTo(63, 18, 74, 25, 72.5f, 46);
                    p.cubicTo(70.5f, 40, 68, 35.5f, 64, 33.5f);
                    p.cubicTo(57, 37.5f, 45, 37.5f, 37, 33.5f);
                    p.cubicTo(32.5f, 36, 29.5f, 40, 27.5f, 46);
                    break;
                case 3:                                                     // side sweep
                    p.moveTo(27.5f, 47);
                    p.cubicTo(25.5f, 24, 40, 16, 53, 17);
                    p.cubicTo(66, 18, 75, 27, 72.5f, 46);
                    p.cubicTo(70, 36, 64, 30.5f, 56, 30);
                    p.cubicTo(46, 29.5f, 35, 35, 27.5f, 47);
                    break;
                case 4:                                                     // curly
                    p.moveTo(28, 45);
                    p.cubicTo(27, 26, 39, 20, 50, 20);
                    p.cubicTo(61, 20, 73, 26, 72, 45);
                    p.cubicTo(66, 36, 34, 36, 28, 45);
                    c.drawPath(p, fill);
                    for (int i = 0; i <= 8; i++) {
                        double a = Math.PI + Math.PI * i / 8.0;
                        float x = 50 + (float) Math.cos(a) * 21.5f;
                        float y = 40 + (float) Math.sin(a) * 19.5f;
                        c.drawCircle(x, y, 6.2f, fill);
                    }
                    return;
                case 5:                                                     // bun (front cap)
                case 6:                                                     // long (centre part)
                    p.moveTo(27.5f, 48);
                    p.cubicTo(26, 24, 40, 18, 50, 18);
                    p.cubicTo(60, 18, 74, 24, 72.5f, 48);
                    p.cubicTo(70, 36, 60, 30, 50, 28.5f);
                    p.cubicTo(40, 30, 30, 36, 27.5f, 48);
                    break;
                case 7:                                                     // bob fringe
                    p.moveTo(27.5f, 46);
                    p.cubicTo(26, 24, 38, 17, 50, 17);
                    p.cubicTo(62, 17, 74, 24, 72.5f, 46);
                    p.lineTo(72.5f, 39.5f);
                    p.cubicTo(66, 36.5f, 34, 36.5f, 27.5f, 39.5f);
                    p.close();
                    break;
                case 8:                                                     // spiky
                    p.moveTo(28, 44);
                    p.lineTo(29.5f, 30);
                    p.lineTo(34.5f, 33);
                    p.lineTo(37.5f, 21);
                    p.lineTo(43, 28);
                    p.lineTo(48, 16.5f);
                    p.lineTo(53, 27);
                    p.lineTo(58.5f, 18.5f);
                    p.lineTo(62, 29);
                    p.lineTo(67.5f, 23.5f);
                    p.lineTo(69, 33.5f);
                    p.lineTo(72.5f, 31.5f);
                    p.lineTo(72, 44);
                    p.cubicTo(66, 36, 34, 36, 28, 44);
                    break;
                case 9:                                                     // afro hairline (full, soft edge)
                    p.moveTo(27.5f, 46);
                    p.cubicTo(26, 25, 38, 19, 50, 19);
                    p.cubicTo(62, 19, 74, 25, 72.5f, 46);
                    p.cubicTo(69, 39, 64, 36, 58, 35.5f);
                    p.cubicTo(54, 37.5f, 46, 37.5f, 42, 35.5f);
                    p.cubicTo(36, 36, 31, 39, 27.5f, 46);
                    break;
                default:                                                    // bald
                    return;
            }
            p.close();
            c.drawPath(p, fill);
        }

        // ── Hats ─────────────────────────────────────────────────────────────

        void hat(Canvas c) {
            int style = s.get(Part.HAT);
            if (style == 0) return;
            Path p = new Path();
            switch (style) {
                case 1: {                                                   // beanie
                    int col = 0xFFD2553F;
                    fill(col);
                    p.moveTo(26, 39);
                    p.cubicTo(26, 13, 74, 13, 74, 39);
                    p.close();
                    c.drawPath(p, fill);
                    fill(ColorUtils.blendARGB(col, Color.BLACK, 0.2f));
                    c.drawRoundRect(new RectF(24.5f, 34.5f, 75.5f, 42.5f), 4, 4, fill);
                    fill(0xFFEDE6DA);
                    c.drawCircle(50, 13.5f, 4.4f, fill);
                    break;
                }
                case 2: {                                                   // cap
                    int col = 0xFF2F4A6D;
                    fill(col);
                    p.moveTo(27, 39);
                    p.cubicTo(27, 15, 73, 15, 73, 39);
                    p.close();
                    c.drawPath(p, fill);
                    fill(ColorUtils.blendARGB(col, Color.BLACK, 0.25f));
                    Path brim = new Path();
                    brim.moveTo(48, 38);
                    brim.lineTo(80, 39.5f);
                    brim.cubicTo(84, 41.5f, 81, 44.5f, 75, 44);
                    brim.lineTo(48, 41.5f);
                    brim.close();
                    c.drawPath(brim, fill);
                    fill(0xFFE8A04C);
                    c.drawCircle(50, 17.6f, 1.8f, fill);
                    break;
                }
                case 3: {                                                   // headphones
                    stroke(0xFF2A2832, 3.6f);
                    RectF arc = new RectF(25, 19, 75, 69);
                    c.drawArc(arc, 190, 160, false, stroke);
                    fill(0xFF2A2832);
                    c.drawRoundRect(new RectF(22, 41, 31, 58), 4, 4, fill);
                    c.drawRoundRect(new RectF(69, 41, 78, 58), 4, 4, fill);
                    fill(0xFFE8A04C);
                    c.drawRoundRect(new RectF(23.5f, 44, 29.5f, 55), 3, 3, fill);
                    c.drawRoundRect(new RectF(70.5f, 44, 76.5f, 55), 3, 3, fill);
                    break;
                }
                default: {                                                  // headband + flower
                    fill(0xFF8FB39A);
                    p.moveTo(28, 37);
                    p.cubicTo(36, 30, 64, 30, 72, 37);
                    p.lineTo(72.5f, 40.5f);
                    p.cubicTo(64, 34, 36, 34, 27.5f, 40.5f);
                    p.close();
                    c.drawPath(p, fill);
                    fill(0xFFF6C76A);
                    for (int i = 0; i < 5; i++) {
                        double a = Math.PI * 2 * i / 5;
                        c.drawCircle(35 + (float) Math.cos(a) * 2.6f, 33.5f + (float) Math.sin(a) * 2.6f, 2.2f, fill);
                    }
                    fill(0xFFD2553F);
                    c.drawCircle(35, 33.5f, 1.6f, fill);
                    break;
                }
            }
        }

        // ── Glasses ──────────────────────────────────────────────────────────

        void glasses(Canvas c) {
            int style = s.get(Part.GLASSES);
            if (style == 0) return;
            if (style == 3) {                                               // sunglasses
                fill(0xF0141318);
                c.drawRoundRect(new RectF(33.5f, 45.5f, 47.5f, 55.5f), 4, 4, fill);
                c.drawRoundRect(new RectF(52.5f, 45.5f, 66.5f, 55.5f), 4, 4, fill);
                stroke(0xFF141318, 1.6f);
                c.drawLine(47.5f, 48f, 52.5f, 48f, stroke);
                fill(ColorUtils.setAlphaComponent(Color.WHITE, 90));
                c.drawRoundRect(new RectF(35.5f, 47f, 40f, 49f), 1, 1, fill);
                c.drawRoundRect(new RectF(54.5f, 47f, 59f, 49f), 1, 1, fill);
                return;
            }
            stroke(INK, 1.5f);
            if (style == 1) {                                               // round
                c.drawCircle(41, 50.5f, 6f, stroke);
                c.drawCircle(59, 50.5f, 6f, stroke);
            } else {                                                        // square
                c.drawRoundRect(new RectF(34.2f, 45.5f, 47.6f, 55.5f), 2.5f, 2.5f, stroke);
                c.drawRoundRect(new RectF(52.4f, 45.5f, 65.8f, 55.5f), 2.5f, 2.5f, stroke);
            }
            float l = style == 1 ? 47f : 47.6f, r = style == 1 ? 53f : 52.4f;
            Path bridge = new Path();
            bridge.moveTo(l, 50f);
            bridge.quadTo(50, 48f, r, 50f);
            c.drawPath(bridge, stroke);
        }
    }
}
