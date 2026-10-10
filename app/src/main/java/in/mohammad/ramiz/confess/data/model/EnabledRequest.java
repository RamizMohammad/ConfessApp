package in.mohammad.ramiz.confess.data.model;

/** POST users/me/biometric and push/enabled. */
public class EnabledRequest {
    public final boolean enabled;

    public EnabledRequest(boolean enabled) {
        this.enabled = enabled;
    }
}
