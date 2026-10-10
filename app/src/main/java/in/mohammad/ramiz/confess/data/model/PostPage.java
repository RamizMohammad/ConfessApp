package in.mohammad.ramiz.confess.data.model;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/** A page of confessions; pass nextCursor back until it is null. */
public class PostPage {
    public List<Post> posts = new ArrayList<>();
    @Nullable public String nextCursor;
}
