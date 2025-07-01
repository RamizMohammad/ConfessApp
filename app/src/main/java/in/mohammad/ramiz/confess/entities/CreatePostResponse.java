package in.mohammad.ramiz.confess.entities;

public class CreatePostResponse {

    private boolean message;
    private String label;

    public CreatePostResponse(boolean message, String label) {
        this.message = message;
        this.label = label;
    }

    public CreatePostResponse() {
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
