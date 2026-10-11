package in.mohammad.ramiz.confess.data.model;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/** A confession's poll. Option votes are null until I've voted (or it closed, or it's mine). */
public class Poll {
    public static class Option {
        public String text;
        @Nullable public Integer votes;
    }

    public List<Option> options = new ArrayList<>();
    public int totalVotes;
    @Nullable public Integer myVote;
    @Nullable public String closesAt;
    public boolean closed;

    public boolean resultsVisible() {
        return !options.isEmpty() && options.get(0).votes != null;
    }

    /** Cheap change key for DiffUtil. */
    public String signature() {
        return myVote + ":" + totalVotes + ":" + closed + ":" + resultsVisible();
    }
}
