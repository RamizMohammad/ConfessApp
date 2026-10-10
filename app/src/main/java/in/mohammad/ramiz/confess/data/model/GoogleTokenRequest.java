package in.mohammad.ramiz.confess.data.model;

import androidx.annotation.Nullable;

/** POST auth/login. */
public class GoogleTokenRequest {
    public final String idToken;
    @Nullable public final ClientInfo client;

    public GoogleTokenRequest(String idToken, @Nullable ClientInfo client) {
        this.idToken = idToken;
        this.client = client;
    }
}
