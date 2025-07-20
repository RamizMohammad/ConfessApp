package in.mohammad.ramiz.confess.entities;

public class GetProfileResponse {

    private boolean message;
    private String email;
    private String aliasName;
    private String about;
    private String profileLink;

    public GetProfileResponse(boolean message, String email, String aliasName, String about, String profileLink) {
        this.message = message;
        this.email = email;
        this.aliasName = aliasName;
        this.about = about;
        this.profileLink = profileLink;
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

    public String getProfileLink() {
        return profileLink;
    }

    public void setProfileLink(String profileLink) {
        this.profileLink = profileLink;
    }
}
