package in.mohammad.ramiz.confess.ui.entry;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import in.mohammad.ramiz.confess.BuildConfig;
import in.mohammad.ramiz.confess.R;

import in.mohammad.ramiz.confess.data.api.ApiCallback;
import in.mohammad.ramiz.confess.data.api.ApiException;
import in.mohammad.ramiz.confess.data.model.Me;
import in.mohammad.ramiz.confess.data.repo.AuthRepository;
import in.mohammad.ramiz.confess.data.repo.UserRepository;
import in.mohammad.ramiz.confess.data.security.AppSignature;
import in.mohammad.ramiz.confess.data.session.SessionManager;
import in.mohammad.ramiz.confess.ui.auth.GoogleAuth;
import in.mohammad.ramiz.confess.core.DeepLink;
import in.mohammad.ramiz.confess.ui.base.Router;

/**
 * Splash + router. No UI of its own: the system splash (ink + logo) stays up
 * while we decide where to go.
 *
 *   copy not signed by us (release) → blocking notice, nothing else runs
 *   refreshable session              → (unlock | home); the network layer refreshes tokens
 *   old-style session (older app)    → quiet Google re-sign-in to get a refreshable one
 *                                      (falls back to the current token while it's valid)
 *   no session                       → Welcome
 */
public class LauncherActivity extends AppCompatActivity {

    /** Never keep the splash longer than this, whatever the network does. */
    private static final long MAX_WAIT_MS = 8_000;

    private boolean decided = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    @Nullable private CancellationSignal googleCancel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen splash = SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        splash.setKeepOnScreenCondition(() -> !decided);
        DeepLink.capture(getIntent());            // tapped a push → open that confession once in

        if (!BuildConfig.DEBUG && !AppSignature.isGenuine(this)) {
            decided = true;
            showTampered();
            return;
        }

        SessionManager session = SessionManager.get();
        if (!session.isSignedIn() && !session.needsUpgrade()) {
            session.clear();
            finishWith(Router::toWelcome);
            return;
        }
        if (session.isRefreshable()) {
            refreshProfileQuietly();
            finishWith(Router::toApp);
            return;
        }

        // Old-style session from an earlier version: upgrade with a quiet Google re-sign-in.
        handler.postDelayed(this::fallback, MAX_WAIT_MS);
        googleCancel = GoogleAuth.silent(this, new GoogleAuth.Callback() {
            @Override
            public void onIdToken(@NonNull String idToken) {
                new AuthRepository().login(idToken, new ApiCallback<Me>() {
                    @Override
                    public void onSuccess(Me me) {
                        finishWith(Router::toApp);
                    }

                    @Override
                    public void onError(ApiException error) {
                        fallback();
                    }
                });
            }

            @Override
            public void onCancelled() {
                fallback();
            }

            @Override
            public void onFailed(@NonNull String reason) {
                fallback();
            }
        });
    }

    /** Couldn't refresh: keep going if the old token still works, else start over. */
    private void fallback() {
        if (SessionManager.get().isSignedIn()) {
            finishWith(Router::toApp);
        } else {
            SessionManager.get().clear();
            finishWith(Router::toWelcome);
        }
    }

    private void refreshProfileQuietly() {
        new UserRepository().me(new ApiCallback<Me>() {
            @Override public void onSuccess(Me data) { }    // cached by the repository
            @Override public void onError(ApiException error) { }
        });
    }

    /** Re-signed APK: no sign-in, no network — just say so and point to the real app. */
    private void showTampered() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.tampered_title)
                .setMessage(R.string.tampered_body)
                .setCancelable(false)
                .setPositiveButton(R.string.tampered_action, (d, w) -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://play.google.com/store/apps/details?id=" + getPackageName())));
                    } catch (Exception ignored) {
                        // no browser / store
                    }
                    finishAndRemoveTask();
                })
                .show();
    }

    private interface Destination {
        void go(AppCompatActivity from);
    }

    private void finishWith(Destination destination) {
        if (decided) return;
        decided = true;
        handler.removeCallbacksAndMessages(null);
        destination.go(this);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (googleCancel != null) googleCancel.cancel();
        super.onDestroy();
    }
}
