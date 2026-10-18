package in.mohammad.ramiz.confess.data.local;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import in.mohammad.ramiz.confess.data.api.ApiClient;

/**
 * What the app already knows, kept on the phone so screens open at once — even on a slow or no
 * connection — and the server is asked only when something may have changed.
 *
 * Each entry is the JSON of a server answer (a feed page, the story tray, a profile, a book…),
 * with the time it was saved. Screens show the saved copy first and then refresh it quietly;
 * {@link #ageMs} lets them skip the refresh when the copy is only seconds old.
 *
 * Lives in the app's private storage (no other app can read it). It belongs to the signed-in
 * account: it is wiped on sign-out and when a different account signs in.
 * Reads are memory → disk (small files); writes are serialised at once and saved off the main thread.
 */
public final class LocalStore {

    private static final String DIR = "local";
    private static final String OWNER = "_owner";
    private static final int MEMORY_ENTRIES = 48;

    private static volatile LocalStore instance;

    private final File dir;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "local-store");
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    private final Map<String, Saved> memory = new LinkedHashMap<String, Saved>(32, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Saved> eldest) {
            return size() > MEMORY_ENTRIES;
        }
    };

    private static final class Saved {
        final String json;
        final long savedAt;

        Saved(String json, long savedAt) {
            this.json = json;
            this.savedAt = savedAt;
        }
    }

    private LocalStore(@NonNull Context ctx) {
        dir = new File(ctx.getApplicationContext().getFilesDir(), DIR);
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
    }

    public static void init(@NonNull Context ctx) {
        if (instance == null) {
            synchronized (LocalStore.class) {
                if (instance == null) instance = new LocalStore(ctx);
            }
        }
    }

    /** Null before {@link #init} (never in the app: ConfessApp initialises it first). */
    @Nullable
    public static LocalStore get() {
        return instance;
    }

    // ── Reading ───────────────────────────────────────────────────────────

    /** The saved copy, or null (never saved, wiped, or unreadable after an app update). */
    @Nullable
    public <T> T read(@NonNull String key, @NonNull Type type) {
        Saved e = entry(key);
        if (e == null) return null;
        try {
            return ApiClient.gson().fromJson(e.json, type);
        } catch (Exception ex) {
            remove(key);
            return null;
        }
    }

    /** How old the saved copy is; Long.MAX_VALUE when there's none. */
    public long ageMs(@NonNull String key) {
        Saved e = entry(key);
        return e == null ? Long.MAX_VALUE : Math.max(0, System.currentTimeMillis() - e.savedAt);
    }

    public boolean has(@NonNull String key) {
        return entry(key) != null;
    }

    @Nullable
    private Saved entry(@NonNull String key) {
        synchronized (memory) {
            Saved e = memory.get(key);
            if (e != null) return e;
        }
        Saved e = load(file(key));
        if (e != null) synchronized (memory) { memory.put(key, e); }
        return e;
    }

    // ── Writing ───────────────────────────────────────────────────────────

    /** Save (replace) a copy. The object is turned into JSON now, so later changes to it don't leak in. */
    public void write(@NonNull String key, @Nullable Object value) {
        if (value == null) {
            remove(key);
            return;
        }
        final String json;
        try {
            json = ApiClient.gson().toJson(value);
        } catch (Exception ex) {
            return;
        }
        Saved e = new Saved(json, System.currentTimeMillis());
        synchronized (memory) { memory.put(key, e); }
        File f = file(key);
        io.execute(() -> save(f, e));
    }

    public void remove(@NonNull String key) {
        synchronized (memory) { memory.remove(key); }
        File f = file(key);
        io.execute(() -> {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        });
    }

    /** Keep only the newest {@code keep} entries whose key starts with {@code prefix} (e.g. offline books). */
    public void trim(@NonNull String prefix, int keep) {
        String start = safe(prefix);
        io.execute(() -> {
            File[] all = dir.listFiles((d, name) -> name.startsWith(start));
            if (all == null || all.length <= keep) return;
            Arrays.sort(all, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            for (int i = keep; i < all.length; i++) {
                //noinspection ResultOfMethodCallIgnored
                all[i].delete();
            }
            synchronized (memory) {
                List<String> drop = new ArrayList<>();
                for (String k : memory.keySet()) if (k.startsWith(prefix) && !file(k).exists()) drop.add(k);
                for (String k : drop) memory.remove(k);
            }
        });
    }

    /** Everything goes (sign-out, account gone). */
    public void wipe() {
        synchronized (memory) { memory.clear(); }
        io.execute(() -> {
            File[] all = dir.listFiles();
            if (all != null) for (File f : all) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        });
    }

    /** A different account signed in on this phone: start from nothing. */
    public void ensureOwner(@Nullable String userId) {
        if (userId == null) return;
        Saved owner = entry(OWNER);
        if (owner != null && userId.equals(owner.json)) return;
        if (owner != null) wipe();
        Saved e = new Saved(userId, System.currentTimeMillis());
        synchronized (memory) { memory.put(OWNER, e); }
        File f = file(OWNER);
        io.execute(() -> save(f, e));
    }

    // ── Files ─────────────────────────────────────────────────────────────

    @NonNull
    private File file(@NonNull String key) {
        return new File(dir, safe(key) + "-" + Integer.toHexString(key.hashCode()) + ".json");
    }

    @NonNull
    private static String safe(@NonNull String key) {
        StringBuilder sb = new StringBuilder(key.length());
        for (int i = 0; i < key.length() && sb.length() < 80; i++) {
            char c = key.charAt(i);
            sb.append(Character.isLetterOrDigit(c) || c == '.' || c == '_' ? c : '_');
        }
        return sb.toString();
    }

    private static void save(@NonNull File f, @NonNull Saved e) {
        File tmp = new File(f.getPath() + ".tmp");
        try (OutputStream out = new FileOutputStream(tmp)) {
            out.write((e.savedAt + "\n").getBytes(StandardCharsets.UTF_8));
            out.write(e.json.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return;
        }
        if (!tmp.renameTo(f)) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }

    @Nullable
    private static Saved load(@NonNull File f) {
        if (!f.isFile()) return null;
        try (InputStream in = new FileInputStream(f)) {
            byte[] all = new byte[(int) f.length()];
            int n = 0;
            while (n < all.length) {
                int r = in.read(all, n, all.length - n);
                if (r < 0) break;
                n += r;
            }
            String s = new String(all, 0, n, StandardCharsets.UTF_8);
            int nl = s.indexOf('\n');
            if (nl <= 0) return null;
            return new Saved(s.substring(nl + 1), Long.parseLong(s.substring(0, nl).trim()));
        } catch (Exception ex) {
            return null;
        }
    }
}
