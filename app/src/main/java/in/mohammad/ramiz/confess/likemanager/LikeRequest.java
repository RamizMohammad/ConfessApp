package in.mohammad.ramiz.confess.likemanager;

import com.google.gson.annotations.SerializedName;

public class LikeRequest {

    @SerializedName("email")
    private String email;

    @SerializedName("postId")
    private String postId;

    public LikeRequest(String email, String postId) {
        this.email = email;
        this.postId = postId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPostId() {
        return postId;
    }

    public void setPostId(String postId) {
        this.postId = postId;
    }
}
