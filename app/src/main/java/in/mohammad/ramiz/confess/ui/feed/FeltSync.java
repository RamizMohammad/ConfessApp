package in.mohammad.ramiz.confess.ui.feed;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import in.mohammad.ramiz.confess.core.PostEvents;
import in.mohammad.ramiz.confess.data.api.ApiCallback;
import in.mohammad.ramiz.confess.data.api.ApiException;
import in.mohammad.ramiz.confess.data.api.ErrorCode;
import in.mohammad.ramiz.confess.data.model.LikeState;
import in.mohammad.ramiz.confess.data.model.Post;
import in.mohammad.ramiz.confess.data.repo.PostRepository;

/**
 * Optimistic reactions ("felt" and friends), shared by the feed, profiles and the confession screen.
 *  - tap toggles ❤️; picking from the long-press bar sets (or switches to) that reaction
 *  - the card changes at once (through {@link PostEvents}, so every list agrees)
 *  - one request in flight per confession; quick changes settle on the last choice
 *  - the server's numbers win when it answers; on failure the change is undone
 */
public final class FeltSync {

    public interface Lookup {
        /** Current copy of the confession, or null if no longer shown. */
        @Nullable Post find(@NonNull String postId);
    }

    public interface Errors {
        void onError(@NonNull ApiException e);
    }

    /** Marks "no reaction" in {@link #wanted} (HashMap can't tell a null value from a missing key cleanly). */
    private static final String NONE = "";

    private final PostRepository repo = new PostRepository();
    private final Map<String, String> wanted = new HashMap<>();
    private final Lookup lookup;
    private final Errors errors;

    public FeltSync(@NonNull Lookup lookup, @NonNull Errors errors) {
        this.lookup = lookup;
        this.errors = errors;
    }

    /** Tap on the heart: no reaction → ❤️; any reaction → none. */
    public void toggle(@NonNull Post post) {
        Post current = current(post);
        set(current, Reactions.mine(current) == null ? Reactions.FELT : null);
    }

    /** Picked from the reaction bar: set it (picking my current one again removes it). */
    public void react(@NonNull Post post, @NonNull String reaction) {
        Post current = current(post);
        set(current, reaction.equals(Reactions.mine(current)) ? null : reaction);
    }

    private Post current(@NonNull Post post) {
        Post c = lookup.find(post.postId);
        return c != null ? c : post;
    }

    private void set(@NonNull Post current, @Nullable String want) {
        if (Objects.equals(Reactions.mine(current), want)) return;
        Post optimistic = current.copy();
        Reactions.apply(optimistic, want);
        PostEvents.updated(optimistic);

        boolean inFlight = wanted.containsKey(current.postId);
        wanted.put(current.postId, want == null ? NONE : want);
        if (!inFlight) send(current.postId, want, Reactions.mine(current));
    }

    /** @param before what I had before this request — used to undo on failure */
    private void send(String postId, @Nullable String want, @Nullable String before) {
        repo.setReaction(postId, want, new ApiCallback<LikeState>() {
            @Override
            public void onSuccess(LikeState s) {
                String latestRaw = wanted.get(postId);
                String latest = latestRaw == null || latestRaw.isEmpty() ? null : latestRaw;
                if (latestRaw != null && !Objects.equals(latest, want)) {   // changed their mind meanwhile
                    send(postId, latest, want);
                    return;
                }
                wanted.remove(postId);
                Post p = lookup.find(postId);
                if (p == null) return;
                Post confirmed = p.copy();
                confirmed.likedByMe = s.liked;
                confirmed.likeCount = s.likeCount;
                confirmed.myReaction = s.liked ? (s.myReaction != null ? s.myReaction : want) : null;
                if (s.reactions != null) confirmed.reactions = new HashMap<>(s.reactions);
                PostEvents.updated(confirmed);
            }

            @Override
            public void onError(ApiException e) {
                wanted.remove(postId);
                if (e.is(ErrorCode.NOT_FOUND)) {                // deleted meanwhile
                    PostEvents.removed(postId);
                } else {
                    Post p = lookup.find(postId);
                    if (p != null) {                            // undo the optimistic change
                        Post reverted = p.copy();
                        Reactions.apply(reverted, before);
                        PostEvents.updated(reverted);
                    }
                }
                errors.onError(e);
            }
        });
    }
}
