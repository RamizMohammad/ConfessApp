package in.mohammad.ramiz.confess.data.model;

import androidx.annotation.Nullable;

/** One line of a book's contents. state / publishAt only on my own book. */
public class ChapterEntry {
    public static final String RELEASED = "released";
    public static final String SCHEDULED = "scheduled";
    public static final String DRAFT = "draft";

    public String postId;
    public int chapterNo;
    /** Null → shown as "Chapter n". */
    @Nullable public String title;
    public String excerpt;
    @Nullable public String mood;
    public int likeCount;
    public int commentCount;
    @Nullable public String releasedAt;
    @Nullable public String state;
    @Nullable public String publishAt;

    public boolean isReleased() {
        return state == null || RELEASED.equals(state);
    }
}
