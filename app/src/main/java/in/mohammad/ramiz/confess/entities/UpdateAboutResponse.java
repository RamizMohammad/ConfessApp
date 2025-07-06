package in.mohammad.ramiz.confess.entities;

public class UpdateAboutResponse {

    private boolean message;
    private String label;

    public UpdateAboutResponse(boolean message, String label) {
        this.message = message;
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public boolean isMessage() {
        return message;
    }

    public void setMessage(boolean message) {
        this.message = message;
    }
}
