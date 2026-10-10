package in.mohammad.ramiz.confess.data.session;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import in.mohammad.ramiz.confess.core.Event;

/**
 * App-wide session signals. Activities observe {@link #expiredEvents()} and,
 * on an unhandled event, send the user back through sign-in (Section 2).
 */
public final class SessionEvents {

    private static final MutableLiveData<Event<Boolean>> EXPIRED = new MutableLiveData<>();

    private SessionEvents() { }

    /** Called when the server rejects our token (401). Safe from any thread. */
    public static void expired() {
        EXPIRED.postValue(new Event<>(true));
    }

    public static LiveData<Event<Boolean>> expiredEvents() {
        return EXPIRED;
    }
}
