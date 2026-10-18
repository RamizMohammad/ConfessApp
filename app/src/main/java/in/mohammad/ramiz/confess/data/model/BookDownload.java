package in.mohammad.ramiz.confess.data.model;

import java.util.ArrayList;
import java.util.List;

/** GET /books/{id}/download — the book and every released chapter in full, for reading offline. */
public class BookDownload {
    public Book book;
    public List<Post> chapters = new ArrayList<>();
}
