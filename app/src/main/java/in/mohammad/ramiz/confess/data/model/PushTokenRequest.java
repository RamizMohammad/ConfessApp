package in.mohammad.ramiz.confess.data.model;

/** POST push/token and push/token/remove. */
public class PushTokenRequest {
    public final String token;
    /** Suffix of this phone's notification channel ids (".v3") — its own sound. Null on remove. */
    @androidx.annotation.Nullable public final String channelTag;

    public PushTokenRequest(String token) {
        this(token, null);
    }

    public PushTokenRequest(String token, @androidx.annotation.Nullable String channelTag) {
        this.token = token;
        this.channelTag = channelTag;
    }
}
