package in.mohammad.ramiz.confess.server;

import android.util.Log;

import in.mohammad.ramiz.confess.BuildConfig;
import in.mohammad.ramiz.confess.debugmonitor.TelegramLogs;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.util.concurrent.TimeUnit;

public class ServerConfigs {

    private static final String TAG = "ServerConfigs";

    private static Retrofit retrofit;
    private static final String primaryUrl = BuildConfig.SERVER_URL;
    private static final String backupUrl = BuildConfig.BACKUP_URL;

    // once switched, app stays on backup until restart
    private static boolean usingBackup = false;

    private static final Interceptor failOverInterceptor = chain -> {
        Request request = chain.request();

        // Which base are we using right now?
        String base = usingBackup ? backupUrl : primaryUrl;
        Log.d(TAG, "➡️ Using base URL: " + base + " for request: " + request.url().encodedPath());

        // Build request URL with correct base
        HttpUrl newBase = HttpUrl.parse(base);
        if (newBase == null) throw new IllegalStateException("Invalid base URL: " + base);

        HttpUrl newUrl = request.url().newBuilder()
                .scheme(newBase.scheme())
                .host(newBase.host())
                .port(newBase.port())
                .encodedPath(newBase.encodedPath() + request.url().encodedPath().replaceFirst("^/", ""))
                .build();

        Request newRequest = request.newBuilder().url(newUrl).build();

        try {
            Response response = chain.proceed(newRequest);
            Log.d(TAG, "✅ Response from " + base + " status: " + response.code());

            // If primary gives HTTP error (not network error), failover to backup
            if (!response.isSuccessful() && !usingBackup) {
                usingBackup = true;
                Log.w(TAG, "⚠️ Primary failed with code: " + response.code() + " → switching to backup.");
                sendTelegramFailoverLog(request, "HTTP " + response.code());

                return chain.proceed(rebuildWithBackup(request)); // retry transparently
            }

            return response;

        } catch (Exception e) {
            Log.e(TAG, "❌ Exception while calling " + base + ": " + e.getMessage());

            if (!usingBackup) {
                usingBackup = true;
                Log.w(TAG, "⚠️ Primary connection failed → retrying on backup.");
                sendTelegramFailoverLog(request, e.getMessage());

                try {
                    return chain.proceed(rebuildWithBackup(request)); // ✅ backup retry
                } catch (Exception ex) {
                    Log.e(TAG, "❌ Backup also failed: " + ex.getMessage());
                    throw ex; // only now let Retrofit call onFailure()
                }
            }
            throw e; // already using backup and still failed → propagate
        }
    };

    private static Request rebuildWithBackup(Request originalRequest) {
        HttpUrl backupBase = HttpUrl.parse(backupUrl);
        if (backupBase == null) throw new IllegalStateException("Invalid backup URL: " + backupUrl);

        HttpUrl newUrl = originalRequest.url().newBuilder()
                .scheme(backupBase.scheme())
                .host(backupBase.host())
                .port(backupBase.port())
                .encodedPath(backupBase.encodedPath() + originalRequest.url().encodedPath().replaceFirst("^/", ""))
                .build();

        Log.d(TAG, "🔄 Retrying request with backup URL: " + newUrl);
        return originalRequest.newBuilder().url(newUrl).build();
    }

    private static void sendTelegramFailoverLog(Request request, String cause) {
        String message = String.format(
                "⚠️ Failover triggered!\nMethod: %s\nPath: %s\nCause: %s\nNow using: %s",
                request.method(),
                request.url().encodedPath(),
                cause,
                backupUrl
        );
        try {
            TelegramLogs.sendTelegramLog(message);
        } catch (Exception ex) {
            Log.e(TAG, "Failed to send Telegram log: " + ex.getMessage());
        }
    }

    public static Retrofit getInstance() {
        if (retrofit == null) {
            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(failOverInterceptor)
                    // ⏳ Fail fast
                    .connectTimeout(2, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.SECONDS)
                    .writeTimeout(5, TimeUnit.SECONDS)
                    .callTimeout(5, TimeUnit.SECONDS) // hard cap for any call
                    .build();

            retrofit = new Retrofit.Builder()
                    .baseUrl(primaryUrl) // always start with primary
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;
    }

    public static boolean isUsingBackup() {
        return usingBackup;
    }
}
