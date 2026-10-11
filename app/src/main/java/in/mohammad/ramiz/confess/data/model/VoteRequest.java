package in.mohammad.ramiz.confess.data.model;

/** POST posts/{id}/vote. */
public class VoteRequest {
    public final int option;

    public VoteRequest(int option) {
        this.option = option;
    }
}
