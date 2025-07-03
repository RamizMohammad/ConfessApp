package in.mohammad.ramiz.confess.entities;

public class CheckUserPassResponse {

    private boolean isUser;
    private boolean isPassword;
    private boolean isBiometric;

    public CheckUserPassResponse(boolean isUser, boolean isPassword, boolean isBiometric) {
        this.isUser = isUser;
        this.isPassword = isPassword;
        this.isBiometric = isBiometric;
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

    public boolean isBiometric() {
        return isBiometric;
    }

    public void setBiometric(boolean biometric) {
        isBiometric = biometric;
    }
}
