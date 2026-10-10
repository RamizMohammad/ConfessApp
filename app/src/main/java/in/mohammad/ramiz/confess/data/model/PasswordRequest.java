package in.mohammad.ramiz.confess.data.model;

/** POST users/me/password/verify | set | remove. */
public class PasswordRequest {
    public final String password;

    public PasswordRequest(String password) {
        this.password = password;
    }
}
