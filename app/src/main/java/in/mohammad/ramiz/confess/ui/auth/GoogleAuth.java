package in.mohammad.ramiz.confess.ui.auth;

import android.app.Activity;
import android.content.Context;
import android.os.CancellationSignal;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.credentials.ClearCredentialStateRequest;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.ClearCredentialException;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.exceptions.NoCredentialException;

import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;

import in.mohammad.ramiz.confess.BuildConfig;

/**
 * Google sign-in through Credential Manager (replaces the deprecated
 * GoogleSignIn client from the old app). Produces a Google ID token, which
 * the server verifies and exchanges for its own session token.
 */
public final class GoogleAuth {

    public interface Callback {
        void onIdToken(@NonNull String idToken);

        /** User closed the picker — not an error, just stay on the screen. */
        void onCancelled();

        /** No usable Google account (silent mode) or a real failure. */
        void onFailed(@NonNull String reason);
    }

    private static final String TAG = "ConfessAuth";

    /**
     * Google reports "[16] Account reauth failed" as a *cancellation*, but it really
     * means the app couldn't be matched to an OAuth client: the signing SHA-1 of
     * this build isn't registered, or WEB_CLIENT_ID is from another project.
     */
    private static final String CONFIG_HINT =
            "Google couldn't verify this app build (register this build's SHA-1 in Firebase "
                    + "and check WEB_CLIENT_ID)";

    static boolean isConfigError(GetCredentialException e) {
        String m = String.valueOf(e.getMessage());
        return m.contains("[16]") || m.contains("[10]") || m.toLowerCase().contains("reauth failed")
                || m.toLowerCase().contains("developer console");
    }

    private GoogleAuth() { }

    /**
     * "Continue with Google" button. Tries the "Sign in with Google" flow first;
     * if that fails for any reason other than the user cancelling (some OEM
     * builds, e.g. MIUI, reject it), falls back to the standard Google account
     * picker (GetGoogleIdOption, all accounts on the device).
     */
    public static CancellationSignal signIn(@NonNull Activity activity, @NonNull Callback cb) {
        GetSignInWithGoogleOption option = new GetSignInWithGoogleOption.Builder(BuildConfig.WEB_CLIENT_ID).build();
        CancellationSignal cancel = new CancellationSignal();
        request(activity, new GetCredentialRequest.Builder().addCredentialOption(option).build(), cancel,
                new Callback() {
                    @Override public void onIdToken(@NonNull String idToken) { cb.onIdToken(idToken); }
                    @Override public void onCancelled() { cb.onCancelled(); }
                    @Override public void onFailed(@NonNull String reason) {
                        if (cancel.isCanceled() || activity.isFinishing()) return;
                        Log.w(TAG, "SignInWithGoogle failed (" + reason + ") — falling back to account picker");
                        GetGoogleIdOption picker = new GetGoogleIdOption.Builder()
                                .setServerClientId(BuildConfig.WEB_CLIENT_ID)
                                .setFilterByAuthorizedAccounts(false)
                                .setAutoSelectEnabled(false)
                                .build();
                        request(activity, new GetCredentialRequest.Builder().addCredentialOption(picker).build(),
                                cancel, new Callback() {
                                    @Override public void onIdToken(@NonNull String idToken) { cb.onIdToken(idToken); }
                                    @Override public void onCancelled() { cb.onCancelled(); }
                                    @Override public void onFailed(@NonNull String reason2) {
                                        cb.onFailed("1) " + reason + "\n2) " + reason2);
                                    }
                                });
                    }
                });
        return cancel;
    }

    /**
     * Quiet re-sign-in for an account that already used Confess on this device
     * (splash, when the session token is expiring). May briefly show the
     * system "Signing in as …" bar; never shows a chooser.
     */
    public static CancellationSignal silent(@NonNull Activity activity, @NonNull Callback cb) {
        GetGoogleIdOption option = new GetGoogleIdOption.Builder()
                .setServerClientId(BuildConfig.WEB_CLIENT_ID)
                .setFilterByAuthorizedAccounts(true)
                .setAutoSelectEnabled(true)
                .build();
        CancellationSignal cancel = new CancellationSignal();
        request(activity, new GetCredentialRequest.Builder().addCredentialOption(option).build(), cancel, cb);
        return cancel;
    }

    /** Forget the Google choice on this device (sign out / delete account). */
    public static void signOut(@NonNull Context context) {
        CredentialManager.create(context).clearCredentialStateAsync(
                new ClearCredentialStateRequest(),
                new CancellationSignal(),
                ContextCompat.getMainExecutor(context),
                new CredentialManagerCallback<Void, ClearCredentialException>() {
                    @Override public void onResult(Void unused) { }
                    @Override public void onError(@NonNull ClearCredentialException e) { }
                });
    }

    private static void request(Activity activity, GetCredentialRequest request, CancellationSignal cancel, Callback cb) {
        CredentialManager.create(activity).getCredentialAsync(
                activity,
                request,
                cancel,
                ContextCompat.getMainExecutor(activity),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse response) {
                        Credential c = response.getCredential();
                        if (c instanceof CustomCredential
                                && GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(c.getType())) {
                            try {
                                cb.onIdToken(GoogleIdTokenCredential.createFrom(c.getData()).getIdToken());
                            } catch (Exception e) {
                                cb.onFailed("Unreadable Google credential");
                            }
                        } else {
                            cb.onFailed("Unexpected credential type");
                        }
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException e) {
                        // Logged in full: "error returned from framework" in logcat hides the real cause.
                        Log.w(TAG, "getCredential failed: type=" + e.getType() + " class="
                                + e.getClass().getSimpleName() + " message=" + e.getMessage(), e);
                        if (e instanceof GetCredentialCancellationException && !isConfigError(e)) cb.onCancelled();
                        else if (isConfigError(e)) cb.onFailed(CONFIG_HINT + " (" + e.getMessage() + ")");
                        else if (e instanceof NoCredentialException) cb.onFailed("NoCredential: " + e.getMessage());
                        else cb.onFailed(e.getClass().getSimpleName() + ": " + e.getMessage());
                    }
                });
    }
}
