package in.mohammad.ramiz.confess.encryptiondb;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface SaltDAO {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertTheSalts(SaltEntity entity);

    @Query("SELECT * FROM userSalts WHERE userId = :userId")
    SaltEntity getSalts(String userId);
}
