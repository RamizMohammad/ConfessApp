package in.mohammad.ramiz.confess.ui.auth;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import in.mohammad.ramiz.confess.R;

/**
 * Strong (class 3) biometrics only. Used to confirm turning biometric unlock on
 * (onboarding, settings) and to unlock the app (Section 4).
 */
public final class Biometrics {

    public interface Callback {
        void onSuccess();

        /** Cancelled, locked out or failed — the UI just stays where it is. */
        void onFailure(@NonNull CharSequence reason);
    }

    private static final int AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_STRONG;

    private Biometrics() { }

    /** Hardware present and at least one fingerprint/face enrolled. */
    public static boolean available(@NonNull Context context) {
        return BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS;
    }

    public static void prompt(@NonNull FragmentActivity activity, @NonNull CharSequence title,
                              @NonNull CharSequence subtitle, @NonNull Callback cb) {
        BiometricPrompt prompt = new BiometricPrompt(activity, ContextCompat.getMainExecutor(activity),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        cb.onSuccess();
                    }

                    @Override
                    public void onAuthenticationError(int code, @NonNull CharSequence message) {
                        cb.onFailure(message);
                    }
                    // onAuthenticationFailed (one bad read) is ignored: the prompt stays up.
                });
        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setNegativeButtonText(activity.getString(R.string.action_cancel))
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build();
        prompt.authenticate(info);
    }
}
