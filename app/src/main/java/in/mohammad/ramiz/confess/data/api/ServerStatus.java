package in.mohammad.ramiz.confess.data.api;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import in.mohammad.ramiz.confess.BuildConfig;
import in.mohammad.ramiz.confess.data.security.CertPins;
import okhttp3.Call;
import okhttp3.CertificatePinner;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Is the server up? Asks GET /ready (Postgres + Redis reachable) on SERVER_URL, then on
 * BACKUP_URL if one is set. Only a 200 counts as "up" — anything else (5xx, 4xx, timeout,
 * connection refused, nginx error page) means maintenance.
 *
 * Its own small client: no auth, no signing, no envelope parsing, short timeouts, so the
 * splash is never held for long. Same certificate pins as the API.
 */
public final class ServerStatus {

    public interface Callback {
        /** Main thread. */
        void onResult(boolean up);
    }

    /** Lets the caller drop the answer (e.g. the screen went away). */
    public static final class Check {
        private volatile boolean cancelled;
        @Nullable private volatile Call call;

        public void cancel() {
            cancelled = true;
            Call c = call;
            if (c != null) c.cancel();
        }
    }

    private static final long TIMEOUT_SECONDS = 4;
    private static volatile OkHttpClient client;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private ServerStatus() { }

    /**
     * No usable network on the phone at all → it's not the server's fault, so don't show
     * maintenance (the app has offline bits, e.g. downloaded books).
     */
    public static boolean isDeviceOffline(@NonNull Context context) {
        ConnectivityManager cm = context.getSystemService(ConnectivityManager.class);
        if (cm == null) return false;
        Network network = cm.getActiveNetwork();
        NetworkCapabilities caps = network == null ? null : cm.getNetworkCapabilities(network);
        return caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    @NonNull
    public static Check check(@NonNull Callback callback) {
        Check check = new Check();
        ask(check, readyUrl(BuildConfig.SERVER_URL), true, callback);
        return check;
    }

    private static void ask(Check check, @Nullable HttpUrl url, boolean tryBackupNext, Callback callback) {
        if (check.cancelled) return;
        if (url == null) {
            next(check, tryBackupNext, callback);
            return;
        }
        Request request = new Request.Builder()
                .url(url)
                .header("x-api-key", BuildConfig.CLIENT_API)
                .header("Accept", "application/json")
                .header("Cache-Control", "no-cache")
                .get()
                .build();
        Call call = http().newCall(request);
        check.call = call;
        call.enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NonNull Call c, @NonNull IOException e) {
                next(check, tryBackupNext, callback);
            }

            @Override
            public void onResponse(@NonNull Call c, @NonNull Response response) {
                boolean up = response.code() == 200;
                response.close();
                if (up) deliver(check, true, callback);
                else next(check, tryBackupNext, callback);
            }
        });
    }

    /** Primary isn't answering 200 → try the backup once; otherwise it's maintenance. */
    private static void next(Check check, boolean tryBackupNext, Callback callback) {
        HttpUrl backup = tryBackupNext ? readyUrl(BuildConfig.BACKUP_URL) : null;
        if (backup != null) ask(check, backup, false, callback);
        else deliver(check, false, callback);
    }

    private static void deliver(Check check, boolean up, Callback callback) {
        MAIN.post(() -> {
            if (!check.cancelled) callback.onResult(up);
        });
    }

    /** /ready lives at the server root, outside /api/v2. */
    @Nullable
    private static HttpUrl readyUrl(@Nullable String base) {
        if (base == null || base.trim().isEmpty()) return null;
        HttpUrl b = HttpUrl.parse(base.trim());
        if (b == null) return null;
        return b.newBuilder().encodedPath("/ready").query(null).fragment(null).build();
    }

    private static OkHttpClient http() {
        if (client == null) {
            synchronized (ServerStatus.class) {
                if (client == null) {
                    OkHttpClient.Builder b = new OkHttpClient.Builder()
                            .followRedirects(false)          // a redirect isn't "up"
                            .followSslRedirects(false)
                            .retryOnConnectionFailure(false)
                            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                            .callTimeout(TIMEOUT_SECONDS + 1, TimeUnit.SECONDS);
                    CertificatePinner pins = CertPins.pinner();
                    if (pins != null) b.certificatePinner(pins);
                    client = b.build();
                }
            }
        }
        return client;
    }
}
