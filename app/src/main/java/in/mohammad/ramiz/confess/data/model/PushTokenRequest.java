package in.mohammad.ramiz.confess.data.model;

/** POST push/token and push/token/remove. */
public class PushTokenRequest {
    public final String token;

    public PushTokenRequest(String token) {
        this.token = token;
    }
}
