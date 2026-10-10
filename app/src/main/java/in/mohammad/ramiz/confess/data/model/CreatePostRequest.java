package in.mohammad.ramiz.confess.data.model;

/** POST posts. */
public class CreatePostRequest {
    public final String text;
    public final boolean commentsEnabled;

    public CreatePostRequest(String text, boolean commentsEnabled) {
        this.text = text;
        this.commentsEnabled = commentsEnabled;
    }
}
