package in.mohammad.ramiz.confess.ui.profile;

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
import in.mohammad.ramiz.confess.core.FollowEvents;
import in.mohammad.ramiz.confess.core.PostEvents;
import in.mohammad.ramiz.confess.data.api.ApiCallback;
import in.mohammad.ramiz.confess.data.api.ApiException;
import in.mohammad.ramiz.confess.data.api.ErrorCode;
import in.mohammad.ramiz.confess.data.model.FollowState;
import in.mohammad.ramiz.confess.data.model.Me;
import in.mohammad.ramiz.confess.data.model.Post;
import in.mohammad.ramiz.confess.data.model.PostPage;
import in.mohammad.ramiz.confess.data.model.PublicProfile;
import in.mohammad.ramiz.confess.data.repo.PostRepository;
import in.mohammad.ramiz.confess.data.repo.UserRepository;
import in.mohammad.ramiz.confess.data.session.SessionManager;
import in.mohammad.ramiz.confess.ui.feed.FeltSync;
import retrofit2.Call;

/**
 * A profile + its letters. Me tab (old ProfileFragment + YourConfession) and other
 * people's profiles (old User_LookUp) share this.
 *
 *  - mine: users/me + posts/mine; felt-received shown; new confessions appear on top
 *  - theirs: users/{alias} + users/{alias}/posts
 */
public class ProfileViewModel extends ViewModel {

    public enum Screen { LOADING, CONTENT, GONE, ERROR }

    public enum Footer { NONE, LOADING, ERROR, END }

    /** What the header shows. feltCount is null for other people. */
    public static final class Header {
        public final boolean isMe;
        @NonNull public final String alias;
        @Nullable public final String about;
        @Nullable public final String avatar;
        @Nullable public final String createdAt;
        public final int postCount;
        @Nullable public final Integer feltCount;
        public final int followers;
        public final int following;
        public final boolean isFollowing;
        /** I blocked this person. */
        public final boolean blocked;

        Header(boolean isMe, @NonNull String alias, @Nullable String about, @Nullable String avatar,
               @Nullable String createdAt, int postCount, @Nullable Integer feltCount,
               int followers, int following, boolean isFollowing) {
            this(isMe, alias, about, avatar, createdAt, postCount, feltCount, followers, following, isFollowing, false);
        }

        Header(boolean isMe, @NonNull String alias, @Nullable String about, @Nullable String avatar,
               @Nullable String createdAt, int postCount, @Nullable Integer feltCount,
               int followers, int following, boolean isFollowing, boolean blocked) {
            this.isMe = isMe;
            this.alias = alias;
            this.about = about;
            this.avatar = avatar;
            this.createdAt = createdAt;
            this.postCount = postCount;
            this.feltCount = feltCount;
            this.followers = followers;
            this.following = following;
            this.isFollowing = isFollowing;
            this.blocked = blocked;
        }

        Header withPostCount(int n) {
            return new Header(isMe, alias, about, avatar, createdAt, Math.max(0, n), feltCount,
                    followers, following, isFollowing, blocked);
        }

        Header withFollow(boolean nowFollowing, int nowFollowers) {
            return new Header(isMe, alias, about, avatar, createdAt, postCount, feltCount,
                    Math.max(0, nowFollowers), following, nowFollowing, blocked);
        }

        /** Blocking also ends any follow between us (the server removes it both ways). */
        Header withBlocked(boolean nowBlocked) {
            boolean f = !nowBlocked && isFollowing;
            int n = nowBlocked && isFollowing ? Math.max(0, followers - 1) : followers;
            return new Header(isMe, alias, about, avatar, createdAt, postCount, feltCount, n, following, f, nowBlocked);
        }
    }

    public static final class State {
        @NonNull public final Screen screen;
        @Nullable public final Header header;
        @NonNull public final List<Post> posts;
        @NonNull public final Footer footer;
        public final boolean refreshing;
        @Nullable public final ApiException error;

        State(@NonNull Screen screen, @Nullable Header header, @NonNull List<Post> posts, @NonNull Footer footer,
              boolean refreshing, @Nullable ApiException error) {
            this.screen = screen;
            this.header = header;
            this.posts = Collections.unmodifiableList(posts);
            this.footer = footer;
            this.refreshing = refreshing;
            this.error = error;
        }
    }

    public static final class Message {
        @StringRes public final int res;
        @Nullable public final ApiException error;

        Message(@StringRes int res, @Nullable ApiException error) {
            this.res = res;
            this.error = error;
        }
    }

    private final UserRepository users = new UserRepository();
    private final PostRepository postsRepo = new PostRepository();
    private final MutableLiveData<State> state = new MutableLiveData<>();
    private final MutableLiveData<Event<Message>> messages = new MutableLiveData<>();

    private boolean started, isMe;
    @Nullable private String alias;
    private Screen screen = Screen.LOADING;
    @Nullable private Header header;
    private final List<Post> posts = new ArrayList<>();
    private Footer footer = Footer.NONE;
    @Nullable private String cursor;
    private boolean postsLoaded, refreshing;
    @Nullable private ApiException error;
    @Nullable private Call<PostPage> postsCall;

    private final FeltSync felt = new FeltSync(this::find, e -> say(0, e));
    private final in.mohammad.ramiz.confess.ui.feed.PollSync polls =
            new in.mohammad.ramiz.confess.ui.feed.PollSync(e -> say(0, e));
    private final Observer<PostEvents.Change> changes = this::apply;

    public ProfileViewModel() {
        PostEvents.changes().observeForever(changes);
    }

    public LiveData<State> state() { return state; }
    public LiveData<Event<Message>> messages() { return messages; }
    public boolean isMe() { return isMe; }

    /** alias == null (or my own alias) → my profile. */
    public void start(@Nullable String forAlias) {
        if (started) return;
        started = true;
        Me me = SessionManager.get().cachedMe();
        isMe = forAlias == null || (me != null && forAlias.equalsIgnoreCase(me.alias));
        alias = isMe ? (me != null ? me.alias : null) : forAlias;
        if (isMe && me != null) {
            header = fromMe(me);
            screen = Screen.CONTENT;            // show the cached header at once
        }
        publish();
        loadHeader();
        loadPosts(null);
    }

    public void refresh() {
        refreshing = true;
        if (screen == Screen.ERROR) { screen = Screen.LOADING; refreshing = false; }
        publish();
        loadHeader();
        loadPosts(null);
    }

    // ── Header ───────────────────────────────────────────────────────────────

    private void loadHeader() {
        if (isMe) {
            users.me(new ApiCallback<Me>() {
                @Override public void onSuccess(Me me) {
                    header = fromMe(me);
                    alias = me.alias;
                    screen = Screen.CONTENT;
                    error = null;
                    publish();
                }
                @Override public void onError(ApiException e) { headerFailed(e); }
            });
        } else if (alias != null) {
            users.profile(alias, new ApiCallback<PublicProfile>() {
                @Override public void onSuccess(PublicProfile p) {
                    header = new Header(false, p.alias, p.about, p.avatar, p.createdAt, p.postCount, null,
                            p.followers, p.following, p.isFollowing, p.blockedByMe);
                    screen = Screen.CONTENT;
                    error = null;
                    publish();
                }
                @Override public void onError(ApiException e) { headerFailed(e); }
            });
        }
    }

    private void headerFailed(ApiException e) {
        refreshing = false;
        if (header != null) {                       // keep what we have
            say(0, e);
        } else if (e.is(ErrorCode.NOT_FOUND)) {
            screen = Screen.GONE;
        } else {
            screen = Screen.ERROR;
            error = e;
        }
        publish();
    }

    private static Header fromMe(Me me) {
        return new Header(true, me.alias, me.about, me.avatar, me.createdAt, me.postCount, me.likesReceived,
                me.followers, me.following, false);
    }

    // ── Follow ───────────────────────────────────────────────────────────────

    private boolean followBusy = false;

    /** Follow / unfollow this person: flips at once, the server's counts settle it. */
    public void toggleFollow() {
        Header h = header;
        if (isMe || h == null || followBusy) return;
        boolean next = !h.isFollowing;
        header = h.withFollow(next, h.followers + (next ? 1 : -1));
        publish();
        followBusy = true;
        users.setFollowing(h.alias, next, new ApiCallback<FollowState>() {
            @Override public void onSuccess(FollowState s) {
                followBusy = false;
                if (header != null) header = header.withFollow(s.following, s.followers);
                publish();
                FollowEvents.changed(h.alias, s.following, s.followers);
            }
            @Override public void onError(ApiException e) {
                followBusy = false;
                header = h;                         // back to how it was
                publish();
                say(0, e);
            }
        });
    }

    // ── Blocking ─────────────────────────────────────────────────────────────

    public boolean blocked() {
        return header != null && header.blocked;
    }

    /** Unblock from the profile; their letters come back. */
    public void unblock() {
        Header h = header;
        if (isMe || h == null || !h.blocked) return;
        header = h.withBlocked(false);
        publish();
        users.setBlocked(h.alias, false, new ApiCallback<com.google.gson.JsonObject>() {
            @Override public void onSuccess(com.google.gson.JsonObject r) {
                say(R.string.unblocked_toast, null);
                loadPosts(null);
            }
            @Override public void onError(ApiException e) {
                header = h;
                publish();
                say(0, e);
            }
        });
    }

    // ── Letters ──────────────────────────────────────────────────────────────

    private void loadPosts(@Nullable String from) {
        if (postsCall != null) {
            if (from != null) return;
            postsCall.cancel();
        }
        if (!isMe && alias == null) return;
        footer = Footer.LOADING;
        ApiCallback<PostPage> cb = new ApiCallback<PostPage>() {
            @Override
            public void onSuccess(PostPage page) {
                postsCall = null;
                refreshing = false;
                if (from == null) posts.clear();
                for (Post p : page.posts) if (p != null && p.postId != null && indexOf(p.postId) < 0) posts.add(p);
                postsLoaded = true;
                cursor = page.nextCursor;
                footer = cursor == null ? Footer.END : Footer.NONE;
                publish();
            }

            @Override
            public void onError(ApiException e) {
                postsCall = null;
                refreshing = false;
                if (e.is(ErrorCode.NOT_FOUND)) { footer = Footer.END; publish(); return; }
                footer = Footer.ERROR;
                publish();
            }
        };
        postsCall = isMe ? postsRepo.myPosts(from, cb) : users.postsByAlias(alias, from, cb);
    }

    public void loadMore() {
        if (!postsLoaded || cursor == null || postsCall != null || footer == Footer.ERROR) return;
        footer = Footer.LOADING;
        publish();
        loadPosts(cursor);
    }

    public void retryMore() {
        if (!postsLoaded) { loadPosts(null); publish(); return; }
        footer = Footer.NONE;
        loadMore();
    }

    // ── Actions ──────────────────────────────────────────────────────────────

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

    public void delete(@NonNull Post post) {
        postsRepo.delete(post.postId, new ApiCallback<JsonObject>() {
            @Override public void onSuccess(JsonObject data) {
                PostEvents.removed(post.postId);
                say(R.string.deleted, null);
            }
            @Override public void onError(ApiException e) {
                if (e.is(ErrorCode.NOT_FOUND)) PostEvents.removed(post.postId);
                say(0, e);
            }
        });
    }

    public void report(@NonNull String postId, @NonNull String reason) {
        PostEvents.removed(postId);
        postsRepo.report(postId, reason, new ApiCallback<JsonObject>() {
            @Override public void onSuccess(JsonObject data) { say(R.string.reported, null); }
            @Override public void onError(ApiException e) { if (!e.is(ErrorCode.NOT_FOUND)) say(0, e); }
        });
    }

    // ── Shared changes ───────────────────────────────────────────────────────

    private void apply(@Nullable PostEvents.Change c) {
        if (c == null) return;
        if (c.kind == PostEvents.Kind.BLOCKED) {
            if (header != null && !isMe && c.alias != null && c.alias.equalsIgnoreCase(header.alias)) {
                header = header.withBlocked(true);
                posts.clear();
                footer = Footer.END;
                publish();
            }
            return;
        }
        int i = indexOf(c.postId);
        switch (c.kind) {
            case UPDATED:
                if (i >= 0 && c.post != null) { posts.set(i, c.post.copy()); publish(); }
                break;
            case REMOVED:
                if (i >= 0) {
                    posts.remove(i);
                    if (header != null) header = header.withPostCount(header.postCount - 1);
                    publish();
                }
                break;
            case CREATED:
                if (isMe && i < 0 && postsLoaded && c.post != null) {
                    posts.add(0, c.post.copy());
                    if (header != null) header = header.withPostCount(header.postCount + 1);
                    publish();
                }
                break;
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    @Nullable
    private Post find(@NonNull String postId) {
        int i = indexOf(postId);
        return i >= 0 ? posts.get(i) : null;
    }

    private int indexOf(String postId) {
        for (int i = 0; i < posts.size(); i++) if (postId.equals(posts.get(i).postId)) return i;
        return -1;
    }

    private void say(@StringRes int res, @Nullable ApiException e) {
        messages.setValue(new Event<>(new Message(res, e)));
    }

    private void publish() {
        state.setValue(new State(screen, header, new ArrayList<>(posts), footer, refreshing, error));
    }

    @Override
    protected void onCleared() {
        PostEvents.changes().removeObserver(changes);
        if (postsCall != null) postsCall.cancel();
    }
}
