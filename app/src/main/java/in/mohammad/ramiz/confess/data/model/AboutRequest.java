package in.mohammad.ramiz.confess.data.model;

/** POST users/me/about. */
public class AboutRequest {
    public final String about;

    public AboutRequest(String about) {
        this.about = about;
    }
}
