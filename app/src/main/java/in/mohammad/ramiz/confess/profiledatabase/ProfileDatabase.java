package in.mohammad.ramiz.confess.profiledatabase;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {ProfileEntity.class}, version = 1, exportSchema = false)
public abstract class ProfileDatabase extends RoomDatabase {

    public static ProfileDatabase database;
    public abstract ProfileDao profileDao();

    public static synchronized ProfileDatabase getInstance(Context context){
        if(database == null){
            database = Room.databaseBuilder(
                    context.getApplicationContext(),
                    ProfileDatabase.class,
                    "ProfileEntitesDb"
            ).build();
        }
        return database;
    }
}
