package in.mohammad.ramiz.confess.entities;

public class GetProfileResponse {

    private boolean message;
    private String email;
    private String aliasName;
    private String about;

    public GetProfileResponse(boolean message, String email, String aliasName, String about) {
        this.message = message;
        this.email = email;
        this.aliasName = aliasName;
        this.about = about;
    }

    public boolean isMessage() {
        return message;
    }

    public void setMessage(boolean message) {
        this.message = message;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAbout() {
        return about;
    }

    public void setAbout(String about) {
        this.about = about;
    }

    public String getAliasName() {
        return aliasName;
    }

    public void setAliasName(String aliasName) {
        this.aliasName = aliasName;
    }
}
