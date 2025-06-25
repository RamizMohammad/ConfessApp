package in.mohammad.ramiz.confess.encryptiondb;

import android.content.Context;
import android.util.Base64;

import java.security.SecureRandom;

public class SaltManager {

    private final SaltRepo repo;

    public SaltManager(Context context){
        this.repo = new SaltRepo(context);
    }

    public String genrateKey(){
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return Base64.encodeToString(salt, Base64.NO_WRAP);
    }

    public void createUserSalt(String userId, boolean hasPassword){
        String emailSalt = genrateKey();
        String passwordSalt = genrateKey();

        SaltEntity entity = new SaltEntity(userId,emailSalt,passwordSalt);
        repo.insertSalt(entity);
    }

    public SaltEntity getSalts(String userId){
        return repo.getSalts(userId);
    }

    public String getEmailSalt(String userId) {
        SaltEntity e = repo.getSalts(userId);
        return e != null ? e.getEmailSalt() : null;
    }

    public String getPasswordSalt(String userId) {
        SaltEntity e = repo.getSalts(userId);
        return e != null ? e.getPasswordSalt() : null;
    }
}
