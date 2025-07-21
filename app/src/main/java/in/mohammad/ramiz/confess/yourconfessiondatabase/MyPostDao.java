package in.mohammad.ramiz.confess.yourconfessiondatabase;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface MyPostDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertPostsDao(List<MyPostsData> posts);

    @Query("DELETE FROM myposts")
    void deleteAll();

    @Query("SELECT * FROM myposts ORDER BY date DESC")
    LiveData<List<MyPostsData>> getAllPosts();
}
