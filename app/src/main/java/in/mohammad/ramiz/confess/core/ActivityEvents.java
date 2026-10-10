package in.mohammad.ramiz.confess.core;

import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

/** "New activity arrived" (a push while the app is open) — screens re-check the unread count. */
public final class ActivityEvents {

    private static final MutableLiveData<Long> PINGS = new MutableLiveData<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private ActivityEvents() { }

    public static LiveData<Long> pings() {
        return PINGS;
    }

    /** Safe from any thread (the push service runs off the main thread). */
    public static void ping() {
        MAIN.post(() -> PINGS.setValue(System.currentTimeMillis()));
    }
}
