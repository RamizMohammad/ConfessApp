package in.mohammad.ramiz.confess.postdatabase;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {PostsData.class}, version = 1, exportSchema = false)
public abstract class PostDatabase extends RoomDatabase {

    private static volatile PostDatabase INSTANCE;

    public abstract PostDao dao();

    public static PostDatabase getInstance(Context context){
        if (INSTANCE == null){
            synchronized (PostDatabase.class){
                if(INSTANCE == null){
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            PostDatabase.class,
                            "PostDatabase"
                    ).fallbackToDestructiveMigration().build();
                }
            }
        }
        return  INSTANCE;
    }
}
