package in.mohammad.ramiz.confess.yourconfessiondatabase;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.google.gson.annotations.SerializedName;

@Entity(tableName = "myposts")
public class MyPostsData {

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

    @SerializedName("isComment")
    @ColumnInfo(name = "isComment")
    private boolean isComment;

    @SerializedName("isUserLiked")
    @ColumnInfo(name = "isUserLiked")
    private boolean isUserLiked;

    @SerializedName("totalLikes")
    @ColumnInfo(name = "totalLikes")
    private String totalLikes;

    public MyPostsData(@NonNull String postId, String aliasName,
                     String email, String post,
                     String date, String formatDate, String profileLink,
                       boolean isComment, boolean isUserLiked, String totalLikes) {
        this.postId = postId;
        this.aliasName = aliasName;
        this.email = email;
        this.post = post;
        this.date = date;
        this.formatDate = formatDate;
        this.profileLink = profileLink;
        this.isComment = isComment;
        this.isUserLiked = isUserLiked;
        this.totalLikes = totalLikes;
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

    public boolean isComment() {
        return isComment;
    }

    public void setComment(boolean comment) {
        isComment = comment;
    }

    public boolean isUserLiked() {
        return isUserLiked;
    }

    public void setUserLiked(boolean userLiked) {
        isUserLiked = userLiked;
    }

    public String getTotalLikes() {
        return totalLikes;
    }

    public void setTotalLikes(String totalLikes) {
        this.totalLikes = totalLikes;
    }
}
