package in.mohammad.ramiz.confess.data.model;

import java.util.ArrayList;
import java.util.List;

/** GET books/{id} (and every writer action): the book and its contents, in chapter order. */
public class BookDetail {
    public Book book;
    public List<ChapterEntry> chapters = new ArrayList<>();
}
