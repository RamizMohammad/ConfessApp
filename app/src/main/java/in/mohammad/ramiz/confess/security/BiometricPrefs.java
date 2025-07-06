package in.mohammad.ramiz.confess.security;

import android.content.Context;
import android.content.SharedPreferences;

public class BiometricPrefs {

    private static final String PREF_NAME = "biometric_prefs";
    private static final String BIOMETRIC_KEY = "is_biometric_enabled";

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

    // Set boolean value
    public void setBiometricEnabled(boolean enabled) {
        sharedPreferences.edit().putBoolean(BIOMETRIC_KEY, enabled).apply();
    }

    // Get boolean value
    public boolean isBiometricEnabled() {
        return sharedPreferences.getBoolean(BIOMETRIC_KEY, false); // default is false
    }
}
