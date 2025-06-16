package in.mohammad.ramiz.confess.entities;

public class CheckUserPassResponse {

    private boolean isUser;
    private boolean isPassword;

    public CheckUserPassResponse(boolean isUser, boolean isPassword) {
        this.isUser = isUser;
        this.isPassword = isPassword;
    }

    public CheckUserPassResponse() {
    }

    public boolean isUser() {
        return isUser;
    }

    public void setUser(boolean user) {
        isUser = user;
    }

    public boolean isPassword() {
        return isPassword;
    }

    public void setPassword(boolean password) {
        isPassword = password;
    }
}
