package in.mohammad.ramiz.confess.data.model;

import androidx.annotation.Nullable;

/** Post.chapter — set when a confession row is a book chapter. */
public class ChapterMeta {
    public String bookId;
    public int chapterNo;
    @Nullable public String title;
    /** released · scheduled · draft */
    public String state;
    @Nullable public String publishAt;
}
