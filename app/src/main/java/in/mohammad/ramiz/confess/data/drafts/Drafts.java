package in.mohammad.ramiz.confess.data.drafts;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import in.mohammad.ramiz.confess.data.security.SecureStore;

/**
 * Unsent confessions, kept only on this phone, per account, encrypted with the
 * Keystore (SecureStore) and excluded from backups. Newest first; at most
 * {@link #MAX} per account (the oldest falls off).
 *
 * "Resume": leaving the composer with text marks that draft; opening the
 * composer again within {@link #RESUME_WINDOW_MS} continues it, later opens blank.
 */
public final class Drafts {

    public static final int MAX = 30;
    public static final long RESUME_WINDOW_MS = 60_000;

    private static final String FILE = "confess_drafts_v1";
    private static final String LEGACY_PREFS = "compose_draft";
    private static final Gson GSON = new Gson();

    private static volatile Drafts instance;
    private final SecureStore store;
    private final Context app;

    public static final class Draft {
        public final String id;
        public final String text;
        public final boolean allowResponses;
        public final long updatedAt;

        Draft(String id, String text, boolean allowResponses, long updatedAt) {
            this.id = id;
            this.text = text;
            this.allowResponses = allowResponses;
            this.updatedAt = updatedAt;
        }
    }

    private Drafts(Context context) {
        app = context.getApplicationContext();
        store = new SecureStore(app, FILE);
    }

    public static Drafts get(@NonNull Context context) {
        if (instance == null) {
            synchronized (Drafts.class) {
                if (instance == null) instance = new Drafts(context);
            }
        }
        return instance;
    }

    // ── Read ─────────────────────────────────────────────────────────────────

    @NonNull
    public synchronized List<Draft> list(@Nullable String userId) {
        if (userId == null) return new ArrayList<>();
        migrateLegacy(userId);
        String json = store.get(key(userId));
        if (json == null) return new ArrayList<>();
        try {
            List<Draft> l = GSON.fromJson(json, new TypeToken<List<Draft>>() { }.getType());
            return l == null ? new ArrayList<>() : new ArrayList<>(l);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public int count(@Nullable String userId) {
        return list(userId).size();
    }

    @Nullable
    public Draft find(@Nullable String userId, @Nullable String id) {
        if (id == null) return null;
        for (Draft d : list(userId)) if (d.id.equals(id)) return d;
        return null;
    }

    // ── Write ────────────────────────────────────────────────────────────────

    /**
     * Create or update a draft. Blank text deletes it.
     * @return the draft's id (new when {@code id} was null), or null when it was deleted
     */
    @Nullable
    public synchronized String save(@Nullable String userId, @Nullable String id, @NonNull String text, boolean allow) {
        if (userId == null) return id;
        if (text.trim().isEmpty()) {
            delete(userId, id);
            return null;
        }
        List<Draft> l = list(userId);
        String draftId = id != null ? id : UUID.randomUUID().toString();
        for (int i = 0; i < l.size(); i++) {
            if (l.get(i).id.equals(draftId)) {
                l.remove(i);
                break;
            }
        }
        l.add(0, new Draft(draftId, text, allow, System.currentTimeMillis()));
        while (l.size() > MAX) l.remove(l.size() - 1);
        write(userId, l);
        return draftId;
    }

    /** Put a deleted draft back (undo). */
    public synchronized void restore(@Nullable String userId, @NonNull Draft d) {
        if (userId == null) return;
        List<Draft> l = list(userId);
        l.add(d);
        Collections.sort(l, (a, b) -> Long.compare(b.updatedAt, a.updatedAt));
        while (l.size() > MAX) l.remove(l.size() - 1);
        write(userId, l);
    }

    public synchronized void delete(@Nullable String userId, @Nullable String id) {
        if (userId == null || id == null) return;
        List<Draft> l = list(userId);
        boolean changed = false;
        for (int i = l.size() - 1; i >= 0; i--) {
            if (l.get(i).id.equals(id)) {
                l.remove(i);
                changed = true;
            }
        }
        if (changed) write(userId, l);
        String r = store.get(resumeKey(userId));
        if (r != null && r.startsWith(id + "|")) store.put(resumeKey(userId), null);
    }

    /** Account deleted: forget everything written on it. */
    public synchronized void clearAll(@Nullable String userId) {
        if (userId == null) return;
        store.put(key(userId), null);
        store.put(resumeKey(userId), null);
    }

    // ── Resume ───────────────────────────────────────────────────────────────

    /** The composer was left with this draft just now. */
    public void markLeft(@Nullable String userId, @Nullable String id) {
        if (userId == null || id == null) return;
        store.put(resumeKey(userId), id + "|" + System.currentTimeMillis());
    }

    /** The draft to continue if the composer was left less than a minute ago (consumed). */
    @Nullable
    public Draft takeResumable(@Nullable String userId) {
        if (userId == null) return null;
        String r = store.get(resumeKey(userId));
        store.put(resumeKey(userId), null);
        if (r == null) return null;
        int bar = r.lastIndexOf('|');
        if (bar < 0) return null;
        long at;
        try {
            at = Long.parseLong(r.substring(bar + 1));
        } catch (NumberFormatException e) {
            return null;
        }
        long age = System.currentTimeMillis() - at;
        if (age < 0 || age > RESUME_WINDOW_MS) return null;
        return find(userId, r.substring(0, bar));
    }

    public void clearResume(@Nullable String userId) {
        if (userId != null) store.put(resumeKey(userId), null);
    }

    // ── Internals ────────────────────────────────────────────────────────────

    private void write(String userId, List<Draft> l) {
        store.put(key(userId), l.isEmpty() ? null : GSON.toJson(l));
    }

    /** The old single plain-text draft (ComposeDraft) moves in here once, encrypted. */
    private void migrateLegacy(String userId) {
        SharedPreferences p = app.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE);
        String text = p.getString("text_" + userId, null);
        if (text == null) return;
        boolean allow = p.getBoolean("allow_" + userId, true);
        p.edit().remove("text_" + userId).remove("allow_" + userId).commit();
        if (text.trim().isEmpty()) return;
        String json = store.get(key(userId));
        List<Draft> l = new ArrayList<>();
        try {
            if (json != null) {
                List<Draft> existing = GSON.fromJson(json, new TypeToken<List<Draft>>() { }.getType());
                if (existing != null) l.addAll(existing);
            }
        } catch (Exception ignored) {
            // start fresh
        }
        l.add(0, new Draft(UUID.randomUUID().toString(), text, allow, System.currentTimeMillis()));
        write(userId, l);
    }

    private static String key(String userId) {
        return "drafts_" + userId;
    }

    private static String resumeKey(String userId) {
        return "resume_" + userId;
    }
}
