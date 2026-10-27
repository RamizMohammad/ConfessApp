package in.mohammad.ramiz.confess.data.model;

/** POST push/token and push/token/remove. */
public class PushTokenRequest {
    public final String token;
    /** Suffix of this phone's notification channel ids (".v3") — its own sound. Null on remove. */
    @androidx.annotation.Nullable public final String channelTag;
    /** Notification templates this build draws (PushTemplates.VERSION). Null on remove. */
    @androidx.annotation.Nullable public final Integer templateVersion;

    public PushTokenRequest(String token) {
        this(token, null);
    }

    public PushTokenRequest(String token, @androidx.annotation.Nullable String channelTag) {
        this(token, channelTag, null);
    }

    public PushTokenRequest(String token, @androidx.annotation.Nullable String channelTag,
                            @androidx.annotation.Nullable Integer templateVersion) {
        this.token = token;
        this.channelTag = channelTag;
        this.templateVersion = templateVersion;
    }
}
