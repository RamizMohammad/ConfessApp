package in.mohammad.ramiz.confess.core;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import in.mohammad.ramiz.confess.data.model.Post;

/**
 * App-wide "a confession changed" signal, so every list showing it agrees:
 * felt on the detail screen → feed card updates; deleted/reported anywhere →
 * gone from the feed and from "my letters"; posted from the composer → on top.
 *
 * Changes are applied idempotently by listeners, so a re-delivered change is harmless.
 */
public final class PostEvents {

    public enum Kind { CREATED, UPDATED, REMOVED, BLOCKED }

    public static final class Change {
        @NonNull public final Kind kind;
        @NonNull public final String postId;
        /** The new state for CREATED / UPDATED; null for REMOVED. */
        public final Post post;
        /** BLOCKED: the person I just blocked (postId is "" then). */
        public final String alias;

        Change(@NonNull Kind kind, @NonNull String postId, Post post) {
            this(kind, postId, post, null);
        }

        Change(@NonNull Kind kind, @NonNull String postId, Post post, String alias) {
            this.kind = kind;
            this.postId = postId;
            this.post = post;
            this.alias = alias;
        }

        /** BLOCKED and this post is by the blocked person. */
        public boolean hides(@NonNull Post p) {
            return kind == Kind.BLOCKED && alias != null && p.author != null && alias.equalsIgnoreCase(p.author.alias);
        }
    }

    private static final MutableLiveData<Change> CHANGES = new MutableLiveData<>();

    private PostEvents() { }

    public static LiveData<Change> changes() {
        return CHANGES;
    }

    public static void created(@NonNull Post post) {
        CHANGES.setValue(new Change(Kind.CREATED, post.postId, post.copy()));
    }

    public static void updated(@NonNull Post post) {
        CHANGES.setValue(new Change(Kind.UPDATED, post.postId, post.copy()));
    }

    /** I blocked someone: every confession of theirs disappears from my lists. */
    public static void blocked(@NonNull String alias) {
        CHANGES.setValue(new Change(Kind.BLOCKED, "", null, alias));
    }

    /** Deleted by me, or reported by me (hidden for me). */
    public static void removed(@NonNull String postId) {
        CHANGES.setValue(new Change(Kind.REMOVED, postId, null));
    }
}
