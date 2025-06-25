package in.mohammad.ramiz.confess.encryptiondb;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {SaltEntity.class}, version = 1, exportSchema = false)
public abstract class SaltDatabase extends RoomDatabase {

    public static SaltDatabase database;
    public abstract SaltDAO saltDAO();

    public static synchronized SaltDatabase getInstance(Context context){
        if(database == null){
            database = Room.databaseBuilder(
                    context.getApplicationContext(),
                    SaltDatabase.class,
                    "SecureSaltDb"
            ).build();
        }
        return database;
    }
}
