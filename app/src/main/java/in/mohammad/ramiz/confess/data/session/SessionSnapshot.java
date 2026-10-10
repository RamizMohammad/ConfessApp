package in.mohammad.ramiz.confess.data.session;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Credentials captured before sign-out, so the "forget my push token" and
 * "revoke this session" calls can still be authenticated and signed after the
 * local session is gone. Attached to a Retrofit call with {@code @Tag}.
 */
public final class SessionSnapshot {
    @NonNull public final String accessToken;
    @Nullable public final String sessionId;
    @Nullable public final byte[] signingKey;

    SessionSnapshot(@NonNull String accessToken, @Nullable String sessionId, @Nullable byte[] signingKey) {
        this.accessToken = accessToken;
        this.sessionId = sessionId;
        this.signingKey = signingKey;
    }

    @NonNull
    public String bearer() {
        return "Bearer " + accessToken;
    }
}
