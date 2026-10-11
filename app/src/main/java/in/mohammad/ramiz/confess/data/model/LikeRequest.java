package in.mohammad.ramiz.confess.data.model;

/** POST posts/{id}/like body — which reaction. */
public class LikeRequest {
    public final String reaction;

    public LikeRequest(String reaction) {
        this.reaction = reaction;
    }
}
