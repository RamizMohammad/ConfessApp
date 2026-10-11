package in.mohammad.ramiz.confess.data.model;

import androidx.annotation.Nullable;

/**
 * The per-type push switches (Me.notifications, POST users/me/notifications).
 * As a request, null fields are left unchanged (Gson omits them).
 */
public class NotificationPrefs {
    @Nullable public Boolean likes;
    @Nullable public Boolean comments;
    @Nullable public Boolean follows;
    @Nullable public Boolean followingPosts;
    @Nullable public Boolean newPosts;
    @Nullable public Boolean nudges;

    public static boolean on(@Nullable Boolean b) {
        return b == null || b;
    }
}
