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
import in.mohammad.ramiz.confess.core.FollowEvents;
import in.mohammad.ramiz.confess.core.PostEvents;
import in.mohammad.ramiz.confess.data.api.ApiCallback;
import in.mohammad.ramiz.confess.data.api.ApiException;
import in.mohammad.ramiz.confess.data.api.ErrorCode;
import in.mohammad.ramiz.confess.data.local.LocalStore;
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
 *  - every tab is saved on the phone (LocalStore): opening the app shows the saved
 *    confessions at once — also offline — and the fresh page replaces them quietly; a copy
 *    under 45 s old isn't fetched again
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

    private static final long FRESH_MS = 45_000;

    private final PostRepository repo = new PostRepository();
    @Nullable private final LocalStore local = LocalStore.get();
    private final Map<String, Tab> tabs = new HashMap<>();
    /** Ask for one card per burst (the reader didn't choose "one by one"). */
    private boolean bursts = true;
    private final MutableLiveData<Integer> unread = new MutableLiveData<>(savedUnread());
    private final MutableLiveData<Event<Message>> messages = new MutableLiveData<>();

    private final FeltSync felt = new FeltSync(this::find, e -> say(0, e));
    private final PollSync polls = new PollSync(e -> say(0, e));

    private final Observer<PostEvents.Change> changeObserver = this::apply;
    private final Observer<FollowEvents.Change> followObserver = c -> { if (c != null) resetFollowing(); };

    public FeedViewModel() {
        for (String scope : new String[]{PostRepository.SCOPE_ALL, PostRepository.SCOPE_FOLLOWING}) {
            for (String sort : new String[]{PostRepository.SORT_LATEST, PostRepository.SORT_TOP}) {
                Tab t = new Tab(sort, scope);
                tabs.put(t.key, t);
            }
        }
        PostEvents.changes().observeForever(changeObserver);
        FollowEvents.changes().observeForever(followObserver);
    }

    /** Who I follow changed: the Following tabs start over (the visible one reloads now). */
    private void resetFollowing() {
        for (Tab t : tabs.values()) {
            if (!PostRepository.SCOPE_FOLLOWING.equals(t.scope)) continue;
            if (t.firstCall != null) t.firstCall.cancel();
            if (t.moreCall != null) t.moreCall.cancel();
            t.firstCall = null;
            t.moreCall = null;
            t.posts.clear();
            t.cursor = null;
            t.loaded = false;
            t.error = null;
            t.refreshing = false;
            t.footer = FeedState.Footer.NONE;
            t.screen = FeedState.Screen.LOADING;
            if (local != null) local.remove(storeKey(t));
            if (t.live.hasActiveObservers()) {
                t.publish();
                loadFirst(t);
            }
        }
    }

    /** The tab key the fragment uses: sort for everyone ("latest"/"top"), "following:<sort>" otherwise. */
    @NonNull
    public static String key(@NonNull String sort, @NonNull String scope) {
        return PostRepository.SCOPE_FOLLOWING.equals(scope) ? "following:" + sort : sort;
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
        if (restore(t)) {                                   // the saved copy now, the fresh one quietly
            t.publish();
            if (local == null || local.ageMs(storeKey(t)) > FRESH_MS) loadFirst(t);
            return;
        }
        t.screen = FeedState.Screen.LOADING;
        t.publish();
        loadFirst(t);
    }

    // ── Saved on the phone ───────────────────────────────────────────────────

    @NonNull
    private String storeKey(@NonNull Tab t) {
        return "feed/" + t.key + (bursts ? "/bursts" : "");
    }

    /** "One by one" on or off: the feed is a different list from the server, so start over. */
    public void setBursts(boolean on) {
        if (on == bursts) return;
        bursts = on;
        for (Tab t : tabs.values()) {
            if (!t.loaded && t.firstCall == null) continue;
            if (t.firstCall != null) t.firstCall.cancel();
            if (t.moreCall != null) t.moreCall.cancel();
            t.firstCall = null;
            t.moreCall = null;
            t.posts.clear();
            t.cursor = null;
            t.loaded = false;
            t.error = null;
            t.refreshing = false;
            t.footer = FeedState.Footer.NONE;
            t.screen = FeedState.Screen.LOADING;
            if (t.live.hasActiveObservers()) {
                restore(t);
                t.publish();
                loadFirst(t);
            }
        }
    }

    private boolean restore(@NonNull Tab t) {
        if (local == null) return false;
        PostPage saved = local.read(storeKey(t), PostPage.class);
        if (saved == null || saved.posts == null || saved.posts.isEmpty()) return false;
        t.posts.clear();
        addUnique(t, saved.posts);
        t.cursor = saved.nextCursor;
        t.loaded = true;
        t.footer = t.cursor == null ? FeedState.Footer.END : FeedState.Footer.NONE;
        t.screen = FeedState.Screen.CONTENT;
        t.error = null;
        return true;
    }

    /** Save what the tab shows (up to 100 cards; past that, offline paging stops there). */
    private void persist(@NonNull Tab t) {
        if (local == null || !t.loaded) return;
        PostPage page = new PostPage();
        int n = Math.min(t.posts.size(), 100);
        page.posts = new ArrayList<>(t.posts.subList(0, n));
        page.nextCursor = n == t.posts.size() ? t.cursor : null;
        local.write(storeKey(t), page);
    }

    private int savedUnread() {
        UnreadCount u = local == null ? null : local.read("unread", UnreadCount.class);
        return u == null ? 0 : u.unread;
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
        t.firstCall = repo.feed(t.sort, t.scope, null, bursts, new ApiCallback<PostPage>() {
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
                persist(t);
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
        t.moreCall = repo.feed(t.sort, t.scope, t.cursor, bursts, new ApiCallback<PostPage>() {
            @Override
            public void onSuccess(PostPage page) {
                t.moreCall = null;
                addUnique(t, page.posts);
                t.cursor = page.nextCursor;
                t.footer = t.cursor == null ? FeedState.Footer.END : FeedState.Footer.NONE;
                t.publish();
                persist(t);
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

    public void react(@NonNull Post post, @NonNull String reaction) {
        felt.react(post, reaction);
    }

    public void vote(@NonNull Post post, int option) {
        Post current = find(post.postId);
        polls.vote(current != null ? current : post, option);
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
            @Override public void onSuccess(UnreadCount u) {
                unread.setValue(u.unread);
                if (local != null) local.write("unread", u);
            }
            @Override public void onError(ApiException e) { }
        });
    }

    // ── Shared changes ───────────────────────────────────────────────────────

    private void apply(@Nullable PostEvents.Change c) {
        if (c == null) return;
        if (c.kind == PostEvents.Kind.BLOCKED) {
            for (Tab t : tabs.values()) {
                boolean changed = false;
                for (int i = t.posts.size() - 1; i >= 0; i--) {
                    if (c.hides(t.posts.get(i))) { t.posts.remove(i); changed = true; }
                }
                if (!changed) continue;
                if (t.posts.isEmpty() && t.footer == FeedState.Footer.END) t.screen = FeedState.Screen.EMPTY;
                t.publish(); persist(t);
                if (t.posts.isEmpty()) loadMore(t.key);
            }
            return;
        }
        for (Tab t : tabs.values()) {
            int i = indexOf(t.posts, c.postId);
            switch (c.kind) {
                case UPDATED:
                    if (i >= 0) { t.posts.set(i, c.post.copy()); t.publish(); persist(t); }
                    break;
                case REMOVED:
                    if (i >= 0) {
                        t.removedAt.put(c.postId, i);
                        t.posts.remove(i);
                        if (t.posts.isEmpty() && t.footer == FeedState.Footer.END) t.screen = FeedState.Screen.EMPTY;
                        t.publish(); persist(t);
                        if (t.posts.isEmpty()) loadMore(t.key);       // emptied a page — fetch the next
                    }
                    break;
                case CREATED:
                    // A new confession (mine) belongs on top of everyone's "Latest" only.
                    if (i < 0 && t.loaded && PostRepository.SORT_LATEST.equals(t.sort)
                            && PostRepository.SCOPE_ALL.equals(t.scope)) {
                        t.posts.add(0, c.post.copy());
                        t.screen = FeedState.Screen.CONTENT;
                        t.publish(); persist(t);
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
        FollowEvents.changes().removeObserver(followObserver);
        for (Tab t : tabs.values()) {
            if (t.firstCall != null) t.firstCall.cancel();
            if (t.moreCall != null) t.moreCall.cancel();
        }
    }

    /** Mutable per-(sort, scope) state; publishes immutable {@link FeedState} snapshots. */
    private static final class Tab {
        final String key;
        final String sort;
        final String scope;
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

        Tab(String sort, String scope) {
            this.sort = sort;
            this.scope = scope;
            this.key = key(sort, scope);
        }

        void publish() {
            live.setValue(new FeedState(screen, new ArrayList<>(posts),
                    screen == FeedState.Screen.CONTENT ? footer : FeedState.Footer.NONE, refreshing, error));
        }
    }
}
