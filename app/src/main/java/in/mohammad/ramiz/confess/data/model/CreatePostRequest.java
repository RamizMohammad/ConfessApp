package in.mohammad.ramiz.confess.data.model;

import androidx.annotation.Nullable;

import java.util.List;

/** POST posts. Null fields are left out of the JSON (Gson skips nulls). */
public class CreatePostRequest {
    public final String text;
    public final boolean commentsEnabled;
    /** vent · advice · listen · happy · heartbreak */
    @Nullable public final String mood;
    /** Unsent letter addressee ("my dad"). */
    @Nullable public final String letterTo;
    @Nullable public final PollIn poll;

    public static class PollIn {
        public final List<String> options;
        public final int durationHours;

        public PollIn(List<String> options, int durationHours) {
            this.options = options;
            this.durationHours = durationHours;
        }
    }

    public CreatePostRequest(String text, boolean commentsEnabled) {
        this(text, commentsEnabled, null, null, null);
    }

    public CreatePostRequest(String text, boolean commentsEnabled, @Nullable String mood,
                             @Nullable String letterTo, @Nullable PollIn poll) {
        this.text = text;
        this.commentsEnabled = commentsEnabled;
        this.mood = mood;
        this.letterTo = letterTo;
        this.poll = poll;
    }
}
