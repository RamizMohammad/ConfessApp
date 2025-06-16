package in.mohammad.ramiz.confess.entities;

public class AddUserRequest {
    private String token;
    private String email;
    private String aliasName;
    private String date;
    private boolean isPassword;
    private String password;

    public AddUserRequest(String token, String email, String aliasName, String date, boolean isPassword, String password) {
        this.token = token;
        this.email = email;
        this.aliasName = aliasName;
        this.date = date;
        this.isPassword = isPassword;
        this.password = password;
    }

    public AddUserRequest() {
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAliasName() {
        return aliasName;
    }

    public void setAliasName(String aliasName) {
        this.aliasName = aliasName;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public boolean isPassword() {
        return isPassword;
    }

    public void setPassword(boolean password) {
        isPassword = password;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
