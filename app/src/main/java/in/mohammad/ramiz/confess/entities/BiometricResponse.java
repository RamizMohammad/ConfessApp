package in.mohammad.ramiz.confess.entities;

public class BiometricResponse {

    private boolean message;

    public BiometricResponse(boolean message) {
        this.message = message;
    }

    public boolean isMessage() {
        return message;
    }

    public void setMessage(boolean message) {
        this.message = message;
    }
}
