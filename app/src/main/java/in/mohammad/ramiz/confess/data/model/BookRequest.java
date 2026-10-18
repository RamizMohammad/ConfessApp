package in.mohammad.ramiz.confess.data.model;

import androidx.annotation.Nullable;

/** POST books (title required) and PATCH books/{id} (only the fields set are changed). */
public class BookRequest {
    @Nullable public String title;
    @Nullable public String blurb;
    @Nullable public Integer cover;
    @Nullable public Boolean completed;

    public static BookRequest create(String title, String blurb, int cover) {
        BookRequest r = new BookRequest();
        r.title = title;
        r.blurb = blurb;
        r.cover = cover;
        return r;
    }

    public static BookRequest completed(boolean done) {
        BookRequest r = new BookRequest();
        r.completed = done;
        return r;
    }
}
