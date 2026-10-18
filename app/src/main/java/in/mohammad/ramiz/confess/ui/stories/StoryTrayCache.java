package in.mohammad.ramiz.confess.ui.stories;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import in.mohammad.ramiz.confess.data.local.LocalStore;
import in.mohammad.ramiz.confess.data.model.Story;
import in.mohammad.ramiz.confess.data.model.StoryGroup;
import in.mohammad.ramiz.confess.data.model.StoryTray;

/**
 * The tray the viewer pages through (too big for an Intent). The feed fills it;
 * the viewer marks stories seen in it, so rings update when you come back.
 * Every change re-colours the avatar rings ({@link StoryRings}).
 * Saved on the phone too, so the tray and rings show at once on a cold start (stories past
 * their 24 h are dropped when it's read back); the fresh tray replaces it.
 */
public final class StoryTrayCache {

    private static final String KEY = "stories/tray";

    private static List<StoryGroup> groups = new ArrayList<>();
    private static boolean restored = false;

    private StoryTrayCache() { }

    @MainThread
    public static synchronized void set(@NonNull List<StoryGroup> g) {
        groups = new ArrayList<>(g);
        restored = true;
        save();
        StoryRings.rebuild(groups);
    }

    @NonNull
    public static synchronized List<StoryGroup> get() {
        if (!restored) {
            restored = true;
            LocalStore local = LocalStore.get();
            StoryTray saved = local == null ? null : local.read(KEY, StoryTray.class);
            if (groups.isEmpty() && saved != null && saved.people != null) {
                groups = live(saved.people);
                StoryRings.rebuild(groups);
            }
        }
        return groups;
    }

    private static void save() {
        LocalStore local = LocalStore.get();
        if (local == null) return;
        StoryTray t = new StoryTray();
        t.people = new ArrayList<>(groups);
        local.write(KEY, t);
    }

    /** Drop stories that have expired since they were saved (and people left with none). */
    @NonNull
    private static List<StoryGroup> live(@NonNull List<StoryGroup> in) {
        long now = System.currentTimeMillis();
        List<StoryGroup> out = new ArrayList<>();
        for (StoryGroup g : in) {
            if (g == null || g.stories == null) continue;
            List<Story> keep = new ArrayList<>();
            for (Story s : g.stories) if (s != null && expiresAt(s.expiresAt) > now) keep.add(s);
            if (keep.isEmpty()) continue;
            g.stories = keep;
            boolean unseen = false;
            for (Story s : keep) if (!s.seen) unseen = true;
            g.hasUnseen = !g.isMe && unseen;
            out.add(g);
        }
        return out;
    }

    private static long expiresAt(String iso) {
        if (iso == null) return 0;
        try {
            return java.time.OffsetDateTime.parse(iso).toInstant().toEpochMilli();
        } catch (Exception e) {
            try {
                return java.time.Instant.parse(iso).toEpochMilli();
            } catch (Exception e2) {
                return 0;
            }
        }
    }

    /** Seen flags / removals were made in place — refresh the rings. */
    @MainThread
    public static synchronized void changed() {
        save();
        StoryRings.rebuild(groups);
    }
}
