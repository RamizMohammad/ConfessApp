package in.mohammad.ramiz.confess.ui.stories;

import android.content.Context;
import android.os.Looper;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import in.mohammad.ramiz.confess.data.model.StoryGroup;
import in.mohammad.ramiz.confess.ui.nav.Nav;

/**
 * Who has a live story right now (from the tray), so every avatar in the app can
 * wear the ring: glowing amber = something new, soft amber = all seen.
 * Avatars subscribe while attached and redraw when the tray changes.
 */
public final class StoryRings {

    public static final int NONE = 0;
    public static final int SEEN = 1;
    public static final int UNSEEN = 2;

    public interface Listener {
        void onStoryRingsChanged();
    }

    private static final Map<String, Integer> states = new HashMap<>();
    private static final Set<Listener> listeners = Collections.newSetFromMap(new WeakHashMap<>());

    private StoryRings() { }

    private static String key(@NonNull String alias) {
        String a = alias.trim();
        if (a.startsWith("@")) a = a.substring(1);
        return a.toLowerCase(Locale.ROOT);
    }

    public static int state(@Nullable String alias) {
        if (alias == null || alias.trim().isEmpty()) return NONE;
        Integer s = states.get(key(alias));
        return s == null ? NONE : s;
    }

    /** Recompute from the tray and tell every attached avatar. Main thread. */
    @MainThread
    static void rebuild(@NonNull List<StoryGroup> groups) {
        states.clear();
        for (StoryGroup g : groups) {
            if (g == null || g.author == null || g.author.alias == null || g.stories == null || g.stories.isEmpty()) continue;
            states.put(key(g.author.alias), g.hasUnseen ? UNSEEN : SEEN);
        }
        if (Looper.myLooper() != Looper.getMainLooper()) return;
        for (Listener l : new ArrayList<>(listeners)) {
            if (l != null) l.onStoryRingsChanged();
        }
    }

    @MainThread
    public static void addListener(@NonNull Listener l) {
        listeners.add(l);
    }

    @MainThread
    public static void removeListener(@NonNull Listener l) {
        listeners.remove(l);
    }

    /** Open just this person's stories. False when they have none live. */
    public static boolean open(@NonNull Context ctx, @Nullable String alias) {
        if (state(alias) == NONE) return false;
        List<StoryGroup> groups = StoryTrayCache.get();
        String k = key(alias);
        for (int i = 0; i < groups.size(); i++) {
            StoryGroup g = groups.get(i);
            if (g != null && g.author != null && g.author.alias != null && key(g.author.alias).equals(k)
                    && g.stories != null && !g.stories.isEmpty()) {
                Nav.storiesOf(ctx, i);
                return true;
            }
        }
        return false;
    }
}
