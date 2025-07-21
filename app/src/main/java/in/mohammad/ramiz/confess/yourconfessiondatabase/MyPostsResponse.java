package in.mohammad.ramiz.confess.yourconfessiondatabase;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class MyPostsResponse {

    @SerializedName("status")
    private String status;
    @SerializedName("posts")
    private List<MyPostsData> posts;

    public MyPostsResponse(String status, List<MyPostsData> posts) {
        this.status = status;
        this.posts = posts;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<MyPostsData> getPosts() {
        return posts;
    }

    public void setPosts(List<MyPostsData> posts) {
        this.posts = posts;
    }
}
