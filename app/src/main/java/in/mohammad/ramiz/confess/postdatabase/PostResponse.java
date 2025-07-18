package in.mohammad.ramiz.confess.postdatabase;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class PostResponse {

    @SerializedName("status")
    private String status;
    @SerializedName("posts")
    private List<PostsData> posts;

    public PostResponse(String status, List<PostsData> posts) {
        this.status = status;
        this.posts = posts;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<PostsData> getPosts() {
        return posts;
    }

    public void setPosts(List<PostsData> posts) {
        this.posts = posts;
    }
}
