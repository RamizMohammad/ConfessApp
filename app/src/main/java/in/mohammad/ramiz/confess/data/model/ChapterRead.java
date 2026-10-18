package in.mohammad.ramiz.confess.data.model;

import androidx.annotation.Nullable;

/** GET books/{id}/chapters/{no}: one chapter (a Post) plus the neighbouring chapter numbers. */
public class ChapterRead {
    public Book book;
    public Post chapter;
    @Nullable public Integer prev;
    @Nullable public Integer next;
}
