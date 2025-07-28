package in.mohammad.ramiz.confess.likemanager;

public class LikeResponse {
    private boolean message;

    public LikeResponse(boolean message) {
        this.message = message;
    }

    public boolean isMessage() {
        return message;
    }

    public void setMessage(boolean message) {
        this.message = message;
    }
}
