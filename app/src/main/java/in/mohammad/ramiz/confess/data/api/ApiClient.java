package in.mohammad.ramiz.confess.data.api;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLException;

import in.mohammad.ramiz.confess.BuildConfig;
import in.mohammad.ramiz.confess.data.security.AuthInterceptor;
import in.mohammad.ramiz.confess.data.security.CertPins;
import in.mohammad.ramiz.confess.data.security.SigningInterceptor;
import in.mohammad.ramiz.confess.data.security.TamperedResponseException;
import okhttp3.CertificatePinner;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;

/**
 * The single Retrofit/OkHttp stack. Interceptors, outermost first:
 *
 *   failover  — BACKUP_URL on connection errors / proxy 5xx (never on 4xx, TLS or
 *               signature failures)
 *   headers   — x-api-key (identifies the app; not a secret)
 *   auth      — Bearer access token; refreshes it before expiry / on UNAUTHORIZED
 *   signing   — X-Sig request signature + X-Resp-Sig verification
 *
 * Every client pins the API certificate (CertPins) and only trusts system CAs
 * (res/xml/network_security_config.xml). Debug builds log with credentials redacted.
 *
 * A session that can't be refreshed ends in {@link Calls} (401 → SessionEvents):
 * Google sign-in needs an Activity on Android 14+, so it's never done silently here.
 */
public final class ApiClient {

    private static volatile ConfessApi api;
    private static volatile ConfessApi uploadApi;
    private static volatile ConfessApi authApi;
    private static volatile boolean usingBackup = false;

    private ApiClient() { }

    public static ConfessApi api() {
        if (api == null) {
            synchronized (ApiClient.class) {
                if (api == null) api = build(15, true);
            }
        }
        return api;
    }

    /** Same API with patient timeouts: photo upload = re-encode + moderation + storage. */
    public static ConfessApi uploads() {
        if (uploadApi == null) {
            synchronized (ApiClient.class) {
                if (uploadApi == null) uploadApi = build(60, true);
            }
        }
        return uploadApi;
    }

    /** Without the auth interceptor — used only by TokenRefresher (no refresh recursion). */
    public static ConfessApi authApi() {
        if (authApi == null) {
            synchronized (ApiClient.class) {
                if (authApi == null) authApi = build(15, false);
            }
        }
        return authApi;
    }

    public static boolean isUsingBackup() {
        return usingBackup;
    }

    public static Gson gson() {
        return GSON;
    }

    private static final Gson GSON = new GsonBuilder().create();   // nulls are omitted

    // ── Construction ─────────────────────────────────────────────────────────

    private static ConfessApi build(int readSeconds, boolean withAuth) {
        OkHttpClient.Builder http = new OkHttpClient.Builder()
                .addInterceptor(failover)
                .addInterceptor(headers);
        if (withAuth) http.addInterceptor(new AuthInterceptor());
        http.addInterceptor(new SigningInterceptor())
                .followRedirects(false)                      // an API never redirects; a redirect is a red flag
                .followSslRedirects(false)
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(readSeconds, TimeUnit.SECONDS)
                .writeTimeout(Math.max(15, readSeconds), TimeUnit.SECONDS)
                .callTimeout(readSeconds * 2L, TimeUnit.SECONDS);

        CertificatePinner pins = CertPins.pinner();
        if (pins != null) http.certificatePinner(pins);

        if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor log = new HttpLoggingInterceptor();
            log.setLevel(HttpLoggingInterceptor.Level.HEADERS);    // bodies carry tokens
            log.redactHeader("Authorization");
            log.redactHeader("x-api-key");
            log.redactHeader("X-Sig");
            log.redactHeader("X-Integrity-Token");
            http.addNetworkInterceptor(log);
        }

        return new Retrofit.Builder()
                .baseUrl(normalise(BuildConfig.SERVER_URL))
                .client(http.build())
                .addConverterFactory(EnvelopeConverterFactory.create(GSON))
                .build()
                .create(ConfessApi.class);
    }

    /** Retrofit needs a trailing slash; secrets.properties may not have one. */
    static String normalise(String url) {
        String u = url == null ? "" : url.trim();
        if (u.isEmpty()) {
            throw new IllegalStateException("SERVER_URL is empty — set it in secrets.properties");
        }
        return u.endsWith("/") ? u : u + "/";
    }

    // ── Interceptors ─────────────────────────────────────────────────────────

    private static final Interceptor headers = chain -> {
        Request.Builder rb = chain.request().newBuilder()
                .header("x-api-key", BuildConfig.CLIENT_API)
                .header("Accept", "application/json");
        return chain.proceed(rb.build());
    };

    private static boolean hasBackup() {
        String b = BuildConfig.BACKUP_URL;
        return b != null && !b.trim().isEmpty() && HttpUrl.parse(normalise(b)) != null;
    }

    /** Primary → backup on IOException / 5xx only. Once switched, stays on backup until restart. */
    private static final Interceptor failover = new Interceptor() {
        @NonNull
        @Override
        public Response intercept(@NonNull Chain chain) throws IOException {
            Request original = chain.request();
            if (usingBackup && hasBackup()) {
                return chain.proceed(rebase(original, BuildConfig.BACKUP_URL));
            }
            try {
                Response response = chain.proceed(original);
                // Our own server answered (it tags every reply) → a real app error such as
                // MODERATION_UNAVAILABLE, not an outage: don't switch servers for that.
                boolean fromApp = response.header("X-Request-ID") != null;
                if (response.code() >= 500 && !fromApp && hasBackup()) {
                    response.close();
                    usingBackup = true;
                    return chain.proceed(rebase(original, BuildConfig.BACKUP_URL));
                }
                return response;
            } catch (IOException e) {
                // Pinning / TLS / signature failures are attacks or misconfiguration, not outages.
                if (!hasBackup() || chain.call().isCanceled() || e instanceof SSLException
                        || e instanceof TamperedResponseException) throw e;
                usingBackup = true;
                return chain.proceed(rebase(original, BuildConfig.BACKUP_URL));
            }
        }
    };

    private static Request rebase(Request request, String base) {
        HttpUrl b = HttpUrl.parse(normalise(base));
        if (b == null) return request;
        HttpUrl primary = HttpUrl.parse(normalise(BuildConfig.SERVER_URL));
        String path = request.url().encodedPath();
        if (primary != null && path.startsWith(primary.encodedPath())) {
            path = path.substring(primary.encodedPath().length());     // strip primary's base path
        } else if (path.startsWith("/")) {
            path = path.substring(1);
        }
        HttpUrl url = request.url().newBuilder()
                .scheme(b.scheme()).host(b.host()).port(b.port())
                .encodedPath(b.encodedPath() + path)
                .build();
        return request.newBuilder().url(url).build();
    }
}
