package in.mohammad.ramiz.confess.ui.me;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import in.mohammad.ramiz.confess.data.api.ApiCallback;
import in.mohammad.ramiz.confess.data.api.ApiException;
import in.mohammad.ramiz.confess.data.api.ErrorCode;
import in.mohammad.ramiz.confess.data.model.Comment;
import in.mohammad.ramiz.confess.data.model.CommentPage;
import in.mohammad.ramiz.confess.data.model.Liker;
import in.mohammad.ramiz.confess.data.model.LikerPage;
import in.mohammad.ramiz.confess.data.model.Post;
import in.mohammad.ramiz.confess.data.model.PostPage;
import in.mohammad.ramiz.confess.data.repo.PostRepository;
import retrofit2.Call;

/**
 * My letters that were felt (or responded to), each expandable to show who.
 * Letters come from posts/mine (only those with a count > 0 are shown);
 * people load per letter when expanded: posts/{id}/likes or posts/{id}/comments.
 */
public class ReceivedViewModel extends ViewModel {

    public enum Screen { LOADING, CONTENT, EMPTY, ERROR }

    public enum Footer { NONE, LOADING, ERROR, END }

    /** One letter's expanded part. Immutable; replaced on every change. */
    public static final class Group {
        public final boolean expanded;
        public final boolean loading;
        public final boolean failed;
        public final boolean loadedOnce;
        @NonNull public final List<Liker> likers;
        @NonNull public final List<Comment> comments;
        @Nullable public final String cursor;

        Group(boolean expanded, boolean loading, boolean failed, boolean loadedOnce,
              @NonNull List<Liker> likers, @NonNull List<Comment> comments, @Nullable String cursor) {
            this.expanded = expanded;
            this.loading = loading;
            this.failed = failed;
            this.loadedOnce = loadedOnce;
            this.likers = Collections.unmodifiableList(likers);
            this.comments = Collections.unmodifiableList(comments);
            this.cursor = cursor;
        }

        static Group empty() {
            return new Group(false, false, false, false, new ArrayList<>(), new ArrayList<>(), null);
        }

        Group with(boolean expanded, boolean loading, boolean failed) {
            return new Group(expanded, loading, failed, loadedOnce, new ArrayList<>(likers), new ArrayList<>(comments), cursor);
        }
    }

    public static final class State {
        @NonNull public final Screen screen;
        @NonNull public final List<Post> posts;
        @NonNull public final Map<String, Group> groups;
        @NonNull public final Footer footer;
        public final boolean refreshing;
        @Nullable public final ApiException error;

        State(@NonNull Screen screen, @NonNull List<Post> posts, @NonNull Map<String, Group> groups,
              @NonNull Footer footer, boolean refreshing, @Nullable ApiException error) {
            this.screen = screen;
            this.posts = Collections.unmodifiableList(posts);
            this.groups = Collections.unmodifiableMap(groups);
            this.footer = footer;
            this.refreshing = refreshing;
            this.error = error;
        }
    }

    private final PostRepository repo = new PostRepository();
    private final MutableLiveData<State> state = new MutableLiveData<>();

    private boolean felt;
    private boolean started, loaded, refreshing;
    private Screen screen = Screen.LOADING;
    private Footer footer = Footer.NONE;
    private final List<Post> posts = new ArrayList<>();
    private final Map<String, Group> groups = new HashMap<>();
    @Nullable private String cursor;
    @Nullable private ApiException error;
    @Nullable private Call<PostPage> postsCall;

    public LiveData<State> state() { return state; }

    public void start(boolean feltMode) {
        if (started) return;
        started = true;
        felt = feltMode;
        publish();
        loadPosts(null);
    }

    public void refresh() {
        if (screen == Screen.ERROR) screen = Screen.LOADING; else refreshing = true;
        publish();
        loadPosts(null);
    }

    // ── My letters (filtered) ────────────────────────────────────────────────

    private void loadPosts(@Nullable String from) {
        if (postsCall != null) {
            if (from != null) return;
            postsCall.cancel();
        }
        postsCall = repo.myPosts(from, new ApiCallback<PostPage>() {
            @Override
            public void onSuccess(PostPage page) {
                postsCall = null;
                refreshing = false;
                if (from == null) { posts.clear(); groups.clear(); }
                for (Post p : page.posts) {
                    if (p == null || p.postId == null || count(p) <= 0 || indexOf(p.postId) >= 0) continue;
                    posts.add(p);
                }
                loaded = true;
                cursor = page.nextCursor;
                footer = cursor == null ? Footer.END : Footer.NONE;
                screen = posts.isEmpty() && cursor == null ? Screen.EMPTY : Screen.CONTENT;
                error = null;
                publish();
            }

            @Override
            public void onError(ApiException e) {
                postsCall = null;
                refreshing = false;
                if (posts.isEmpty() && from == null) { screen = Screen.ERROR; error = e; }
                else footer = Footer.ERROR;
                publish();
            }
        });
    }

    public void loadMore() {
        if (!loaded || cursor == null || postsCall != null || footer == Footer.ERROR) return;
        footer = Footer.LOADING;
        publish();
        loadPosts(cursor);
    }

    public void retryMore() {
        footer = Footer.NONE;
        loadMore();
    }

    // ── Expand / people ──────────────────────────────────────────────────────

    public void toggle(@NonNull String postId) {
        Group g = group(postId);
        boolean open = !g.expanded;
        groups.put(postId, g.with(open, g.loading, g.failed));
        publish();
        if (open && !g.loadedOnce && !g.loading) loadPeople(postId, null);
    }

    public void morePeople(@NonNull String postId) {
        Group g = group(postId);
        if (g.loading) return;
        loadPeople(postId, g.failed && !g.loadedOnce ? null : g.cursor);
    }

    private void loadPeople(String postId, @Nullable String from) {
        Group g = group(postId);
        groups.put(postId, g.with(g.expanded, true, false));
        publish();
        if (felt) {
            repo.likers(postId, from, new ApiCallback<LikerPage>() {
                @Override public void onSuccess(LikerPage page) {
                    Group cur = group(postId);
                    List<Liker> list = new ArrayList<>(from == null ? new ArrayList<>() : cur.likers);
                    list.addAll(page.users);
                    groups.put(postId, new Group(cur.expanded, false, false, true, list,
                            new ArrayList<>(), page.nextCursor));
                    publish();
                }
                @Override public void onError(ApiException e) { peopleFailed(postId, e); }
            });
        } else {
            repo.comments(postId, from, new ApiCallback<CommentPage>() {
                @Override public void onSuccess(CommentPage page) {
                    Group cur = group(postId);
                    List<Comment> list = new ArrayList<>(from == null ? new ArrayList<>() : cur.comments);
                    list.addAll(page.comments);
                    groups.put(postId, new Group(cur.expanded, false, false, true, new ArrayList<>(),
                            list, page.nextCursor));
                    publish();
                }
                @Override public void onError(ApiException e) { peopleFailed(postId, e); }
            });
        }
    }

    private void peopleFailed(String postId, ApiException e) {
        if (e.is(ErrorCode.NOT_FOUND)) {                 // letter deleted meanwhile
            int i = indexOf(postId);
            if (i >= 0) posts.remove(i);
            groups.remove(postId);
            publish();
            return;
        }
        Group g = group(postId);
        groups.put(postId, g.with(g.expanded, false, true));
        publish();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    public boolean feltMode() {
        return felt;
    }

    private int count(Post p) {
        return felt ? p.likeCount : p.commentCount;
    }

    private Group group(String postId) {
        Group g = groups.get(postId);
        return g != null ? g : Group.empty();
    }

    private int indexOf(String postId) {
        for (int i = 0; i < posts.size(); i++) if (postId.equals(posts.get(i).postId)) return i;
        return -1;
    }

    private void publish() {
        state.setValue(new State(screen, new ArrayList<>(posts), new HashMap<>(groups),
                screen == Screen.CONTENT ? footer : Footer.NONE, refreshing, error));
    }

    @Override
    protected void onCleared() {
        if (postsCall != null) postsCall.cancel();
    }
}
