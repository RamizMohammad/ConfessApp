package in.mohammad.ramiz.confess.entities;

public class ForgotPasswordResponse {

    private boolean message;

    public ForgotPasswordResponse(boolean message) {
        this.message = message;
    }

    public ForgotPasswordResponse() {
    }

    public boolean isMessage() {
        return message;
    }

    public void setMessage(boolean message) {
        this.message = message;
    }
}
