package in.mohammad.ramiz.confess.entities;

public class UpdateAboutRequest {

    private String email;
    private String about;

    public UpdateAboutRequest(String email, String about) {
        this.email = email;
        this.about = about;
    }

    public UpdateAboutRequest() {
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
}
