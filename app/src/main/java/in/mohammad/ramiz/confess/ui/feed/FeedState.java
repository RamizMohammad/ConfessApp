package in.mohammad.ramiz.confess.ui.feed;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

import in.mohammad.ramiz.confess.data.api.ApiException;
import in.mohammad.ramiz.confess.data.model.Post;

/** What one feed tab (Latest / Most felt) shows right now. Immutable snapshot. */
public final class FeedState {

    /** The whole-screen state. */
    public enum Screen { LOADING, CONTENT, EMPTY, ERROR }

    /** The row under the last card. */
    public enum Footer { NONE, LOADING, ERROR, END }

    @NonNull public final Screen screen;
    @NonNull public final List<Post> posts;
    @NonNull public final Footer footer;
    /** Pull-to-refresh spinner. */
    public final boolean refreshing;
    /** Why the first page failed (screen == ERROR). */
    @Nullable public final ApiException error;

    FeedState(@NonNull Screen screen, @NonNull List<Post> posts, @NonNull Footer footer,
              boolean refreshing, @Nullable ApiException error) {
        this.screen = screen;
        this.posts = Collections.unmodifiableList(posts);
        this.footer = footer;
        this.refreshing = refreshing;
        this.error = error;
    }
}
