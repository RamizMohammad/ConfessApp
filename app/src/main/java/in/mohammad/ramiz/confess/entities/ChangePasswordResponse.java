package in.mohammad.ramiz.confess.entities;

public class ChangePasswordResponse {

    private boolean message;
    private String label;

    public ChangePasswordResponse(boolean message, String label) {
        this.message = message;
        this.label = label;
    }

    public boolean isMessage() {
        return message;
    }

    public void setMessage(boolean message) {
        this.message = message;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}
