package in.mohammad.ramiz.confess.encryptiondb;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "userSalts")
public class SaltEntity {

    @NonNull
    @PrimaryKey
    @ColumnInfo(name = "userId")
    private String userId;
    @ColumnInfo(name = "emailSalt")
    private String emailSalt;
    @ColumnInfo(name = "passwordSalt")
    private String passwordSalt;

    public SaltEntity(@NonNull String userId, String emailSalt, String passwordSalt) {
        this.emailSalt = emailSalt;
        this.passwordSalt = passwordSalt;
        this.userId = userId;
    }

    @NonNull
    public String getUserId() {
        return userId;
    }

    public void setUserId(@NonNull String userId) {
        this.userId = userId;
    }

    public String getEmailSalt() {
        return emailSalt;
    }

    public void setEmailSalt(String emailSalt) {
        this.emailSalt = emailSalt;
    }

    public String getPasswordSalt() {
        return passwordSalt;
    }

    public void setPasswordSalt(String passwordSalt) {
        this.passwordSalt = passwordSalt;
    }
}
