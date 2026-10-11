package in.mohammad.ramiz.confess.core;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

/**
 * "Someone was followed / unfollowed" — the Following feed, Me counts and any
 * open profile refresh from this. Value = alias + following flag.
 */
public final class FollowEvents {

    public static final class Change {
        @NonNull public final String alias;
        public final boolean following;
        public final int followers;     // their new follower count

        Change(@NonNull String alias, boolean following, int followers) {
            this.alias = alias;
            this.following = following;
            this.followers = followers;
        }
    }

    private static final MutableLiveData<Change> CHANGES = new MutableLiveData<>();

    private FollowEvents() { }

    /** Safe from any thread. */
    public static void changed(@NonNull String alias, boolean following, int followers) {
        CHANGES.postValue(new Change(alias, following, followers));
    }

    public static LiveData<Change> changes() {
        return CHANGES;
    }
}
