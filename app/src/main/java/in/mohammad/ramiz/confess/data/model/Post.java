package in.mohammad.ramiz.confess.data.model;

/** A confession. likeCount/commentCount are plain numbers — format on device. */
public class Post {
    public String postId;
    public Author author;
    public String text;
    public boolean commentsEnabled;
    public int likeCount;
    public int commentCount;
    public boolean likedByMe;
    public boolean isMine;
    public String createdAt;

    /** Shallow copy — lists hand out copies so DiffUtil can see what changed. */
    public Post copy() {
        Post p = new Post();
        p.postId = postId;
        p.author = author;
        p.text = text;
        p.commentsEnabled = commentsEnabled;
        p.likeCount = likeCount;
        p.commentCount = commentCount;
        p.likedByMe = likedByMe;
        p.isMine = isMine;
        p.createdAt = createdAt;
        return p;
    }
}
