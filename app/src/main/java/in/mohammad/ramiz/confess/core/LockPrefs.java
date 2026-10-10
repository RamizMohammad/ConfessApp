package in.mohammad.ramiz.confess.core;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import in.mohammad.ramiz.confess.R;

/** How long Confess may sit in the background before the password lock comes back (this phone only). */
public final class LockPrefs {

    private static final String PREFS = "lock";
    private static final String KEY_TIMEOUT = "relock_after_ms";
    private static final String KEY_SECURE = "secure_screen";

    /** Choices shown in Settings. */
    public static final long[] OPTIONS_MS = {0L, 60_000L, 5 * 60_000L, 15 * 60_000L};
    @StringRes
    public static final int[] LABELS = {R.string.autolock_immediately, R.string.autolock_1m,
            R.string.autolock_5m, R.string.autolock_15m};
    public static final long DEFAULT_MS = 5 * 60_000L;

    private LockPrefs() { }

    public static long timeoutMs(@NonNull Context ctx) {
        return prefs(ctx).getLong(KEY_TIMEOUT, DEFAULT_MS);
    }

    public static void setTimeoutMs(@NonNull Context ctx, long ms) {
        prefs(ctx).edit().putLong(KEY_TIMEOUT, ms).apply();
    }

    /** Private screen: FLAG_SECURE on every screen (no screenshots, blank in recents). */
    public static boolean secureScreen(@NonNull Context ctx) {
        return prefs(ctx).getBoolean(KEY_SECURE, false);
    }

    public static void setSecureScreen(@NonNull Context ctx, boolean on) {
        prefs(ctx).edit().putBoolean(KEY_SECURE, on).apply();
    }

    @StringRes
    public static int label(long ms) {
        for (int i = 0; i < OPTIONS_MS.length; i++) if (OPTIONS_MS[i] == ms) return LABELS[i];
        return R.string.autolock_5m;
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
