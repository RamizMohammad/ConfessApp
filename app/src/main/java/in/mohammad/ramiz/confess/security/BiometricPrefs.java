package in.mohammad.ramiz.confess.security;

import android.content.Context;
import android.content.SharedPreferences;

public class BiometricPrefs {

    private static final String PREF_NAME = "biometric_prefs";
    private static final String BIOMETRIC_KEY = "is_biometric_enabled";
    private static final String LOGIN_KEY = "is_biometric_login";
    private static final String PASSWORD_KEY = "is_password_enable";
    private static final String MAX_KEY = "max_key";
    private static final String COUNT_KEY = "count_key";

    private static BiometricPrefs instance;
    private final SharedPreferences sharedPreferences;

    private BiometricPrefs(Context context) {
        sharedPreferences = context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized BiometricPrefs getInstance(Context context) {
        if (instance == null) {
            instance = new BiometricPrefs(context);
        }
        return instance;
    }

    // === Biometric Enabled Flag ===

    public void setBiometricEnabled(boolean enabled) {
        sharedPreferences.edit()
                .putBoolean(BIOMETRIC_KEY, enabled)
                .apply();
    }

    public boolean isBiometricEnabled() {
        return sharedPreferences.getBoolean(BIOMETRIC_KEY, false);
    }

    // === Login Success Flag ===

    public void setBiometricLogin(boolean login) {
        sharedPreferences.edit()
                .putBoolean(LOGIN_KEY, login)
                .apply();
    }

    public boolean isBiometricLogin() {
        return sharedPreferences.getBoolean(LOGIN_KEY, false);
    }

    public void setPasswordStatus(boolean status){
        sharedPreferences.edit()
                .putBoolean(PASSWORD_KEY, status)
                .apply();
    }

    public boolean getPasswordStatus(){
        return sharedPreferences.getBoolean(PASSWORD_KEY, false);
    }

    public void setMaxAttempt(int attempt){
        sharedPreferences.edit()
                .putInt(MAX_KEY, attempt)
                .apply();
    }

    public int getMaxAttempt(){
        return sharedPreferences.getInt(MAX_KEY, 3);
    }

    public void setCurrentCount(int count){
        sharedPreferences.edit()
                .putInt(COUNT_KEY, count)
                .apply();
    }

    public int getCurrentCount(){
        return sharedPreferences.getInt(COUNT_KEY, 0);
    }

    // === Clear both flags ===

    public void clearBiometricData() {
        sharedPreferences.edit()
                .remove(BIOMETRIC_KEY)
                .remove(LOGIN_KEY)
                .apply();
    }
}