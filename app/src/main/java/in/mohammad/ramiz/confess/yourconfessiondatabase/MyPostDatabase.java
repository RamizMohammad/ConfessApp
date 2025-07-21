package in.mohammad.ramiz.confess.yourconfessiondatabase;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {MyPostsData.class}, version = 1, exportSchema = false)
public abstract class MyPostDatabase extends RoomDatabase {

    private static volatile MyPostDatabase INSTANCE;

    public abstract MyPostDao myPostDao();

    public static MyPostDatabase getInstance(Context context){
        if(INSTANCE == null){
            synchronized (MyPostDatabase.class){
                if (INSTANCE == null){
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            MyPostDatabase.class,
                            "MyPostDatabase"
                    ).fallbackToDestructiveMigration().build();
                }
            }
        }
        return INSTANCE;
    }
}
