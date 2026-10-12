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
 *
 * The sheet itself is drawn by Android (apps can't restyle it, so it can't be faked
 * and can point at an in-screen sensor); we control its words and react around it.
 */
public final class Biometrics {

    public interface Callback {
        void onSuccess();

        /** Cancelled, locked out or failed — the UI just stays where it is. */
        void onFailure(@NonNull CharSequence reason);

        /** The sheet's own button (e.g. "Use password") was tapped. Defaults to a plain failure. */
        default void onNegative() { onFailure(""); }
    }

    private static final int AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_STRONG;

    private Biometrics() { }

    /** Hardware present and at least one fingerprint/face enrolled. */
    public static boolean available(@NonNull Context context) {
        return BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS;
    }

    public static void prompt(@NonNull FragmentActivity activity, @NonNull CharSequence title,
                              @NonNull CharSequence subtitle, @NonNull Callback cb) {
        prompt(activity, title, subtitle, activity.getString(R.string.action_cancel), cb);
    }

    public static void prompt(@NonNull FragmentActivity activity, @NonNull CharSequence title,
                              @NonNull CharSequence subtitle, @NonNull CharSequence negative,
                              @NonNull Callback cb) {
        BiometricPrompt prompt = new BiometricPrompt(activity, ContextCompat.getMainExecutor(activity),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        cb.onSuccess();
                    }

                    @Override
                    public void onAuthenticationError(int code, @NonNull CharSequence message) {
                        if (code == BiometricPrompt.ERROR_NEGATIVE_BUTTON) cb.onNegative();
                        else cb.onFailure(message);
                    }
                    // onAuthenticationFailed (one bad read) is ignored: the prompt stays up.
                });
        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setNegativeButtonText(negative)
                .setConfirmationRequired(false)      // face unlock: no extra "Confirm" tap
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build();
        prompt.authenticate(info);
    }
}
