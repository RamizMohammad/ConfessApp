package in.mohammad.ramiz.confess.commentlogic;

public class GetCommentsRequest {

    private String postId;
    private String date;

    public GetCommentsRequest(String postId, String date) {
        this.postId = postId;
        this.date = date;
    }

    public String getPostId() {
        return postId;
    }

    public void setPostId(String postId) {
        this.postId = postId;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }
}
