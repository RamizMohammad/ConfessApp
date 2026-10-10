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
    /** "X started following you" → their profile. */
    public static final String EXTRA_ALIAS = "deeplink_alias";

    @Nullable private static String postId;
    @Nullable private static String alias;
    private static boolean toResponses;

    private DeepLink() { }

    /** Remember the target from the launcher's intent, if any. */
    public static void capture(@Nullable Intent intent) {
        if (intent == null) return;
        // Our own notifications use EXTRA_*; ones Android shows while the app is closed carry the raw
        // FCM data keys ("type", "postId", "alias") as extras instead.
        String type = intent.getStringExtra("type");
        String who = intent.getStringExtra(EXTRA_ALIAS);
        if (who == null && "follow".equals(type)) who = intent.getStringExtra("alias");
        if (who != null) alias = who;
        String id = intent.getStringExtra(EXTRA_POST_ID);
        boolean responses = intent.getBooleanExtra(EXTRA_TO_RESPONSES, false);
        if (id == null) {
            id = intent.getStringExtra("postId");
            responses = "comment".equals(type);
        }
        if (id == null) return;
        postId = id;
        toResponses = responses;
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

    /** A profile to open (from a follow push), cleared on read. */
    @Nullable
    public static String consumeAlias() {
        String a = alias;
        alias = null;
        return a;
    }
}
