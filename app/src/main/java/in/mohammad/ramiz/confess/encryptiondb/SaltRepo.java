package in.mohammad.ramiz.confess.encryptiondb;

import android.content.Context;

import java.util.concurrent.Executors;

public class SaltRepo {

    private final SaltDAO saltDAO;

    public SaltRepo(Context context){
        SaltDatabase db = SaltDatabase.getInstance(context);
        this.saltDAO = db.saltDAO();
    }

    public void insertSalt(SaltEntity saltEntity){
        Executors.newSingleThreadExecutor().execute(() -> saltDAO.insertTheSalts(saltEntity));
    }

    public SaltEntity getSalts(String userId){
        return saltDAO.getSalts(userId);
    }
}
