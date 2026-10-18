package in.mohammad.ramiz.confess.data.model;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/** A page of books (GET books, books/mine); pass nextCursor back until it is null. */
public class BookPage {
    public List<Book> books = new ArrayList<>();
    @Nullable public String nextCursor;
}
