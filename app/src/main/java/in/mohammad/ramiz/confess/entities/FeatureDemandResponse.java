package in.mohammad.ramiz.confess.entities;

public class FeatureDemandResponse {

    private boolean message;

    public FeatureDemandResponse(boolean message) {
        this.message = message;
    }

    public boolean isMessage() {
        return message;
    }

    public void setMessage(boolean message) {
        this.message = message;
    }
}
