package in.mohammad.ramiz.confess.data.security;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.play.core.integrity.IntegrityManager;
import com.google.android.play.core.integrity.IntegrityManagerFactory;
import com.google.android.play.core.integrity.IntegrityTokenRequest;

import java.util.concurrent.atomic.AtomicBoolean;

import in.mohammad.ramiz.confess.BuildConfig;
import in.mohammad.ramiz.confess.data.api.ApiCallback;
import in.mohammad.ramiz.confess.data.api.ApiClient;
import in.mohammad.ramiz.confess.data.api.ApiException;
import in.mohammad.ramiz.confess.data.api.Calls;
import in.mohammad.ramiz.confess.data.model.IntegrityNonce;

/**
 * Google Play Integrity (classic request) — the server decides what a verdict
 * means; the app only fetches one:
 *
 *   server nonce (single use) → Play Integrity token → sent with sign-in /
 *   registration (ClientInfo) or as X-Integrity-* headers on sensitive actions.
 *
 * Off when CLOUD_PROJECT_NUMBER isn't set in secrets.properties. Never blocks
 * the user on its own: on any failure or after 8 s it hands back nulls and the
 * server's PLAY_INTEGRITY_*_MODE decides.
 */
public final class PlayIntegrity {

    public interface Callback {
        /** Main thread. Both null when no verdict could be obtained. */
        void onResult(@Nullable String token, @Nullable String nonce);
    }

    private static final long TIMEOUT_MS = 8_000;

    private PlayIntegrity() { }

    public static boolean enabled() {
        return BuildConfig.CLOUD_PROJECT_NUMBER > 0;
    }

    public static void attest(@NonNull Context context, @NonNull Callback cb) {
        Handler main = new Handler(Looper.getMainLooper());
        if (!enabled()) {
            main.post(() -> cb.onResult(null, null));
            return;
        }
        Context app = context.getApplicationContext();
        AtomicBoolean done = new AtomicBoolean(false);
        Runnable timeout = () -> finish(done, cb, null, null);
        main.postDelayed(timeout, TIMEOUT_MS);

        Calls.enqueue(ApiClient.api().integrityNonce(), new ApiCallback<IntegrityNonce>() {
            @Override
            public void onSuccess(IntegrityNonce n) {
                if (n == null || n.nonce == null) {
                    main.removeCallbacks(timeout);
                    finish(done, cb, null, null);
                    return;
                }
                try {
                    IntegrityManager manager = IntegrityManagerFactory.create(app);
                    manager.requestIntegrityToken(IntegrityTokenRequest.builder()
                                    .setNonce(n.nonce)
                                    .setCloudProjectNumber(BuildConfig.CLOUD_PROJECT_NUMBER)
                                    .build())
                            .addOnSuccessListener(r -> {
                                main.removeCallbacks(timeout);
                                finish(done, cb, r.token(), n.nonce);
                            })
                            .addOnFailureListener(e -> {
                                main.removeCallbacks(timeout);
                                finish(done, cb, null, null);
                            });
                } catch (RuntimeException e) {
                    main.removeCallbacks(timeout);
                    finish(done, cb, null, null);
                }
            }

            @Override
            public void onError(ApiException error) {
                main.removeCallbacks(timeout);
                finish(done, cb, null, null);
            }
        });
    }

    private static void finish(AtomicBoolean done, Callback cb, @Nullable String token, @Nullable String nonce) {
        if (done.compareAndSet(false, true)) cb.onResult(token, nonce);
    }
}
