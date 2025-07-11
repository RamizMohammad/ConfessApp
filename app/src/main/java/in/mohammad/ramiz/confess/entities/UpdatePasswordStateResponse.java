package in.mohammad.ramiz.confess.entities;

public class UpdatePasswordStateResponse {

    private boolean message;

    public UpdatePasswordStateResponse(boolean message) {
        this.message = message;
    }

    public boolean isMessage() {
        return message;
    }

    public void setMessage(boolean message) {
        this.message = message;
    }
}
