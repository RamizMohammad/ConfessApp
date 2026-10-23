package in.mohammad.ramiz.confess.data.model;

/** A response to a confession. */
public class Comment {
    public String commentId;
    public String postId;
    public Author author;
    public String text;
    /** A GIF response (the text may then be empty). */
    @androidx.annotation.Nullable public Gif gif;
    public boolean isMine;
    public boolean isPostAuthor;
    public String createdAt;
}
