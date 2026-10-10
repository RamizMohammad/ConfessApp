package in.mohammad.ramiz.confess.data.model;

/** POST auth/refresh. */
public class RefreshRequest {
    public final String refreshToken;

    public RefreshRequest(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}
