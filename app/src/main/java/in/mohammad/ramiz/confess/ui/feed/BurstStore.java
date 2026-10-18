package in.mohammad.ramiz.confess.ui.feed;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import in.mohammad.ramiz.confess.core.PostEvents;
import in.mohammad.ramiz.confess.data.api.ApiCallback;
import in.mohammad.ramiz.confess.data.api.ApiException;
import in.mohammad.ramiz.confess.data.local.LocalStore;
import in.mohammad.ramiz.confess.data.model.BurstPage;
import in.mohammad.ramiz.confess.data.model.Post;
import in.mohammad.ramiz.confess.data.repo.PostRepository;
import retrofit2.Call;

/**
 * The confessions of each burst, fetched from the server a set at a time (GET /posts/bursts/{id},
 * oldest first) — the way a feed scrolls, but sideways: the slider and the burst reader ask for
 * more as the reader nears the end of what's loaded. A burst of 217 is one group; only the sets
 * actually reached are ever fetched.
 *
 * Shared by every screen showing the same burst (feed slider, stack card, reader), so a page
 * loaded in one is there in the other. The first set is kept on the phone (LocalStore) so a
 * burst opens at once next time; it's refreshed quietly the first time it's shown again.
 * Reactions and removals anywhere ({@link PostEvents}) are applied here too.
 */
public final class BurstStore {

    /** Confessions fetched per request. */
    public static final int SET = 5;

    public interface Listener {
        /** The burst's loaded confessions, total or loading state changed. */
        void onBurstChanged(@NonNull String key);
    }

    /** One burst as far as it's loaded. */
    public static final class Entry {
        @NonNull public final String id, scope, key;
        /** Oldest first. */
        @NonNull public final List<Post> posts = new ArrayList<>();
        public int total;
        public boolean done, loading, failed;
        @Nullable String cursor;
        /** Shown from the phone's copy: fetch the first set again once, for fresh counts. */
        boolean stale;

        Entry(@NonNull String id, @NonNull String scope, int total) {
            this.id = id;
            this.scope = scope;
            this.key = id + "|" + scope;
            this.total = total;
        }
    }

    private static final Map<String, Entry> entries = new HashMap<>();
    private static final List<Listener> listeners = new ArrayList<>();
    private static final PostRepository repo = new PostRepository();
    private static boolean observing = false;

    private BurstStore() { }

    @MainThread
    @NonNull
    public static Entry get(@NonNull String id, @NonNull String scope, int total) {
        observe();
        String key = id + "|" + scope;
        Entry e = entries.get(key);
        if (e == null) {
            e = new Entry(id, scope, total);
            LocalStore local = LocalStore.get();
            BurstPage saved = local == null ? null : local.read(store(key), BurstPage.class);
            if (saved != null && saved.posts != null && !saved.posts.isEmpty()) {
                addUnique(e, saved.posts);
                e.cursor = saved.nextCursor;
                e.done = saved.nextCursor == null;
                e.total = Math.max(total, saved.total);
                e.stale = true;
            }
            entries.put(key, e);
        } else if (total > e.total) {
            e.total = total;                             // more came out since: there's more to fetch
            if (e.done) e.done = false;
        }
        return e;
    }

    /** Make sure at least {@code want} confessions are loaded (fetches the next set if needed). */
    @MainThread
    public static void want(@NonNull Entry e, int want) {
        if (e.loading) return;
        if (e.stale) {
            refreshFirst(e);
            return;
        }
        if (e.done || e.posts.size() >= want) return;
        e.loading = true;
        e.failed = false;
        boolean first = e.cursor == null && e.posts.isEmpty();
        notifyChanged(e.key);
        repo.burst(e.id, e.scope, e.cursor, SET, new ApiCallback<BurstPage>() {
            @Override
            public void onSuccess(BurstPage page) {
                e.loading = false;
                addUnique(e, page.posts);
                e.cursor = page.nextCursor;
                e.done = page.nextCursor == null;
                e.total = Math.max(page.total, e.posts.size());
                if (first) save(e, page);
                notifyChanged(e.key);
            }

            @Override
            public void onError(ApiException err) {
                e.loading = false;
                e.failed = true;
                notifyChanged(e.key);
            }
        });
    }

    /** The saved first set is on screen: fetch it fresh (counts, removals) without losing later sets. */
    private static void refreshFirst(@NonNull Entry e) {
        e.stale = false;
        e.loading = true;
        repo.burst(e.id, e.scope, null, SET, new ApiCallback<BurstPage>() {
            @Override
            public void onSuccess(BurstPage page) {
                e.loading = false;
                List<Post> fresh = page.posts == null ? new ArrayList<>() : page.posts;
                if (e.posts.size() <= SET) {
                    e.posts.clear();
                    addUnique(e, fresh);
                    e.cursor = page.nextCursor;
                    e.done = page.nextCursor == null;
                } else {
                    for (Post p : fresh) {
                        int i = indexOf(e, p.postId);
                        if (i >= 0) e.posts.set(i, p);
                    }
                }
                e.total = Math.max(page.total, e.posts.size());
                save(e, page);
                notifyChanged(e.key);
            }

            @Override
            public void onError(ApiException err) {
                e.loading = false;                       // offline: the saved set stands
                notifyChanged(e.key);
            }
        });
    }

    // ── Listeners ─────────────────────────────────────────────────────────

    @MainThread
    public static void listen(@NonNull Listener l) {
        if (!listeners.contains(l)) listeners.add(l);
    }

    @MainThread
    public static void unlisten(@NonNull Listener l) {
        listeners.remove(l);
    }

    private static void notifyChanged(@NonNull String key) {
        for (Listener l : new ArrayList<>(listeners)) l.onBurstChanged(key);
    }

    /** Felt / poll / delete / report anywhere: the same in every loaded burst. */
    private static void observe() {
        if (observing) return;
        observing = true;
        PostEvents.changes().observeForever(c -> {
            if (c == null) return;
            for (Entry e : entries.values()) {
                if (c.kind == PostEvents.Kind.BLOCKED) {
                    boolean changed = false;
                    for (int i = e.posts.size() - 1; i >= 0; i--) {
                        if (c.hides(e.posts.get(i))) {
                            e.posts.remove(i);
                            e.total = Math.max(0, e.total - 1);
                            changed = true;
                        }
                    }
                    if (changed) notifyChanged(e.key);
                    continue;
                }
                int i = indexOf(e, c.postId);
                if (i < 0) continue;
                if (c.kind == PostEvents.Kind.UPDATED && c.post != null) {
                    e.posts.set(i, c.post.copy());
                    notifyChanged(e.key);
                } else if (c.kind == PostEvents.Kind.REMOVED) {
                    e.posts.remove(i);
                    e.total = Math.max(0, e.total - 1);
                    notifyChanged(e.key);
                }
            }
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    @Nullable
    public static Post find(@NonNull String postId) {
        for (Entry e : entries.values()) {
            int i = indexOf(e, postId);
            if (i >= 0) return e.posts.get(i);
        }
        return null;
    }

    private static int indexOf(@NonNull Entry e, @Nullable String postId) {
        if (postId == null) return -1;
        for (int i = 0; i < e.posts.size(); i++) if (postId.equals(e.posts.get(i).postId)) return i;
        return -1;
    }

    private static void addUnique(@NonNull Entry e, @Nullable List<Post> incoming) {
        if (incoming == null) return;
        for (Post p : incoming) {
            if (p != null && p.postId != null && indexOf(e, p.postId) < 0) e.posts.add(p);
        }
    }

    @NonNull
    private static String store(@NonNull String key) {
        return "burst/" + key;
    }

    private static void save(@NonNull Entry e, @NonNull BurstPage first) {
        LocalStore local = LocalStore.get();
        if (local != null) local.write(store(e.key), first);
    }
}
