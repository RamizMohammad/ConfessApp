package in.mohammad.ramiz.confess.data.model;

/** POST stories. */
public class CreateStoryRequest {
    public final String text;
    public final int style;

    public CreateStoryRequest(String text, int style) {
        this.text = text;
        this.style = style;
    }
}
