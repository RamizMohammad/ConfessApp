package in.mohammad.ramiz.confess.data.model;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/** GET stories/{id}/viewers (writer only). */
public class StoryViewers {
    public static class Viewer {
        public String alias;
        public String avatar;
        public String viewedAt;
        /** felt · hug · same · strength · tears, or null when they only looked. */
        @Nullable public String reaction;
    }

    public List<Viewer> users = new ArrayList<>();
    public int viewCount;
    public int reactionCount;
    @Nullable public String nextCursor;
}
