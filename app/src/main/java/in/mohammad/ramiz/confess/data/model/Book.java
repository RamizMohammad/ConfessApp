package in.mohammad.ramiz.confess.data.model;

import androidx.annotation.Nullable;

/**
 * A book (server 3.6): a serialised confession whose chapters come out one by one.
 * chapterCount = released chapters. The writer-only fields are null on other people's books.
 */
public class Book {
    public static final String DRAFT = "draft";
    public static final String ONGOING = "ongoing";
    public static final String COMPLETED = "completed";

    public String bookId;
    public String title;
    public String blurb;
    /** Cover background 0–4 (the story palette). */
    public int cover;
    public Author author;
    public boolean isMine;
    /** draft · ongoing · completed */
    public String status;
    public int chapterCount;
    public int followerCount;
    /** In my library (I follow it: a push when a chapter comes out). */
    public boolean following;
    /** The furthest chapter I opened, or null. */
    @Nullable public Integer lastReadChapter;
    public boolean hasUnread;
    @Nullable public String publishedAt;
    @Nullable public String lastReleaseAt;
    public String createdAt;

    // Mine only
    @Nullable public Integer totalChapters;
    @Nullable public Integer scheduledCount;
    @Nullable public Integer draftCount;
    @Nullable public String nextReleaseAt;
    @Nullable public Boolean hidden;

    public boolean isCompleted() {
        return COMPLETED.equals(status);
    }

    public boolean isDraft() {
        return DRAFT.equals(status);
    }
}
