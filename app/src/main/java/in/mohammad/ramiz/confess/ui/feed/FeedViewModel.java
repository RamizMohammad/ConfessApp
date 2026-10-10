package in.mohammad.ramiz.confess.ui.feed;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.core.Event;
import in.mohammad.ramiz.confess.core.PostEvents;
import in.mohammad.ramiz.confess.data.api.ApiCallback;
import in.mohammad.ramiz.confess.data.api.ApiException;
import in.mohammad.ramiz.confess.data.api.ErrorCode;
import in.mohammad.ramiz.confess.data.model.Post;
import in.mohammad.ramiz.confess.data.model.PostPage;
import in.mohammad.ramiz.confess.data.model.UnreadCount;
import in.mohammad.ramiz.confess.data.repo.ActivityRepository;
import in.mohammad.ramiz.confess.data.repo.PostRepository;
import retrofit2.Call;

/**
 * Feed logic for both tabs. Old: HomeFragment did paging/likes inline with Volley.
 *
 *  - each sort keeps its own list + cursor, so switching tabs is instant
 *  - felt is optimistic: the card flips immediately, the server confirms the count;
 *    quick double taps settle on the last state the user chose
 *  - delete / report / felt changes go through {@link PostEvents} so both tabs
 *    (and later the detail screen and "my letters") stay in step
 */
public class FeedViewModel extends ViewModel {

    /** A one-off message for a snackbar: a string resource, or an API error to explain. */
    public static final class Message {
        @StringRes public final int res;
        @Nullable public final ApiException error;

        Message(@StringRes int res, @Nullable ApiException error) {
            this.res = res;
            this.error = error;
        }
    }

    private final PostRepository repo = new PostRepository();
    private final Map<String, Tab> tabs = new HashMap<>();
    private final MutableLiveData<Integer> unread = new MutableLiveData<>(0);
    private final MutableLiveData<Event<Message>> messages = new MutableLiveData<>();

    private final FeltSync felt = new FeltSync(this::find, e -> say(0, e));

    private final Observer<PostEvents.Change> changeObserver = this::apply;

    public FeedViewModel() {
        tabs.put(PostRepository.SORT_LATEST, new Tab(PostRepository.SORT_LATEST));
        tabs.put(PostRepository.SORT_TOP, new Tab(PostRepository.SORT_TOP));
        PostEvents.changes().observeForever(changeObserver);
    }

    public LiveData<FeedState> state(@NonNull String sort) {
        return tab(sort).live;
    }

    public LiveData<Integer> unread() {
        return unread;
    }

    public LiveData<Event<Message>> messages() {
        return messages;
    }

    // ── Loading ──────────────────────────────────────────────────────────────

    /** First load for a tab (no-op if it already has data or is loading). */
    public void start(@NonNull String sort) {
        Tab t = tab(sort);
        if (t.loaded || t.firstCall != null) return;
        t.screen = FeedState.Screen.LOADING;
        t.publish();
        loadFirst(t);
    }

    /** Pull-to-refresh, or "Try again" on the error screen. */
    public void refresh(@NonNull String sort) {
        Tab t = tab(sort);
        if (t.firstCall != null) return;
        if (t.screen == FeedState.Screen.ERROR) {
            t.screen = FeedState.Screen.LOADING;
        } else {
            t.refreshing = true;
        }
        t.publish();
        loadFirst(t);
    }

    private void loadFirst(Tab t) {
        if (t.moreCall != null) { t.moreCall.cancel(); t.moreCall = null; }
        t.firstCall = repo.feed(t.sort, null, new ApiCallback<PostPage>() {
            @Override
            public void onSuccess(PostPage page) {
                t.firstCall = null;
                t.loaded = true;
                t.refreshing = false;
                t.posts.clear();
                addUnique(t, page.posts);
                t.cursor = page.nextCursor;
                t.footer = t.cursor == null ? FeedState.Footer.END : FeedState.Footer.NONE;
                t.screen = t.posts.isEmpty() ? FeedState.Screen.EMPTY : FeedState.Screen.CONTENT;
                t.error = null;
                t.publish();
            }

            @Override
            public void onError(ApiException e) {
                t.firstCall = null;
                t.refreshing = false;
                if (t.posts.isEmpty()) {
                    t.screen = FeedState.Screen.ERROR;
                    t.error = e;
                } else {
                    say(0, e);                     // keep what we have, explain in a snackbar
                }
                t.publish();
            }
        });
    }

    /** Called as the user nears the end of the list. */
    public void loadMore(@NonNull String sort) {
        Tab t = tab(sort);
        if (!t.loaded || t.cursor == null || t.moreCall != null || t.firstCall != null
                || t.footer == FeedState.Footer.ERROR) return;
        t.footer = FeedState.Footer.LOADING;
        t.publish();
        t.moreCall = repo.feed(t.sort, t.cursor, new ApiCallback<PostPage>() {
            @Override
            public void onSuccess(PostPage page) {
                t.moreCall = null;
                addUnique(t, page.posts);
                t.cursor = page.nextCursor;
                t.footer = t.cursor == null ? FeedState.Footer.END : FeedState.Footer.NONE;
                t.publish();
            }

            @Override
            public void onError(ApiException e) {
                t.moreCall = null;
                t.footer = FeedState.Footer.ERROR;
                t.publish();
            }
        });
    }

    /** "Try again" on the footer. */
    public void retryMore(@NonNull String sort) {
        Tab t = tab(sort);
        if (t.footer == FeedState.Footer.ERROR) t.footer = FeedState.Footer.NONE;
        loadMore(sort);
    }

    private static void addUnique(Tab t, List<Post> incoming) {
        for (Post p : incoming) {
            if (p == null || p.postId == null) continue;
            if (indexOf(t.posts, p.postId) < 0) t.posts.add(p);
        }
    }

    // ── Felt (like) ──────────────────────────────────────────────────────────

    public void toggleFelt(@NonNull Post post) {
        felt.toggle(post);
    }

    // ── Delete / report ──────────────────────────────────────────────────────

    public void delete(@NonNull Post post) {
        repo.delete(post.postId, new ApiCallback<JsonObject>() {
            @Override
            public void onSuccess(JsonObject data) {
                PostEvents.removed(post.postId);
                say(R.string.deleted, null);
            }

            @Override
            public void onError(ApiException e) {
                if (e.is(ErrorCode.NOT_FOUND)) PostEvents.removed(post.postId);
                say(0, e);
            }
        });
    }

    public void report(@NonNull String postId, @NonNull String reason) {
        Post post = find(postId);
        if (post == null) return;
        PostEvents.removed(post.postId);       // hidden for me right away; the server does the same
        repo.report(post.postId, reason, new ApiCallback<JsonObject>() {
            @Override
            public void onSuccess(JsonObject data) {
                say(R.string.reported, null);
            }

            @Override
            public void onError(ApiException e) {
                if (e.is(ErrorCode.NOT_FOUND)) return;          // already gone
                // Put it back where it was in each list that showed it.
                for (Tab t : tabs.values()) {
                    Integer at = t.removedAt.remove(post.postId);
                    if (at != null && indexOf(t.posts, post.postId) < 0) {
                        t.posts.add(Math.min(at, t.posts.size()), post);
                        t.screen = FeedState.Screen.CONTENT;
                        t.publish();
                    }
                }
                say(0, e);
            }
        });
    }

    // ── Notifications dot ────────────────────────────────────────────────────

    public void refreshUnread() {
        new ActivityRepository().unreadCount(new ApiCallback<UnreadCount>() {
            @Override public void onSuccess(UnreadCount u) { unread.setValue(u.unread); }
            @Override public void onError(ApiException e) { }
        });
    }

    // ── Shared changes ───────────────────────────────────────────────────────

    private void apply(@Nullable PostEvents.Change c) {
        if (c == null) return;
        for (Tab t : tabs.values()) {
            int i = indexOf(t.posts, c.postId);
            switch (c.kind) {
                case UPDATED:
                    if (i >= 0) { t.posts.set(i, c.post.copy()); t.publish(); }
                    break;
                case REMOVED:
                    if (i >= 0) {
                        t.removedAt.put(c.postId, i);
                        t.posts.remove(i);
                        if (t.posts.isEmpty() && t.footer == FeedState.Footer.END) t.screen = FeedState.Screen.EMPTY;
                        t.publish();
                        if (t.posts.isEmpty()) loadMore(t.sort);       // emptied a page — fetch the next
                    }
                    break;
                case CREATED:
                    // A new confession belongs on top of "Latest" only.
                    if (i < 0 && t.loaded && PostRepository.SORT_LATEST.equals(t.sort)) {
                        t.posts.add(0, c.post.copy());
                        t.screen = FeedState.Screen.CONTENT;
                        t.publish();
                    }
                    break;
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    @Nullable
    private Post find(String postId) {
        for (Tab t : tabs.values()) {
            int i = indexOf(t.posts, postId);
            if (i >= 0) return t.posts.get(i);
        }
        return null;
    }

    private static int indexOf(List<Post> list, String postId) {
        for (int i = 0; i < list.size(); i++) {
            if (postId.equals(list.get(i).postId)) return i;
        }
        return -1;
    }

    private void say(@StringRes int res, @Nullable ApiException e) {
        messages.setValue(new Event<>(new Message(res, e)));
    }

    private Tab tab(String sort) {
        Tab t = tabs.get(sort);
        return t != null ? t : tabs.get(PostRepository.SORT_LATEST);
    }

    @Override
    protected void onCleared() {
        PostEvents.changes().removeObserver(changeObserver);
        for (Tab t : tabs.values()) {
            if (t.firstCall != null) t.firstCall.cancel();
            if (t.moreCall != null) t.moreCall.cancel();
        }
    }

    /** Mutable per-sort state; publishes immutable {@link FeedState} snapshots. */
    private static final class Tab {
        final String sort;
        final MutableLiveData<FeedState> live = new MutableLiveData<>();
        final List<Post> posts = new ArrayList<>();
        final Map<String, Integer> removedAt = new HashMap<>();
        FeedState.Screen screen = FeedState.Screen.LOADING;
        FeedState.Footer footer = FeedState.Footer.NONE;
        boolean refreshing;
        boolean loaded;
        @Nullable String cursor;
        @Nullable ApiException error;
        @Nullable Call<PostPage> firstCall;
        @Nullable Call<PostPage> moreCall;

        Tab(String sort) {
            this.sort = sort;
        }

        void publish() {
            live.setValue(new FeedState(screen, new ArrayList<>(posts),
                    screen == FeedState.Screen.CONTENT ? footer : FeedState.Footer.NONE, refreshing, error));
        }
    }
}
