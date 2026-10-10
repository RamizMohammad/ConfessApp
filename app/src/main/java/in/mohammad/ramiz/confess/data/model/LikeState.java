package in.mohammad.ramiz.confess.data.model;

import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

/** POST/DELETE posts/{id}/like. */
public class LikeState {
    public boolean liked;
    public int likeCount;
    /** felt · hug · same · strength · tears — null when not reacted (older server: absent). */
    @Nullable public String myReaction;
    @Nullable public Map<String, Integer> reactions = new HashMap<>();
}
