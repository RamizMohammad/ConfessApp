package in.mohammad.ramiz.confess.data.model;

import androidx.annotation.Nullable;

/** In-app activity: someone felt (type=like) or responded to (type=comment) your confession. */
public class Notification {
    public static final String TYPE_LIKE = "like";
    public static final String TYPE_COMMENT = "comment";
    
    public String id;
    public String type;
    public Author actor;
    public String postId;
    public String postExcerpt;
    @Nullable public String commentExcerpt;
    public boolean read;
    public String createdAt;
}
