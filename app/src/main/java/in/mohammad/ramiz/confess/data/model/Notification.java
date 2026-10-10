package in.mohammad.ramiz.confess.data.model;

import androidx.annotation.Nullable;

/**
 * In-app activity: someone felt (like) or responded to (comment) your confession,
 * followed you (follow — no post), or someone you follow wrote a new letter (post).
 */
public class Notification {
    public static final String TYPE_LIKE = "like";
    public static final String TYPE_COMMENT = "comment";
    public static final String TYPE_FOLLOW = "follow";
    public static final String TYPE_POST = "post";
    
    public String id;
    public String type;
    public Author actor;
    @Nullable public String postId;
    @Nullable public String postExcerpt;
    @Nullable public String commentExcerpt;
    public boolean read;
    public String createdAt;
}
