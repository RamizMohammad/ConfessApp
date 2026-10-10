package in.mohammad.ramiz.confess.ui.compose;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The unsent confession, kept on this phone only (app-private storage, not backed up),
 * per account — so leaving the composer never loses what was written.
 */
public final class ComposeDraft {

    private static final String PREFS = "compose_draft";

    @NonNull public final String text;
    public final boolean allowResponses;

    private ComposeDraft(@NonNull String text, boolean allowResponses) {
        this.text = text;
        this.allowResponses = allowResponses;
    }

    @Nullable
    public static ComposeDraft load(@NonNull Context ctx, @Nullable String userId) {
        if (userId == null) return null;
        SharedPreferences p = prefs(ctx);
        String text = p.getString("text_" + userId, null);
        if (text == null || text.trim().isEmpty()) return null;
        return new ComposeDraft(text, p.getBoolean("allow_" + userId, true));
    }

    public static void save(@NonNull Context ctx, @Nullable String userId, @NonNull String text, boolean allowResponses) {
        if (userId == null) return;
        if (text.trim().isEmpty()) {
            clear(ctx, userId);
            return;
        }
        prefs(ctx).edit()
                .putString("text_" + userId, text)
                .putBoolean("allow_" + userId, allowResponses)
                .apply();
    }

    public static void clear(@NonNull Context ctx, @Nullable String userId) {
        if (userId == null) return;
        prefs(ctx).edit().remove("text_" + userId).remove("allow_" + userId).apply();
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
