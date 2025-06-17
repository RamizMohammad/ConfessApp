package in.mohammad.ramiz.confess.entities;

public class CheckPasswordResponse {

    private boolean message;

    public CheckPasswordResponse(boolean message) {
        this.message = message;
    }

    public CheckPasswordResponse() {
    }

    public boolean isMessage() {
        return message;
    }

    public void setMessage(boolean message) {
        this.message = message;
    }
}
