package in.mohammad.ramiz.confess.data.model;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/** GET /posts/bursts/{id} — a set of the burst, oldest first, and how many it has in all. */
public class BurstPage {
    public List<Post> posts = new ArrayList<>();
    @Nullable public String nextCursor;
    public int total;
}
