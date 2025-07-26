package in.mohammad.ramiz.confess.entities;

public class CreatePostRequest {

    private String email;
    private String post;
    private String date;
    private boolean isComment;

    public CreatePostRequest(String email, String post, String date, boolean isComment) {
        this.email = email;
        this.post = post;
        this.date = date;
        this.isComment = isComment;
    }

    public CreatePostRequest() {
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPost() {
        return post;
    }

    public void setPost(String post) {
        this.post = post;
    }

    public boolean isComment() {
        return isComment;
    }

    public void setComment(boolean comment) {
        isComment = comment;
    }
}
