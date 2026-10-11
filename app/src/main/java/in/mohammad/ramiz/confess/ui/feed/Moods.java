package in.mohammad.ramiz.confess.ui.feed;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import in.mohammad.ramiz.confess.R;

/**
 * How a confession wants to be received (server keys): vent · advice · listen · happy · heartbreak.
 * "listen" = reactions only, no responses.
 */
public final class Moods {

    public static final String LISTEN = "listen";
    public static final String[] KEYS = {"vent", "advice", "listen", "happy", "heartbreak"};
    private static final String[] EMOJI = {"🌧️", "💬", "🫂", "☀️", "💔"};
    @StringRes private static final int[] LABELS = {
            R.string.mood_vent, R.string.mood_advice, R.string.mood_listen, R.string.mood_happy, R.string.mood_heartbreak};

    private Moods() { }

    private static int index(@Nullable String key) {
        if (key == null) return -1;
        for (int i = 0; i < KEYS.length; i++) if (KEYS[i].equals(key)) return i;
        return -1;
    }

    public static boolean known(@Nullable String key) {
        return index(key) >= 0;
    }

    @NonNull
    public static String emoji(@NonNull String key) {
        int i = index(key);
        return i < 0 ? "" : EMOJI[i];
    }

    @StringRes
    public static int label(@NonNull String key) {
        int i = index(key);
        return i < 0 ? R.string.mood_vent : LABELS[i];
    }
}
