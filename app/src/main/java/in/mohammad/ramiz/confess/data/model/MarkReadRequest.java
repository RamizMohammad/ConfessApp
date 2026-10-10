package in.mohammad.ramiz.confess.data.model;

import androidx.annotation.Nullable;

import java.util.List;

/** POST notifications/read. ids == null → mark everything read. */
public class MarkReadRequest {
    @Nullable public final List<String> ids;

    public MarkReadRequest(@Nullable List<String> ids) {
        this.ids = ids;
    }
}
