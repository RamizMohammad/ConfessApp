package in.mohammad.ramiz.confess.entities;

public class CreatePostResponse {

    private boolean message;

    public CreatePostResponse(boolean message) {
        this.message = message;
    }

    public CreatePostResponse() {
    }

    public boolean isMessage() {
        return message;
    }

    public void setMessage(boolean message) {
        this.message = message;
    }
}
