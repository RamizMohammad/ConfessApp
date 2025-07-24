package in.mohammad.ramiz.confess.notificationdatabase;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface NotificationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertNotification(NotificationEntity notification);

    @Query("DELETE FROM notificationTable")
    void deleteAll();

    @Query("SELECT * FROM notificationTable")
    List<NotificationEntity> fetchAll();
}
