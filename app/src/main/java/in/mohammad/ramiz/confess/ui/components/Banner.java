package in.mohammad.ramiz.confess.ui.components;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A profile banner: one of the drawn patterns in one of the 12 aura colours. Server value
 * "bn1:p&lt;pattern&gt;.c&lt;colour&gt;" (users.banner); "" = automatic — the avatar's colours and a pattern
 * picked from the alias, so every profile has one before anyone chooses.
 *
 * Drawn by {@link BannerView} — never an image, so there is nothing to upload or moderate.
 * Keep {@link #PATTERNS} in sync with BANNER_PATTERNS on the server.
 */
public final class Banner {

    public static final int GLOW = 0, STARS = 1, WAVES = 2, RULED = 3, LETTERS = 4,
            MOON = 5, RAIN = 6, CONSTELLATION = 7, DUNES = 8, BOKEH = 9;
    public static final int PATTERNS = 10;

    private static final Pattern SPEC = Pattern.compile("^bn1:p([0-9])\\.c([0-9]|1[01])$");

    public final int pattern;
    @NonNull public final Aura colour;
    /** Not chosen: follows the avatar. */
    public final boolean automatic;

    private Banner(int pattern, @NonNull Aura colour, boolean automatic) {
        this.pattern = Math.floorMod(pattern, PATTERNS);
        this.colour = colour;
        this.automatic = automatic;
    }

    @NonNull
    public static Banner of(int pattern, @NonNull Aura colour) {
        return new Banner(pattern, colour, false);
    }

    /** What to draw for a profile: its chosen banner, or the automatic one. */
    @NonNull
    public static Banner resolve(@Nullable String spec, @Nullable String avatar, @Nullable String alias) {
        if (spec != null) {
            Matcher m = SPEC.matcher(spec.trim());
            if (m.matches()) {
                return new Banner(Integer.parseInt(m.group(1)), Aura.values()[Integer.parseInt(m.group(2))], false);
            }
        }
        return automatic(avatar, alias);
    }

    @NonNull
    public static Banner automatic(@Nullable String avatar, @Nullable String alias) {
        int h = alias == null ? 0 : alias.toLowerCase(Locale.ROOT).hashCode();
        return new Banner(Math.floorMod(h, PATTERNS), Aura.resolve(avatar), true);
    }

    @NonNull
    public Banner withPattern(int p) {
        return new Banner(p, colour, false);
    }

    @NonNull
    public Banner withColour(@NonNull Aura c) {
        return new Banner(pattern, c, false);
    }

    /** The value saved on the server ("" for automatic). */
    @NonNull
    public String spec() {
        return automatic ? "" : String.format(Locale.ROOT, "bn1:p%d.c%d", pattern, colour.ordinal());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Banner)) return false;
        Banner b = (Banner) o;
        return pattern == b.pattern && colour == b.colour && automatic == b.automatic;
    }

    @Override
    public int hashCode() {
        return (pattern * 31 + colour.ordinal()) * 2 + (automatic ? 1 : 0);
    }
}
