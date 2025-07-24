package in.mohammad.ramiz.confess.entities;

public class FcmTokenResponse {

    private boolean message;

    public FcmTokenResponse(boolean message) {
        this.message = message;
    }

    public boolean isMessage() {
        return message;
    }

    public void setMessage(boolean message) {
        this.message = message;
    }
}
