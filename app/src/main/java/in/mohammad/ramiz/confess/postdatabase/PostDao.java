package in.mohammad.ramiz.confess.postdatabase;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface PostDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertPostsDao(List<PostsData> posts);

    @Query("DELETE FROM posts")
    void deleteAll();

    @Query("SELECT * FROM posts ORDER BY date DESC")
    LiveData<List<PostsData>> getAllPosts();
}
