package in.mohammad.ramiz.confess.core;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

/**
 * "The password was just reset from the emailed link" (the reset page sent us
 * confess://reset-done). Until the next successful unlock: no biometric shortcut,
 * and the unlock screen explains why.
 */
public final class ResetNotice {

    private static final String PREFS = "reset_notice";
    private static final String KEY = "just_reset";

    /** Asked "set fingerprint up again?" in this app run (don't nag on every resume). */
    private static boolean askedThisRun;

    private ResetNotice() { }

    public static void markJustReset(@NonNull Context ctx) {
        prefs(ctx).edit().putBoolean(KEY, true).apply();
    }

    public static boolean justReset(@NonNull Context ctx) {
        return prefs(ctx).getBoolean(KEY, false);
    }

    public static void clear(@NonNull Context ctx) {
        prefs(ctx).edit().remove(KEY).apply();
    }

    public static boolean shouldAskBiometricAgain() {
        if (askedThisRun) return false;
        askedThisRun = true;
        return true;
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
