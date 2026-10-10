package in.mohammad.ramiz.confess.ui.post;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.core.Event;
import in.mohammad.ramiz.confess.core.PostEvents;
import in.mohammad.ramiz.confess.data.api.ApiCallback;
import in.mohammad.ramiz.confess.data.api.ApiException;
import in.mohammad.ramiz.confess.data.api.ErrorCode;
import in.mohammad.ramiz.confess.data.model.Comment;
import in.mohammad.ramiz.confess.data.model.CommentPage;
import in.mohammad.ramiz.confess.data.model.Post;
import in.mohammad.ramiz.confess.data.repo.PostRepository;
import in.mohammad.ramiz.confess.ui.feed.FeltSync;
import retrofit2.Call;

/**
 * One confession + its responses. Old: PostDetails/CommentActivity loaded everything at once.
 *
 *  - GET posts/{id} and the first page of responses (oldest first), then cursor paging
 *  - my new responses are shown at the end right away (kept in "sent" until a page brings them)
 *  - felt / delete / report go through PostEvents so the feed agrees
 *  - responses can be deleted by their writer or by the confession's author
 */
public class PostDetailViewModel extends ViewModel {

    public enum Screen { LOADING, CONTENT, GONE, ERROR }

    public enum Footer { LOADING, ERROR, END }

    /** Immutable snapshot for the screen. */
    public static final class State {
        @NonNull public final Screen screen;
        @Nullable public final Post post;
        @NonNull public final List<Comment> comments;
        @NonNull public final Footer footer;
        @Nullable public final ApiException error;

        State(@NonNull Screen screen, @Nullable Post post, @NonNull List<Comment> comments,
              @NonNull Footer footer, @Nullable ApiException error) {
            this.screen = screen;
            this.post = post;
            this.comments = Collections.unmodifiableList(comments);
            this.footer = footer;
            this.error = error;
        }
    }

    /** A snackbar: a string resource, or an API error to explain. */
    public static final class Message {
        @StringRes public final int res;
        @Nullable public final ApiException error;

        Message(@StringRes int res, @Nullable ApiException error) {
            this.res = res;
            this.error = error;
        }
    }

    private final PostRepository repo = new PostRepository();
    private final MutableLiveData<State> state = new MutableLiveData<>();
    private final MutableLiveData<Boolean> sending = new MutableLiveData<>(false);
    private final MutableLiveData<Event<Message>> messages = new MutableLiveData<>();
    /** A response the server refused (moderation / closed) — shown above the input. */
    private final MutableLiveData<Event<ApiException>> sendErrors = new MutableLiveData<>();
    /** A response went out — clear the input and scroll to it. */
    private final MutableLiveData<Event<Comment>> sent = new MutableLiveData<>();
    /** Deleted or reported from here — close the screen with this message. */
    private final MutableLiveData<Event<Integer>> closed = new MutableLiveData<>();

    private String postId;
    private Screen screen = Screen.LOADING;
    @Nullable private Post post;
    private final List<Comment> loaded = new ArrayList<>();
    private final List<Comment> mine = new ArrayList<>();
    private Footer footer = Footer.LOADING;
    @Nullable private String cursor;
    private boolean commentsLoaded;
    @Nullable private ApiException error;
    @Nullable private Call<Post> postCall;
    @Nullable private Call<CommentPage> commentsCall;

    private final FeltSync felt = new FeltSync(
            id -> id.equals(postId) ? post : null,
            e -> say(0, e));
    private final Observer<PostEvents.Change> changes = this::apply;

    public PostDetailViewModel() {
        PostEvents.changes().observeForever(changes);
    }

    public LiveData<State> state() { return state; }
    public LiveData<Boolean> sending() { return sending; }
    public LiveData<Event<Message>> messages() { return messages; }
    public LiveData<Event<ApiException>> sendErrors() { return sendErrors; }
    public LiveData<Event<Comment>> sent() { return sent; }
    public LiveData<Event<Integer>> closed() { return closed; }

    @Nullable
    public Post post() { return post; }

    // ── Loading ──────────────────────────────────────────────────────────────

    /** Called once per screen; no-op after the first time (rotation etc.). */
    public void start(@NonNull String id) {
        if (postId != null) return;
        postId = id;
        load();
    }

    public void retry() {
        screen = Screen.LOADING;
        publish();
        load();
    }

    private void load() {
        if (postCall != null) postCall.cancel();
        postCall = repo.post(postId, new ApiCallback<Post>() {
            @Override
            public void onSuccess(Post p) {
                postCall = null;
                post = p;
                screen = Screen.CONTENT;
                error = null;
                publish();
            }

            @Override
            public void onError(ApiException e) {
                postCall = null;
                if (e.is(ErrorCode.NOT_FOUND)) {
                    screen = Screen.GONE;
                    PostEvents.removed(postId);
                } else {
                    screen = Screen.ERROR;
                    error = e;
                }
                publish();
            }
        });
        loadComments(null);
    }

    private void loadComments(@Nullable String from) {
        if (commentsCall != null) return;
        footer = Footer.LOADING;
        publish();
        commentsCall = repo.comments(postId, from, new ApiCallback<CommentPage>() {
            @Override
            public void onSuccess(CommentPage page) {
                commentsCall = null;
                if (from == null) loaded.clear();
                for (Comment c : page.comments) {
                    if (c != null && c.commentId != null && indexOf(loaded, c.commentId) < 0) loaded.add(c);
                }
                commentsLoaded = true;
                cursor = page.nextCursor;
                footer = cursor == null ? Footer.END : Footer.LOADING;
                publish();
            }

            @Override
            public void onError(ApiException e) {
                commentsCall = null;
                if (e.is(ErrorCode.NOT_FOUND)) return;            // the post load handles "gone"
                footer = Footer.ERROR;
                publish();
            }
        });
    }

    /** Near the end of the list. */
    public void loadMore() {
        if (screen != Screen.CONTENT || !commentsLoaded || cursor == null
                || commentsCall != null || footer == Footer.ERROR) return;
        loadComments(cursor);
    }

    public void retryMore() {
        if (!commentsLoaded) { loadComments(null); return; }
        footer = Footer.LOADING;
        if (cursor != null) loadComments(cursor); else publish();
    }

    // ── Felt / delete / report ───────────────────────────────────────────────

    public void toggleFelt() {
        if (post != null) felt.toggle(post);
    }

    public void react(@NonNull String reaction) {
        if (post != null) felt.react(post, reaction);
    }

    public void deletePost() {
        if (post == null) return;
        repo.delete(post.postId, new ApiCallback<JsonObject>() {
            @Override
            public void onSuccess(JsonObject data) {
                PostEvents.removed(postId);
                closed.setValue(new Event<>(R.string.deleted));
            }

            @Override
            public void onError(ApiException e) {
                if (e.is(ErrorCode.NOT_FOUND)) PostEvents.removed(postId);
                say(0, e);
            }
        });
    }

    public void report(@NonNull String reason) {
        if (post == null) return;
        repo.report(post.postId, reason, new ApiCallback<JsonObject>() {
            @Override
            public void onSuccess(JsonObject data) {
                PostEvents.removed(postId);                       // hidden for me everywhere
                closed.setValue(new Event<>(R.string.reported));
            }

            @Override
            public void onError(ApiException e) {
                say(0, e);
            }
        });
    }

    // ── Responses ────────────────────────────────────────────────────────────

    public boolean canDelete(@NonNull Comment c) {
        return c.isMine || (post != null && post.isMine);
    }

    public void send(@NonNull String text) {
        String body = text.trim();
        if (post == null || body.isEmpty() || Boolean.TRUE.equals(sending.getValue())) return;
        sending.setValue(true);
        repo.addComment(post.postId, body, new ApiCallback<Comment>() {
            @Override
            public void onSuccess(Comment c) {
                sending.setValue(false);
                mine.add(c);
                bumpCount(+1);
                publish();
                sent.setValue(new Event<>(c));
            }

            @Override
            public void onError(ApiException e) {
                sending.setValue(false);
                if (e.is(ErrorCode.COMMENTS_DISABLED) && post != null) {   // closed meanwhile
                    Post p = post.copy();
                    p.commentsEnabled = false;
                    PostEvents.updated(p);
                }
                if (e.is(ErrorCode.NOT_FOUND)) {
                    screen = Screen.GONE;
                    PostEvents.removed(postId);
                    publish();
                    return;
                }
                sendErrors.setValue(new Event<>(e));
            }
        });
    }

    public void deleteComment(@NonNull Comment c) {
        repo.deleteComment(c.commentId, new ApiCallback<JsonObject>() {
            @Override
            public void onSuccess(JsonObject data) {
                removeComment(c.commentId);
                say(R.string.response_deleted, null);
            }

            @Override
            public void onError(ApiException e) {
                if (e.is(ErrorCode.NOT_FOUND)) removeComment(c.commentId);
                say(0, e);
            }
        });
    }

    private void removeComment(String commentId) {
        int i = indexOf(loaded, commentId);
        if (i >= 0) loaded.remove(i);
        int j = indexOf(mine, commentId);
        if (j >= 0) mine.remove(j);
        if (i >= 0 || j >= 0) {
            bumpCount(-1);
            publish();
        }
    }

    private void bumpCount(int delta) {
        if (post == null) return;
        Post p = post.copy();
        p.commentCount = Math.max(0, p.commentCount + delta);
        PostEvents.updated(p);                 // updates this screen (apply) and the feed
    }

    // ── Shared changes ───────────────────────────────────────────────────────

    private void apply(@Nullable PostEvents.Change c) {
        if (c == null || postId == null || !postId.equals(c.postId)) return;
        if (c.kind == PostEvents.Kind.UPDATED && c.post != null && post != null) {
            post = c.post.copy();
            publish();
        } else if (c.kind == PostEvents.Kind.REMOVED && screen == Screen.CONTENT) {
            screen = Screen.GONE;
            publish();
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void publish() {
        List<Comment> all = new ArrayList<>(loaded);
        for (Comment c : mine) if (indexOf(loaded, c.commentId) < 0) all.add(c);
        state.setValue(new State(screen, post == null ? null : post.copy(), all, footer, error));
    }

    private static int indexOf(List<Comment> list, String id) {
        for (int i = 0; i < list.size(); i++) if (id.equals(list.get(i).commentId)) return i;
        return -1;
    }

    private void say(@StringRes int res, @Nullable ApiException e) {
        messages.setValue(new Event<>(new Message(res, e)));
    }

    @Override
    protected void onCleared() {
        PostEvents.changes().removeObserver(changes);
        if (postCall != null) postCall.cancel();
        if (commentsCall != null) commentsCall.cancel();
    }
}
