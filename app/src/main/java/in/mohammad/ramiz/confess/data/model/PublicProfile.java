package in.mohammad.ramiz.confess.data.model;

/** Someone else's profile (GET users/{alias}). */
public class PublicProfile {
    public String alias;
    public String about;
    public String avatar;
    public String createdAt;
    public int postCount;
    public int followers;
    public int following;
    public boolean isFollowing;
    public boolean isMe;
}
