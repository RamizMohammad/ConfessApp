package in.mohammad.ramiz.confess.core;

import androidx.annotation.Nullable;

/** One-shot LiveData payload (navigation, snackbars): handled once, then ignored. */
public class Event<T> {

    private final T content;
    private boolean handled = false;

    public Event(T content) {
        this.content = content;
    }

    /** The content, or null if already handled. */
    @Nullable
    public synchronized T consume() {
        if (handled) return null;
        handled = true;
        return content;
    }

    public T peek() {
        return content;
    }
}
