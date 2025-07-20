package in.mohammad.ramiz.confess.entities;

public class UpdateProfilePictureResponse {

    private boolean message;

    public UpdateProfilePictureResponse(boolean message) {
        this.message = message;
    }

    public boolean isMessage() {
        return message;
    }

    public void setMessage(boolean message) {
        this.message = message;
    }
}
