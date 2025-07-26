package in.mohammad.ramiz.confess.commentlogic;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.PrimaryKey;

import com.google.gson.annotations.SerializedName;

public class CommentData {
    
    @SerializedName("commentId")
    private String commentId;
    @SerializedName("aliasName")
    private String aliasName;
    @SerializedName("email")
    private String email;
    @SerializedName("comment")
    private String comment;
    @SerializedName("date")
    private String date;
    @SerializedName("formatDate")
    private String formatDate;
    @SerializedName("profileLink")
    private String profileLink;
    

    public CommentData(@NonNull String commentId, String aliasName,
                     String email, String comment,
                     String date, String formatDate, String profileLink) {
        this.commentId = commentId;
        this.aliasName = aliasName;
        this.email = email;
        this.comment = comment;
        this.date = date;
        this.formatDate = formatDate;
        this.profileLink = profileLink;
    }

    @NonNull
    public String getcommentId() {
        return commentId;
    }

    public void setcommentId(@NonNull String commentId) {
        this.commentId = commentId;
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

    public String getcomment() {
        return comment;
    }

    public void setcomment(String comment) {
        this.comment = comment;
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
