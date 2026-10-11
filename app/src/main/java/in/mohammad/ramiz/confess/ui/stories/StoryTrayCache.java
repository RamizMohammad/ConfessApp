package in.mohammad.ramiz.confess.ui.stories;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import in.mohammad.ramiz.confess.data.model.StoryGroup;

/**
 * The tray the viewer pages through (too big for an Intent). The feed fills it;
 * the viewer marks stories seen in it, so rings update when you come back.
 * Every change re-colours the avatar rings ({@link StoryRings}).
 */
public final class StoryTrayCache {

    private static List<StoryGroup> groups = new ArrayList<>();

    private StoryTrayCache() { }

    @MainThread
    public static synchronized void set(@NonNull List<StoryGroup> g) {
        groups = new ArrayList<>(g);
        StoryRings.rebuild(groups);
    }

    @NonNull
    public static synchronized List<StoryGroup> get() {
        return groups;
    }

    /** Seen flags / removals were made in place — refresh the rings. */
    @MainThread
    public static synchronized void changed() {
        StoryRings.rebuild(groups);
    }
}
