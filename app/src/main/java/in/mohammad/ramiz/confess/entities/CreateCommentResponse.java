package in.mohammad.ramiz.confess.entities;

public class CreateCommentResponse {

    private boolean message;
    private String label;

    public CreateCommentResponse(boolean message, String label) {
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
