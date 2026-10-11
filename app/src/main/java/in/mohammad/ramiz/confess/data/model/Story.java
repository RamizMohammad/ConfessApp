package in.mohammad.ramiz.confess.data.model;

import androidx.annotation.Nullable;

/** One text story (≤ 100 chars, 24 h). viewCount only on my own. */
public class Story {
    public String id;
    public String text;
    public int style;
    public boolean seen;
    public String createdAt;
    public String expiresAt;
    @Nullable public Integer viewCount;
    /** Mine only: how many viewers reacted. */
    @Nullable public Integer reactionCount;
    /** Others' only: my reaction (felt · hug · same · strength · tears) or null. */
    @Nullable public String myReaction;
}
