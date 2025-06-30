package in.mohammad.ramiz.confess.profiledatabase;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "ProfileTable")
public class ProfileEntity {

    @NonNull
    @PrimaryKey
    @ColumnInfo(name = "email")
    private String email;

    @ColumnInfo(name = "aliasName")
    private String aliasName;

    @ColumnInfo(name = "about")
    private String about;

    public ProfileEntity(@NonNull  String email, String about, String aliasName) {
        this.email = email;
        this.about = about;
        this.aliasName = aliasName;
    }

    public ProfileEntity() {
    }

    @NonNull
    public String getEmail() {
        return email;
    }

    public void setEmail(@NonNull String email) {
        this.email = email;
    }

    public String getAliasName() {
        return aliasName;
    }

    public void setAliasName(String aliasName) {
        this.aliasName = aliasName;
    }

    public String getAbout() {
        return about;
    }

    public void setAbout(String about) {
        this.about = about;
    }
}
