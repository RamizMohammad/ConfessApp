package in.mohammad.ramiz.confess.ui.components;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.util.Random;

import in.mohammad.ramiz.confess.R;

/**
 * A designed avatar, stored in the same {@code avatar} field as auras and photos:
 * {@code "av1:k2.h3.c1.e0.b1.m2.f0.g0.x0.o3.d5"} — one letter per part, one number per choice.
 * The server checks exactly these keys and ranges (app/schemas/user.py), so keep them in sync.
 *
 * Illustrated and non-photographic on purpose: it shows a mood, never who you are.
 */
public final class AvatarSpec {

    public static final String PREFIX = "av1:";

    /** One customisable part. */
    public enum Part {
        SKIN('k', 8, R.string.avs_skin, true),
        HAIR('h', 10, R.string.avs_hair, false),
        HAIR_COLOR('c', 10, R.string.avs_hair_color, true),
        EYES('e', 6, R.string.avs_eyes, false),
        BROWS('b', 4, R.string.avs_brows, false),
        MOUTH('m', 6, R.string.avs_mouth, false),
        BEARD('f', 5, R.string.avs_beard, false),
        GLASSES('g', 4, R.string.avs_glasses, false),
        HAT('x', 5, R.string.avs_hat, false),
        OUTFIT('o', 10, R.string.avs_outfit, true),
        BACKDROP('d', 12, R.string.avs_backdrop, true);

        public final char key;
        public final int count;
        @StringRes public final int label;
        /** Shown as colour swatches rather than little avatars. */
        public final boolean swatch;

        Part(char key, int count, @StringRes int label, boolean swatch) {
            this.key = key;
            this.count = count;
            this.label = label;
            this.swatch = swatch;
        }
    }

    private final int[] v = new int[Part.values().length];

    public AvatarSpec() {
        v[Part.HAIR.ordinal()] = 2;
        v[Part.HAIR_COLOR.ordinal()] = 1;
        v[Part.SKIN.ordinal()] = 2;
        v[Part.OUTFIT.ordinal()] = 0;
        v[Part.BACKDROP.ordinal()] = 3;
    }

    public int get(@NonNull Part p) {
        return v[p.ordinal()];
    }

    @NonNull
    public AvatarSpec with(@NonNull Part p, int value) {
        AvatarSpec c = copy();
        c.v[p.ordinal()] = clamp(p, value);
        return c;
    }

    @NonNull
    public AvatarSpec copy() {
        AvatarSpec c = new AvatarSpec();
        System.arraycopy(v, 0, c.v, 0, v.length);
        return c;
    }

    private static int clamp(Part p, int value) {
        return value < 0 || value >= p.count ? 0 : value;
    }

    public static boolean isSpec(@Nullable String value) {
        return value != null && value.startsWith(PREFIX);
    }

    /** Parse leniently (unknown keys / bad numbers fall back to defaults). */
    @NonNull
    public static AvatarSpec parse(@Nullable String value) {
        AvatarSpec s = new AvatarSpec();
        if (!isSpec(value)) return s;
        for (String token : value.substring(PREFIX.length()).split("\\.")) {
            if (token.length() < 2) continue;
            char key = token.charAt(0);
            int n;
            try {
                n = Integer.parseInt(token.substring(1));
            } catch (NumberFormatException e) {
                continue;
            }
            for (Part p : Part.values()) {
                if (p.key == key) s.v[p.ordinal()] = clamp(p, n);
            }
        }
        return s;
    }

    @NonNull
    public String encode() {
        StringBuilder sb = new StringBuilder(PREFIX);
        Part[] parts = Part.values();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append('.');
            sb.append(parts[i].key).append(v[i]);
        }
        return sb.toString();
    }

    /** A pleasant random look (no beard/glasses/hat most of the time). */
    @NonNull
    public static AvatarSpec random(@NonNull Random r) {
        AvatarSpec s = new AvatarSpec();
        for (Part p : Part.values()) s.v[p.ordinal()] = r.nextInt(p.count);
        if (r.nextInt(3) > 0) s.v[Part.BEARD.ordinal()] = 0;
        if (r.nextInt(3) > 0) s.v[Part.GLASSES.ordinal()] = 0;
        if (r.nextInt(3) > 0) s.v[Part.HAT.ordinal()] = 0;
        if (s.v[Part.HAIR_COLOR.ordinal()] >= 7 && r.nextBoolean()) s.v[Part.HAIR_COLOR.ordinal()] = r.nextInt(5);
        return s;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof AvatarSpec && java.util.Arrays.equals(v, ((AvatarSpec) o).v);
    }

    @Override
    public int hashCode() {
        return java.util.Arrays.hashCode(v);
    }
}
