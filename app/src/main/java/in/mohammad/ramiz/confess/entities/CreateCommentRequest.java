package in.mohammad.ramiz.confess.entities;

public class CreateCommentRequest {

    private String postId;
    private String email;
    private String date;
    private String comment;

    public CreateCommentRequest(String postId, String email, String date, String comment) {
        this.postId = postId;
        this.email = email;
        this.date = date;
        this.comment = comment;
    }

    public String getPostId() {
        return postId;
    }

    public void setPostId(String postId) {
        this.postId = postId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
