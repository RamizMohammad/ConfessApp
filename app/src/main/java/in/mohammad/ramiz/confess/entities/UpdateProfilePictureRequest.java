package in.mohammad.ramiz.confess.entities;

public class UpdateProfilePictureRequest {

    private String email;
    private String profileUrl;

    public UpdateProfilePictureRequest(String email, String profileUrl) {
        this.email = email;
        this.profileUrl = profileUrl;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getProfileUrl() {
        return profileUrl;
    }

    public void setProfileUrl(String profileUrl) {
        this.profileUrl = profileUrl;
    }
}
