package in.mohammad.ramiz.confess.data.model;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/** A page of responses (oldest first). */
public class CommentPage {
    public List<Comment> comments = new ArrayList<>();
    @Nullable public String nextCursor;
}
