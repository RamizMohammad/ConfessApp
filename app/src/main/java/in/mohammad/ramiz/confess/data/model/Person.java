package in.mohammad.ramiz.confess.data.model;

import androidx.annotation.Nullable;

/** Someone in a followers / following list. */
public class Person {
    public String alias;
    public String about;
    public String avatar;
    public boolean followedByMe;
    @Nullable public String followedAt;
}
