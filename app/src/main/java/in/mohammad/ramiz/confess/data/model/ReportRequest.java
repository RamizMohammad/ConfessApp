package in.mohammad.ramiz.confess.data.model;

/** POST posts/{id}/report. */
public class ReportRequest {
    public final String reason;

    public ReportRequest(String reason) {
        this.reason = reason;
    }
}
