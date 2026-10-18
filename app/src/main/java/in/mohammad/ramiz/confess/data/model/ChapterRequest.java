package in.mohammad.ramiz.confess.data.model;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * One chapter to add (POST books/{id}/chapters) or a change to one (PATCH …/chapters/{no}).
 * release: now · schedule (publishAt = ISO-8601 with offset) · draft. Null fields are left alone.
 */
public class ChapterRequest {
    public static final String NOW = "now";
    public static final String SCHEDULE = "schedule";
    public static final String DRAFT = "draft";

    @Nullable public String text;
    @Nullable public String title;
    @Nullable public Boolean clearTitle;
    @Nullable public String mood;
    @Nullable public Boolean commentsEnabled;
    @Nullable public String release;
    @Nullable public String publishAt;

    /** The body of POST books/{id}/chapters. */
    public static class Batch {
        public List<ChapterRequest> chapters = new ArrayList<>();

        public Batch(List<ChapterRequest> chapters) {
            this.chapters = chapters;
        }
    }

    /** The body of PUT books/{id}/order: unreleased chapter numbers in their new order. */
    public static class Order {
        public List<Integer> chapters;

        public Order(List<Integer> chapters) {
            this.chapters = chapters;
        }
    }
}
