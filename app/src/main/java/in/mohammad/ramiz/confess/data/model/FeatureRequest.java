package in.mohammad.ramiz.confess.data.model;

/** POST feedback/feature. category: feed | privacy | comments | other. */
public class FeatureRequest {
    public final String text;
    public final String category;

    public FeatureRequest(String text, String category) {
        this.text = text;
        this.category = category;
    }
}
