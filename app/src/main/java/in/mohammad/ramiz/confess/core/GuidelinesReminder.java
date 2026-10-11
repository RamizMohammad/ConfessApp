package in.mohammad.ramiz.confess.core;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.ui.entry.GuidelinesActivity;

/**
 * India's IT Rules (as amended in 2026) ask platforms to remind people of their rules at least
 * every three months, and to tell them when the rules change. One gentle dialog:
 *   guidelines changed since you last saw them → "We've updated our community guidelines"
 *   90 days since the last reminder            → "A quick reminder"
 * Accepting the guidelines during sign-up counts as seeing them.
 */
public final class GuidelinesReminder {

    /** Bump when assets/terms.json changes in a way people should hear about. */
    public static final int VERSION = 2;
    private static final long EVERY_MS = 90L * 24 * 60 * 60 * 1000;
    private static final String PREFS = "guidelines";
    private static final String K_VERSION = "seen_version";
    private static final String K_AT = "reminded_at";
    private static boolean shownThisRun = false;

    private GuidelinesReminder() { }

    public static void markSeen(@NonNull Context ctx) {
        prefs(ctx).edit().putInt(K_VERSION, VERSION).putLong(K_AT, System.currentTimeMillis()).apply();
    }

    public static void maybeShow(@NonNull Activity a) {
        if (shownThisRun || a.isFinishing()) return;
        SharedPreferences p = prefs(a);
        long now = System.currentTimeMillis();
        boolean changed = p.getInt(K_VERSION, 0) < VERSION;
        boolean due = now - p.getLong(K_AT, 0) >= EVERY_MS;
        if (!changed && !due) return;
        shownThisRun = true;
        markSeen(a);
        new MaterialAlertDialogBuilder(a)
                .setTitle(changed ? R.string.guidelines_updated_title : R.string.guidelines_reminder_title)
                .setMessage(R.string.guidelines_reminder_body)
                .setNegativeButton(R.string.guidelines_reminder_ok, null)
                .setPositiveButton(R.string.guidelines_reminder_read, (d, w) -> a.startActivity(
                        new Intent(a, GuidelinesActivity.class).putExtra(GuidelinesActivity.EXTRA_READ_ONLY, true)))
                .show();
    }

    private static SharedPreferences prefs(@NonNull Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
