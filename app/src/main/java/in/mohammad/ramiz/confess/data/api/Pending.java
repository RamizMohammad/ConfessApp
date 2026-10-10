package in.mohammad.ramiz.confess.data.api;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import retrofit2.Call;

/**
 * A request that may first need an async step (a Play Integrity verdict) before
 * its Retrofit call exists. Screens keep this to cancel in onDestroy.
 */
public final class Pending {

    private volatile boolean cancelled;
    @Nullable private volatile Call<?> call;

    public void attach(@NonNull Call<?> c) {
        call = c;
        if (cancelled) c.cancel();
    }

    public void cancel() {
        cancelled = true;
        Call<?> c = call;
        if (c != null) c.cancel();
    }

    public boolean isCancelled() {
        return cancelled;
    }
}
