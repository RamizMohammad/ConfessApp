package in.mohammad.ramiz.confess.ui.components;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The 12 preset "aura" avatars (server ids aura_01 … aura_12).
 * Generative and abstract on purpose — nothing that looks like a real face.
 *
 * Each aura is a radial glow: {@code glow} at an off-centre focus fading to
 * {@code mid} and then the deep {@code base} at the rim.
 */
public enum Aura {
    AURA_01("aura_01", "Deep Indigo",      0xFF8C7CF0, 0xFF3A2F86, 0xFF120F2A, -0.20f, -0.25f),
    AURA_02("aura_02", "Dusk Emerald",     0xFF7FD3A8, 0xFF1F6B4E, 0xFF08201A,  0.15f, -0.20f),
    AURA_03("aura_03", "Sapphire Ring",    0xFF7FC4F0, 0xFF1E4E86, 0xFF0A1630,  0.00f,  0.00f),
    AURA_04("aura_04", "Eclipsed Amber",   0xFFFFD08A, 0xFFE8A04C, 0xFF3A2208, -0.15f, -0.20f),
    AURA_05("aura_05", "Nocturnal Cobalt", 0xFF6E8BFF, 0xFF22307A, 0xFF0A0E2A,  0.20f,  0.15f),
    AURA_06("aura_06", "Bronze Twilight",  0xFFE0A070, 0xFF7A4426, 0xFF24120A,  0.25f, -0.25f),
    AURA_07("aura_07", "Amethyst Fog",     0xFFC79BF0, 0xFF6A3A8C, 0xFF1C0F28, -0.10f,  0.10f),
    AURA_08("aura_08", "Charcoal Pearl",   0xFFD9D6DE, 0xFF55525C, 0xFF16151A, -0.25f, -0.30f),
    AURA_09("aura_09", "Smoky Topaz",      0xFFE8C27A, 0xFF6B5426, 0xFF1E1708,  0.10f,  0.20f),
    AURA_10("aura_10", "Midnight Teal",    0xFF6FD6D0, 0xFF1B5F64, 0xFF071C1E, -0.20f,  0.20f),
    AURA_11("aura_11", "Ember Moss",       0xFFC9D27A, 0xFF556226, 0xFF141A08,  0.20f, -0.10f),
    AURA_12("aura_12", "Plum Ash",         0xFFE08AB0, 0xFF6A2E4C, 0xFF1E0C16, -0.05f, -0.30f);

    public final String id;
    public final String displayName;
    public final int glow, mid, base;
    /** Focus of the glow, as a fraction of the radius from the centre. */
    public final float focusX, focusY;

    Aura(String id, String displayName, int glow, int mid, int base, float focusX, float focusY) {
        this.id = id;
        this.displayName = displayName;
        this.glow = glow;
        this.mid = mid;
        this.base = base;
        this.focusX = focusX;
        this.focusY = focusY;
    }

    /** 1-based number for labels: "Aura 04 · Eclipsed Amber". */
    public int number() {
        return ordinal() + 1;
    }

    /** Exact preset for an id like "aura_04", or null. */
    @Nullable
    public static Aura fromId(@Nullable String id) {
        if (id == null) return null;
        for (Aura a : values()) if (a.id.equalsIgnoreCase(id.trim())) return a;
        return null;
    }

    /**
     * Never-null aura for any avatar value: the preset if it is one, otherwise
     * a stable pick from the string's hash (used for unknown/legacy values and
     * as the placeholder while an uploaded image loads).
     */
    @NonNull
    public static Aura resolve(@Nullable String value) {
        Aura exact = fromId(value);
        if (exact != null) return exact;
        if (AvatarSpec.isSpec(value)) {                        // designed avatar → its backdrop
            return values()[AvatarSpec.parse(value).get(AvatarSpec.Part.BACKDROP) % values().length];
        }
        int h = value == null ? 0 : value.hashCode();
        return values()[Math.floorMod(h, values().length)];
    }
}
