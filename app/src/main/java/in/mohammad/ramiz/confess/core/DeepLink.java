package in.mohammad.ramiz.confess.core;

import android.content.Intent;

import androidx.annotation.Nullable;

/**
 * A confession to open once the user is in (tapped push → launcher → [unlock] → home).
 * Kept in memory only: survives the launcher/unlock hops, not a process restart.
 */
public final class DeepLink {

    public static final String EXTRA_POST_ID = "deeplink_post_id";
    public static final String EXTRA_TO_RESPONSES = "deeplink_to_responses";

    @Nullable private static String postId;
    private static boolean toResponses;

    private DeepLink() { }

    /** Remember the target from the launcher's intent, if any. */
    public static void capture(@Nullable Intent intent) {
        if (intent == null) return;
        String id = intent.getStringExtra(EXTRA_POST_ID);
        if (id == null) return;
        postId = id;
        toResponses = intent.getBooleanExtra(EXTRA_TO_RESPONSES, false);
    }

    /** The pending target as {postId, "1"|"0"}, cleared on read; null if none. */
    @Nullable
    public static String[] consume() {
        if (postId == null) return null;
        String[] r = {postId, toResponses ? "1" : "0"};
        postId = null;
        toResponses = false;
        return r;
    }
}
