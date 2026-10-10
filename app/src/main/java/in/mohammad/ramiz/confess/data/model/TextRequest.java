package in.mohammad.ramiz.confess.data.model;

/** POST posts/{id}/comments. */
public class TextRequest {
    public final String text;

    public TextRequest(String text) {
        this.text = text;
    }
}
