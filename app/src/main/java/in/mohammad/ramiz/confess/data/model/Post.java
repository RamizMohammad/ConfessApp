package in.mohammad.ramiz.confess.data.model;

import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

/** A confession. likeCount/commentCount are plain numbers — format on device. */
public class Post {
    public String postId;
    public Author author;
    public String text;
    public boolean commentsEnabled;
    public int likeCount;
    public int commentCount;
    public boolean likedByMe;
    /** felt · hug · same · strength · tears — null when I haven't reacted. */
    @Nullable public String myReaction;
    /** {reaction: count}, non-zero only. May be null from an older server. */
    @Nullable public Map<String, Integer> reactions;
    public boolean isMine;
    public String createdAt;
    /** vent · advice · listen · happy · heartbreak, or null. */
    @Nullable public String mood;
    /** Unsent letter addressee, or null. */
    @Nullable public String letterTo;
    /** GIF confession: a GIPHY GIF (the text is then ≤ 150). */
    @Nullable public Gif gif;
    @Nullable public Poll poll;
    /** Set when this confession is a book chapter (opened from a reaction push, say). */
    @Nullable public ChapterMeta chapter;
    /** In the feed with bursts: this card stands for a burst of several (null = on its own). */
    @Nullable public BurstInfo burst;
    /** Sensitivity 0-100 from moderation (null = not scored). */
    @Nullable public Integer riskScore;
    /** Scored at or above the server's threshold: blurred for others until tapped. */
    public boolean sensitive;

    /** Shallow copy — lists hand out copies so DiffUtil can see what changed. */
    public Post copy() {
        Post p = new Post();
        p.postId = postId;
        p.author = author;
        p.text = text;
        p.commentsEnabled = commentsEnabled;
        p.likeCount = likeCount;
        p.commentCount = commentCount;
        p.likedByMe = likedByMe;
        p.myReaction = myReaction;
        p.reactions = reactions == null ? null : new HashMap<>(reactions);
        p.isMine = isMine;
        p.createdAt = createdAt;
        p.mood = mood;
        p.letterTo = letterTo;
        p.gif = gif;
        p.poll = poll;
        p.chapter = chapter;
        p.burst = burst;
        p.riskScore = riskScore;
        p.sensitive = sensitive;
        return p;
    }
}
