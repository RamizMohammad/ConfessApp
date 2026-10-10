package in.mohammad.ramiz.confess.ui.activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import in.mohammad.ramiz.confess.core.ActivityEvents;
import in.mohammad.ramiz.confess.core.Event;
import in.mohammad.ramiz.confess.data.api.ApiCallback;
import in.mohammad.ramiz.confess.data.api.ApiException;
import in.mohammad.ramiz.confess.data.model.Notification;
import in.mohammad.ramiz.confess.data.model.NotificationPage;
import in.mohammad.ramiz.confess.data.repo.ActivityRepository;
import retrofit2.Call;

/**
 * Activity list (server-side now; the old app kept a local Room table of pushes).
 * Newest first, cursor paging, read state per item, filter by kind on device.
 */
public class NotificationsViewModel extends ViewModel {

    public enum Screen { LOADING, CONTENT, EMPTY, ERROR }

    public enum Footer { NONE, LOADING, ERROR, END }

    public enum Filter { ALL, FELT, RESPONSES }

    public static final class State {
        @NonNull public final Screen screen;
        /** Already filtered. */
        @NonNull public final List<Notification> items;
        @NonNull public final Footer footer;
        @NonNull public final Filter filter;
        public final boolean refreshing;
        public final boolean anyUnread;
        @Nullable public final ApiException error;

        State(@NonNull Screen screen, @NonNull List<Notification> items, @NonNull Footer footer,
              @NonNull Filter filter, boolean refreshing, boolean anyUnread, @Nullable ApiException error) {
            this.screen = screen;
            this.items = Collections.unmodifiableList(items);
            this.footer = footer;
            this.filter = filter;
            this.refreshing = refreshing;
            this.anyUnread = anyUnread;
            this.error = error;
        }
    }

    private final ActivityRepository repo = new ActivityRepository();
    private final MutableLiveData<State> state = new MutableLiveData<>();
    private final MutableLiveData<Event<ApiException>> errors = new MutableLiveData<>();

    private final List<Notification> all = new ArrayList<>();
    private Screen screen = Screen.LOADING;
    private Footer footer = Footer.NONE;
    private Filter filter = Filter.ALL;
    private boolean refreshing, loaded;
    @Nullable private String cursor;
    @Nullable private ApiException error;
    @Nullable private Call<NotificationPage> firstCall, moreCall;

    public LiveData<State> state() { return state; }
    public LiveData<Event<ApiException>> errors() { return errors; }

    public void start() {
        if (loaded || firstCall != null) return;
        screen = Screen.LOADING;
        publish();
        loadFirst();
    }

    public void refresh() {
        if (firstCall != null) return;
        if (screen == Screen.ERROR) screen = Screen.LOADING; else refreshing = true;
        publish();
        loadFirst();
    }

    private void loadFirst() {
        if (moreCall != null) { moreCall.cancel(); moreCall = null; }
        firstCall = repo.notifications(null, new ApiCallback<NotificationPage>() {
            @Override
            public void onSuccess(NotificationPage page) {
                firstCall = null;
                loaded = true;
                refreshing = false;
                all.clear();
                addUnique(page.notifications);
                cursor = page.nextCursor;
                footer = cursor == null ? Footer.END : Footer.NONE;
                error = null;
                screen = Screen.CONTENT;
                publish();
            }

            @Override
            public void onError(ApiException e) {
                firstCall = null;
                refreshing = false;
                if (all.isEmpty()) { screen = Screen.ERROR; error = e; }
                else errors.setValue(new Event<>(e));
                publish();
            }
        });
    }

    public void loadMore() {
        if (!loaded || cursor == null || moreCall != null || firstCall != null || footer == Footer.ERROR) return;
        footer = Footer.LOADING;
        publish();
        moreCall = repo.notifications(cursor, new ApiCallback<NotificationPage>() {
            @Override
            public void onSuccess(NotificationPage page) {
                moreCall = null;
                addUnique(page.notifications);
                cursor = page.nextCursor;
                footer = cursor == null ? Footer.END : Footer.NONE;
                publish();
            }

            @Override
            public void onError(ApiException e) {
                moreCall = null;
                footer = Footer.ERROR;
                publish();
            }
        });
    }

    public void retryMore() {
        if (footer == Footer.ERROR) footer = Footer.NONE;
        loadMore();
    }

    public void setFilter(@NonNull Filter f) {
        if (f == filter) return;
        filter = f;
        publish();
    }

    // ── Read state ───────────────────────────────────────────────────────────

    public boolean anyUnread() {
        for (Notification n : all) if (!n.read) return true;
        return false;
    }

    /** Tapped one: mark it read here and on the server (best effort). */
    public void markRead(@NonNull Notification n) {
        if (n.read) return;
        setRead(n.id);
        publish();
        repo.markRead(Collections.singletonList(n.id), ignore());
    }

    /** "Mark all read" (and quietly when leaving the screen). */
    public void markAllRead(boolean publishNow) {
        if (!anyUnread()) return;
        for (int i = 0; i < all.size(); i++) if (!all.get(i).read) setRead(all.get(i).id);
        if (publishNow) publish();
        repo.markRead(null, new ApiCallback<JsonObject>() {
            @Override public void onSuccess(JsonObject data) { ActivityEvents.ping(); }   // home's bell re-checks
            @Override public void onError(ApiException e) { }
        });
    }

    private void setRead(String id) {
        for (int i = 0; i < all.size(); i++) {
            Notification n = all.get(i);
            if (id.equals(n.id) && !n.read) {
                Notification c = copy(n);
                c.read = true;
                all.set(i, c);                     // new object so the list diff sees the change
            }
        }
    }

    private static Notification copy(Notification n) {
        Notification c = new Notification();
        c.id = n.id;
        c.type = n.type;
        c.actor = n.actor;
        c.postId = n.postId;
        c.postExcerpt = n.postExcerpt;
        c.commentExcerpt = n.commentExcerpt;
        c.read = n.read;
        c.createdAt = n.createdAt;
        return c;
    }

    private static ApiCallback<JsonObject> ignore() {
        return new ApiCallback<JsonObject>() {
            @Override public void onSuccess(JsonObject data) { }
            @Override public void onError(ApiException e) { }
        };
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void addUnique(List<Notification> incoming) {
        outer:
        for (Notification n : incoming) {
            if (n == null || n.id == null) continue;
            for (Notification have : all) if (have.id.equals(n.id)) continue outer;
            all.add(n);
        }
    }

    private void publish() {
        List<Notification> shown = new ArrayList<>();
        for (Notification n : all) {
            boolean like = Notification.TYPE_LIKE.equals(n.type);
            if (filter == Filter.ALL || (filter == Filter.FELT && like) || (filter == Filter.RESPONSES && !like)) {
                shown.add(n);
            }
        }
        Screen s = screen == Screen.CONTENT && all.isEmpty() ? Screen.EMPTY : screen;
        state.setValue(new State(s, shown, s == Screen.CONTENT ? footer : Footer.NONE, filter,
                refreshing, anyUnread(), error));
    }

    @Override
    protected void onCleared() {
        if (firstCall != null) firstCall.cancel();
        if (moreCall != null) moreCall.cancel();
    }
}
