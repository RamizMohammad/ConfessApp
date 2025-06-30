package in.mohammad.ramiz.confess.profiledatabase;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface ProfileDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void InsertProfileData(ProfileEntity profileEntity);

    @Query("SELECT * FROM ProfileTable WHERE email = :email")
    ProfileEntity getProfileData(String email);
}
