package in.mohammad.ramiz.confess.data.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** POST posts/{id}/comments: words, a GIF, or both (null fields are left out). */
public class CommentRequest {
    public final String text;
    @Nullable public final Gif.In gif;

    public CommentRequest(@NonNull String text, @Nullable Gif gif) {
        this.text = text;
        this.gif = gif == null ? null : new Gif.In(gif);
    }
}
