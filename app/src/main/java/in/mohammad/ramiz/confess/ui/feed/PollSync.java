package in.mohammad.ramiz.confess.ui.feed;

import androidx.annotation.NonNull;

import java.util.HashSet;
import java.util.Set;

import in.mohammad.ramiz.confess.core.PostEvents;
import in.mohammad.ramiz.confess.data.api.ApiCallback;
import in.mohammad.ramiz.confess.data.api.ApiException;
import in.mohammad.ramiz.confess.data.api.ErrorCode;
import in.mohammad.ramiz.confess.data.model.Post;
import in.mohammad.ramiz.confess.data.repo.PostRepository;

/**
 * Poll votes, shared by the feed, profiles and the confession screen. One request per poll at a
 * time; the server's answer (the confession with results) goes out through {@link PostEvents}
 * so every list showing it reveals the results together.
 */
public final class PollSync {

    private final PostRepository repo = new PostRepository();
    private final Set<String> inFlight = new HashSet<>();
    private final FeltSync.Errors errors;

    public PollSync(@NonNull FeltSync.Errors errors) {
        this.errors = errors;
    }

    public void vote(@NonNull Post post, int option) {
        if (post.poll == null || post.poll.closed || post.poll.myVote != null) return;
        if (!inFlight.add(post.postId)) return;
        repo.vote(post.postId, option, new ApiCallback<Post>() {
            @Override public void onSuccess(Post updated) {
                inFlight.remove(post.postId);
                PostEvents.updated(updated);
            }
            @Override public void onError(ApiException e) {
                inFlight.remove(post.postId);
                if (e.is(ErrorCode.NOT_FOUND)) PostEvents.removed(post.postId);
                errors.onError(e);
            }
        });
    }
}
