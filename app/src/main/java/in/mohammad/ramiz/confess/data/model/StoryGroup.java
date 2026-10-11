package in.mohammad.ramiz.confess.data.model;

import java.util.ArrayList;
import java.util.List;

/** Everything one person has live, oldest → newest (the tray shows one bubble per group). */
public class StoryGroup {
    public Author author;
    public boolean isMe;
    public boolean followed;
    public boolean hasUnseen;
    public String latestAt;
    public List<Story> stories = new ArrayList<>();
}
