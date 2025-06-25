package in.mohammad.ramiz.confess.entities;

public class CreatePostRequest {

    private String email;
    private String post;

    public CreatePostRequest(String email, String post) {
        this.email = email;
        this.post = post;
    }

    public CreatePostRequest() {
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
}
