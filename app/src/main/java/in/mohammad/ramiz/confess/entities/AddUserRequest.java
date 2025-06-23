package in.mohammad.ramiz.confess.entities;

public class AddUserRequest {
    private String email;
    private String aliasName;
    private String about;
    private String date;
    private boolean isPassword;
    private String password;

    public AddUserRequest(String email, String aliasName, String about, String date, boolean isPassword, String password) {
        this.email = email;
        this.aliasName = aliasName;
        this.about = about;
        this.date = date;
        this.isPassword = isPassword;
        this.password = password;
    }

    public AddUserRequest() {
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

    public String getAbout() {
        return about;
    }

    public void setAbout(String about) {
        this.about = about;
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
