package in.mohammad.ramiz.confess.entities;

public class UpdateAboutResponse {

    private boolean message;

    public UpdateAboutResponse(boolean message) {
        this.message = message;
    }

    public UpdateAboutResponse() {
    }

    public boolean isMessage() {
        return message;
    }

    public void setMessage(boolean message) {
        this.message = message;
    }
}
