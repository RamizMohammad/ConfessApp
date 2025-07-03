package in.mohammad.ramiz.confess.security;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import java.util.concurrent.Executor;

public class BiometricLogin {

    public interface AuthCallback {
        void onSuccess();
        void onError(String e);
        void onFail();
        void onUsePassword(); // Called when "Use password" is pressed
    }

    // Check if biometric is available
    public static boolean isBiometricThere(Context context) {
        BiometricManager biometricManager = BiometricManager.from(context);
        int result = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG);
        return result == BiometricManager.BIOMETRIC_SUCCESS;
    }

    // Start biometric prompt
    public static void authenticte(Context context, AuthCallback callback) {
        Executor executor = ContextCompat.getMainExecutor(context);

        BiometricPrompt biometricPrompt = new BiometricPrompt(
                (FragmentActivity) context,
                executor,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);

                        if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                            // User clicked "Use password"
                            callback.onUsePassword();
                        } else {
                            // Other biometric-related error
                            callback.onError(errString.toString());
                        }
                    }

                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        callback.onSuccess();
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        super.onAuthenticationFailed();
                        callback.onFail();
                    }
                }
        );

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo
                .Builder()
                .setTitle("Confess Login")
                .setSubtitle("Use biometric to continue")
                .setNegativeButtonText("Use password") // This will trigger onError with ERROR_NEGATIVE_BUTTON
                .build();

        biometricPrompt.authenticate(promptInfo);
    }
}
