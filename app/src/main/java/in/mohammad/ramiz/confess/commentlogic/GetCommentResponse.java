package in.mohammad.ramiz.confess.commentlogic;

import java.util.List;

public class GetCommentResponse {

    private String status;
    private List<CommentData> comments;

    public GetCommentResponse(String status, List<CommentData> comments) {
        this.status = status;
        this.comments = comments;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<CommentData> getComments() {
        return comments;
    }

    public void setComments(List<CommentData> comments) {
        this.comments = comments;
    }
}
