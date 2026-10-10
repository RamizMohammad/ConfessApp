package in.mohammad.ramiz.confess.data.model;

/** POST users/me/password/change. */
public class ChangePasswordRequest {
    public final String oldPassword;
    public final String newPassword;

    public ChangePasswordRequest(String oldPassword, String newPassword) {
        this.oldPassword = oldPassword;
        this.newPassword = newPassword;
    }
}
