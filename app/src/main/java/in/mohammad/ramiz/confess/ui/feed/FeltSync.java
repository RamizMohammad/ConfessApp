package in.mohammad.ramiz.confess.ui.feed;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

import in.mohammad.ramiz.confess.core.PostEvents;
import in.mohammad.ramiz.confess.data.api.ApiCallback;
import in.mohammad.ramiz.confess.data.api.ApiException;
import in.mohammad.ramiz.confess.data.api.ErrorCode;
import in.mohammad.ramiz.confess.data.model.LikeState;
import in.mohammad.ramiz.confess.data.model.Post;
import in.mohammad.ramiz.confess.data.repo.PostRepository;

/**
 * Optimistic "felt" (like) toggling, shared by the feed and the confession screen.
 *  - flips the heart at once (through {@link PostEvents}, so every list agrees)
 *  - one request in flight per confession; quick double taps settle on the last choice
 *  - the server's count wins when it answers; on failure the flip is undone
 */
public final class FeltSync {

    public interface Lookup {
        /** Current copy of the confession, or null if no longer shown. */
        @Nullable Post find(@NonNull String postId);
    }

    public interface Errors {
        void onError(@NonNull ApiException e);
    }

    private final PostRepository repo = new PostRepository();
    private final Map<String, Boolean> wanted = new HashMap<>();
    private final Lookup lookup;
    private final Errors errors;

    public FeltSync(@NonNull Lookup lookup, @NonNull Errors errors) {
        this.lookup = lookup;
        this.errors = errors;
    }

    public void toggle(@NonNull Post post) {
        Post current = lookup.find(post.postId);
        if (current == null) current = post;
        boolean want = !current.likedByMe;

        Post optimistic = current.copy();
        optimistic.likedByMe = want;
        optimistic.likeCount = Math.max(0, current.likeCount + (want ? 1 : -1));
        PostEvents.updated(optimistic);

        boolean inFlight = wanted.containsKey(post.postId);
        wanted.put(post.postId, want);
        if (!inFlight) send(post.postId, want);
    }

    private void send(String postId, boolean want) {
        repo.setFelt(postId, want, new ApiCallback<LikeState>() {
            @Override
            public void onSuccess(LikeState s) {
                Boolean latest = wanted.get(postId);
                if (latest != null && latest != s.liked) {      // changed their mind meanwhile
                    send(postId, latest);
                    return;
                }
                wanted.remove(postId);
                Post p = lookup.find(postId);
                if (p != null && (p.likedByMe != s.liked || p.likeCount != s.likeCount)) {
                    Post confirmed = p.copy();
                    confirmed.likedByMe = s.liked;
                    confirmed.likeCount = s.likeCount;
                    PostEvents.updated(confirmed);
                }
            }

            @Override
            public void onError(ApiException e) {
                wanted.remove(postId);
                if (e.is(ErrorCode.NOT_FOUND)) {                // deleted meanwhile
                    PostEvents.removed(postId);
                } else {
                    Post p = lookup.find(postId);
                    if (p != null) {                            // undo the optimistic flip
                        Post reverted = p.copy();
                        reverted.likedByMe = !want;
                        reverted.likeCount = Math.max(0, p.likeCount + (want ? -1 : 1));
                        PostEvents.updated(reverted);
                    }
                }
                errors.onError(e);
            }
        });
    }
}
