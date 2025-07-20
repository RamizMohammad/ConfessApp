package in.mohammad.ramiz.confess.postdatabase;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.google.gson.annotations.SerializedName;

@Entity(tableName = "posts")
public class PostsData {

    @NonNull
    @PrimaryKey
    @SerializedName("postId")
    @ColumnInfo(name = "postId")
    private String postId;
    @SerializedName("aliasName")
    @ColumnInfo(name = "aliasName")
    private String aliasName;
    @SerializedName("email")
    @ColumnInfo(name = "email")
    private String email;
    @SerializedName("post")
    @ColumnInfo(name = "post")
    private String post;
    @SerializedName("date")
    @ColumnInfo(name = "date")
    private String date;
    @SerializedName("formatDate")
    @ColumnInfo(name = "formatDate")
    private String formatDate;

    @SerializedName("profileLink")
    @ColumnInfo(name = "profileLink")
    private String profileLink;

    public PostsData(@NonNull String postId, String aliasName,
                     String email, String post,
                     String date, String formatDate, String profileLink) {
        this.postId = postId;
        this.aliasName = aliasName;
        this.email = email;
        this.post = post;
        this.date = date;
        this.formatDate = formatDate;
        this.profileLink = profileLink;
    }

    @NonNull
    public String getPostId() {
        return postId;
    }

    public void setPostId(@NonNull String postId) {
        this.postId = postId;
    }

    public String getAliasName() {
        return aliasName;
    }

    public void setAliasName(String aliasName) {
        this.aliasName = aliasName;
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

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }
    public String getFormatDate() {
        return formatDate;
    }
    public void setFormatDate(String formatDate) {
        this.formatDate = formatDate;
    }
    public String getProfileLink() {
        return profileLink;
    }
    public void setProfileLink(String profileLink) {
        this.profileLink = profileLink;
    }
}
